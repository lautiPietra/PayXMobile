package com.example.payxmobile.plazofijo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.JwtFalso;
import com.example.payxmobile.model.TasaPlazoFijo;
import com.example.payxmobile.model.TasasPlazoFijoResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

/** Constituir un plazo fijo: /tasas (P1), formulario (P2), contrato del POST (P3), 400 (P4) y envío único/incierto (P8). */
public class ConstitucionPlazoFijoTest {

    /** Configuración "A" (la de fábrica del backend). */
    static final String TASAS_A = "{\"tasas\":[{\"dias\":30,\"tna\":35.50},{\"dias\":60,\"tna\":36.50},"
            + "{\"dias\":90,\"tna\":38.00},{\"dias\":180,\"tna\":40.00},{\"dias\":365,\"tna\":42.00}],"
            + "\"montoMinimo\":1000.00,\"maxActivos\":5}";
    /** Configuración "B", como si el admin la hubiera cambiado: otros plazos, tasas, mínimo y máximo. */
    static final String TASAS_B = "{\"tasas\":[{\"dias\":7,\"tna\":20.25},{\"dias\":45,\"tna\":33.10},"
            + "{\"dias\":400,\"tna\":51.00}],\"montoMinimo\":50.50,\"maxActivos\":2}";

    static String plazoFijo(String id, String monto, String tna, int dias, String interes, String total, String estado,
                            String inicio, String vencimiento, String creacion) {
        return "{\"id\":\"" + id + "\",\"monto\":" + monto + ",\"tna\":" + tna + ",\"plazoDias\":" + dias
                + ",\"interesEstimado\":" + interes + ",\"montoTotal\":" + total + ",\"estado\":\"" + estado
                + "\",\"fechaInicio\":\"" + inicio + "\",\"fechaVencimiento\":\"" + vencimiento
                + "\",\"fechaCreacion\":\"" + creacion + "\"}";
    }

    /** 201 real para $ 100.000 a 30 días al 35,5 %. */
    static final String CREADO = plazoFijo("5a0f6c1e-2b3d-4e5f-8a9b-0c1d2e3f4a5b", "100000.00", "35.50", 30,
            "2917.81", "102917.81", "ACTIVO", "2026-09-28", "2026-10-28", "2026-09-28T07:58:12.345678-03:00");

    private static final BigDecimal SALDO = new BigDecimal("250000.00");

