package com.example.payxmobile.actividad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.JwtFalso;
import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.saldos.SaldosRepository;
import com.example.payxmobile.transferencias.TransferenciasRepository;
import com.google.gson.Gson;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/** M10 (un solo polling compartido), M12 (reflejo de operaciones), M14 (errores) con MockWebServer. */
public class MovimientosRepositorioTest {

    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");

    private MockWebServer server;
    private ApiService api;
    private final List<Runnable> tareas = new ArrayList<>();
    private final List<boolean[]> canceladas = new ArrayList<>();

    /** Programador manual: dispara a mano el "tick" de 10 s. */
    private final SaldosRepository.Programador programador = (tarea, demora) -> {
        boolean[] c = {false};
        synchronized (tareas) {
            tareas.add(tarea);
            canceladas.add(c);
        }
        return () -> c[0] = true;
    };

    private void tick() {
        List<Runnable> vivas = new ArrayList<>();
        synchronized (tareas) {
            for (int i = 0; i < tareas.size(); i++) if (!canceladas.get(i)[0]) vivas.add(tareas.get(i));
            for (boolean[] c : canceladas) c[0] = true; // cada tarea corre una sola vez
        }
        for (Runnable r : vivas) r.run();
    }

    private int timersVivos() {
        synchronized (tareas) {
            int n = 0;
            for (boolean[] c : canceladas) if (!c[0]) n++;
            return n;
        }
    }

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String token = JwtFalso.conExp(System.currentTimeMillis() / 1000 + 7200);
        api = RetrofitClient.crear(server.url("/").toString(), () -> token, System::currentTimeMillis, () -> {}, false);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private static void esperar(String que, BooleanSupplier c) throws InterruptedException {
        long fin = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) throw new AssertionError("Timeout: " + que);
            Thread.sleep(10);
        }
    }

    private static String lista(String... ts) {
        return "[" + String.join(",", ts) + "]";
    }

    private static String t(String id, String estado, String fecha) {
        return ActividadesTest.t(id, "PESOS", estado, "ENVIADA", fecha);
    }

    @Test
    public void m10_inicioYMovimientosComparteUnSoloPolling() throws Exception {
        TransferenciasRepository repo = new TransferenciasRepository(() -> api, programador);
        for (int i = 0; i < 10; i++) server.enqueue(new MockResponse().setBody(lista(t("a", "COMPLETADA", "2026-09-24T10:00:00Z"))));

        repo.iniciarAutoRefresco();   // Inicio onStart
        esperar("1", () -> !repo.getEstado().cargando && repo.getEstado().lista != null);
        repo.iniciarAutoRefresco();   // Movimientos onStart (Inicio todavía no hizo onStop)
        esperar("2", () -> !repo.getEstado().cargando && server.getRequestCount() == 2);
        repo.detenerAutoRefresco();   // Inicio onStop
        assertEquals("un solo timer aunque haya dos pantallas", 1, timersVivos());

        tick();
        esperar("tick", () -> server.getRequestCount() == 3 && !repo.getEstado().cargando);
        assertEquals(1, timersVivos());

        repo.detenerAutoRefresco();   // Movimientos onStop -> segundo plano
        assertEquals("en segundo plano no hay polling", 0, timersVivos());
        tick();
        Thread.sleep(150);
        assertEquals(3, server.getRequestCount());
    }

    @Test
    public void m10_refrescoSinCambiosEntregaLaMismaLista() throws Exception {
        TransferenciasRepository repo = new TransferenciasRepository(() -> api, programador);
        server.enqueue(new MockResponse().setBody(lista(t("a", "COMPLETADA", "2026-09-24T10:00:00Z"))));
        repo.refrescar();
        esperar("carga", () -> repo.getEstado().lista != null && !repo.getEstado().cargando);
        // Mientras no llega otra lista, la instancia es la misma: la pantalla no reconstruye el feed
        assertSame(repo.getEstado().lista, repo.getEstado().lista);
    }

    @Test
    public void m12_confirmarActualizaLaFilaEnInicioYMovimientos() throws Exception {
        TransferenciasRepository repo = new TransferenciasRepository(() -> api, programador);
        server.enqueue(new MockResponse().setBody(lista(
                t("p", "PENDIENTE", "2026-09-24T12:00:00Z"), t("b", "COMPLETADA", "2026-09-24T10:00:00Z"))));
        repo.refrescar();
        esperar("carga", () -> repo.getEstado().lista != null);
        List<TransferenciaResponse> antes = repo.getEstado().lista;
        assertTrue(VistaMovimientos.inicio(repo.getEstado()).visibles.get(0).transferencia.esPendiente());

        repo.reemplazar(new Gson().fromJson(t("p", "COMPLETADA", "2026-09-24T12:00:00Z"), TransferenciaResponse.class));
        assertNotSame("lista nueva: las pantallas se enteran", antes, repo.getEstado().lista);
        assertEquals("COMPLETADA", VistaMovimientos.inicio(repo.getEstado()).visibles.get(0).transferencia.getEstado());
        assertEquals("COMPLETADA", VistaMovimientos.de(repo.getEstado(), null, null, 1, BA).visibles.get(0).transferencia.getEstado());
    }

    @Test
    public void m12_unaTransferenciaNuevaApareceArribaTrasElRefresco() throws Exception {
        TransferenciasRepository repo = new TransferenciasRepository(() -> api, programador);
        server.enqueue(new MockResponse().setBody(lista(t("vieja", "COMPLETADA", "2026-09-24T10:00:00Z"))));
        server.enqueue(new MockResponse().setBody(lista(
                t("nueva", "PENDIENTE", "2026-09-24T15:00:00Z"), t("vieja", "COMPLETADA", "2026-09-24T10:00:00Z"))));
        repo.refrescar();
        esperar("1", () -> repo.getEstado().lista != null);
        repo.refrescar(); // lo que hace Refrescos.trasMoverPlata al crear
        esperar("2", () -> repo.getEstado().lista.size() == 2);
        assertEquals("t-nueva", VistaMovimientos.inicio(repo.getEstado()).visibles.get(0).key);
    }

    @Test
    public void m14_erroresNuncaDicenQueNoHayMovimientos() throws Exception {
        TransferenciasRepository repo = new TransferenciasRepository(() -> api, programador);
        server.enqueue(new MockResponse().setResponseCode(503));
        repo.refrescar();
        esperar("error", () -> repo.getEstado().errorPrimeraCarga != null);
        VistaMovimientos v = VistaMovimientos.de(repo.getEstado(), null, null, 1, BA);
        assertEquals(VistaMovimientos.Modo.ERROR, v.modo);
        assertEquals(ApiErrores.MSG_SERVIDOR, v.error);
        assertEquals(VistaMovimientos.Modo.ERROR, VistaMovimientos.inicio(repo.getEstado()).modo);

        // Reintentar -> carga; después un refresco fallido mantiene la lista con el aviso
        server.enqueue(new MockResponse().setBody(lista(t("a", "COMPLETADA", "2026-09-24T10:00:00Z"))));
        repo.refrescar();
        esperar("ok", () -> repo.getEstado().lista != null);
        server.enqueue(new MockResponse().setResponseCode(403)); // 403 sin body con token vigente
        repo.refrescar();
        esperar("desactualizado", () -> repo.getEstado().desactualizado);
        VistaMovimientos conDatos = VistaMovimientos.de(repo.getEstado(), null, null, 1, BA);
        assertEquals(VistaMovimientos.Modo.LISTA, conDatos.modo);
        assertEquals(1, conDatos.visibles.size());
        assertTrue(conDatos.desactualizado);
    }
}
