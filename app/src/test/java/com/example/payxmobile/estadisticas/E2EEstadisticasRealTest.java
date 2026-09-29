package com.example.payxmobile.estadisticas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.example.payxmobile.model.CategoriaGastoResponse;
import com.example.payxmobile.model.CrearTransferenciaRequest;
import com.example.payxmobile.model.EstadisticaGastosResponse;
import com.example.payxmobile.model.LoginRequest;
import com.example.payxmobile.model.LoginResponse;
import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.utils.MontoFormatter;

import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.BooleanSupplier;

import retrofit2.Response;

/**
 * E9 contra el backend LOCAL real. Solo corre con PAYX_E2E=1 y las cuentas A y B de las pruebas de
 * transferencias: PAYX_E2E_EMAIL_A, PAYX_E2E_EMAIL_B, PAYX_E2E_PASSWORD, PAYX_E2E_ALIAS_A,
 * PAYX_E2E_ALIAS_B (opcional PAYX_E2E_URL). Arma a propósito, HOY, en la cuenta A:
 * - una transferencia ENVIADA, DIRECTA, en PESOS ($ 123,45)  -> SÍ cuenta
 * - una PENDIENTE que después se cancela ($ 50)               -> NO cuenta
 * - una RECIBIDA de B ($ 10)                                  -> NO cuenta
 * y compara las estadísticas de "hoy" (dias=1) antes/después, con la clase de la pantalla y con la
 * llamada directa al endpoint. Mueve $ 113,45 netos de A a B.
 */
public class E2EEstadisticasRealTest {

    private static final String URL = env("PAYX_E2E_URL", "http://localhost:8080/");
    private static final BigDecimal ENVIADA = new BigDecimal("123.45");

    private static ApiService apiA, apiASinReintentos, apiBSinReintentos;
    private static String aliasA, aliasB;

    @BeforeClass
    public static void login() throws Exception {
        assumeTrue("Definí PAYX_E2E=1 para correr contra el backend real", System.getenv("PAYX_E2E") != null);
        String emailA = requerida("PAYX_E2E_EMAIL_A");
        String emailB = requerida("PAYX_E2E_EMAIL_B");
        String password = requerida("PAYX_E2E_PASSWORD");
        aliasA = requerida("PAYX_E2E_ALIAS_A");
        aliasB = requerida("PAYX_E2E_ALIAS_B");
        String jwtA = token(emailA, password), jwtB = token(emailB, password);
        apiA = RetrofitClient.crear(URL, () -> jwtA, () -> {}, false, true);
        apiASinReintentos = RetrofitClient.crear(URL, () -> jwtA, () -> {}, false, false);
        apiBSinReintentos = RetrofitClient.crear(URL, () -> jwtB, () -> {}, false, false);
    }

    private static String token(String email, String password) throws Exception {
        ApiService sinToken = RetrofitClient.crear(URL, () -> null, () -> {}, false);
        Response<LoginResponse> r = sinToken.login(new LoginRequest(email, password)).execute();
        assertTrue("login -> " + r.code(), r.isSuccessful());
        return r.body().getToken();
    }

    private static String env(String nombre, String porDefecto) {
        String v = System.getenv(nombre);
        return v != null && !v.isEmpty() ? v : porDefecto;
    }

    private static String requerida(String nombre) {
        String v = System.getenv(nombre);
        assumeTrue("Falta la variable de entorno " + nombre, v != null && !v.isEmpty());
        return v;
    }

