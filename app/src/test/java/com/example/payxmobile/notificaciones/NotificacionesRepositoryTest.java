package com.example.payxmobile.notificaciones;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.JwtFalso;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.saldos.SaldosRepository;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
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

/**
 * Infraestructura de notificaciones contra un backend simulado y un reloj controlado:
 * D1 badge, D2 lista tal cual, D3 estados, D4 marcar leídas, D6 refresco/polling, D7 errores.
 */
public class NotificacionesRepositoryTest {

    private static final String LISTA = "/api/notificaciones";
    private static final String SIN_LEER = "/api/notificaciones/sin-leer";
    private static final String LEER = "/api/notificaciones/leer";

    static final String DOS_NOTIFICACIONES = "["
            + "{\"id\":12,\"usuarioId\":\"8a6e0804-2bd0-4672-b79d-d97027f9071a\",\"plantillaCodigo\":\"DOLARES_COMPRADOS\","
            + "\"mensaje\":\"Compraste US$ 3,55 por $ 5.000,00 (cotización $ 1.410,00) el 27/09/2026 a las 10:15.\","
            + "\"leida\":false,\"fecha\":\"2026-09-27T13:15:30.123456Z\"},"
            + "{\"id\":11,\"usuarioId\":\"8a6e0804-2bd0-4672-b79d-d97027f9071a\",\"plantillaCodigo\":\"CODIGO_INVENTADO\","
            + "\"mensaje\":\"Algo nuevo <b>sin</b> título {{x}}\",\"leida\":false,\"fecha\":\"2026-09-27T13:00:00Z\"}]";

    private MockWebServer server;
    private final Map<String, ConcurrentLinkedQueue<MockResponse>> respuestas = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> pedidos = new ConcurrentHashMap<>();
    private final List<String> metodos = new ArrayList<>();
    private Reloj reloj;
    private NotificacionesRepository repo;

    /** Programador con tiempo manual: las tareas corren solo al llamar a avanzar(). */
    static class Reloj implements SaldosRepository.Programador {
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
            for (Tarea t : tareas) if (!t.cancelada) n++;
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
                synchronized (metodos) { metodos.add(request.getMethod() + " " + path); }
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
        reloj = new Reloj();
        repo = new NotificacionesRepository(() -> api, reloj);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private void responder(String path, MockResponse r) {
        respuestas.computeIfAbsent(path, k -> new ConcurrentLinkedQueue<>()).add(r);
    }

    private static MockResponse cantidad(long n) {
        return new MockResponse().setBody("{\"cantidad\":" + n + "}");
    }

