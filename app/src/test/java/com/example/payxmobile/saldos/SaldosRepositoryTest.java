package com.example.payxmobile.saldos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.JwtFalso;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

/**
 * Repositorio de saldos contra un backend simulado y un reloj controlado:
 * S6 (estados), S7 (auto-refresco), S8 (refrescar externo), S9 (logout sin fugas).
 */
public class SaldosRepositoryTest {

    private static final String PERFIL = "/api/perfil";
    private static final String COTIZ = "/api/cotizacion/cripto";
    private static final String COTIZACIONES_OK = "[{\"simbolo\":\"BTC\",\"nombre\":\"Bitcoin\",\"precio\":127818114,\"desactualizada\":false}]";

    private MockWebServer server;
    private final Map<String, ConcurrentLinkedQueue<MockResponse>> respuestas = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> pedidos = new ConcurrentHashMap<>();
    private ProgramadorFalso reloj;
    private SaldosRepository repo;

    /** Programador con tiempo manual: las tareas corren solo al llamar a avanzar(). */
    static class ProgramadorFalso implements SaldosRepository.Programador {
        static class Tarea { long cuando; Runnable r; boolean cancelada; }
        final List<Tarea> tareas = new ArrayList<>();
        long ahora = 0;

        @Override
        public synchronized Runnable programar(Runnable tarea, long demoraMs) {
            Tarea t = new Tarea();
            t.cuando = ahora + demoraMs;
            t.r = tarea;
            tareas.add(t);
            return () -> t.cancelada = true;
        }

        void avanzar(long ms) {
            long fin = ahora + ms;
            while (true) {
                Tarea proxima = null;
                synchronized (this) {
                    for (Tarea t : tareas) {
                        if (!t.cancelada && t.cuando <= fin && (proxima == null || t.cuando < proxima.cuando)) proxima = t;
                    }
                    if (proxima == null) { ahora = fin; return; }
                    tareas.remove(proxima);
                    ahora = proxima.cuando;
                }
                proxima.r.run();
            }
        }