    private static void esperar(String que, BooleanSupplier c) throws InterruptedException {
        long fin = System.currentTimeMillis() + 15000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) throw new AssertionError("Timeout: " + que);
            Thread.sleep(20);
        }
    }

    /** "Hoy" (dias=1) directo contra el endpoint, como un curl. */
    private static EstadisticaGastosResponse hoyDirecto() throws Exception {
        Response<EstadisticaGastosResponse> r = apiA.obtenerEstadisticaGastos(1).execute();
        assertTrue("estadísticas -> " + r.code(), r.isSuccessful());
        return r.body();
    }

    private static BigDecimal categoria(EstadisticaGastosResponse e, String codigo) {
        for (CategoriaGastoResponse c : e.getCategorias()) if (codigo.equals(c.getCodigo())) return c.getMonto();
        throw new AssertionError("falta la categoría " + codigo);
    }

    private static TransferenciaResponse transferir(ApiService api, String a, String monto, String tipo) throws Exception {
        Response<TransferenciaResponse> r = api.crearTransferencia(
                new CrearTransferenciaRequest(a, "PESOS", new BigDecimal(monto), "E2E estadísticas", tipo)).execute();
        assertTrue("transferencia " + tipo + " -> " + r.code(), r.isSuccessful());
        return r.body();
    }

    @Test
    public void e9_soloCuentanLasEnviadasCompletadasEnPesos() throws Exception {
        EstadisticaGastosResponse antes = hoyDirecto();
        assertEquals("las 5 categorías, siempre", 5, antes.getCategorias().size());

        transferir(apiASinReintentos, aliasB, ENVIADA.toPlainString(), "DIRECTA");              // cuenta
        TransferenciaResponse pendiente = transferir(apiASinReintentos, aliasB, "50", "PENDIENTE");
        assertTrue(apiASinReintentos.cancelarTransferencia(pendiente.getId()).execute().isSuccessful()); // no cuenta
        transferir(apiBSinReintentos, aliasA, "10", "DIRECTA");                                   // recibida: no cuenta

        EstadisticaGastosResponse despues = hoyDirecto();
        System.out.println("HOY antes:   total $ " + MontoFormatter.fiat(antes.getTotalGastado()) + " | ops " + antes.getCantidadOperaciones());
        System.out.println("HOY después: total $ " + MontoFormatter.fiat(despues.getTotalGastado()) + " | ops " + despues.getCantidadOperaciones());
        assertEquals("sube SOLO lo enviado y completado", 0,
                antes.getTotalGastado().add(ENVIADA).compareTo(despues.getTotalGastado()));
        assertEquals(antes.getCantidadOperaciones() + 1, despues.getCantidadOperaciones());
        assertEquals(0, categoria(antes, "TRANSFERENCIAS").add(ENVIADA).compareTo(categoria(despues, "TRANSFERENCIAS")));
        assertEquals("hasta = hoy", despues.getHasta(), despues.getDesde());

        // La clase de la pantalla ve exactamente lo mismo que la llamada directa
        CargaEstadisticas carga = new CargaEstadisticas(() -> apiA, () -> LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")));
        carga.seleccionar(PeriodoEstadistica.SIETE_DIAS);
        esperar("app", () -> carga.getEstado() != CargaEstadisticas.Estado.CARGANDO);
        EstadisticaGastosResponse app = carga.getDatos();
        assertNotNull("error: " + carga.getError(), app);
        EstadisticaGastosResponse directo7 = apiA.obtenerEstadisticaGastos(7).execute().body();
        assertEquals(0, directo7.getTotalGastado().compareTo(app.getTotalGastado()));
        assertEquals(directo7.getCantidadOperaciones(), app.getCantidadOperaciones());
        VistaEstadisticas v = VistaEstadisticas.de(app);
        System.out.println("APP 7 días: " + v.rango + " | " + v.total + " | " + v.operaciones
                + (v.comparacion != null ? " | " + v.comparacion.texto : ""));
        for (VistaEstadisticas.Categoria c : v.categorias) System.out.println("  " + c.etiqueta + " " + c.monto + " " + c.porcentaje);
        assertEquals(7, app.getPorDia().size());

        // "Todo el tiempo": desde y anterior null, hasta = hoy
        EstadisticaGastosResponse todo = apiA.obtenerEstadisticaGastos(0).execute().body();
        assertEquals(null, todo.getDesde());
        assertEquals(null, todo.getTotalPeriodoAnterior());
        assertNotNull(todo.getHasta());
        // Sin parámetro el backend usa 30 (por eso la app manda siempre "dias")
        EstadisticaGastosResponse treinta = apiA.obtenerEstadisticaGastos(30).execute().body();
        assertEquals(LocalDate.parse(treinta.getHasta()).minusDays(29).toString(), treinta.getDesde());
    }
}
