package com.example.payxmobile.dolares;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.JwtFalso;
import com.example.payxmobile.model.CotizacionDolar;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.utils.MontoFormatter;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

/**
 * Compra/venta de dólares: reglas puras con cotización fija (D8, D9) y contrato HTTP contra un
 * backend simulado (D8 503, D10-D14).
 */
public class CambioDolaresTest {

    // Asimétrica a propósito: si se confunden "compra" y "venta" los números no dan.
    static final String COTIZACION = "{\"compra\":1385.00,\"venta\":1435.00,"
            + "\"fechaActualizacion\":\"2026-09-27T13:00:00Z\",\"desactualizada\":false}";

    static String operacion(String tipo, String usd, String pesos, String cotizacion) {
        return "{\"id\":\"0b7c1d2e-3f40-4a5b-8c6d-7e8f90a1b2c3\",\"tipo\":\"" + tipo + "\",\"montoUsd\":" + usd
                + ",\"montoPesos\":" + pesos + ",\"cotizacion\":" + cotizacion
                + ",\"fecha\":\"2026-09-27T13:15:30.123456-03:00\"}";
    }

    private static final BigDecimal SALDO_PESOS = new BigDecimal("100000.00");
    private static final BigDecimal SALDO_USD = new BigDecimal("50.00");

    private MockWebServer server;
    private ApiService api, apiSinReintentos;
    private final AtomicInteger refrescos = new AtomicInteger();

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String token = JwtFalso.conExp(System.currentTimeMillis() / 1000 + 7200);
        String url = server.url("/").toString();
        api = RetrofitClient.crear(url, () -> token, () -> {}, false, true);
        apiSinReintentos = RetrofitClient.crear(url, () -> token, () -> {}, false, false);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private CambioDolares nuevo(CambioDolares.Tipo tipo) {
        return new CambioDolares(tipo, () -> api, () -> apiSinReintentos, refrescos::incrementAndGet);
    }

    private static CotizacionDolar cotizacion(String json) {
        return new Gson().fromJson(json, CotizacionDolar.class);
    }

