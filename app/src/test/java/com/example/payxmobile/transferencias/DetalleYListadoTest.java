package com.example.payxmobile.transferencias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.JwtFalso;
import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.saldos.SaldosRepository;
import com.google.gson.Gson;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/** Detalle (T16-T20) y listado con auto-refresco (T21, T22). */
public class DetalleYListadoTest {

    private static final String ID = "0f2a1c3e-0000-4000-8000-000000000001";

    static String json(String estado, boolean esEmisor, String concepto, String fechaConf) {
        return "{\"id\":\"" + ID + "\",\"moneda\":\"PESOS\",\"monto\":100.00000000,\"concepto\":"
                + (concepto == null ? "null" : "\"" + concepto + "\"") + ",\"estado\":\"" + estado + "\","
                + "\"fecha\":\"2026-09-24T13:15:30Z\",\"fechaConfirmacion\":"
                + (fechaConf == null ? "null" : "\"" + fechaConf + "\"") + ",\"direccion\":\""
                + (esEmisor ? "ENVIADA" : "RECIBIDA") + "\",\"contraparteNombre\":\"Bea Gómez\","
                + "\"contraparteAlias\":\"bea.gomez.payx\",\"esEmisor\":" + esEmisor + "}";
    }

    static TransferenciaResponse t(String estado, boolean esEmisor) {
        return new Gson().fromJson(json(estado, esEmisor, null, null), TransferenciaResponse.class);
    }

    private MockWebServer server;
    private ApiService api;
    private final AtomicInteger sesionesVencidas = new AtomicInteger();
    private final AtomicInteger movimientos = new AtomicInteger();
    private final List<TransferenciaResponse> reemplazos = new CopyOnWriteArrayList<>();

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String token = JwtFalso.conExp(System.currentTimeMillis() / 1000 + 7200);
        api = RetrofitClient.crear(server.url("/").toString(), () -> token, System::currentTimeMillis,
                sesionesVencidas::incrementAndGet, false);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private DetalleTransferencia detalle(TransferenciaResponse inicial) {
        return new DetalleTransferencia(inicial, () -> api, reemplazos::add, movimientos::incrementAndGet);
    }

