package com.example.payxmobile.cripto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.example.payxmobile.model.CotizacionCripto;
import com.example.payxmobile.model.CrearOperacionCriptoRequest;
import com.example.payxmobile.model.LoginRequest;
import com.example.payxmobile.model.LoginResponse;
import com.example.payxmobile.model.NotificacionResponse;
import com.example.payxmobile.model.OperacionCriptoResponse;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.notificaciones.TitulosNotificacion;
import com.example.payxmobile.transferencias.Moneda;
import com.example.payxmobile.utils.MontoFormatter;

import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import retrofit2.Response;

/**
 * C10 extremo a extremo contra el backend LOCAL real, con el código de la app (no la UI).
 * Solo corre con PAYX_E2E=1 (PAYX_E2E_EMAIL_A, PAYX_E2E_PASSWORD; opcional PAYX_E2E_URL).
 * Compra $ 1.000 de cada una de las 6 cripto y vende exactamente lo comprado. Necesita $ 7.000.
 * Límite del backend: 15 POST/min -> 13 POST, pausa de 61 s y 4 POST de errores.
 */
public class E2ECriptoRealTest {

    private static final String URL = env("PAYX_E2E_URL", "http://localhost:8080/");
    private static final BigDecimal PESOS = new BigDecimal("1000.00");
    private static final Moneda[] CRIPTOS = {Moneda.BTC, Moneda.ETH, Moneda.SOL, Moneda.USDT, Moneda.BNB, Moneda.XRP};

    private static ApiService api, apiSinReintentos;