    private static MockResponse json(String body) {
        return new MockResponse().setBody(body);
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

    /** Espera a que lleguen n pedidos a ese path y a que el repositorio los procese. */
    private void esperarPedidos(String path, int n) throws InterruptedException {
        esperar(n + " pedidos a " + path, () -> pedidosA(path) >= n);
        Thread.sleep(150);
    }

    // ── D1: badge = /sin-leer ────────────────────────────────────────────────

    @Test
    public void d1_badgeEsSinLeerYSeOcultaEnCero() throws Exception {
        assertNull("antes de cargar no se sabe: sin badge", repo.getEstado().sinLeer);
        assertFalse(repo.getEstado().mostrarBadge());

        responder(SIN_LEER, cantidad(2));
        responder(LISTA, json(DOS_NOTIFICACIONES));
        repo.refrescar();
        esperar("cantidad 2", () -> Long.valueOf(2).equals(repo.getEstado().sinLeer));
        assertTrue(repo.getEstado().mostrarBadge());
        assertEquals("2", repo.getEstado().textoBadge());
        // Precarga: el panel ya tiene la lista cuando se abre
        esperar("lista precargada", () -> repo.getEstado().lista != null);
        assertEquals(2, repo.getEstado().lista.size());

        responder(SIN_LEER, cantidad(2));
        repo.refrescar();
        esperarPedidos(SIN_LEER, 2);
        assertEquals("el conteo coincide: no se vuelve a pedir la lista", 1, pedidosA(LISTA));

        responder(SIN_LEER, cantidad(0));
        repo.refrescar();
        esperar("cantidad 0", () -> Long.valueOf(0).equals(repo.getEstado().sinLeer));
        assertFalse("sin badge en 0", repo.getEstado().mostrarBadge());
        assertTrue("0 sin leer = lista vacía, sin pedirla", repo.getEstado().vacia());
        assertEquals(1, pedidosA(LISTA));

        responder(SIN_LEER, cantidad(150));
        repo.refrescar();
        esperar("cantidad 150", () -> Long.valueOf(150).equals(repo.getEstado().sinLeer));
        assertEquals("99+", repo.getEstado().textoBadge());
    }

    @Test
    public void d1_siFallaElConteoSeMantieneElUltimo() throws Exception {
        responder(SIN_LEER, cantidad(2));
        repo.refrescar();
        esperar("cantidad 2", () -> Long.valueOf(2).equals(repo.getEstado().sinLeer));
        responder(SIN_LEER, new MockResponse().setResponseCode(500));
        repo.refrescar();
        esperarPedidos(SIN_LEER, 2);
        assertEquals(Long.valueOf(2), repo.getEstado().sinLeer);
    }

    // ── D2: lista y mensaje tal cual ─────────────────────────────────────────

    @Test
    public void d2_listaSeMuestraTalCualIncluidoCodigoDesconocido() throws Exception {
        responder(SIN_LEER, cantidad(2));
        responder(LISTA, json(DOS_NOTIFICACIONES));
        repo.abrirPanel();
        esperar("lista", () -> repo.getEstado().lista != null);
        NotificacionesRepository.Estado e = repo.getEstado();
        assertEquals("ninguna fila se esconde", 2, e.lista.size());
        assertEquals("orden del backend", Long.valueOf(12), e.lista.get(0).getId());
        assertEquals("DOLARES_COMPRADOS", e.lista.get(0).getPlantillaCodigo());
        assertEquals("mensaje sin reprocesar",
                "Compraste US$ 3,55 por $ 5.000,00 (cotización $ 1.410,00) el 27/09/2026 a las 10:15.",
                e.lista.get(0).getMensaje());
        assertEquals("Algo nuevo <b>sin</b> título {{x}}", e.lista.get(1).getMensaje());
        assertEquals("Compra de dólares", TitulosNotificacion.de(e.lista.get(0).getPlantillaCodigo()));
        assertEquals("Notificación", TitulosNotificacion.de(e.lista.get(1).getPlantillaCodigo()));
        assertEquals("2026-09-27T13:15:30.123456Z", e.lista.get(0).getFecha());
    }

    // ── D3: vacía solo tras carga exitosa con 0 ──────────────────────────────

    @Test
    public void d3_cargandoErrorYVaciaSonEstadosDistintos() throws Exception {
        NotificacionesRepository.Estado inicial = repo.getEstado();
        assertTrue(inicial.esPrimeraCarga());
        assertFalse("cargando NO es vacía", inicial.vacia());

        responder(SIN_LEER, new MockResponse().setResponseCode(500));
        responder(LISTA, new MockResponse().setResponseCode(500));
        repo.abrirPanel();
        esperarPedidos(LISTA, 1);
        NotificacionesRepository.Estado e = repo.getEstado();
        assertNull(e.lista);
        assertEquals(ApiErrores.MSG_SERVIDOR, e.errorPrimeraCarga);
        assertFalse("un error NUNCA es 'no tenés notificaciones'", e.vacia());
        assertFalse(e.esPrimeraCarga());

        // Reintentar -> carga OK con 0
        responder(SIN_LEER, cantidad(0));
        responder(LISTA, json("[]"));
        repo.refrescar();
        esperarPedidos(LISTA, 2);
        e = repo.getEstado();
        assertTrue(e.vacia());
        assertNull(e.errorPrimeraCarga);
    }

    // ── D4: marcar todas como leídas ─────────────────────────────────────────

    @Test
    public void d4_patchOkVaciaListaYBadgeAlInstante() throws Exception {
        responder(SIN_LEER, cantidad(2));
        responder(LISTA, json(DOS_NOTIFICACIONES));
        repo.abrirPanel();
        esperar("lista", () -> repo.getEstado().lista != null && Long.valueOf(2).equals(repo.getEstado().sinLeer));

        responder(LEER, new MockResponse().setResponseCode(200).setHeadersDelay(400, TimeUnit.MILLISECONDS));
        repo.marcarTodasLeidas();
        // AL INSTANTE: sin esperar la respuesta del PATCH
        NotificacionesRepository.Estado e = repo.getEstado();
        assertTrue("se vacía en el momento", e.vacia());
        assertEquals(Long.valueOf(0), e.sinLeer);
        assertFalse(e.mostrarBadge());
        assertTrue(e.marcando);
        repo.marcarTodasLeidas(); // doble tap
        esperar("PATCH terminado", () -> !repo.getEstado().marcando);
        e = repo.getEstado();
        assertTrue(e.vacia());
        assertNull(e.errorMarcar);
        Thread.sleep(150);
        assertEquals("un solo PATCH", 1, pedidosA(LEER));
        assertEquals("sin esperar al próximo poll: no hubo GET nuevos", 1, pedidosA(LISTA));
        assertEquals(1, pedidosA(SIN_LEER));
        synchronized (metodos) { assertTrue(metodos.contains("PATCH " + LEER)); }
    }

    @Test
    public void d4_patchFallaNoVaciaYSePuedeReintentar() throws Exception {
        responder(SIN_LEER, cantidad(2));
        responder(LISTA, json(DOS_NOTIFICACIONES));
        repo.abrirPanel();
        esperar("lista", () -> repo.getEstado().lista != null);

        responder(LEER, new MockResponse().setResponseCode(500));
        repo.marcarTodasLeidas();
        esperar("error al marcar", () -> repo.getEstado().errorMarcar != null);
        NotificacionesRepository.Estado e = repo.getEstado();
        assertEquals("se restauraron tal cual", 2, e.lista.size());
        assertEquals(Long.valueOf(2), e.sinLeer);
        assertEquals(NotificacionesRepository.MSG_NO_SE_PUDO_MARCAR + " " + ApiErrores.MSG_SERVIDOR, e.errorMarcar);
        assertFalse(e.marcando);

        // Reintento OK
        responder(LEER, new MockResponse().setResponseCode(200));
        repo.marcarTodasLeidas();
        esperar("PATCH terminado", () -> !repo.getEstado().marcando);
        assertTrue(repo.getEstado().vacia());
        assertNull(repo.getEstado().errorMarcar);
        assertEquals(2, pedidosA(LEER));
    }

    @Test
    public void d4_unGetPedidoAntesDelPatchNoResucitaLasLeidas() throws Exception {
        responder(SIN_LEER, cantidad(2));
        responder(LISTA, json(DOS_NOTIFICACIONES));
        repo.abrirPanel();
        esperar("lista", () -> repo.getEstado().lista != null && repo.getEstado().sinLeer != null);

        // Poll lento que salió ANTES del PATCH y vuelve con las dos viejas
        responder(LISTA, json(DOS_NOTIFICACIONES).setHeadersDelay(600, TimeUnit.MILLISECONDS));
        responder(SIN_LEER, cantidad(2).setHeadersDelay(600, TimeUnit.MILLISECONDS));
        repo.refrescar();
        esperarPedidos(LISTA, 2);
        responder(LEER, new MockResponse().setResponseCode(200));
        responder(LISTA, json("[]")); // el re-pedido automático ya ve la verdad
        responder(SIN_LEER, cantidad(0));
        repo.marcarTodasLeidas();
        esperar("vacía", () -> repo.getEstado().vacia());
        Thread.sleep(900);
        NotificacionesRepository.Estado e = repo.getEstado();
        assertTrue("la respuesta vieja se descartó", e.vacia());
        assertEquals(Long.valueOf(0), e.sinLeer);
        assertEquals("la vieja se descartó y se volvió a pedir", 3, pedidosA(LISTA));
    }

    // ── D6: refresco y polling ───────────────────────────────────────────────

    @Test
    public void d6_pollingCada10sSoloEnPrimerPlanoYListaPrecargada() throws Exception {
        for (int i = 0; i < 10; i++) {
            responder(SIN_LEER, cantidad(2));
            responder(LISTA, json(DOS_NOTIFICACIONES));
        }
        repo.iniciarAutoRefresco(); // Home en onStart
        esperarPedidos(SIN_LEER, 1);
        assertEquals("refresca YA", 1, pedidosA(SIN_LEER));
        esperar("lista precargada", () -> repo.getEstado().lista != null);
        assertEquals(1, pedidosA(LISTA));

        reloj.avanzar(NotificacionesRepository.INTERVALO_MS);
        esperarPedidos(SIN_LEER, 2);
        assertEquals("el conteo coincide: no se repide la lista", 1, pedidosA(LISTA));

        // Se abre el panel (otra pantalla que también pide polling): la lista va en cada poll
        repo.abrirPanel();
        repo.iniciarAutoRefresco();
        esperarPedidos(LISTA, 2);
        int conPanel = pedidosA(LISTA);
        reloj.avanzar(NotificacionesRepository.INTERVALO_MS);
        esperarPedidos(LISTA, conPanel + 1);

        // Se cierra el panel: el Home sigue en primer plano -> solo el conteo
        repo.detenerAutoRefresco();
        repo.cerrarPanel();
        int listas = pedidosA(LISTA);
        int conteos = pedidosA(SIN_LEER);
        reloj.avanzar(NotificacionesRepository.INTERVALO_MS);
        esperarPedidos(SIN_LEER, conteos + 1);
        assertEquals(listas, pedidosA(LISTA));

        // App en segundo plano (onStop del Home): no hay más pedidos
        repo.detenerAutoRefresco();
        assertEquals(0, reloj.pendientes());
        int antes = pedidosA(SIN_LEER);
        reloj.avanzar(5 * NotificacionesRepository.INTERVALO_MS);
        Thread.sleep(200);
        assertEquals(antes, pedidosA(SIN_LEER));
    }

    @Test
    public void d6_sinSolaparPedidos() throws Exception {
        responder(SIN_LEER, cantidad(0).setHeadersDelay(500, TimeUnit.MILLISECONDS));
        responder(LISTA, json("[]").setHeadersDelay(500, TimeUnit.MILLISECONDS));
        repo.abrirPanel();
        repo.refrescar();
        repo.refrescar(); // ej. operación + poll al mismo tiempo
        esperar("respuesta", () -> repo.getEstado().lista != null);
        Thread.sleep(150);
        assertEquals(1, pedidosA(SIN_LEER));
        assertEquals(1, pedidosA(LISTA));
    }

    @Test
    public void d6_refrescarTrasMoverPlataTraeLaNuevaNotificacion() throws Exception {
        responder(SIN_LEER, cantidad(0));
        responder(LISTA, json("[]"));
        repo.abrirPanel();
        esperar("vacía", () -> repo.getEstado().vacia());

        // Refrescos.trasMoverPlata -> repo.refrescar(): la compra de dólares ya aparece
        responder(SIN_LEER, cantidad(2));
        responder(LISTA, json(DOS_NOTIFICACIONES));
        repo.refrescar();
        esperar("nueva", () -> repo.getEstado().lista != null && repo.getEstado().lista.size() == 2);
        esperar("badge", () -> Long.valueOf(2).equals(repo.getEstado().sinLeer));
    }

    // ── D7: 403 y errores de red ─────────────────────────────────────────────

    @Test
    public void d7_403SinBodyCortaElPollingSinBucle() throws Exception {
        responder(SIN_LEER, new MockResponse().setResponseCode(403));
        repo.iniciarAutoRefresco();
        esperar("sesión inválida", () -> repo.getEstado().sesionInvalida);
        assertEquals("sin timer: no hay bucle de pedidos", 0, reloj.pendientes());
        reloj.avanzar(5 * NotificacionesRepository.INTERVALO_MS);
        repo.refrescar();
        repo.marcarTodasLeidas();
        Thread.sleep(200);
        assertEquals(1, pedidosA(SIN_LEER));
        assertEquals(0, pedidosA(LEER));

        // Logout (SesionUtils) limpia: la próxima sesión arranca de cero
        repo.limpiar();
        assertFalse(repo.getEstado().sesionInvalida);
        assertNull(repo.getEstado().sinLeer);
    }

    @Test
    public void d7_sinConexionO5xxNoBorraLoCargadoYEsReintentable() throws Exception {
        responder(SIN_LEER, cantidad(2));
        responder(LISTA, json(DOS_NOTIFICACIONES));
        repo.abrirPanel();
        esperar("lista", () -> repo.getEstado().lista != null && repo.getEstado().sinLeer != null);

        responder(SIN_LEER, new MockResponse().setResponseCode(503));
        responder(LISTA, new MockResponse().setResponseCode(502));
        repo.refrescar();
        esperar("desactualizado", () -> repo.getEstado().desactualizado);
        NotificacionesRepository.Estado e = repo.getEstado();
        assertEquals("no se borra lo cargado", 2, e.lista.size());
        assertEquals(Long.valueOf(2), e.sinLeer);
        assertNull(e.errorPrimeraCarga);

        // Backend caído del todo
        String url = server.url("/").toString();
        server.shutdown();
        ApiService caido = RetrofitClient.crear(url, () -> "x", System::currentTimeMillis, () -> {}, false);
        NotificacionesRepository sinBackend = new NotificacionesRepository(() -> caido, reloj);
        sinBackend.abrirPanel();
        esperar("error", () -> sinBackend.getEstado().errorPrimeraCarga != null);
        assertEquals(ApiErrores.MSG_SIN_CONEXION, sinBackend.getEstado().errorPrimeraCarga);
        assertFalse(sinBackend.getEstado().vacia());
        assertNotNull(sinBackend.getEstado());
    }

    @Test
    public void limpiarDescartaRespuestasDeLaSesionAnterior() throws Exception {
        responder(SIN_LEER, cantidad(5).setHeadersDelay(400, TimeUnit.MILLISECONDS));
        repo.refrescar();
        esperarPedidos(SIN_LEER, 1);
        repo.limpiar(); // logout mientras vuelve la respuesta
        Thread.sleep(600);
        assertNull("la otra cuenta no ve el badge de esta", repo.getEstado().sinLeer);
    }
}
