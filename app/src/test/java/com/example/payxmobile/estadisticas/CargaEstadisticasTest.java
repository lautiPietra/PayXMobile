package com.example.payxmobile.estadisticas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.JwtFalso;
import com.example.payxmobile.model.EstadisticaGastosResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/** GET /api/estadisticas/gastos contra MockWebServer: E1, E2, E6, E7 y E8. */
public class CargaEstadisticasTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 28);

    private MockWebServer server;
    private ApiService api;
    private final Map<String, ConcurrentLinkedQueue<MockResponse>> respuestas = new ConcurrentHashMap<>();
    private final List<RecordedRequest> pedidos = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest r) {
                pedidos.add(r);
                ConcurrentLinkedQueue<MockResponse> cola = respuestas.get(r.getPath());
                MockResponse m = cola != null ? cola.poll() : null;
                return m != null ? m : new MockResponse().setResponseCode(404);
            }
        });
        server.start();
        String token = JwtFalso.conExp(System.currentTimeMillis() / 1000 + 7200);
        api = RetrofitClient.crear(server.url("/").toString(), () -> token, System::currentTimeMillis, () -> {}, false);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private void responder(int dias, MockResponse r) {
        respuestas.computeIfAbsent("/api/estadisticas/gastos?dias=" + dias, k -> new ConcurrentLinkedQueue<>()).add(r);
    }

    private static MockResponse ok(String json) {
        return new MockResponse().setBody(json);
    }

    private CargaEstadisticas nueva() {
        return nueva(api);
    }

    private CargaEstadisticas nueva(ApiService s) {
        return new CargaEstadisticas(() -> s, () -> HOY);
    }

    private static void esperar(String que, BooleanSupplier c) throws InterruptedException {
        long fin = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) throw new AssertionError("Timeout: " + que);
            Thread.sleep(10);
        }
    }

    private static void listo(CargaEstadisticas c) throws InterruptedException {
        esperar("carga", () -> c.getEstado() != CargaEstadisticas.Estado.CARGANDO);
    }

    // ── E1: el "dias" de cada atajo ────────────────────────────────────────────

    @Test
    public void e1_cadaAtajoMandaSuDias() throws Exception {
        String sieteDias = EstadisticasLogicaTest.SIETE_DIAS;
        responder(30, ok(sieteDias));
        responder(7, ok(sieteDias));
        responder(28, ok(sieteDias));
        responder(90, ok(sieteDias));
        responder(0, ok(EstadisticasLogicaTest.TODO));
        CargaEstadisticas c = nueva();
        assertEquals("nada se pide al construir", 0, pedidos.size());
        c.refrescar(); // al abrir: el default (30), mandado EXPLÍCITO
        listo(c);
        for (PeriodoEstadistica p : new PeriodoEstadistica[]{PeriodoEstadistica.SIETE_DIAS, PeriodoEstadistica.ESTE_MES,
                PeriodoEstadistica.NOVENTA_DIAS, PeriodoEstadistica.TODO}) {
            c.seleccionar(p);
            listo(c);
        }
        assertEquals(5, pedidos.size());
        String[] esperados = {"dias=30", "dias=7", "dias=28", "dias=90", "dias=0"};
        for (int i = 0; i < esperados.length; i++) {
            assertEquals("/api/estadisticas/gastos?" + esperados[i], pedidos.get(i).getPath());
            assertTrue(pedidos.get(i).getHeader("Authorization").startsWith("Bearer "));
        }
        // El mismo atajo ya cargado no vuelve a pedir
        c.seleccionar(PeriodoEstadistica.TODO);
        Thread.sleep(100);
        assertEquals(5, pedidos.size());
    }

    // ── E2: mapeo ─────────────────────────────────────────────────────────────

    @Test
    public void e2_mapeoDeLaRespuesta() throws Exception {
        responder(0, ok(EstadisticasLogicaTest.TODO));
        CargaEstadisticas c = nueva();
        c.seleccionar(PeriodoEstadistica.TODO);
        listo(c);
        EstadisticaGastosResponse d = c.getDatos();
        assertNotNull(d);
        assertEquals(new BigDecimal("250000.00"), d.getTotalGastado());
        assertNull(d.getDesde());
        assertNull(d.getTotalPeriodoAnterior());
        assertTrue(d.getPorDia().isEmpty());
        assertEquals(120, d.getCantidadOperaciones());
    }

    // ── E6 / E7: errores ────────────────────────────────────────────────────────

    @Test
    public void e6_rateLimitMensajeClaroYReintentable() throws Exception {
        responder(30, new MockResponse().setResponseCode(429)
                .setBody("{\"error\":\"Demasiadas solicitudes. Intenta de nuevo en unos minutos\"}"));
        CargaEstadisticas c = nueva();
        c.refrescar();
        listo(c);
        assertEquals(CargaEstadisticas.Estado.ERROR, c.getEstado());
        assertEquals(CargaEstadisticas.MSG_RATE_LIMIT, c.getError());
        assertNull("sin un total inventado", c.getDatos());
        responder(30, ok(EstadisticasLogicaTest.SIETE_DIAS));
        c.refrescar(); // "Reintentar"
        listo(c);
        assertEquals(CargaEstadisticas.Estado.LISTO, c.getEstado());
    }

    @Test
    public void e7_403SinBodyEsSesionInvalida() throws Exception {
        responder(30, new MockResponse().setResponseCode(403));
        CargaEstadisticas c = nueva();
        c.refrescar();
        listo(c);
        assertTrue("la pantalla cierra la sesión (una vez: SesionUtils ignora si ya no hay token)", c.isSesionInvalida());
        assertNull(c.getDatos());
    }

    @Test
    public void e7_servidorCaidoOSinConexionSinInventarUnCero() throws Exception {
        responder(30, new MockResponse().setResponseCode(500));
        CargaEstadisticas c = nueva();
        c.refrescar();
        listo(c);
        assertEquals(ApiErrores.MSG_SERVIDOR, c.getError());
        assertNull("nunca un $ 0 inventado", c.getDatos());
        assertFalse(c.isSesionInvalida());

        MockWebServer apagado = new MockWebServer();
        apagado.start();
        String url = apagado.url("/").toString();
        apagado.shutdown();
        ApiService caido = RetrofitClient.crear(url, () -> "x", System::currentTimeMillis, () -> {}, false);
        CargaEstadisticas sinRed = nueva(caido);
        sinRed.refrescar();
        listo(sinRed);
        assertEquals(ApiErrores.MSG_SIN_CONEXION, sinRed.getError());
        assertNull(sinRed.getDatos());

        responder(30, new MockResponse().setBody("{\"desde\":\"2026-09-01\"}")); // sin total ni listas
        CargaEstadisticas incompleta = nueva();
        incompleta.refrescar();
        listo(incompleta);
        assertEquals(ApiErrores.MSG_RESPUESTA_INESPERADA, incompleta.getError());
        assertNull(incompleta.getDatos());
    }

    // ── E8: cambiar de atajo actualiza TODO, sin mezclar respuestas ────────────

    @Test
    public void e8_unaRespuestaViejaQueLlegaTardeSeDescarta() throws Exception {
        responder(7, ok(EstadisticasLogicaTest.SIETE_DIAS).setBodyDelay(400, TimeUnit.MILLISECONDS));
        responder(0, ok(EstadisticasLogicaTest.TODO));
        CargaEstadisticas c = nueva();
        c.seleccionar(PeriodoEstadistica.SIETE_DIAS);
        c.seleccionar(PeriodoEstadistica.TODO); // tocó otro atajo antes de que vuelva el primero
        listo(c);
        Thread.sleep(600); // llega la de 7 días, tarde
        assertEquals(PeriodoEstadistica.TODO, c.getPeriodo());
        EstadisticaGastosResponse d = c.getDatos();
        assertEquals("los datos son los del atajo elegido", new BigDecimal("250000.00"), d.getTotalGastado());
        assertEquals(120, d.getCantidadOperaciones());
        assertNull(d.getTotalPeriodoAnterior());
    }

    @Test
    public void e8_mientrasCargaOtroAtajoNoSeMuestranLosDatosViejos() throws Exception {
        responder(30, ok(EstadisticasLogicaTest.SIETE_DIAS));
        responder(0, ok(EstadisticasLogicaTest.TODO).setBodyDelay(300, TimeUnit.MILLISECONDS));
        CargaEstadisticas c = nueva();
        c.refrescar();
        listo(c);
        assertNotNull(c.getDatos());
        c.seleccionar(PeriodoEstadistica.TODO);
        assertEquals(CargaEstadisticas.Estado.CARGANDO, c.getEstado());
        assertNull("no se muestra lo de 30 días bajo 'Todo el tiempo'", c.getDatos());
        listo(c);
        assertEquals(new BigDecimal("250000.00"), c.getDatos().getTotalGastado());
    }

    @Test
    public void e8_siFallaElNuevoAtajoNoQuedanLosDatosDelAnterior() throws Exception {
        responder(30, ok(EstadisticasLogicaTest.SIETE_DIAS));
        responder(7, new MockResponse().setResponseCode(503));
        CargaEstadisticas c = nueva();
        c.refrescar();
        listo(c);
        c.seleccionar(PeriodoEstadistica.SIETE_DIAS);
        listo(c);
        assertEquals(CargaEstadisticas.Estado.ERROR, c.getEstado());
        assertNull(c.getDatos());
        // Refrescar el MISMO atajo que falla después de haber cargado: se conservan sus datos
        responder(7, ok(EstadisticasLogicaTest.SIETE_DIAS));
        c.refrescar();
        listo(c);
        responder(7, new MockResponse().setResponseCode(503));
        c.refrescar();
        listo(c);
        assertEquals(CargaEstadisticas.Estado.ERROR, c.getEstado());
        assertNotNull("mismo período: se siguen viendo sus datos, con el error", c.getDatos());
    }
}