    @BeforeClass
    public static void login() throws Exception {
        assumeTrue("Definí PAYX_E2E=1 para correr contra el backend real", System.getenv("PAYX_E2E") != null);
        String email = requerida("PAYX_E2E_EMAIL_A");
        String password = requerida("PAYX_E2E_PASSWORD");
        ApiService sinToken = RetrofitClient.crear(URL, () -> null, System::currentTimeMillis, () -> {}, false);
        Response<LoginResponse> r = sinToken.login(new LoginRequest(email, password)).execute();
        assertTrue("login -> " + r.code(), r.isSuccessful());
        String token = r.body().getToken();
        api = RetrofitClient.crear(URL, () -> token, System::currentTimeMillis, () -> {}, false, true);
        apiSinReintentos = RetrofitClient.crear(URL, () -> token, System::currentTimeMillis, () -> {}, false, false);
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

    private static PerfilResponse perfil() throws Exception {
        Response<PerfilResponse> r = api.obtenerPerfil().execute();
        assertTrue("perfil -> " + r.code(), r.isSuccessful());
        return r.body();
    }

    private static Map<String, CotizacionCripto> cotizaciones() throws Exception {
        Response<List<CotizacionCripto>> r = api.obtenerCotizacionesCripto().execute();
        assertTrue("cotizaciones -> " + r.code(), r.isSuccessful());
        Map<String, CotizacionCripto> m = new LinkedHashMap<>();
        for (CotizacionCripto c : r.body()) m.put(c.getSimbolo(), c);
        return m;
    }

    /** Con la MISMA clase que usa la pantalla: validación + resumen + confirmar. */
    private static OperacionCripto operar(OperacionCripto.Tipo tipo, Moneda cripto, String monto,
                                          Map<String, CotizacionCripto> cot, PerfilResponse p) throws Exception {
        OperacionCripto op = new OperacionCripto(tipo, () -> apiSinReintentos, () -> {});
        op.setCotizaciones(cot, false);
        op.setCripto(cripto);
        assertTrue(op.setMonto(monto));
        op.continuar(OperacionCripto.saldoDisponible(tipo, cripto, p));
        assertEquals("validación local: " + op.getError(), OperacionCripto.Paso.CONFIRMAR, op.getPaso());
        op.confirmar();
        esperar("fin del POST", () -> !op.isEnviando());
        return op;
    }

    private static String errorDe(String tipo, String simbolo, String monto) throws Exception {
        Response<OperacionCriptoResponse> r = apiSinReintentos
                .crearOperacionCripto(new CrearOperacionCriptoRequest(tipo, simbolo, new BigDecimal(monto))).execute();
        assertEquals("debe rechazar " + tipo + " " + simbolo + " " + monto, 400, r.code());
        return ApiErrores.mensaje(r);
    }

    @Test
    public void c10_comprarYVenderLasSeisCuadraConPerfilYNotifica() throws Exception {
        Map<String, CotizacionCripto> cot = cotizaciones();
        assertEquals("orden del backend", "[BTC, ETH, SOL, USDT, BNB, XRP]", cot.keySet().toString());
        PerfilResponse inicio = perfil();
        assumeTrue("La cuenta A necesita al menos $ 7.000", inicio.getSaldoPesos().compareTo(new BigDecimal("7000")) >= 0);
        long sinLeerAntes = api.contarSinLeer().execute().body().getCantidad();
        List<String> ids = new ArrayList<>();
        String bajoVenta = null;

        for (Moneda m : CRIPTOS) {
            PerfilResponse antes = perfil();
            // ── COMPRA de $ 1.000 ──
            OperacionCripto compra = operar(OperacionCripto.Tipo.COMPRA, m, "1000", cot, antes);
            assertEquals(m + " compra: " + compra.getError(), OperacionCripto.Paso.EXITO, compra.getPaso());
            OperacionCriptoResponse rc = compra.getResultado();
            assertEquals(m.codigo(), rc.getSimbolo());
            assertEquals("montoCripto = pesos / precio, 8 decimales HACIA ABAJO",
                    0, PESOS.divide(rc.getCotizacion(), 8, RoundingMode.DOWN).compareTo(rc.getMontoCripto()));
            PerfilResponse trasCompra = perfil();
            assertEquals(m + ": pesos bajan exacto", 0, antes.getSaldoPesos().subtract(PESOS).compareTo(trasCompra.getSaldoPesos()));
            assertEquals(m + ": suma exacto lo del backend", 0,
                    antes.getSaldoCripto(m.codigo()).add(rc.getMontoCripto()).compareTo(trasCompra.getSaldoCripto(m.codigo())));
            ids.add(rc.getId());

            // XRP: "monto muy bajo" en la venta (0,00000001 × ~$ 2.300 = $ 0,00002 -> 0,00)
            if (m == Moneda.XRP) {
                OperacionCripto baja = operar(OperacionCripto.Tipo.VENTA, m, "0,00000001", cot, trasCompra);
                bajoVenta = baja.getError();
            }

            // ── VENTA de exactamente lo comprado ──
            String cant = MontoFormatter.cripto(rc.getMontoCripto()).replace(".", "");
            OperacionCripto venta = operar(OperacionCripto.Tipo.VENTA, m, cant, cot, trasCompra);
            assertEquals(m + " venta: " + venta.getError(), OperacionCripto.Paso.EXITO, venta.getPaso());
            OperacionCriptoResponse rv = venta.getResultado();
            assertEquals(0, rc.getMontoCripto().compareTo(rv.getMontoCripto()));
            assertEquals("montoPesos = cantidad × precio, 2 decimales HACIA ABAJO",
                    0, rv.getMontoCripto().multiply(rv.getCotizacion()).setScale(2, RoundingMode.DOWN).compareTo(rv.getMontoPesos()));
            PerfilResponse despues = perfil();
            assertEquals(m + ": la cripto vuelve al inicio", 0,
                    antes.getSaldoCripto(m.codigo()).compareTo(despues.getSaldoCripto(m.codigo())));
            assertEquals(m + ": pesos = - pagado + recibido", 0,
                    antes.getSaldoPesos().subtract(rc.getMontoPesos()).add(rv.getMontoPesos()).compareTo(despues.getSaldoPesos()));
            assertTrue("ida y vuelta nunca devuelve más de lo puesto", rv.getMontoPesos().compareTo(PESOS) <= 0);
            ids.add(rv.getId());
            System.out.println(m.codigo() + ": compra " + MontoFormatter.cripto(rc.getMontoCripto()) + " a $ "
                    + MontoFormatter.fiat(rc.getCotizacion()) + " | venta -> $ " + MontoFormatter.fiat(rv.getMontoPesos())
                    + " | ida y vuelta: -$ " + MontoFormatter.fiat(PESOS.subtract(rv.getMontoPesos())));
        }
        assertEquals("El monto ingresado es muy bajo para esta cotizacion", bajoVenta);

        // Historial: las 12, más recientes primero
        List<OperacionCriptoResponse> historial = api.listarOperacionesCripto().execute().body();
        for (int i = 0; i < ids.size(); i++) {
            assertEquals("historial " + i, ids.get(ids.size() - 1 - i), historial.get(i).getId());
        }

        // Campana: 12 nuevas con el título correcto y el símbolo en el mensaje
        assertTrue(api.contarSinLeer().execute().body().getCantidad() >= sinLeerAntes + 12);
        List<NotificacionResponse> notifs = api.obtenerNotificaciones().execute().body();
        for (int i = 0; i < 12; i++) {
            NotificacionResponse n = notifs.get(i);
            Moneda m = CRIPTOS[CRIPTOS.length - 1 - i / 2];
            boolean esVenta = i % 2 == 0;
            assertEquals(esVenta ? "CRIPTO_VENDIDA" : "CRIPTO_COMPRADA", n.getPlantillaCodigo());
            assertEquals(esVenta ? "Venta de cripto" : "Compra de cripto", TitulosNotificacion.de(n.getPlantillaCodigo()));
            assertTrue("menciona " + m + ": " + n.getMensaje(), n.getMensaje().contains(m.codigo()));
            if (i < 2) System.out.println("Campana: [" + TitulosNotificacion.de(n.getPlantillaCodigo()) + "] " + n.getMensaje());
        }

        // ── Errores reales (tras la ventana del límite de 15/min) ──
        Thread.sleep(61_000);
        PerfilResponse p = perfil();
        String masQueLosPesos = p.getSaldoPesos().add(new BigDecimal("1000")).setScale(2, RoundingMode.DOWN).toPlainString();
        assertEquals("No tenes saldo en pesos suficiente para esta compra", errorDe("COMPRA", "BTC", masQueLosPesos));
        String masQueEth = p.getSaldoEth().add(new BigDecimal("1000")).toPlainString();
        assertEquals("No tenes suficiente ETH para esta venta", errorDe("VENTA", "ETH", masQueEth));
        assertEquals("El monto ingresado es muy bajo: a esta cotizacion no alcanza para comprar nada de BTC",
                errorDe("COMPRA", "BTC", "0.01"));
        Response<OperacionCriptoResponse> invalido = apiSinReintentos
                .crearOperacionCripto(new CrearOperacionCriptoRequest("COMPRA", "DOGE", new BigDecimal("10"))).execute();
        System.out.println("Símbolo inválido (DOGE) -> HTTP " + invalido.code() + " / " + ApiErrores.mensaje(invalido));
        assertTrue("el backend lo rechaza", invalido.code() == 400 || invalido.code() == 403);
    }
}
