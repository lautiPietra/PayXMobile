package com.example.payxmobile.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.JwtFalso;
import com.example.payxmobile.actividad.Actividad;
import com.example.payxmobile.actividad.Actividades;
import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.model.FacturaResponse;
import com.example.payxmobile.model.NotificacionResponse;
import com.example.payxmobile.model.ServicioConFacturaResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.notificaciones.NotificacionesRepository;
import com.example.payxmobile.notificaciones.TitulosNotificacion;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

/** Pago de servicios contra MockWebServer: S1, S3-S6, S8, S9 y S12. */
public class ServiciosContratoTest {

    static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");
    static final String ID_LUZ = "a1111111-1111-1111-1111-111111111111";

    static String servicio(String codigo, String nombre, String proveedor, String categoria, String id, String monto,
                           String estado, boolean vencida, String fechaPago) {
        return "{\"servicioCodigo\":\"" + codigo + "\",\"nombre\":\"" + nombre + "\",\"proveedor\":\"" + proveedor
                + "\",\"categoria\":\"" + categoria + "\",\"facturaId\":\"" + id + "\",\"monto\":" + monto
                + ",\"fechaVencimiento\":\"2026-09-15\",\"estado\":\"" + estado + "\",\"vencida\":" + vencida
                + ",\"fechaPago\":" + (fechaPago == null ? "null" : "\"" + fechaPago + "\"") + "}";
    }

    /** Respuesta con la forma real de GET /api/facturas (6 servicios, en el orden del backend). */
    static final String CATALOGO = "["
            + servicio("LUZ", "Luz", "Edesur", "Energía", ID_LUZ, "8123.45", "PENDIENTE", true, null) + ","
            + servicio("GAS", "Gas", "Metrogas", "Gas natural", "a2222222-2222-2222-2222-222222222222", "3401.07", "PAGADA", false, "2026-09-10T11:30:00.123456-03:00") + ","
            + servicio("AGUA", "Agua", "AySA", "Agua potable", "a3333333-3333-3333-3333-333333333333", "2210.00", "PENDIENTE", true, null) + ","
            + servicio("INTERNET", "Internet", "Fibertel", "Internet", "a4444444-4444-4444-4444-444444444444", "9876.54", "PENDIENTE", true, null) + ","
            + servicio("CABLE", "TV por cable", "DirecTV", "Televisión", "a5555555-5555-5555-5555-555555555555", "7000.99", "PENDIENTE", true, null) + ","
            + servicio("TELEFONIA", "Telefonía celular", "Movistar", "Telefonía", "a6666666-6666-6666-6666-666666666666", "4500.01", "PENDIENTE", true, null)
            + "]";

    static String factura(String id, String codigo, String nombre, String periodo, String monto, String vence,
                          String estado, String fechaPago) {
        return "{\"id\":\"" + id + "\",\"servicioCodigo\":\"" + codigo + "\",\"servicioNombre\":\"" + nombre
                + "\",\"periodo\":\"" + periodo + "\",\"monto\":" + monto + ",\"fechaVencimiento\":\"" + vence
                + "\",\"estado\":\"" + estado + "\",\"fechaPago\":" + (fechaPago == null ? "null" : "\"" + fechaPago + "\"") + "}";
    }

    static final String LUZ_PAGADA = factura(ID_LUZ, "LUZ", "Luz", "2026-09", "8123.45", "2026-09-15", "PAGADA",
            "2026-09-28T10:15:00.654321-03:00");