        synchronized int pendientes() {
            int n = 0;
            for (Iterator<Tarea> it = tareas.iterator(); it.hasNext(); ) if (!it.next().cancelada) n++;
            return n;
        }
    }

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                pedidos.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
                ConcurrentLinkedQueue<MockResponse> cola = respuestas.get(path);
                MockResponse r = cola != null ? cola.poll() : null;
                return r != null ? r : new MockResponse().setResponseCode(404);
            }
        });
        server.start();
        String token = JwtFalso.conExp(System.currentTimeMillis() / 1000 + 7200);
        ApiService api = RetrofitClient.crear(server.url("/").toString(), () -> token,
                System::currentTimeMillis, () -> {}, false);
        reloj = new ProgramadorFalso();
        repo = new SaldosRepository(() -> api, reloj);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    /** Envuelve la API para que cada llamada venza a los 300 ms (en la app el timeout es de 30 s). */
    private static ApiService conTimeoutCorto(ApiService api) {
        return (ApiService) java.lang.reflect.Proxy.newProxyInstance(ApiService.class.getClassLoader(),
                new Class<?>[]{ApiService.class}, (proxy, metodo, args) -> {
                    Object r = metodo.invoke(api, args);
                    if (r instanceof retrofit2.Call) ((retrofit2.Call<?>) r).timeout().timeout(300, TimeUnit.MILLISECONDS);
                    return r;
                });
    }

    private void responder(String path, MockResponse r) {
        respuestas.computeIfAbsent(path, k -> new ConcurrentLinkedQueue<>()).add(r);
    }

    private static MockResponse perfil(String saldoPesos) {
        return new MockResponse().setBody("{\"saldoPesos\":" + saldoPesos + ",\"saldoUsd\":150.00,\"saldoBtc\":0E-8}");
    }

    private int pedidosA(String path) {
        AtomicInteger n = pedidos.get(path);
        return n != null ? n.get() : 0;
    }

    private static void esperar(String que, BooleanSupplier condicion) throws InterruptedException {
        long limite = System.currentTimeMillis() + 5000;
        while (!condicion.getAsBoolean()) {
            if (System.currentTimeMillis() > limite) throw new AssertionError("Timeout esperando: " + que);
            Thread.sleep(10);
        }
    }

    private void esperarQuieto() throws InterruptedException {
        esperar("sin requests en vuelo", () -> {
            EstadoSaldos e = repo.getEstado();
            return !e.cargandoSaldo && !e.cargandoCotizaciones;
        });
    }

    // ── S6: estados ──────────────────────────────────────────────────────────

    @Test
    public void s6_antesDeCargarNoHaySaldo() {
        EstadoSaldos e = repo.getEstado();
        assertNull(e.perfil);
        assertTrue(e.esPrimeraCarga());
    }

    @Test
    public void s6_errorEnPrimeraCargaSinInventarCeroYReintentarFunciona() throws Exception {
        responder(PERFIL, new MockResponse().setResponseCode(500));
        responder(COTIZ, new MockResponse().setBody(COTIZACIONES_OK));
        repo.refrescarTodo();
        esperarQuieto();
        EstadoSaldos e = repo.getEstado();
        assertNull("nunca un 0 inventado", e.perfil);
        assertEquals(ApiErrores.MSG_SERVIDOR, e.errorPrimeraCarga);

        // "Reintentar"
        responder(PERFIL, perfil("5000.00"));
        repo.refrescar();
        esperarQuieto();
        e = repo.getEstado();
        assertEquals(new BigDecimal("5000.00"), e.perfil.getSaldoPesos());
        assertNull(e.errorPrimeraCarga);
    }

    @Test
    public void s6_timeoutYSinConexionEnPrimeraCarga() throws Exception {
        // Backend caído: servidor apagado (OkHttp reintenta solo los cortes, así que no se simula con un corte)
        MockWebServer apagado = new MockWebServer();
        apagado.start();
        String url = apagado.url("/").toString();
        apagado.shutdown();
        ApiService sinBackend = RetrofitClient.crear(url, () -> "x", System::currentTimeMillis, () -> {}, false);
        SaldosRepository sinConexion = new SaldosRepository(() -> sinBackend, reloj);
        sinConexion.refrescar();
        esperar("fallo de conexión", () -> !sinConexion.getEstado().cargandoSaldo);
        assertNull(sinConexion.getEstado().perfil);
        assertEquals(ApiErrores.MSG_SIN_CONEXION, sinConexion.getEstado().errorPrimeraCarga);

        // Timeout: el servidor acepta pero nunca responde
        responder(PERFIL, new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        ApiService lento = RetrofitClient.crear(server.url("/").toString(), () -> "x", System::currentTimeMillis, () -> {}, false);
        SaldosRepository conTimeout = new SaldosRepository(() -> conTimeoutCorto(lento), reloj);
        conTimeout.refrescar();
        esperar("timeout", () -> !conTimeout.getEstado().cargandoSaldo);
        assertNull(conTimeout.getEstado().perfil);
        assertEquals(ApiErrores.MSG_TIMEOUT, conTimeout.getEstado().errorPrimeraCarga);
    }

    @Test
    public void s6_refrescoFallidoConDatoPrevioMantieneElValor() throws Exception {
        responder(PERFIL, perfil("5000.00"));
        repo.refrescar();
        esperarQuieto();
        responder(PERFIL, new MockResponse().setResponseCode(503));
        repo.refrescar();
        esperarQuieto();
        EstadoSaldos e = repo.getEstado();
        assertEquals(new BigDecimal("5000.00"), e.perfil.getSaldoPesos());
        assertTrue(e.desactualizado);
        assertNull(e.errorPrimeraCarga);
        // y al volver la conexión se limpia el aviso
        responder(PERFIL, perfil("5000.00"));
        repo.refrescar();
        esperarQuieto();
        assertFalse(repo.getEstado().desactualizado);
    }

    @Test
    public void s6_forbiddenEnPerfilEsSesionInvalidaYCortaElAutoRefresco() throws Exception {
        responder(PERFIL, new MockResponse().setResponseCode(403));
        responder(COTIZ, new MockResponse().setBody(COTIZACIONES_OK));
        repo.iniciarAutoRefresco();
        esperarQuieto();
        assertTrue(repo.getEstado().sesionInvalida);
        reloj.avanzar(60_000);
        Thread.sleep(200);
        assertEquals("sin bucle de reintentos", 1, pedidosA(PERFIL));
    }

    @Test
    public void s6_cotizacionFallidaMantieneLasUltimas() throws Exception {
        responder(COTIZ, new MockResponse().setBody(COTIZACIONES_OK));
        repo.refrescarTodo();
        esperarQuieto();
        responder(COTIZ, new MockResponse().setResponseCode(503)
                .setBody("{\"error\":\"No se pudo obtener la cotizacion de las criptomonedas en este momento. Intenta de nuevo en unos segundos\"}"));
        repo.refrescarTodo();
        esperarQuieto();
        assertEquals(new BigDecimal("127818114"), repo.getEstado().precio("BTC"));
        assertNull("una cripto que falta no tiene precio (no es 0)", repo.getEstado().precio("ETH"));
    }

    // ── S7: auto-refresco ────────────────────────────────────────────────────

    @Test
    public void s7_enPrimerPlanoSaldoCada10sYCotizacionCada20s() throws Exception {
        for (int i = 0; i < 10; i++) {
            responder(PERFIL, perfil("5000.00"));
            responder(COTIZ, new MockResponse().setBody(COTIZACIONES_OK));
        }
        repo.iniciarAutoRefresco();                  // al entrar: refresca YA
        esperarQuieto();
        assertEquals(1, pedidosA(PERFIL));
        assertEquals(1, pedidosA(COTIZ));

        reloj.avanzar(10_000);
        esperarQuieto();
        assertEquals(2, pedidosA(PERFIL));
        assertEquals(1, pedidosA(COTIZ));

        reloj.avanzar(10_000);                       // t = 20 s
        esperarQuieto();
        assertEquals(3, pedidosA(PERFIL));
        assertEquals(2, pedidosA(COTIZ));
    }

    @Test
    public void s7_enSegundoPlanoNoDisparaYAlVolverRefrescaEnseguida() throws Exception {
        for (int i = 0; i < 10; i++) {
            responder(PERFIL, perfil("5000.00"));
            responder(COTIZ, new MockResponse().setBody(COTIZACIONES_OK));
        }
        repo.iniciarAutoRefresco();
        esperarQuieto();
        repo.detenerAutoRefresco();                  // onStop
        assertEquals(0, reloj.pendientes());
        reloj.avanzar(120_000);
        Thread.sleep(200);
        assertEquals(1, pedidosA(PERFIL));
        assertEquals(1, pedidosA(COTIZ));

        repo.iniciarAutoRefresco();                  // onStart
        esperarQuieto();
        assertEquals(2, pedidosA(PERFIL));
        assertEquals(2, pedidosA(COTIZ));
    }

    @Test
    public void s7_iniciarDosVecesNoDuplicaLosTimers() throws Exception {
        for (int i = 0; i < 5; i++) {
            responder(PERFIL, perfil("5000.00"));
            responder(COTIZ, new MockResponse().setBody(COTIZACIONES_OK));
        }
        repo.iniciarAutoRefresco();
        repo.iniciarAutoRefresco();
        esperarQuieto();
        assertEquals(2, reloj.pendientes());
    }

    @Test
    public void s7_requestLentaNoAcumula() throws Exception {
        responder(PERFIL, perfil("5000.00").setHeadersDelay(1500, TimeUnit.MILLISECONDS));
        responder(PERFIL, perfil("5000.00"));
        repo.refrescar();
        esperar("request en vuelo", () -> pedidosA(PERFIL) == 1);
        repo.refrescar();                            // tick mientras la anterior sigue en vuelo
        repo.refrescar();
        assertTrue(repo.getEstado().cargandoSaldo);
        esperarQuieto();
        Thread.sleep(200);
        assertEquals("se saltearon los refrescos solapados", 1, pedidosA(PERFIL));
    }

    // ── S8: refrescar() externo ─────────────────────────────────────────────

    @Test
    public void s8_refrescarTraeElSaldoNuevoYAvisaALosObservadores() throws Exception {
        responder(PERFIL, perfil("5000.00"));
        responder(PERFIL, perfil("3999.99"));        // ej: después de una transferencia
        List<BigDecimal> vistos = new java.util.concurrent.CopyOnWriteArrayList<>();
        repo.observar(e -> { if (e.perfil != null) vistos.add(e.perfil.getSaldoPesos()); });
        repo.refrescar();
        esperarQuieto();
        repo.refrescar();
        esperarQuieto();
        assertEquals(new BigDecimal("3999.99"), repo.getEstado().perfil.getSaldoPesos());
        assertTrue(vistos.contains(new BigDecimal("5000.00")));
        assertEquals(new BigDecimal("3999.99"), vistos.get(vistos.size() - 1));
    }

    // ── S9: logout ──────────────────────────────────────────────────────────

    @Test
    public void s9_limpiarBorraTodoYDescartaRespuestasTardias() throws Exception {
        responder(PERFIL, perfil("5000.00"));
        responder(COTIZ, new MockResponse().setBody(COTIZACIONES_OK));
        repo.iniciarAutoRefresco();
        esperarQuieto();
        assertNotNull(repo.getEstado().perfil);

        // Cuenta A tiene una request lenta en vuelo cuando cierra sesión
        responder(PERFIL, perfil("777777.77").setHeadersDelay(800, TimeUnit.MILLISECONDS));
        repo.refrescar();
        esperar("request lenta en vuelo", () -> pedidosA(PERFIL) == 2);
        repo.limpiar();

        EstadoSaldos e = repo.getEstado();
        assertNull(e.perfil);
        assertTrue(e.cotizaciones.isEmpty());
        assertTrue(e.esPrimeraCarga());
        assertEquals("el auto-refresco quedó cortado", 0, reloj.pendientes());

        Thread.sleep(1200); // llega la respuesta vieja: se descarta
        assertNull("la cuenta B no ve el saldo de A", repo.getEstado().perfil);

        // Cuenta B carga lo suyo
        responder(PERFIL, perfil("10.00"));
        repo.refrescar();
        esperarQuieto();
        assertEquals(new BigDecimal("10.00"), repo.getEstado().perfil.getSaldoPesos());
    }
}
