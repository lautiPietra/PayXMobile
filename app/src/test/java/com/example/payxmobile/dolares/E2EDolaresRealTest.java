package com.example.payxmobile.dolares;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.example.payxmobile.model.CotizacionDolar;
import com.example.payxmobile.model.CrearCambioDolaresRequest;
import com.example.payxmobile.model.LoginRequest;
import com.example.payxmobile.model.LoginResponse;
import com.example.payxmobile.model.NotificacionResponse;
import com.example.payxmobile.model.OperacionCambioResponse;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.notificaciones.TitulosNotificacion;
import com.example.payxmobile.utils.MontoFormatter;

import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.BooleanSupplier;

import retrofit2.Response;

/**
 * D15 extremo a extremo contra el backend LOCAL real, usando el código de la app (no la UI).
 * Solo corre con PAYX_E2E=1. Usa la cuenta A de las pruebas de transferencias:
 * PAYX_E2E_EMAIL_A y PAYX_E2E_PASSWORD (opcional PAYX_E2E_URL). Necesita al menos
 * $ 2.000 en pesos. Mueve plata simulada: compra y vuelve a vender lo comprado.
 * Dosificado: 2 GET cotización y 5 POST (límite 15/min).
 */
public class E2EDolaresRealTest {

    private static final String URL = env("PAYX_E2E_URL", "http://localhost:8080/");
    private static final BigDecimal PESOS_A_COMPRAR = new BigDecimal("1500.00");

    private static ApiService api, apiSinReintentos;