    private MockWebServer server;
    private ApiService api;
    private final Map<String, ConcurrentLinkedQueue<MockResponse>> respuestas = new ConcurrentHashMap<>();
    private final List<RecordedRequest> pedidos = new CopyOnWriteArrayList<>();
    private final AtomicInteger refrescos = new AtomicInteger();
    private final AtomicReference<FacturaResponse> pagada = new AtomicReference<>();

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest r) {
                pedidos.add(r);
                ConcurrentLinkedQueue<MockResponse> cola = respuestas.get(r.getMethod() + " " + r.getPath());
                MockResponse m = cola != null ? cola.poll() : null;
                return m != null ? m : new MockResponse().setResponseCode(404);
            }
        });
        server.start();
        String token = JwtFalso.conExp(System.currentTimeMillis() / 1000 + 7200);
        api = RetrofitClient.crear(server.url("/").toString(), () -> token, System::currentTimeMillis, () -> {}, false, false);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private void responder(String metodoYPath, MockResponse r) {
        respuestas.computeIfAbsent(metodoYPath, k -> new ConcurrentLinkedQueue<>()).add(r);
    }

    private static MockResponse json(int codigo, String body) {
        return new MockResponse().setResponseCode(codigo).setBody(body);
    }

    private static void esperar(String que, BooleanSupplier c) throws InterruptedException {
        long fin = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) throw new AssertionError("Timeout: " + que);
            Thread.sleep(10);
        }
    }

    private long pedidosA(String metodo, String path) {
        return pedidos.stream().filter(r -> r.getMethod().equals(metodo) && r.getPath().equals(path)).count();
    }

    private <T> ListaRemota<T> lista(java.util.function.Function<ApiService, retrofit2.Call<List<T>>> pedido,
                                     java.util.function.Function<T, String> id) {
        return new ListaRemota<>(() -> api, pedido, id, (t, d) -> () -> {});
    }

    private static FacturaAPagar luz() {
        return new FacturaAPagar(ID_LUZ, "LUZ", "Luz", "Edesur", new BigDecimal("8123.45"), null, true);
    }

    private PagoFactura pago(ApiService s) {
        return new PagoFactura(luz(), () -> s, pagada::set, refrescos::incrementAndGet);
    }

    private void confirmar(PagoFactura p, String saldo) throws InterruptedException {
        p.confirmar(new BigDecimal(saldo));
        esperar("fin del pago", () -> !p.isEnviando());
    }

    private static final String PAGAR = "/api/facturas/" + ID_LUZ + "/pagar";

    // ── S1: catálogo con los datos EXACTOS ─────────────────────────────────────

    @Test
    public void s1_losSeisServiciosConLosDatosDeLaRespuesta() throws Exception {
        responder("GET /api/facturas", json(200, CATALOGO));
        ListaRemota<ServicioConFacturaResponse> catalogo = lista(ApiService::listarServicios, ServicioConFacturaResponse::getServicioCodigo);
        catalogo.refrescar();
        esperar("catálogo", () -> catalogo.getEstado().lista != null);
        RecordedRequest r = pedidos.get(0);
        assertEquals("GET", r.getMethod());
        assertTrue(r.getHeader("Authorization").startsWith("Bearer "));

        VistaServicios v = VistaServicios.de(catalogo.getEstado(), null, LocalDate.of(2026, 9, 28), BA);
        assertEquals(6, v.tarjetas.size());
        List<String> codigos = new ArrayList<>();
        for (VistaServicios.Tarjeta t : v.tarjetas) codigos.add(t.codigo);
        assertEquals(java.util.Arrays.asList("LUZ", "GAS", "AGUA", "INTERNET", "CABLE", "TELEFONIA"), codigos);
        VistaServicios.Tarjeta luz = v.tarjetas.get(0);
        assertEquals("Luz", luz.nombre);
        assertEquals("Edesur", luz.proveedor);
        assertEquals("el monto EXACTO del backend", "$ 8.123,45", luz.monto);
        assertEquals("Venció el 15/09/2026", luz.fecha);
        assertEquals(ID_LUZ, luz.servicio.getFacturaId());
        assertEquals("$ 4.500,01", v.tarjetas.get(5).monto);
        assertEquals("Telefonía celular", v.tarjetas.get(5).nombre);
    }

    // ── S3: pagar ──────────────────────────────────────────────────────────────

    @Test
    public void s3_pagarDevuelveLaFacturaPagadaYActualizaTodo() throws Exception {
        responder("POST " + PAGAR, json(200, LUZ_PAGADA));
        PagoFactura p = pago(api);
        confirmar(p, "50000");
        assertEquals(PagoFactura.Paso.EXITO, p.getPaso());
        RecordedRequest r = pedidos.get(0);
        assertEquals("POST", r.getMethod());
        assertEquals(PAGAR, r.getPath());
        assertEquals("sin body", 0, r.getBodySize());
        assertEquals("PAGADA", p.getResultado().getEstado());
        assertEquals("2026-09-28T10:15:00.654321-03:00", p.getResultado().getFechaPago());
        assertEquals(new BigDecimal("8123.45"), pagada.get().getMonto());
        assertEquals("refresca saldos, notificaciones, catálogo e historial", 1, refrescos.get());

        // La tarjeta de ESE servicio pasa a PAGADA con la fecha de pago (sin esperar al GET)
        ServicioConFacturaResponse antes = new com.google.gson.Gson().fromJson(
                servicio("LUZ", "Luz", "Edesur", "Energía", ID_LUZ, "8123.45", "PENDIENTE", true, null), ServicioConFacturaResponse.class);
        ServicioConFacturaResponse despues = antes.conPago(p.getResultado());
        assertTrue(despues.esPagada());
        assertFalse("pagada ya no está vencida", despues.isVencida());
        assertEquals("2026-09-28T10:15:00.654321-03:00", despues.getFechaPago());
        assertEquals("Edesur", despues.getProveedor());
    }

    @Test
    public void s12_pagarAgregaAlFeedSinRecargarTodo() throws Exception {
        String pendiente = factura(ID_LUZ, "LUZ", "Luz", "2026-09", "8123.45", "2026-09-15", "PENDIENTE", null);
        responder("GET /api/facturas/historial", json(200, "[" + pendiente + "]"));
        ListaRemota<FacturaResponse> historial = lista(ApiService::historialFacturas, FacturaResponse::getId);
        historial.refrescar();
        esperar("historial", () -> historial.getEstado().lista != null);
        assertTrue("una pendiente no es un movimiento", Actividades.construir(Collections.emptyList(), null, null, null,
                null, historial.getEstado().lista, BA).isEmpty());

        responder("POST " + PAGAR, json(200, LUZ_PAGADA));
        PagoFactura p = new PagoFactura(luz(), () -> api, historial::actualizar, () -> {});
        confirmar(p, "50000");
        List<Actividad> feed = Actividades.construir(Collections.emptyList(), null, null, null, null,
                historial.getEstado().lista, BA);
        assertEquals(1, feed.size());
        assertEquals("sv-" + ID_LUZ, feed.get(0).key);
        assertEquals("sin volver a pedir el historial", 1, pedidosA("GET", "/api/facturas/historial"));
    }

    @Test
    public void s3_saldoInsuficienteLocalNoMandaElPost() throws Exception {
        PagoFactura p = pago(api);
        p.confirmar(new BigDecimal("8123.44"));
        assertEquals(PagoFactura.MSG_INSUFICIENTE, p.getError());
        p.confirmar(null);
        assertEquals(PagoFactura.MSG_SIN_SALDO, p.getError());
        assertNull(PagoFactura.validar(new BigDecimal("8123.45"), new BigDecimal("8123.45")));
        Thread.sleep(100);
        assertEquals(0, pedidos.size());
    }

    // ── S4 / S5: rechazos con su texto ─────────────────────────────────────────

    @Test
    public void s4_s5_rechazosDelBackend() throws Exception {
        String[] textos = {"Esta factura ya fue pagada", "No tenes saldo suficiente para pagar esta factura"};
        for (String t : textos) {
            responder("POST " + PAGAR, json(400, "{\"error\":\"" + t + "\"}"));
            PagoFactura p = pago(api);
            confirmar(p, "50000");
            assertEquals(t, p.getError());
            assertEquals(PagoFactura.Paso.CONFIRMAR, p.getPaso());
        }
        assertEquals("refresca (p. ej. la tarjeta tiene que pasar a PAGADA)", 2, refrescos.get());

        responder("POST " + PAGAR, json(403, "{\"error\":\"No tenes acceso a esta factura\"}"));
        PagoFactura ajena = pago(api);
        confirmar(ajena, "50000");
        assertEquals("No tenes acceso a esta factura", ajena.getError());
        assertNull(pagada.get());
    }

    // ── S6: un solo pedido e incertidumbre ────────────────────────────────────

    @Test
    public void s6_dobleTapEsUnSoloPost() throws Exception {
        responder("POST " + PAGAR, json(200, LUZ_PAGADA).setBodyDelay(300, TimeUnit.MILLISECONDS));
        PagoFactura p = pago(api);
        p.confirmar(new BigDecimal("50000"));
        p.confirmar(new BigDecimal("50000"));
        p.confirmar(new BigDecimal("50000"));
        esperar("pago", () -> !p.isEnviando());
        p.confirmar(new BigDecimal("50000")); // ya en ÉXITO
        Thread.sleep(100);
        assertEquals(1, pedidosA("POST", PAGAR));
    }

    @Test
    public void s6_timeoutEsInciertoRefrescaYNoReintenta() throws Exception {
        ApiService lento = (ApiService) java.lang.reflect.Proxy.newProxyInstance(ApiService.class.getClassLoader(),
                new Class<?>[]{ApiService.class}, (proxy, metodo, args) -> {
                    Object r = metodo.invoke(api, args);
                    if (r instanceof retrofit2.Call) ((retrofit2.Call<?>) r).timeout().timeout(300, TimeUnit.MILLISECONDS);
                    return r;
                });
        responder("POST " + PAGAR, new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        PagoFactura p = pago(lento);
        confirmar(p, "50000");
        assertEquals(PagoFactura.Paso.INCIERTO, p.getPaso());
        assertEquals(PagoFactura.MSG_INCIERTO, p.getError());
        assertEquals(1, refrescos.get());
        p.confirmar(new BigDecimal("50000"));
        Thread.sleep(200);
        assertEquals(1, pedidosA("POST", PAGAR));
    }

    @Test
    public void s6_corteDespuesDeEnviarY200Raros() throws Exception {
        responder("POST " + PAGAR, new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        responder("POST " + PAGAR, json(200, LUZ_PAGADA));
        PagoFactura p = pago(api);
        confirmar(p, "50000");
        assertEquals(PagoFactura.Paso.INCIERTO, p.getPaso());
        Thread.sleep(200);
        assertEquals("OkHttp no reintentó", 1, pedidosA("POST", PAGAR));
        respuestas.clear(); // el 200 de respaldo (para detectar un reintento) quedó sin usar: bien

        responder("POST " + PAGAR, new MockResponse().setResponseCode(200));
        PagoFactura sinBody = pago(api);
        confirmar(sinBody, "50000");
        assertEquals(PagoFactura.Paso.INCIERTO, sinBody.getPaso());

        responder("POST " + PAGAR, json(200, factura(ID_LUZ, "LUZ", "Luz", "2026-09", "8123.45", "2026-09-15", "PENDIENTE", null)));
        PagoFactura raro = pago(api);
        confirmar(raro, "50000");
        assertEquals("200 que no dice PAGADA: no se asume nada", PagoFactura.Paso.INCIERTO, raro.getPaso());
        assertNull(pagada.get());
    }

    @Test
    public void s6_sinConexionAntesDeEnviarSePuedeReintentar() throws Exception {
        MockWebServer apagado = new MockWebServer();
        apagado.start();
        String url = apagado.url("/").toString();
        apagado.shutdown();
        ApiService caido = RetrofitClient.crear(url, () -> "x", System::currentTimeMillis, () -> {}, false, false);
        PagoFactura p = pago(caido);
        confirmar(p, "50000");
        assertEquals(ApiErrores.MSG_SIN_CONEXION, p.getError());
        assertEquals(PagoFactura.Paso.CONFIRMAR, p.getPaso());
        assertEquals(0, refrescos.get());
    }

    // ── S8: la campana ─────────────────────────────────────────────────────────

    @Test
    public void s8_notificacionConTituloYNombreDelServicio() throws Exception {
        responder("GET /api/notificaciones/sin-leer", json(200, "{\"cantidad\":1}"));
        responder("GET /api/notificaciones", json(200, "[{\"id\":40,\"plantillaCodigo\":\"SERVICIO_PAGADO\","
                + "\"mensaje\":\"Pagaste $ 8.123,45 de Luz\",\"leida\":false,\"fecha\":\"2026-09-28T10:15:00.7-03:00\"}]"));
        NotificacionesRepository campana = new NotificacionesRepository(() -> api, (t, d) -> () -> {});
        campana.abrirPanel();
        esperar("campana", () -> campana.getEstado().lista != null);
        NotificacionResponse n = campana.getEstado().lista.get(0);
        assertEquals("Pago de servicio", TitulosNotificacion.de(n.getPlantillaCodigo()));
        assertTrue("el mensaje lo arma el backend con el servicio", n.getMensaje().contains("Luz"));
    }

    // ── S9: errores de los GET ─────────────────────────────────────────────────

    @Test
    public void s9_erroresDeLosGetSonReintentables() throws Exception {
        responder("GET /api/facturas", new MockResponse().setResponseCode(503));
        responder("GET /api/facturas/historial", new MockResponse().setResponseCode(403));
        ListaRemota<ServicioConFacturaResponse> catalogo = lista(ApiService::listarServicios, ServicioConFacturaResponse::getServicioCodigo);
        ListaRemota<FacturaResponse> historial = lista(ApiService::historialFacturas, FacturaResponse::getId);
        catalogo.refrescar();
        historial.refrescar();
        esperar("errores", () -> catalogo.getEstado().errorPrimeraCarga != null && historial.getEstado().errorPrimeraCarga != null);
        VistaServicios v = VistaServicios.de(catalogo.getEstado(), historial.getEstado(), LocalDate.of(2026, 9, 28), BA);
        assertEquals(VistaServicios.Modo.ERROR, v.modo);
        assertEquals(ApiErrores.MSG_SERVIDOR, v.error);
        assertEquals(VistaServicios.Modo.ERROR, v.modoHistorial);
        assertEquals("403 sin body: mensaje, sin crash", ApiErrores.MSG_DATOS_INVALIDOS, v.errorHistorial);

        // Reintentar
        responder("GET /api/facturas", json(200, CATALOGO));
        responder("GET /api/facturas/historial", json(200, "[" + LUZ_PAGADA + "]"));
        catalogo.refrescar();
        historial.refrescar();
        esperar("reintento", () -> catalogo.getEstado().lista != null && historial.getEstado().lista != null);
        v = VistaServicios.de(catalogo.getEstado(), historial.getEstado(), LocalDate.of(2026, 9, 28), BA);
        assertEquals(VistaServicios.Modo.LISTA, v.modo);
        assertEquals(VistaServicios.Modo.LISTA, v.modoHistorial);
    }

    @Test
    public void s9_sinConexionEnLosGet() throws Exception {
        MockWebServer apagado = new MockWebServer();
        apagado.start();
        String url = apagado.url("/").toString();
        apagado.shutdown();
        ApiService caido = RetrofitClient.crear(url, () -> "x", System::currentTimeMillis, () -> {}, false);
        ListaRemota<ServicioConFacturaResponse> catalogo = new ListaRemota<>(() -> caido, ApiService::listarServicios,
                ServicioConFacturaResponse::getServicioCodigo, (t, d) -> () -> {});
        catalogo.refrescar();
        esperar("error", () -> catalogo.getEstado().errorPrimeraCarga != null);
        assertEquals(ApiErrores.MSG_SIN_CONEXION, catalogo.getEstado().errorPrimeraCarga);
    }
}