    private static void esperar(String que, BooleanSupplier c) throws InterruptedException {
        long fin = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) throw new AssertionError("Timeout: " + que);
            Thread.sleep(10);
        }
    }

    // ── T16 ───────────────────────────────────────────────────────────────────

    @Test
    public void t16_getPorIdY404Y403ConBody() throws Exception {
        DetalleTransferencia d = detalle(t("PENDIENTE", true));
        server.enqueue(new MockResponse().setBody(json("COMPLETADA", true, null, "2026-09-24T14:00:00Z")));
        d.refrescar();
        RecordedRequest r = server.takeRequest(2, TimeUnit.SECONDS);
        assertEquals("GET", r.getMethod());
        assertEquals("/api/transferencias/" + ID, r.getPath());
        esperar("actualizada", () -> "COMPLETADA".equals(d.getTransferencia().getEstado()));

        server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"error\":\"Transferencia no encontrada\"}"));
        d.refrescar();
        esperar("404", () -> d.getError() != null);
        assertEquals("Transferencia no encontrada", d.getError());

        DetalleTransferencia d2 = detalle(t("PENDIENTE", true));
        server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"error\":\"No tenes acceso a esta transferencia\"}"));
        d2.refrescar();
        esperar("403", () -> d2.getError() != null);
        assertEquals("No tenes acceso a esta transferencia", d2.getError());
        assertEquals("403 con body NO es sesión vencida", 0, sesionesVencidas.get());
    }

    @Test
    public void t16_cambiarConceptoSoloEmisorMax200YVacioPermitido() throws Exception {
        DetalleTransferencia d = detalle(t("COMPLETADA", true));
        assertTrue(d.puedeCambiarConcepto());
        server.enqueue(new MockResponse().setBody(json("COMPLETADA", true, "Alquiler septiembre", null)));
        d.guardarConcepto("Alquiler septiembre");
        RecordedRequest r = server.takeRequest(2, TimeUnit.SECONDS);
        assertEquals("PATCH", r.getMethod());
        assertEquals("/api/transferencias/" + ID + "/concepto", r.getPath());
        assertEquals("Alquiler septiembre", JsonParser.parseString(r.getBody().readUtf8()).getAsJsonObject().get("concepto").getAsString());
        esperar("guardado", () -> !d.isGuardando() && "Alquiler septiembre".equals(d.getTransferencia().getConcepto()));
        assertEquals(1, reemplazos.size());

        server.enqueue(new MockResponse().setBody(json("COMPLETADA", true, "", null)));
        d.guardarConcepto("");
        assertEquals("{\"concepto\":\"\"}", server.takeRequest(2, TimeUnit.SECONDS).getBody().readUtf8());
        esperar("segundo guardado", () -> !d.isGuardando());

        String largo = new String(new char[201]).replace('\0', 'a');
        d.guardarConcepto(largo);
        esperar("sin guardar", () -> !d.isGuardando());
        assertEquals(DetalleTransferencia.MSG_CONCEPTO_LARGO, d.getError());
        Thread.sleep(100);
        assertEquals("el de 201 no se manda", 2, server.getRequestCount());
    }

    // ── T17 / T18 ─────────────────────────────────────────────────────────────

    @Test
    public void t17_confirmarPendienteYDobleTap() throws Exception {
        DetalleTransferencia d = detalle(t("PENDIENTE", true));
        assertTrue(d.puedeConfirmarOCancelar());
        server.enqueue(new MockResponse().setBody(json("COMPLETADA", true, null, "2026-09-24T14:00:00Z"))
                .setHeadersDelay(300, TimeUnit.MILLISECONDS));
        d.confirmar();
        d.confirmar();
        d.cancelar(); // también bloqueado mientras procesa
        assertTrue(d.isProcesando());
        esperar("confirmada", () -> !d.isProcesando());
        assertEquals("COMPLETADA", d.getTransferencia().getEstado());
        assertEquals("2026-09-24T14:00:00Z", d.getTransferencia().getFechaConfirmacion());
        assertFalse(d.puedeConfirmarOCancelar());
        assertEquals("un solo PATCH", 1, server.getRequestCount());
        assertEquals("/api/transferencias/" + ID + "/confirmar", server.takeRequest().getPath());
        assertEquals("saldos y notificaciones se refrescan", 1, movimientos.get());
    }

    @Test
    public void t17_t20_erroresAlConfirmarSeMuestranTextuales() throws Exception {
        String[] errores = {
                "Esta transferencia ya fue confirmada",
                "Esta transferencia ya fue cancelada",
                "Esta transferencia vencio: las pendientes se cancelan a las 24 horas",
                "No tenes saldo suficiente para esta transferencia",
                "El destinatario ya no puede recibir transferencias",
        };
        for (String msg : errores) {
            DetalleTransferencia d = detalle(t("PENDIENTE", true));
            server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"" + msg + "\"}"));
            d.confirmar();
            esperar(msg, () -> !d.isProcesando() && d.getError() != null);
            assertEquals(msg, d.getError());
            assertEquals("sigue como estaba", "PENDIENTE", d.getTransferencia().getEstado());
        }
        assertEquals(0, movimientos.get());
    }

    @Test
    public void t18_cancelarPendiente() throws Exception {
        DetalleTransferencia d = detalle(t("PENDIENTE", true));
        server.enqueue(new MockResponse().setBody(json("CANCELADA", true, null, null)));
        d.cancelar();
        assertEquals("/api/transferencias/" + ID + "/cancelar", server.takeRequest(2, TimeUnit.SECONDS).getPath());
        esperar("cancelada", () -> !d.isProcesando());
        assertTrue(d.getTransferencia().esCancelada());
        FilaTransferencia f = FilaTransferencia.de(d.getTransferencia(), java.time.ZoneId.of("UTC"));
        assertEquals("$ 100,00", f.monto); // sin signo ni color de enviada/recibida
        assertEquals(FilaTransferencia.Estilo.CANCELADA, f.estilo);

        DetalleTransferencia d2 = detalle(t("PENDIENTE", true));
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"error\":\"Solo se pueden cancelar transferencias pendientes\"}"));
        d2.cancelar();
        esperar("400", () -> d2.getError() != null);
        assertEquals("Solo se pueden cancelar transferencias pendientes", d2.getError());
    }

    // ── T19 / T23: permisos ───────────────────────────────────────────────────

    @Test
    public void t19_t23_receptorNoTieneAccionesYUn403ConBodyEsMensaje() throws Exception {
        DetalleTransferencia receptor = detalle(t("PENDIENTE", false));
        assertFalse(receptor.puedeCambiarConcepto());
        assertFalse(receptor.puedeConfirmarOCancelar());
        receptor.confirmar();
        receptor.cancelar();
        receptor.guardarConcepto("x");
        Thread.sleep(100);
        assertEquals("sin botones y sin requests", 0, server.getRequestCount());

        // Si el backend igual lo rechaza (ej. estado viejo en pantalla): mensaje, no logout
        DetalleTransferencia d = detalle(t("PENDIENTE", true));
        server.enqueue(new MockResponse().setResponseCode(403)
                .setBody("{\"error\":\"Solo quien envio la transferencia puede hacer esta accion\"}"));
        d.confirmar();
        esperar("403", () -> d.getError() != null);
        assertEquals("Solo quien envio la transferencia puede hacer esta accion", d.getError());
        assertEquals(0, sesionesVencidas.get());
    }

    @Test
    public void t20_unaPendienteVencidaLlegaCanceladaEnElRefrescoYSeActualizaElDetalle() {
        DetalleTransferencia d = detalle(t("PENDIENTE", true));
        d.actualizarDesdeListado(t("CANCELADA", true)); // el proceso del backend la canceló
        assertTrue(d.getTransferencia().esCancelada());
        assertFalse(d.puedeConfirmarOCancelar());
    }

    // ── T21 / T22: listado ────────────────────────────────────────────────────

    /** Programador con tiempo manual. */
    static class Reloj implements SaldosRepository.Programador {
        final List<long[]> cuando = new ArrayList<>();
        final List<Runnable> tareas = new ArrayList<>();
        final List<boolean[]> canceladas = new ArrayList<>();
        long ahora;

        @Override
        public synchronized Runnable programar(Runnable tarea, long demoraMs) {
            boolean[] cancelada = {false};
            cuando.add(new long[]{ahora + demoraMs});
            tareas.add(tarea);
            canceladas.add(cancelada);
            return () -> cancelada[0] = true;
        }

        void avanzar(long ms) {
            long fin = ahora + ms;
            while (true) {
                Runnable r = null;
                synchronized (this) {
                    int mejor = -1;
                    for (int i = 0; i < tareas.size(); i++) {
                        if (!canceladas.get(i)[0] && cuando.get(i)[0] <= fin
                                && (mejor < 0 || cuando.get(i)[0] < cuando.get(mejor)[0])) mejor = i;
                    }
                    if (mejor < 0) { ahora = fin; return; }
                    ahora = cuando.get(mejor)[0];
                    r = tareas.get(mejor);
                    canceladas.get(mejor)[0] = true;
                }
                r.run();
            }
        }
    }

    private String lista(int n) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(',');
            sb.append(json("COMPLETADA", i % 2 == 0, null, null).replace(ID, "id-" + i));
        }
        return sb.append(']').toString();
    }

    @Test
    public void t21_autoRefrescoCada10sSoloEnPrimerPlanoYSinSolapar() throws Exception {
        Reloj reloj = new Reloj();
        TransferenciasRepository repo = new TransferenciasRepository(() -> api, reloj);
        for (int i = 0; i < 10; i++) server.enqueue(new MockResponse().setBody(lista(1)));

        repo.iniciarAutoRefresco(); // al entrar: YA
        esperar("1", () -> server.getRequestCount() == 1 && !repo.getEstado().cargando);
        reloj.avanzar(10_000);
        esperar("2", () -> server.getRequestCount() == 2 && !repo.getEstado().cargando);
        repo.detenerAutoRefresco(); // segundo plano
        reloj.avanzar(60_000);
        Thread.sleep(150);
        assertEquals(2, server.getRequestCount());

        // Sin solapar: una request lenta + refrescos encima = 1 sola
        server.enqueue(new MockResponse().setBody(lista(1)).setHeadersDelay(500, TimeUnit.MILLISECONDS));
        repo.refrescar();
        repo.refrescar();
        repo.refrescar();
        esperar("quieto", () -> !repo.getEstado().cargando && server.getRequestCount() >= 3);
        Thread.sleep(150);
        assertEquals(3, server.getRequestCount());
    }

    @Test
    public void t21_unConfirmarNoEsPisadoPorUnaListaViejaEnVuelo() throws Exception {
        Reloj reloj = new Reloj();
        TransferenciasRepository repo = new TransferenciasRepository(() -> api, reloj);
        server.enqueue(new MockResponse().setBody("[" + json("PENDIENTE", true, null, null) + "]"));
        repo.refrescar();
        esperar("carga", () -> repo.getEstado().lista != null);

        // Lista pedida ANTES de confirmar (todavía PENDIENTE) que llega DESPUÉS
        server.enqueue(new MockResponse().setBody("[" + json("PENDIENTE", true, null, null) + "]")
                .setHeadersDelay(400, TimeUnit.MILLISECONDS));
        server.enqueue(new MockResponse().setBody("[" + json("COMPLETADA", true, null, "2026-09-24T14:00:00Z") + "]"));
        repo.refrescar();
        repo.reemplazar(t("COMPLETADA", true)); // respuesta del confirmar
        assertEquals("COMPLETADA", repo.getEstado().buscar(ID).getEstado());
        Thread.sleep(700);
        esperar("quieto", () -> !repo.getEstado().cargando);
        assertEquals("la lista vieja se descartó", "COMPLETADA", repo.getEstado().buscar(ID).getEstado());
    }

    @Test
    public void t22_listaDe2000ConAvisoDelTope() throws Exception {
        TransferenciasRepository repo = new TransferenciasRepository(() -> api, new Reloj());
        server.enqueue(new MockResponse().setBody(lista(2000)));
        long inicio = System.currentTimeMillis();
        repo.refrescar();
        esperar("carga", () -> repo.getEstado().lista != null);
        assertEquals(2000, repo.getEstado().lista.size());
        assertTrue(repo.getEstado().llegoAlTope());
        // Armar las 2.000 filas (lo que hace el adapter al dibujar) es instantáneo
        for (TransferenciaResponse t : repo.getEstado().lista) FilaTransferencia.de(t, java.time.ZoneId.of("UTC"));
        assertTrue("parseo + filas en menos de 3 s", System.currentTimeMillis() - inicio < 3000);

        TransferenciasRepository chico = new TransferenciasRepository(() -> api, new Reloj());
        server.enqueue(new MockResponse().setBody(lista(1999)));
        chico.refrescar();
        esperar("carga", () -> chico.getEstado().lista != null);
        assertFalse(chico.getEstado().llegoAlTope());
    }

    @Test
    public void listadoFallidoMantieneLaUltimaListaBuena() throws Exception {
        TransferenciasRepository repo = new TransferenciasRepository(() -> api, new Reloj());
        server.enqueue(new MockResponse().setBody(lista(3)));
        repo.refrescar();
        esperar("carga", () -> repo.getEstado().lista != null);
        server.enqueue(new MockResponse().setResponseCode(500));
        repo.refrescar();
        esperar("falla", () -> repo.getEstado().desactualizado);
        assertEquals(3, repo.getEstado().lista.size());
        assertNull(repo.getEstado().errorPrimeraCarga);
    }
}