    @BeforeClass
    public static void login() throws Exception {
        assumeTrue("Definí PAYX_E2E=1 para correr contra el backend real", System.getenv("PAYX_E2E") != null);
        String email = requerida("PAYX_E2E_EMAIL_A");
        String password = requerida("PAYX_E2E_PASSWORD");
        ApiService sinToken = RetrofitClient.crear(URL, () -> null, () -> {}, false);
        Response<LoginResponse> r = sinToken.login(new LoginRequest(email, password)).execute();
        assertTrue("login -> " + r.code(), r.isSuccessful());
        String token = r.body().getToken();
        api = RetrofitClient.crear(URL, () -> token, () -> {}, false, true);
        apiSinReintentos = RetrofitClient.crear(URL, () -> token, () -> {}, false, false);
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

    /** Opera con la MISMA clase que usa la pantalla (cotización + validación + POST). */
    private static CambioDolares operar(CambioDolares.Tipo tipo, String monto, BigDecimal saldo) throws Exception {
        CambioDolares c = new CambioDolares(tipo, () -> api, () -> apiSinReintentos, () -> {});
        c.cargarCotizacion();
        esperar("cotización", () -> c.getCotizacion() != null || c.getErrorCotizacion() != null);
        assertNotNull("cotización real: " + c.getErrorCotizacion(), c.getCotizacion());
        assertTrue(c.setMonto(monto));
        c.continuar(saldo);
        c.confirmar();
        esperar("fin de la operación", () -> !c.isEnviando() && (c.getPaso() != CambioDolares.Paso.FORMULARIO || c.getError() != null));
        return c;
    }

    private static String errorDe(String tipo, String monto) throws Exception {
        Response<OperacionCambioResponse> r = apiSinReintentos
                .crearCambioDolares(new CrearCambioDolaresRequest(tipo, new BigDecimal(monto))).execute();
        assertEquals("debe rechazar " + tipo + " " + monto, 400, r.code());
        return ApiErrores.mensaje(r);
    }

    private static long sinLeer() throws Exception {
        return api.contarSinLeer().execute().body().getCantidad();
    }

    @Test
    public void d15_comprarYVenderCuadraConPerfilYNotifica() throws Exception {
        PerfilResponse antes = perfil();
        assumeTrue("La cuenta A necesita al menos $ 2.000", antes.getSaldoPesos().compareTo(new BigDecimal("2000")) >= 0);
        long sinLeerAntes = sinLeer();

        // ── COMPRA ──
        CambioDolares compra = operar(CambioDolares.Tipo.COMPRA, "1500", antes.getSaldoPesos());
        assertEquals("error: " + compra.getError(), CambioDolares.Paso.EXITO, compra.getPaso());
        OperacionCambioResponse rc = compra.getResultado();
        CotizacionDolar vista = compra.getCotizacion();
        System.out.println("COMPRA -> montoUsd=" + rc.getMontoUsd() + " montoPesos=" + rc.getMontoPesos()
                + " cotizacion=" + rc.getCotizacion() + " (mostrada: venta=" + vista.getVenta() + ", compra=" + vista.getCompra() + ")");
        assertEquals("COMPRA", rc.getTipo());
        assertEquals(0, PESOS_A_COMPRAR.compareTo(rc.getMontoPesos()));
        assertEquals("montoUsd = pesos / cotización, 2 decimales HALF_UP",
                PESOS_A_COMPRAR.divide(rc.getCotizacion(), 2, RoundingMode.HALF_UP), rc.getMontoUsd());

        PerfilResponse trasCompra = perfil();
        assertEquals("pesos bajan EXACTO lo pedido", antes.getSaldoPesos().subtract(PESOS_A_COMPRAR), trasCompra.getSaldoPesos());
        assertEquals("dólares suben EXACTO el montoUsd del backend", antes.getSaldoUsd().add(rc.getMontoUsd()), trasCompra.getSaldoUsd());

        // ── VENTA de lo comprado ──
        String usd = rc.getMontoUsd().toPlainString().replace('.', ',');
        CambioDolares venta = operar(CambioDolares.Tipo.VENTA, usd, trasCompra.getSaldoUsd());
        assertEquals("error: " + venta.getError(), CambioDolares.Paso.EXITO, venta.getPaso());
        OperacionCambioResponse rv = venta.getResultado();
        System.out.println("VENTA  -> montoUsd=" + rv.getMontoUsd() + " montoPesos=" + rv.getMontoPesos() + " cotizacion=" + rv.getCotizacion());
        assertEquals("VENTA", rv.getTipo());
        assertEquals(rc.getMontoUsd(), rv.getMontoUsd());
        assertEquals("montoPesos = usd × cotización, 2 decimales HALF_UP",
                rv.getMontoUsd().multiply(rv.getCotizacion()).setScale(2, RoundingMode.HALF_UP), rv.getMontoPesos());

        PerfilResponse despues = perfil();
        assertEquals("dólares vuelven al inicio", antes.getSaldoUsd(), despues.getSaldoUsd());
        assertEquals("pesos: - lo pagado + lo recibido",
                antes.getSaldoPesos().subtract(rc.getMontoPesos()).add(rv.getMontoPesos()), despues.getSaldoPesos());
        System.out.println("Spread pagado: $ " + MontoFormatter.fiat(rc.getMontoPesos().subtract(rv.getMontoPesos())));

        // ── Historial: las dos, más recientes primero ──
        List<OperacionCambioResponse> historial = api.listarCambiosDolares().execute().body();
        assertEquals(rv.getId(), historial.get(0).getId());
        assertEquals(rc.getId(), historial.get(1).getId());

        // ── Campana: "Venta de dólares" y "Compra de dólares" con el monto correcto ──
        assertTrue("sin-leer subió", sinLeer() >= sinLeerAntes + 2);
        List<NotificacionResponse> notifs = api.obtenerNotificaciones().execute().body();
        NotificacionResponse nv = notifs.get(0), nc = notifs.get(1);
        System.out.println("Campana: [" + TitulosNotificacion.de(nv.getPlantillaCodigo()) + "] " + nv.getMensaje());
        System.out.println("Campana: [" + TitulosNotificacion.de(nc.getPlantillaCodigo()) + "] " + nc.getMensaje());
        assertEquals("DOLARES_VENDIDOS", nv.getPlantillaCodigo());
        assertEquals("DOLARES_COMPRADOS", nc.getPlantillaCodigo());
        assertEquals("Venta de dólares", TitulosNotificacion.de(nv.getPlantillaCodigo()));
        assertEquals("Compra de dólares", TitulosNotificacion.de(nc.getPlantillaCodigo()));
        String usdTexto = MontoFormatter.fiat(rc.getMontoUsd());
        assertTrue("la notificación de compra menciona el monto: " + nc.getMensaje(),
                nc.getMensaje().contains(usdTexto) || nc.getMensaje().contains(MontoFormatter.fiat(rc.getMontoPesos())));
        assertTrue("la notificación de venta menciona el monto: " + nv.getMensaje(),
                nv.getMensaje().contains(usdTexto) || nv.getMensaje().contains(MontoFormatter.fiat(rv.getMontoPesos())));
    }

    @Test
    public void d12_textosRealesDelBackend() throws Exception {
        PerfilResponse p = perfil();
        String masQueLosPesos = p.getSaldoPesos().add(new BigDecimal("1000")).setScale(2, RoundingMode.DOWN).toPlainString();
        String masQueLosDolares = p.getSaldoUsd().add(new BigDecimal("1000")).setScale(2, RoundingMode.DOWN).toPlainString();
        assertEquals("No tenes saldo en pesos suficiente para esta compra", errorDe("COMPRA", masQueLosPesos));
        assertEquals("No tenes dolares suficientes para esta venta", errorDe("VENTA", masQueLosDolares));
        assertEquals("El monto ingresado es muy bajo: a esta cotizacion no alcanza para comprar ni un centavo de dolar",
                errorDe("COMPRA", "0.01"));
        // "El monto ingresado es muy bajo para esta cotizacion" (VENTA) no se puede provocar con una
        // cotización real: 0,01 US$ × ~1.400 ya son ~$ 14. Queda cubierto con MockWebServer.
    }
}