    private MockWebServer server;
    private ApiService api;
    private volatile String tasasJson = TASAS_A;
    private volatile int codigoTasas = 200;
    private final ConcurrentLinkedQueue<MockResponse> respuestasPost = new ConcurrentLinkedQueue<>();
    private final List<String> pedidos = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final AtomicInteger refrescos = new AtomicInteger();
    private final AtomicInteger rechazos = new AtomicInteger();

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest r) {
                pedidos.add(r.getMethod() + " " + r.getPath());
                if ("/api/plazos-fijos/tasas".equals(r.getPath())) {
                    return new MockResponse().setResponseCode(codigoTasas).setBody(tasasJson);
                }
                if ("POST".equals(r.getMethod()) && "/api/plazos-fijos".equals(r.getPath())) {
                    MockResponse m = respuestasPost.poll();
                    return m != null ? m : new MockResponse().setResponseCode(500);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        server.start();
        String token = JwtFalso.conExp(System.currentTimeMillis() / 1000 + 7200);
        api = RetrofitClient.crear(server.url("/").toString(), () -> token, System::currentTimeMillis,
                () -> {}, false, false);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private ConstitucionPlazoFijo nueva() {
        return nueva(api);
    }

    private ConstitucionPlazoFijo nueva(ApiService servicio) {
        return new ConstitucionPlazoFijo(() -> servicio, () -> servicio, refrescos::incrementAndGet, rechazos::incrementAndGet);
    }

    private static void esperar(String que, BooleanSupplier c) throws InterruptedException {
        long fin = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) throw new AssertionError("Timeout: " + que);
            Thread.sleep(10);
        }
    }

    private ConstitucionPlazoFijo conTasas() throws InterruptedException {
        ConstitucionPlazoFijo c = nueva();
        c.cargarTasas();
        esperar("tasas", () -> c.getTasas() != null || c.getErrorTasas() != null);
        return c;
    }

    private void operar(ConstitucionPlazoFijo c) throws InterruptedException {
        c.continuar(SALDO);
        assertEquals("error local: " + c.getError(), ConstitucionPlazoFijo.Paso.CONFIRMAR, c.getPaso());
        c.confirmar();
        esperar("fin del POST", () -> !c.isEnviando());
    }

    private long posts() {
        return pedidos.stream().filter(p -> p.startsWith("POST")).count();
    }

    private long getsTasas() {
        return pedidos.stream().filter(p -> p.equals("GET /api/plazos-fijos/tasas")).count();
    }

    private static List<Integer> dias(ConstitucionPlazoFijo c) {
        List<Integer> d = new ArrayList<>();
        for (TasaPlazoFijo t : c.getOpciones()) d.add(t.getDias());
        return d;
    }

    // ── P1: /tasas antes del formulario, nada hardcodeado ─────────────────────

    @Test
    public void p1_sinTasasNoHayFormularioNiSePuedeContinuar() {
        ConstitucionPlazoFijo c = nueva();
        assertNull(c.getTasas());
        assertTrue(c.getOpciones().isEmpty());
        assertNull("sin tasas no hay plazo elegido", c.getPlazoDias());
        c.setMonto("5000");
        c.continuar(SALDO);
        assertEquals(ConstitucionPlazoFijo.MSG_SIN_TASAS, c.getError());
        assertEquals(ConstitucionPlazoFijo.Paso.FORMULARIO, c.getPaso());
        assertEquals("nada se pidió todavía", 0, pedidos.size());
    }

    @Test
    public void p1_laUiReflejaLoQueDevuelveTasas_A() throws Exception {
        ConstitucionPlazoFijo c = conTasas();
        assertEquals("GET /api/plazos-fijos/tasas", pedidos.get(0));
        assertEquals(java.util.Arrays.asList(30, 60, 90, 180, 365), dias(c));
        assertEquals("por defecto el primero (como la web)", Integer.valueOf(30), c.getPlazoDias());
        assertEquals(0, new BigDecimal("1000").compareTo(c.getTasas().getMontoMinimo()));
        assertEquals(Integer.valueOf(5), c.getTasas().getMaxActivos());
        assertEquals("30 días · TNA 35.5%", FormatoPlazoFijo.plazoYTna(30, c.getTasaElegida().getTna()));
    }

    @Test
    public void p1_laUiReflejaLoQueDevuelveTasas_B_otraConfiguracion() throws Exception {
        tasasJson = TASAS_B;
        ConstitucionPlazoFijo c = conTasas();
        assertEquals(java.util.Arrays.asList(7, 45, 400), dias(c));
        assertEquals(Integer.valueOf(7), c.getPlazoDias());
        assertEquals("7 días · TNA 20.25%", FormatoPlazoFijo.plazoYTna(7, c.getTasaElegida().getTna()));
        assertEquals(Integer.valueOf(2), c.getTasas().getMaxActivos());
        // El mínimo es el de la respuesta, no uno fijo
        c.setMonto("50,49");
        c.continuar(SALDO);
        assertEquals("El monto mínimo para constituir un plazo fijo es $ 50,50.", c.getError());
        c.setMonto("50,50");
        c.continuar(SALDO);
        assertEquals(ConstitucionPlazoFijo.Paso.CONFIRMAR, c.getPaso());
        // Tope de la respuesta B (2), no el de fábrica (5)
        ConstitucionPlazoFijo otra = conTasas();
        otra.setActivos(2);
        assertTrue(otra.isLimiteAlcanzado());
    }

    @Test
    public void p1_siElAdminCambiaLasTasasSeReflejaAlVolverAPedir() throws Exception {
        ConstitucionPlazoFijo c = conTasas();
        assertTrue(c.elegirPlazo(90));
        tasasJson = TASAS_B;
        c.cargarTasas();
        esperar("tasas nuevas", () -> c.getOpciones().size() == 3);
        assertEquals("el plazo elegido ya no existe: vuelve al primero ofrecido", Integer.valueOf(7), c.getPlazoDias());
    }

    @Test
    public void p1_errorDeTasasMuestraErrorYNoFormulario() throws Exception {
        codigoTasas = 500;
        tasasJson = "{\"error\":\"x\"}";
        ConstitucionPlazoFijo c = conTasas();
        assertNull(c.getTasas());
        assertEquals(ConstitucionPlazoFijo.MSG_ERROR_TASAS, c.getErrorTasas());
        // Reintentar funciona
        codigoTasas = 200;
        tasasJson = TASAS_A;
        c.cargarTasas();
        esperar("tasas", () -> c.getTasas() != null);
        assertNull(c.getErrorTasas());
    }

    @Test
    public void p1_respuestaDeTasasIncompletaNoArmaFormulario() throws Exception {
        tasasJson = "{\"tasas\":[],\"montoMinimo\":1000,\"maxActivos\":5}";
        assertNull(conTasas().getTasas());
        tasasJson = "{\"tasas\":[{\"dias\":30,\"tna\":35.5}],\"maxActivos\":5}"; // sin mínimo
        assertNull(conTasas().getTasas());
        tasasJson = "{\"tasas\":[{\"dias\":30}],\"montoMinimo\":1000,\"maxActivos\":5}"; // sin TNA
        assertNull(conTasas().getTasas());
    }

    // ── P2: formulario ────────────────────────────────────────────────────────

    @Test
    public void p2_soloSePuedeElegirUnPlazoQueLlego() throws Exception {
        ConstitucionPlazoFijo c = conTasas();
        assertFalse("45 no vino en /tasas", c.elegirPlazo(45));
        assertFalse(c.elegirPlazo(0));
        assertEquals(Integer.valueOf(30), c.getPlazoDias());
        assertTrue(c.elegirPlazo(365));
        assertEquals(Integer.valueOf(365), c.getPlazoDias());
        // La regla pura tampoco deja pasar un plazo inventado
        TasasPlazoFijoResponse t = c.getTasas();
        assertEquals(ConstitucionPlazoFijo.MSG_PLAZO_INVALIDO, ConstitucionPlazoFijo.validar(t, 45, "5000", SALDO, 0));
    }

    @Test
    public void p2_validacionesLocalesAntesDelPost() throws Exception {
        ConstitucionPlazoFijo c = conTasas();
        TasasPlazoFijoResponse t = c.getTasas();
        assertEquals(ConstitucionPlazoFijo.MSG_MONTO_INVALIDO, ConstitucionPlazoFijo.validar(t, 30, "", SALDO, 0));
        assertEquals(ConstitucionPlazoFijo.MSG_MONTO_INVALIDO, ConstitucionPlazoFijo.validar(t, 30, "0", SALDO, 0));
        assertEquals(ConstitucionPlazoFijo.MSG_DECIMALES, ConstitucionPlazoFijo.validar(t, 30, "1000,001", SALDO, 0));
        assertEquals(ConstitucionPlazoFijo.MSG_MONTO_INVALIDO,
                ConstitucionPlazoFijo.validar(t, 30, "12345678901234", new BigDecimal("99999999999999"), 0));
        assertEquals("El monto mínimo para constituir un plazo fijo es $ 1.000,00.",
                ConstitucionPlazoFijo.validar(t, 30, "999,99", SALDO, 0));
        assertNull(ConstitucionPlazoFijo.validar(t, 30, "1000", SALDO, 0));
        assertEquals(ConstitucionPlazoFijo.MSG_SIN_SALDO, ConstitucionPlazoFijo.validar(t, 30, "1000", null, 0));
        assertEquals(ConstitucionPlazoFijo.MSG_INSUFICIENTE,
                ConstitucionPlazoFijo.validar(t, 30, "250000,01", SALDO, 0));
        assertNull("exacto al saldo se puede", ConstitucionPlazoFijo.validar(t, 30, "250000", SALDO, 0));

        c.setMonto("999");
        c.continuar(SALDO);
        assertEquals(ConstitucionPlazoFijo.Paso.FORMULARIO, c.getPaso());
        assertEquals("no salió ningún POST", 0, posts());
    }

    @Test
    public void p2_previewConLaFormulaDelBackend() throws Exception {
        ConstitucionPlazoFijo c = conTasas();
        c.setMonto("100000");
        assertEquals(new BigDecimal("2917.81"), c.getInteresPreview()); // 100000 × 35,5 × 30 / 36500
        assertTrue(c.elegirPlazo(180));
        c.setMonto("1000");
        assertEquals(new BigDecimal("197.26"), c.getInteresPreview()); // 1000 × 40 × 180 / 36500
        c.setMonto("");
        assertNull("sin monto no hay preview", c.getInteresPreview());
    }

    @Test
    public void p2_usarTodoPoneElSaldoExacto() throws Exception {
        ConstitucionPlazoFijo c = conTasas();
        c.usarTodo(new BigDecimal("12345.67"));
        assertEquals("12345,67", c.getMontoTexto());
        assertFalse("no deja tipear 3 decimales", c.setMonto("1,234"));
    }

    // ── P3: constituir ────────────────────────────────────────────────────────

    @Test
    public void p3_confirmarMandaMontoYPlazoExactosYDevuelveLoReal() throws Exception {
        respuestasPost.add(new MockResponse().setResponseCode(201).setBody(CREADO));
        ConstitucionPlazoFijo c = conTasas();
        c.setMonto("100000");
        c.continuar(SALDO);
        assertEquals("todavía no se mandó nada: primero confirma", 0, posts());
        c.confirmar();
        esperar("POST", () -> !c.isEnviando());

        assertEquals(ConstitucionPlazoFijo.Paso.EXITO, c.getPaso());
        assertEquals(1, posts());
        RecordedRequest tasas = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("/api/plazos-fijos/tasas", tasas.getPath());
        RecordedRequest post = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("POST", post.getMethod());
        assertTrue(post.getHeader("Authorization").startsWith("Bearer "));
        JsonObject body = JsonParser.parseString(post.getBody().readUtf8()).getAsJsonObject();
        assertEquals(2, body.size());
        assertEquals("el MONTO (no el total)", 0, new BigDecimal("100000").compareTo(body.get("monto").getAsBigDecimal()));
        assertEquals(30, body.get("plazoDias").getAsInt());

        assertEquals("ACTIVO", c.getResultado().getEstado());
        assertEquals(new BigDecimal("102917.81"), c.getResultado().getMontoTotal());
        assertEquals("2026-10-28", c.getResultado().getFechaVencimiento());
        assertEquals("refresca saldos, plazos fijos y notificaciones", 1, refrescos.get());
        assertEquals(0, rechazos.get());
    }

    @Test
    public void p3_decimalesSeMandanSinPerderPrecision() throws Exception {
        respuestasPost.add(new MockResponse().setResponseCode(201).setBody(CREADO));
        ConstitucionPlazoFijo c = conTasas();
        c.setMonto("1234,56");
        operar(c);
        server.takeRequest(1, TimeUnit.SECONDS);
        String body = server.takeRequest(1, TimeUnit.SECONDS).getBody().readUtf8();
        assertTrue(body, body.contains("\"monto\":1234.56"));
    }

    // ── P4: rechazos del backend con su texto ──────────────────────────────────

    private void rechazo(String mensaje) throws Exception {
        respuestasPost.add(new MockResponse().setResponseCode(400).setBody("{\"error\":\"" + mensaje + "\"}"));
        ConstitucionPlazoFijo c = conTasas();
        c.setMonto("5000");
        long tasasAntes = getsTasas();
        operar(c);
        assertEquals(mensaje, c.getError());
        assertEquals("sigue en el resumen: no se movió nada", ConstitucionPlazoFijo.Paso.CONFIRMAR, c.getPaso());
        assertEquals(0, refrescos.get());
        esperar("vuelve a pedir /tasas (la config pudo cambiar)", () -> getsTasas() == tasasAntes + 1);
        assertEquals("refresca la lista (activos)", 1, rechazos.get());
        rechazos.set(0);
    }

    @Test
    public void p4_cadaRechazoSeMuestraConSuTexto() throws Exception {
        rechazo("El plazo elegido no es valido. Los plazos disponibles son: 30, 60, 90, 180, 365 dias");
        rechazo("El monto minimo para constituir un plazo fijo es $ 1000.00");
        rechazo("Ya tenes el maximo de 5 plazos fijos activos");
        rechazo("No tenes saldo suficiente para constituir este plazo fijo");
    }

    @Test
    public void p4_rateLimitYValidacionSinCuerpo() throws Exception {
        respuestasPost.add(new MockResponse().setResponseCode(429));
        ConstitucionPlazoFijo c = conTasas();
        c.setMonto("5000");
        operar(c);
        assertEquals(ApiErrores.MSG_RATE_LIMIT, c.getError());

        respuestasPost.add(new MockResponse().setResponseCode(400).setBody("{\"monto\":\"El monto tiene mas decimales\"}"));
        c.confirmar();
        esperar("POST", () -> !c.isEnviando());
        assertEquals(ConstitucionPlazoFijo.MSG_NO_SE_PUDO, c.getError());
    }

    @Test
    public void p4_conElMaximoDeActivosElBotonYaEstabaDeshabilitado() throws Exception {
        ConstitucionPlazoFijo c = conTasas();
        c.setActivos(4);
        assertFalse(c.isLimiteAlcanzado());
        c.setActivos(5);
        assertTrue("5/5: Constituir deshabilitado", c.isLimiteAlcanzado());
        c.setMonto("5000");
        c.continuar(SALDO);
        assertEquals("Ya tenés el máximo de 5 plazos fijos activos", c.getError());
        assertEquals(ConstitucionPlazoFijo.Paso.FORMULARIO, c.getPaso());
        assertEquals("nunca se intentó el POST", 0, posts());
    }

    @Test
    public void p4_siSeLlenaMientrasMiraElResumenTampocoSeManda() throws Exception {
        ConstitucionPlazoFijo c = conTasas();
        c.setActivos(4);
        c.setMonto("5000");
        c.continuar(SALDO);
        assertEquals(ConstitucionPlazoFijo.Paso.CONFIRMAR, c.getPaso());
        c.setActivos(5); // otro dispositivo constituyó uno; la lista se enteró
        c.confirmar();
        assertEquals("Ya tenés el máximo de 5 plazos fijos activos", c.getError());
        Thread.sleep(150);
        assertEquals(0, posts());
    }

    @Test
    public void p4_sinSaberCuantosActivosTieneNoSeBloquea() throws Exception {
        ConstitucionPlazoFijo c = conTasas();
        assertNull(c.getActivos());
        assertFalse("sin lista no se inventa el tope: decide el backend", c.isLimiteAlcanzado());
    }

    // ── P8: un solo pedido e incertidumbre ────────────────────────────────────

    @Test
    public void p8_dobleTapEsUnSoloPost() throws Exception {
        respuestasPost.add(new MockResponse().setResponseCode(201).setBody(CREADO).setBodyDelay(300, TimeUnit.MILLISECONDS));
        ConstitucionPlazoFijo c = conTasas();
        c.setMonto("100000");
        c.continuar(SALDO);
        c.confirmar();
        c.confirmar();
        c.confirmar();
        assertTrue(c.isEnviando());
        esperar("POST", () -> !c.isEnviando());
        Thread.sleep(100);
        assertEquals(1, posts());
        c.confirmar(); // ya en ÉXITO: no hace nada
        Thread.sleep(100);
        assertEquals(1, posts());
    }

    @Test
    public void p8_timeoutEsInciertoRefrescaYNoReintenta() throws Exception {
        ApiService lento = (ApiService) java.lang.reflect.Proxy.newProxyInstance(ApiService.class.getClassLoader(),
                new Class<?>[]{ApiService.class}, (proxy, metodo, args) -> {
                    Object r = metodo.invoke(api, args);
                    if (r instanceof retrofit2.Call) ((retrofit2.Call<?>) r).timeout().timeout(300, TimeUnit.MILLISECONDS);
                    return r;
                });
        respuestasPost.add(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        ConstitucionPlazoFijo c = nueva(lento);
        c.cargarTasas();
        esperar("tasas", () -> c.getTasas() != null);
        c.setMonto("5000");
        operar(c);
        assertEquals(ConstitucionPlazoFijo.Paso.INCIERTO, c.getPaso());
        assertEquals(ConstitucionPlazoFijo.MSG_INCIERTO, c.getError());
        assertEquals("refresca para ver si se hizo", 1, refrescos.get());
        c.confirmar();
        Thread.sleep(300);
        assertEquals(1, posts());
    }

    @Test
    public void p8_corteDespuesDeEnviarEsIncierto() throws Exception {
        respuestasPost.add(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        respuestasPost.add(new MockResponse().setResponseCode(201).setBody(CREADO));
        ConstitucionPlazoFijo c = conTasas();
        c.setMonto("5000");
        operar(c);
        assertEquals(ConstitucionPlazoFijo.Paso.INCIERTO, c.getPaso());
        Thread.sleep(200);
        assertEquals("OkHttp no reintentó el POST", 1, posts());
        assertEquals(1, refrescos.get());
    }

    @Test
    public void p8_2xxSinBodyEsIncierto() throws Exception {
        respuestasPost.add(new MockResponse().setResponseCode(201));
        ConstitucionPlazoFijo c = conTasas();
        c.setMonto("5000");
        operar(c);
        assertEquals(ConstitucionPlazoFijo.Paso.INCIERTO, c.getPaso());
        assertEquals(1, refrescos.get());
    }

    @Test
    public void p8_sinConexionAntesDeEnviarSePuedeReintentar() throws Exception {
        ConstitucionPlazoFijo con = conTasas();
        MockWebServer apagado = new MockWebServer();
        apagado.start();
        String url = apagado.url("/").toString();
        apagado.shutdown();
        ApiService caido = RetrofitClient.crear(url, () -> "x", System::currentTimeMillis, () -> {}, false, false);
        ConstitucionPlazoFijo c = new ConstitucionPlazoFijo(() -> api, () -> caido, refrescos::incrementAndGet,
                rechazos::incrementAndGet);
        c.cargarTasas();
        esperar("tasas", () -> c.getTasas() != null);
        assertNotNull(con.getTasas());
        c.setMonto("5000");
        operar(c);
        assertEquals(ApiErrores.MSG_SIN_CONEXION, c.getError());
        assertEquals(ConstitucionPlazoFijo.Paso.CONFIRMAR, c.getPaso());
        assertEquals(0, refrescos.get());
    }

    @Test
    public void restaurarTrasMuerteDelProceso() {
        ConstitucionPlazoFijo c = nueva();
        c.setMonto("5000");
        c.restaurar(90, ConstitucionPlazoFijo.Paso.CONFIRMAR, null);
        assertEquals(ConstitucionPlazoFijo.Paso.CONFIRMAR, c.getPaso());
        assertEquals(90, c.getDiasAConfirmar());
        c.confirmar(); // sin tasas todavía: no se manda
        assertEquals(ConstitucionPlazoFijo.MSG_SIN_TASAS, c.getError());

        ConstitucionPlazoFijo incierta = nueva();
        incierta.restaurar(30, ConstitucionPlazoFijo.Paso.INCIERTO, null);
        assertEquals(ConstitucionPlazoFijo.MSG_INCIERTO, incierta.getError());
    }
}