    private static void esperar(String que, BooleanSupplier c) throws InterruptedException {
        long fin = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) throw new AssertionError("Timeout: " + que);
            Thread.sleep(10);
        }
    }

    /** Carga la cotización (1 GET) y deja el monto tipeado. */
    private CambioDolares listo(CambioDolares.Tipo tipo, String monto) throws Exception {
        CambioDolares c = nuevo(tipo);
        server.enqueue(new MockResponse().setBody(COTIZACION));
        c.cargarCotizacion();
        esperar("cotización", () -> c.getCotizacion() != null);
        server.takeRequest(1, TimeUnit.SECONDS);
        assertTrue(c.setMonto(monto));
        return c;
    }

    // ── D8: cotización y qué precio usa cada operación ───────────────────────

    @Test
    public void d8_compraUsaVentaYVentaUsaCompra() {
        CotizacionDolar c = cotizacion(COTIZACION);
        assertEquals("COMPRA paga el precio de VENTA", new BigDecimal("1435.00"), CambioDolares.precioPara(CambioDolares.Tipo.COMPRA, c));
        assertEquals("VENTA recibe el precio de COMPRA", new BigDecimal("1385.00"), CambioDolares.precioPara(CambioDolares.Tipo.VENTA, c));

        // Preview con el precio correcto (si estuvieran cruzados daría 7,22 y 14.350,00).
        // 10.000 / 1.435 = 6,9686... -> 6,96 (DOWN, como el backend)
        assertEquals(new BigDecimal("6.96"), CambioDolares.preview(CambioDolares.Tipo.COMPRA, new BigDecimal("10000"),
                CambioDolares.precioPara(CambioDolares.Tipo.COMPRA, c)));
        assertEquals(new BigDecimal("13850.00"), CambioDolares.preview(CambioDolares.Tipo.VENTA, new BigDecimal("10"),
                CambioDolares.precioPara(CambioDolares.Tipo.VENTA, c)));
    }

    @Test
    public void d8_previewRedondeaComoElBackend() {
        BigDecimal venta = new BigDecimal("1435.00");
        // Lo que recibe el usuario, SIEMPRE hacia abajo (DOWN a 2 decimales): con HALF_UP comprar y vender
        // centavos daba ganancia gratis. 1000 / 1435 = 0,696864... -> 0,69 (HALF_UP daba 0,70)
        assertEquals(new BigDecimal("0.69"), CambioDolares.preview(CambioDolares.Tipo.COMPRA, new BigDecimal("1000"), venta));
        // 0,015 × 1385 = 20,775 -> 20,77 (HALF_UP daba 20,78)
        assertEquals(new BigDecimal("20.77"), CambioDolares.preview(CambioDolares.Tipo.VENTA, new BigDecimal("0.015"), new BigDecimal("1385")));
        // Sin monto o sin precio: no hay preview (nunca un 0 inventado)
        assertNull(CambioDolares.preview(CambioDolares.Tipo.COMPRA, null, venta));
        assertNull(CambioDolares.preview(CambioDolares.Tipo.COMPRA, BigDecimal.ZERO, venta));
        assertNull(CambioDolares.preview(CambioDolares.Tipo.COMPRA, BigDecimal.TEN, null));
        assertNull(CambioDolares.precioPara(CambioDolares.Tipo.COMPRA, null));
        assertNull("precio 0 no sirve", CambioDolares.precioPara(CambioDolares.Tipo.COMPRA,
                cotizacion("{\"compra\":0,\"venta\":0,\"desactualizada\":false}")));
    }

    @Test
    public void d8_getMapeadoContraElServidor() throws Exception {
        CambioDolares c = nuevo(CambioDolares.Tipo.COMPRA);
        server.enqueue(new MockResponse().setBody("{\"compra\":1385.5,\"venta\":1435.25,"
                + "\"fechaActualizacion\":\"2026-09-27T12:59:01.5-03:00\",\"desactualizada\":true}"));
        c.cargarCotizacion();
        esperar("cotización", () -> c.getCotizacion() != null);
        RecordedRequest r = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", r.getMethod());
        assertEquals("/api/cotizacion/dolar", r.getPath());
        assertTrue(r.getHeader("Authorization").startsWith("Bearer "));
        CotizacionDolar cot = c.getCotizacion();
        assertEquals(new BigDecimal("1385.5"), cot.getCompra());
        assertEquals(new BigDecimal("1435.25"), cot.getVenta());
        assertEquals("2026-09-27T12:59:01.5-03:00", cot.getFechaActualizacion());
        assertTrue(cot.isDesactualizada());
        assertEquals(new BigDecimal("1435.25"), c.getPrecioAplicado());
    }

    @Test
    public void d8_503OcultaElPreviewYNoMuestraCero() throws Exception {
        CambioDolares c = nuevo(CambioDolares.Tipo.COMPRA);
        c.setMonto("5000");
        server.enqueue(new MockResponse().setResponseCode(503)
                .setBody("{\"error\":\"No se pudo obtener la cotizacion del dolar en este momento. Intenta de nuevo en unos segundos\"}"));
        c.cargarCotizacion();
        esperar("error de cotización", () -> c.getErrorCotizacion() != null);
        assertNull(c.getCotizacion());
        assertNull("sin preview", c.getPreview());
        assertNull(c.getPrecioAplicado());
        assertEquals(CambioDolares.MSG_ERROR_COTIZACION, c.getErrorCotizacion());

        // Sin cotización no se opera (como la web): ningún POST
        c.continuar(SALDO_PESOS);
        assertEquals(CambioDolares.MSG_SIN_COTIZACION, c.getError());
        Thread.sleep(100);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void d8_503DespuesDeUnaBuenaLaDescartaPeroUnCorteDeRedLaMantiene() throws Exception {
        CambioDolares c = listo(CambioDolares.Tipo.COMPRA, "5000");
        assertNotNull(c.getPreview());

        // Límite de pedidos (429) o 500: se mantiene la última buena
        server.enqueue(new MockResponse().setResponseCode(429));
        c.cargarCotizacion();
        esperar("429", () -> server.getRequestCount() >= 2);
        Thread.sleep(200);
        server.enqueue(new MockResponse().setResponseCode(500));
        c.cargarCotizacion();
        esperar("500", () -> server.getRequestCount() >= 3);
        Thread.sleep(200);
        assertNotNull(c.getCotizacion());
        assertNull(c.getErrorCotizacion());
        assertNotNull(c.getPreview());

        // 503 explícito: el backend ya no tiene ninguna -> sin preview
        server.enqueue(new MockResponse().setResponseCode(503));
        int antes = server.getRequestCount();
        c.cargarCotizacion();
        esperar("503", () -> c.getCotizacion() == null);
        assertTrue(server.getRequestCount() > antes);
        assertNull(c.getPreview());
    }

    // ── D9: validaciones locales, sin request ────────────────────────────────

    @Test
    public void d9_validacionesLocales() {
        BigDecimal precio = new BigDecimal("1435");
        CambioDolares.Tipo C = CambioDolares.Tipo.COMPRA, V = CambioDolares.Tipo.VENTA;
        assertEquals(CambioDolares.MSG_MONTO_INVALIDO, CambioDolares.validar(C, "", SALDO_PESOS, precio));
        assertEquals(CambioDolares.MSG_MONTO_INVALIDO, CambioDolares.validar(C, "0", SALDO_PESOS, precio));
        assertEquals(CambioDolares.MSG_MONTO_INVALIDO, CambioDolares.validar(C, "0,00", SALDO_PESOS, precio));
        assertEquals(CambioDolares.MSG_MONTO_INVALIDO, CambioDolares.validar(C, "-5", SALDO_PESOS, precio));
        assertEquals(CambioDolares.MSG_MONTO_INVALIDO, CambioDolares.validar(C, ",", SALDO_PESOS, precio));
        assertEquals(CambioDolares.MSG_DECIMALES, CambioDolares.validar(C, "10,005", SALDO_PESOS, precio));
        assertEquals(CambioDolares.MSG_MONTO_INVALIDO, CambioDolares.validar(C, "12345678901234", new BigDecimal("1E20"), precio));
        assertEquals(CambioDolares.MSG_INSUFICIENTE_COMPRA, CambioDolares.validar(C, "100000,01", SALDO_PESOS, precio));
        assertEquals(CambioDolares.MSG_INSUFICIENTE_VENTA, CambioDolares.validar(V, "50,01", SALDO_USD, precio));
        assertEquals(CambioDolares.MSG_SIN_SALDO, CambioDolares.validar(C, "10000", null, precio));
        assertEquals(CambioDolares.MSG_SIN_COTIZACION, CambioDolares.validar(C, "10", SALDO_PESOS, null));
        // Válidos: justo el saldo, ceros de más a la derecha, punto o coma
        assertNull(CambioDolares.validar(C, "100000,00", SALDO_PESOS, precio));
        assertNull(CambioDolares.validar(V, "50", SALDO_USD, precio));
        assertNull(CambioDolares.validar(V, "0.01", SALDO_USD, precio));
        assertNull(CambioDolares.validar(C, "10000,500", SALDO_PESOS, precio));
    }

    @Test
    public void d9_montoQueNoRecibeNiUnCentavo_noSeManda() {
        // 7,65 / 1.530 = 0,005 -> 0,00 con DOWN: el backend lo rechazaría, la app ni lo manda
        assertEquals("El monto es muy bajo: no alcanza para comprar ni un centavo de dólar.",
                CambioDolares.validar(CambioDolares.Tipo.COMPRA, "7,65", SALDO_PESOS, new BigDecimal("1530")));
        // 15,30 / 1.530 = 0,01: ya alcanza
        assertNull(CambioDolares.validar(CambioDolares.Tipo.COMPRA, "15,30", SALDO_PESOS, new BigDecimal("1530")));
        // Venta: 0,01 × 0,50 = 0,005 -> 0,00 (solo con una cotización absurda, pero la regla es la misma)
        assertEquals("El monto es muy bajo para esta cotización.",
                CambioDolares.validar(CambioDolares.Tipo.VENTA, "0,01", SALDO_USD, new BigDecimal("0.50")));
        assertNull(CambioDolares.validar(CambioDolares.Tipo.VENTA, "0,01", SALDO_USD, new BigDecimal("1495")));
    }

    @Test
    public void d9_noSeDejaTipearMasDeDosDecimales() {
        CambioDolares c = nuevo(CambioDolares.Tipo.VENTA);
        assertTrue(c.setMonto("10,55"));
        assertFalse(c.setMonto("10,555"));
        assertFalse(c.setMonto("1a"));
        assertFalse(c.setMonto("12345678901234"));
        assertEquals("10,55", c.getMontoTexto());
    }

    @Test
    public void d9_invalidoNoMandaNada() throws Exception {
        CambioDolares c = listo(CambioDolares.Tipo.COMPRA, "");
        int antes = server.getRequestCount();
        c.continuar(SALDO_PESOS);
        assertEquals(CambioDolares.MSG_MONTO_INVALIDO, c.getError());
        c.setMonto("200000");
        c.continuar(SALDO_PESOS);
        assertEquals(CambioDolares.MSG_INSUFICIENTE_COMPRA, c.getError());
        c.setMonto("7");
        c.continuar(SALDO_PESOS);
        assertEquals("$ 7 no alcanza para un centavo", CambioDolares.MSG_MUY_BAJO_COMPRA, c.getError());
        c.setMonto("10000");
        c.continuar(null);
        assertEquals(CambioDolares.MSG_SIN_SALDO, c.getError());
        Thread.sleep(150);
        assertEquals("ninguna request", antes, server.getRequestCount());
        assertEquals(CambioDolares.Paso.FORMULARIO, c.getPaso());
        assertEquals(0, refrescos.get());
    }

    @Test
    public void usarTodoPoneElSaldoExacto() {
        CambioDolares compra = nuevo(CambioDolares.Tipo.COMPRA);
        compra.usarTodo(new BigDecimal("97567742.50")); // el backend manda saldos con 2 decimales
        assertEquals("97567742,50", compra.getMontoTexto());
        CambioDolares venta = nuevo(CambioDolares.Tipo.VENTA);
        venta.usarTodo(new BigDecimal("3.55"));
        assertEquals("3,55", venta.getMontoTexto());
        assertEquals(new BigDecimal("0.00"), venta.saldoLuego(new BigDecimal("3.55")));
    }

    // ── Confirmación: "Comprar/Vender" NO opera, solo muestra el resumen ─────

    @Test
    public void continuarPideConfirmacionSinMandarNada() throws Exception {
        CambioDolares c = listo(CambioDolares.Tipo.COMPRA, "5000");
        int antes = server.getRequestCount();
        c.confirmar(); // desde el formulario no hace nada
        assertEquals(CambioDolares.Paso.FORMULARIO, c.getPaso());

        c.continuar(SALDO_PESOS);
        assertEquals(CambioDolares.Paso.CONFIRMAR, c.getPaso());
        assertEquals(new BigDecimal("5000"), c.getMontoAConfirmar());
        assertNull(c.getError());
        Thread.sleep(150);
        assertEquals("todavía ningún POST", antes, server.getRequestCount());
        assertEquals(0, refrescos.get());

        // "Modificar": vuelve con el monto intacto, y se puede cambiar
        c.editar();
        assertEquals(CambioDolares.Paso.FORMULARIO, c.getPaso());
        assertEquals("5000", c.getMontoTexto());
        c.setMonto("2000");
        c.continuar(SALDO_PESOS);
        assertEquals("se manda lo confirmado", new BigDecimal("2000"), c.getMontoAConfirmar());

        server.enqueue(new MockResponse().setResponseCode(201).setBody(operacion("COMPRA", "1.39", "2000.00", "1435.00")));
        c.confirmar();
        esperar("éxito", () -> c.getPaso() == CambioDolares.Paso.EXITO);
        JsonObject body = JsonParser.parseString(server.takeRequest(1, TimeUnit.SECONDS).getBody().readUtf8()).getAsJsonObject();
        assertEquals("2000", body.get("monto").toString());
    }

    @Test
    public void confirmarSeRestauraTrasMuerteDelProceso() {
        CambioDolares c = nuevo(CambioDolares.Tipo.VENTA);
        c.setMonto("7,25");
        c.restaurar(CambioDolares.Paso.CONFIRMAR, null);
        assertEquals(CambioDolares.Paso.CONFIRMAR, c.getPaso());
        assertEquals(new BigDecimal("7.25"), c.getMontoAConfirmar());
    }

    @Test
    public void cotizacionPrecargadaSeMuestraAlInstante() throws Exception {
        CambioDolares c = nuevo(CambioDolares.Tipo.COMPRA);
        c.usarCotizacionConocida(cotizacion(COTIZACION));
        assertEquals("sin esperar ningún GET", new BigDecimal("1435.00"), c.getPrecioAplicado());
        assertFalse(c.isCargandoCotizacion());
        // El GET de la pantalla la reemplaza
        server.enqueue(new MockResponse().setBody("{\"compra\":1400,\"venta\":1450,\"desactualizada\":false}"));
        c.cargarCotizacion();
        esperar("nueva", () -> new BigDecimal("1450").equals(c.getPrecioAplicado()));
        // Una conocida NO pisa a la que ya llegó
        c.usarCotizacionConocida(cotizacion(COTIZACION));
        assertEquals(new BigDecimal("1450"), c.getPrecioAplicado());
    }

    // ── D10 / D11: operación real (montos del backend, no el preview) ────────

    @Test
    public void d10_compraMandaPesosYMuestraElMontoUsdDelBackend() throws Exception {
        CambioDolares c = listo(CambioDolares.Tipo.COMPRA, "5000");
        assertEquals("preview con la cotización mostrada", new BigDecimal("3.48"), c.getPreview());

        // Entre medio el backend refrescó su cotización: devuelve otro montoUsd
        server.enqueue(new MockResponse().setResponseCode(201).setBody(operacion("COMPRA", "3.50", "5000.00", "1428.57")));
        c.continuar(SALDO_PESOS);
        c.confirmar();
        esperar("éxito", () -> c.getPaso() == CambioDolares.Paso.EXITO);

        RecordedRequest r = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("POST", r.getMethod());
        assertEquals("/api/cambio-dolares", r.getPath());
        JsonObject body = JsonParser.parseString(r.getBody().readUtf8()).getAsJsonObject();
        assertEquals("COMPRA", body.get("tipo").getAsString());
        assertEquals("monto = PESOS a destinar, número plano", "5000", body.get("monto").toString());
        assertEquals(2, body.size());

        assertEquals("lo real, no el preview", new BigDecimal("3.50"), c.getResultado().getMontoUsd());
        assertEquals(new BigDecimal("5000.00"), c.getResultado().getMontoPesos());
        assertEquals("la cotización de ESA operación", new BigDecimal("1428.57"), c.getResultado().getCotizacion());
        assertEquals("US$ 3,50", "US$ " + MontoFormatter.fiat(c.getResultado().getMontoUsd()));
        assertEquals("refresca saldos y notificaciones", 1, refrescos.get());
        assertNull(c.getError());
    }

    @Test
    public void d11_ventaMandaDolaresYMuestraLosPesosDelBackend() throws Exception {
        CambioDolares c = listo(CambioDolares.Tipo.VENTA, "10,5");
        assertEquals(new BigDecimal("14542.50"), c.getPreview());

        server.enqueue(new MockResponse().setResponseCode(201).setBody(operacion("VENTA", "10.50", "14543.55", "1385.10")));
        c.continuar(SALDO_USD);
        c.confirmar();
        esperar("éxito", () -> c.getPaso() == CambioDolares.Paso.EXITO);

        JsonObject body = JsonParser.parseString(server.takeRequest(1, TimeUnit.SECONDS).getBody().readUtf8()).getAsJsonObject();
        assertEquals("VENTA", body.get("tipo").getAsString());
        assertEquals("monto = DÓLARES a vender", "10.5", body.get("monto").toString());
        assertFalse(c.getResultado().esCompra());
        assertEquals(new BigDecimal("14543.55"), c.getResultado().getMontoPesos());
        assertEquals("$ 14.543,55", "$ " + MontoFormatter.fiat(c.getResultado().getMontoPesos()));
        assertEquals(1, refrescos.get());
    }

    // ── D12: cada 400 con su texto exacto ────────────────────────────────────

    @Test
    public void d12_erroresDelBackendConSuTextoExacto() throws Exception {
        String[][] casos = {
                {"COMPRA", "No tenes saldo en pesos suficiente para esta compra"},
                {"VENTA", "No tenes dolares suficientes para esta venta"},
                {"COMPRA", "El monto ingresado es muy bajo: a esta cotizacion no alcanza para comprar ni un centavo de dolar"},
                {"VENTA", "El monto ingresado es muy bajo para esta cotizacion"},
                {"COMPRA", "La cotizacion del dolar esta desactualizada, no se puede operar en este momento. Intenta de nuevo en unos segundos"},
        };
        for (String[] caso : casos) {
            // Montos que pasan la validación local (en la compra, $ 0,01 ya no se manda: no alcanza para
            // un centavo). El "muy bajo" del backend sigue pudiendo llegar si la cotización cambió en el medio.
            CambioDolares.Tipo tipo = CambioDolares.Tipo.valueOf(caso[0]);
            CambioDolares c = listo(tipo, tipo == CambioDolares.Tipo.COMPRA ? "100" : "0,01");
            server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"" + caso[1] + "\"}"));
            c.continuar(new BigDecimal("1000"));
            c.confirmar();
            esperar("error " + caso[1], () -> !c.isEnviando() && c.getError() != null);
            server.takeRequest(1, TimeUnit.SECONDS);
            assertEquals(caso[1], c.getError());
            assertEquals("sigue en el resumen: se puede Modificar o reintentar", CambioDolares.Paso.CONFIRMAR, c.getPaso());
        }
        assertEquals("un rechazo no movió plata", 0, refrescos.get());
    }

    @Test
    public void d12_validacionSinDetalleY429() throws Exception {
        CambioDolares c = listo(CambioDolares.Tipo.COMPRA, "100");
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"Datos invalidos\",\"campos\":{}}"));
        c.continuar(SALDO_PESOS);
        c.confirmar();
        esperar("400", () -> !c.isEnviando() && c.getError() != null);
        assertEquals(CambioDolares.MSG_NO_SE_PUDO, c.getError());

        server.enqueue(new MockResponse().setResponseCode(429));
        c.confirmar();
        esperar("429", () -> !c.isEnviando() && ApiErrores.MSG_RATE_LIMIT.equals(c.getError()));
    }

    // ── D13: un solo pedido en vuelo, sin reintentos ─────────────────────────

    @Test
    public void d13_dobleTapEsUnaSolaRequest() throws Exception {
        CambioDolares c = listo(CambioDolares.Tipo.COMPRA, "5000");
        int antes = server.getRequestCount();
        server.enqueue(new MockResponse().setResponseCode(201).setHeadersDelay(300, TimeUnit.MILLISECONDS)
                .setBody(operacion("COMPRA", "3.48", "5000.00", "1435.00")));
        c.continuar(SALDO_PESOS);
        c.confirmar();
        c.confirmar();
        assertTrue(c.isEnviando());
        esperar("éxito", () -> c.getPaso() == CambioDolares.Paso.EXITO);
        c.confirmar(); // desde EXITO tampoco
        Thread.sleep(200);
        assertEquals(1, server.getRequestCount() - antes);
    }

    @Test
    public void d13_timeoutDespuesDeEnviarEsInciertoYNoReintenta() throws Exception {
        ApiService lento = conTimeoutCorto(apiSinReintentos);
        CambioDolares c = new CambioDolares(CambioDolares.Tipo.COMPRA, () -> api, () -> lento, refrescos::incrementAndGet);
        server.enqueue(new MockResponse().setBody(COTIZACION));
        c.cargarCotizacion();
        esperar("cotización", () -> c.getCotizacion() != null);
        c.setMonto("5000");
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        c.continuar(SALDO_PESOS);
        c.confirmar();
        esperar("incierto", () -> c.getPaso() == CambioDolares.Paso.INCIERTO);
        assertEquals(CambioDolares.MSG_INCIERTO, c.getError());
        assertEquals("refresca saldos y notificaciones", 1, refrescos.get());
        c.confirmar();
        Thread.sleep(300);
        assertEquals("1 GET + 1 POST", 2, server.getRequestCount());
    }

    @Test
    public void d13_corteDespuesDeEnviarEsInciertoYOkHttpNoReintenta() throws Exception {
        CambioDolares c = listo(CambioDolares.Tipo.VENTA, "10");
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        server.enqueue(new MockResponse().setResponseCode(201).setBody(operacion("VENTA", "10.00", "13850.00", "1385.00")));
        c.continuar(SALDO_USD);
        c.confirmar();
        esperar("incierto", () -> c.getPaso() == CambioDolares.Paso.INCIERTO);
        Thread.sleep(300);
        assertEquals("1 GET + 1 POST", 2, server.getRequestCount());
        assertEquals(1, refrescos.get());
    }

    @Test
    public void d13_2xxSinBodyEsIncierto() throws Exception {
        CambioDolares c = listo(CambioDolares.Tipo.COMPRA, "5000");
        server.enqueue(new MockResponse().setResponseCode(201));
        c.continuar(SALDO_PESOS);
        c.confirmar();
        esperar("incierto", () -> c.getPaso() == CambioDolares.Paso.INCIERTO);
        assertEquals(1, refrescos.get());
    }

    @Test
    public void d13_sinConexionAntesDeEnviarSePuedeReintentar() throws Exception {
        // El POST va a un servidor apagado: conexión rechazada, seguro que no salió
        MockWebServer apagado = new MockWebServer();
        apagado.start();
        String url = apagado.url("/").toString();
        apagado.shutdown();
        ApiService caido = RetrofitClient.crear(url, () -> "x", () -> {}, false, false);
        CambioDolares c = new CambioDolares(CambioDolares.Tipo.COMPRA, () -> api, () -> caido, refrescos::incrementAndGet);
        server.enqueue(new MockResponse().setBody(COTIZACION));
        c.cargarCotizacion();
        esperar("cotización", () -> c.getCotizacion() != null);
        c.setMonto("5000");
        c.continuar(SALDO_PESOS);
        c.confirmar();
        esperar("error", () -> !c.isEnviando() && c.getError() != null);
        assertEquals(ApiErrores.MSG_SIN_CONEXION, c.getError());
        assertEquals("sigue en el resumen: se puede reintentar", CambioDolares.Paso.CONFIRMAR, c.getPaso());
        assertEquals(0, refrescos.get());
    }

    // ── D14: el estado sobrevive a la rotación (vive en el ViewModel) ────────

    @Test
    public void d14_respuestaQueLlegaDuranteLaRotacionNoSePierde() throws Exception {
        CambioDolares c = listo(CambioDolares.Tipo.COMPRA, "5000");
        AtomicInteger vistaVieja = new AtomicInteger(), vistaNueva = new AtomicInteger();
        c.setObservador(x -> vistaVieja.incrementAndGet());
        server.enqueue(new MockResponse().setResponseCode(201).setHeadersDelay(300, TimeUnit.MILLISECONDS)
                .setBody(operacion("COMPRA", "3.48", "5000.00", "1435.00")));
        c.continuar(SALDO_PESOS);
        c.confirmar();
        // onDestroy de la Activity vieja...
        c.setObservador(null);
        Thread.sleep(500); // la respuesta llega "entre" Activities
        // ...onCreate de la nueva: mismo objeto del ViewModel
        c.setObservador(x -> vistaNueva.incrementAndGet());
        assertEquals(CambioDolares.Paso.EXITO, c.getPaso());
        assertEquals("5000", c.getMontoTexto());
        assertEquals(new BigDecimal("3.48"), c.getResultado().getMontoUsd());
        assertEquals(1, server.getRequestCount() - 1);
    }

    @Test
    public void d14_restaurarTrasMuerteDelProceso() {
        CambioDolares c = nuevo(CambioDolares.Tipo.VENTA);
        c.setMonto("7,25");
        c.restaurar(CambioDolares.Paso.INCIERTO, null);
        assertEquals(CambioDolares.Paso.INCIERTO, c.getPaso());
        assertEquals(CambioDolares.MSG_INCIERTO, c.getError());
        c.continuar(SALDO_USD);
        c.confirmar(); // desde INCIERTO no se manda nada
        assertEquals(0, server.getRequestCount());
    }

    /** Envuelve la API para que las llamadas venzan a los 300 ms (en la app es 30 s). */
    private static ApiService conTimeoutCorto(ApiService api) {
        return (ApiService) java.lang.reflect.Proxy.newProxyInstance(ApiService.class.getClassLoader(),
                new Class<?>[]{ApiService.class}, (proxy, metodo, args) -> {
                    Object r = metodo.invoke(api, args);
                    if (r instanceof retrofit2.Call) ((retrofit2.Call<?>) r).timeout().timeout(300, TimeUnit.MILLISECONDS);
                    return r;
                });
    }
}
