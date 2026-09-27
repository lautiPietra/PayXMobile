package com.example.payxmobile.dolares;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.JwtFalso;
import com.example.payxmobile.actividad.Actividad;
import com.example.payxmobile.actividad.Actividades;
import com.example.payxmobile.actividad.FiltroMoneda;
import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.actividad.VistaMovimientos;
import com.example.payxmobile.model.CotizacionDolar;
import com.example.payxmobile.model.OperacionCambioResponse;
import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/** Compras/ventas de dólares en "Mis movimientos" e Inicio, alta instantánea y cotización precargada. */
public class FeedDolaresTest {

    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");

    private static OperacionCambioResponse op(String id, String tipo, String usd, String pesos, String fecha) {
        return new OperacionCambioResponse(id, tipo, new BigDecimal(usd), new BigDecimal(pesos),
                new BigDecimal("1545.00"), fecha);
    }

    private static List<TransferenciaResponse> transferencias(String json) {
        return new Gson().fromJson(json, new TypeToken<List<TransferenciaResponse>>() {}.getType());
    }

    private static final String DOS_TRANSFERENCIAS = "["
            + "{\"id\":\"t2\",\"moneda\":\"PESOS\",\"monto\":100,\"estado\":\"COMPLETADA\",\"fecha\":\"2026-09-27T12:00:00Z\","
            + "\"direccion\":\"ENVIADA\",\"contraparteNombre\":\"Bea\",\"esEmisor\":true},"
            + "{\"id\":\"t1\",\"moneda\":\"USD\",\"monto\":5,\"estado\":\"COMPLETADA\",\"fecha\":\"2026-09-25T12:00:00Z\","
            + "\"direccion\":\"RECIBIDA\",\"contraparteNombre\":\"Ana\",\"esEmisor\":false}]";

    // ── Feed ─────────────────────────────────────────────────────────────────

    @Test
    public void elFeedMezclaTransferenciasYDolaresPorFecha() {
        List<OperacionCambioResponse> cambios = Arrays.asList(
                op("c2", "VENTA", "0.97", "1450.15", "2026-09-27T02:39:10.5-03:00"),   // 05:39Z
                op("c1", "COMPRA", "0.97", "1500.00", "2026-09-26T09:00:00-03:00"));   // 26 12:00Z
        List<Actividad> feed = Actividades.construir(transferencias(DOS_TRANSFERENCIAS), cambios);
        assertEquals(4, feed.size());
        assertEquals("t-t2", feed.get(0).key);
        assertEquals("cd-c2", feed.get(1).key);
        assertEquals("cd-c1", feed.get(2).key);
        assertEquals("t-t1", feed.get(3).key);
        assertEquals(Actividad.Tipo.DOLARES, feed.get(1).tipo);
        assertSame(cambios.get(0), feed.get(1).cambioDolares);
        assertNull(feed.get(1).transferencia);
    }

    @Test
    public void sinDolaresCargadosSeVenLasTransferencias() {
        assertEquals(2, Actividades.construir(transferencias(DOS_TRANSFERENCIAS), null).size());
    }

    @Test
    public void filtroDolaresIncluyeCompraVentaYTransferenciasEnUsd() {
        List<Actividad> feed = Actividades.construir(transferencias(DOS_TRANSFERENCIAS),
                Collections.singletonList(op("c1", "COMPRA", "1", "1545", "2026-09-26T12:00:00Z")));
        List<Actividad> dolares = FiltroMoneda.filtrar(feed, FiltroMoneda.DOLARES);
        assertEquals(2, dolares.size());
        assertEquals("cd-c1", dolares.get(0).key);
        assertEquals("t-t1", dolares.get(1).key);
        assertEquals(1, FiltroMoneda.filtrar(feed, FiltroMoneda.PESOS).size());
        assertEquals(0, FiltroMoneda.filtrar(feed, FiltroMoneda.CRIPTO).size());
        assertEquals(Integer.valueOf(2), FiltroMoneda.contar(feed).get(FiltroMoneda.DOLARES));
    }

    @Test
    public void inicioMuestraLasCuatroMasRecientesIncluidosDolares() {
        String json = DOS_TRANSFERENCIAS;
        com.example.payxmobile.transferencias.TransferenciasRepository repo =
                new com.example.payxmobile.transferencias.TransferenciasRepository(() -> null, (t, d) -> () -> {});
        // Estado con lista: se arma a mano con el feed ya construido
        List<Actividad> feed = Actividades.construir(transferencias(json), Arrays.asList(
                op("c3", "COMPRA", "1", "1545", "2026-09-27T13:00:00Z"),
                op("c2", "VENTA", "1", "1495", "2026-09-27T12:30:00Z"),
                op("c1", "COMPRA", "1", "1545", "2026-09-26T12:00:00Z")));
        VistaMovimientos v = VistaMovimientos.inicio(estadoCon(repo, transferencias(json)), feed);
        assertEquals(VistaMovimientos.Modo.LISTA, v.modo);
        assertEquals(4, v.visibles.size());
        assertEquals("cd-c3", v.visibles.get(0).key);
        assertEquals("cd-c2", v.visibles.get(1).key);
        assertEquals("t-t2", v.visibles.get(2).key);
        assertEquals(5, v.total);
    }

    /** Estado del repositorio de transferencias con una lista cargada (por reflexión: el constructor es privado). */
    private static com.example.payxmobile.transferencias.TransferenciasRepository.Estado estadoCon(
            com.example.payxmobile.transferencias.TransferenciasRepository repo, List<TransferenciaResponse> lista) {
        try {
            java.lang.reflect.Constructor<com.example.payxmobile.transferencias.TransferenciasRepository.Estado> c =
                    com.example.payxmobile.transferencias.TransferenciasRepository.Estado.class.getDeclaredConstructor(
                            List.class, String.class, boolean.class, boolean.class);
            c.setAccessible(true);
            return c.newInstance(lista, null, false, false);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // ── Fila (como ActividadItem de la web) ──────────────────────────────────

    @Test
    public void filaDeCompraYDeVenta() {
        FilaCambioDolares compra = FilaCambioDolares.de(op("c1", "COMPRA", "0.97", "1500.00", "2026-09-27T02:39:10-03:00"), BA);
        assertEquals("Compra de dólares", compra.titulo);
        assertEquals("Pagaste $ 1.500,00", compra.detalle);
        assertEquals("+US$ 0,97", compra.monto);
        assertTrue(compra.esCompra);
        assertTrue(compra.fechaCorta, compra.fechaCorta.contains("02:39"));

        FilaCambioDolares venta = FilaCambioDolares.de(op("c2", "VENTA", "1234.5", "1846227.5", "2026-09-27T02:39:10-03:00"), BA);
        assertEquals("Venta de dólares", venta.titulo);
        assertEquals("Recibiste $ 1.846.227,50", venta.detalle);
        assertEquals("siempre 2 decimales", "-US$ 1.234,50", venta.monto);
    }

    // ── ListaRemota: alta instantánea de la operación recién hecha ───────────

    private MockWebServer server;
    private ListaRemota<OperacionCambioResponse> lista;
    private ApiService api;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String token = JwtFalso.conExp(System.currentTimeMillis() / 1000 + 7200);
        api = RetrofitClient.crear(server.url("/").toString(), () -> token, System::currentTimeMillis, () -> {}, false);
        lista = new ListaRemota<>(() -> api, ApiService::listarCambiosDolares, OperacionCambioResponse::getId,
                (t, d) -> () -> {});
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

    private static String json(String... ops) {
        return "[" + String.join(",", ops) + "]";
    }

    private static String opJson(String id, String tipo) {
        return "{\"id\":\"" + id + "\",\"tipo\":\"" + tipo + "\",\"montoUsd\":0.97,\"montoPesos\":1500.00,"
                + "\"cotizacion\":1545.00,\"fecha\":\"2026-09-27T02:39:10.123456-03:00\"}";
    }

    @Test
    public void getMapeaElContratoYAgregarMuestraLaNuevaAlInstante() throws Exception {
        server.enqueue(new MockResponse().setBody(json(opJson("c1", "COMPRA"))));
        lista.refrescar();
        esperar("lista", () -> lista.getEstado().lista != null);
        assertEquals("/api/cambio-dolares", server.takeRequest(1, TimeUnit.SECONDS).getPath());
        OperacionCambioResponse c1 = lista.getEstado().lista.get(0);
        assertEquals(new BigDecimal("0.97"), c1.getMontoUsd());
        assertEquals("2026-09-27T02:39:10.123456-03:00", c1.getFecha());

        // La pantalla de compra agrega el resultado del POST sin esperar al GET
        OperacionCambioResponse nueva = op("c2", "VENTA", "0.97", "1450.15", "2026-09-27T02:40:00-03:00");
        lista.agregar(nueva);
        assertEquals(2, lista.getEstado().lista.size());
        assertSame("la más reciente arriba", nueva, lista.getEstado().lista.get(0));
        lista.agregar(nueva); // rotación: no se duplica
        assertEquals(2, lista.getEstado().lista.size());
    }

    @Test
    public void unGetPedidoAntesDeAgregarNoLaHaceDesaparecer() throws Exception {
        server.enqueue(new MockResponse().setBody(json(opJson("c1", "COMPRA"))));
        lista.refrescar();
        esperar("lista", () -> lista.getEstado().lista != null);

        // GET lento que salió ANTES de la compra (todavía sin c2)
        server.enqueue(new MockResponse().setBody(json(opJson("c1", "COMPRA"))).setHeadersDelay(400, TimeUnit.MILLISECONDS));
        server.enqueue(new MockResponse().setBody(json(opJson("c2", "VENTA"), opJson("c1", "COMPRA"))));
        lista.refrescar();
        Thread.sleep(100);
        lista.agregar(op("c2", "VENTA", "0.97", "1450.15", "2026-09-27T02:40:00-03:00"));
        Thread.sleep(800);
        assertEquals("se descartó la vieja y se volvió a pedir", 3, server.getRequestCount());
        assertEquals(2, lista.getEstado().lista.size());
        assertEquals("c2", lista.getEstado().lista.get(0).getId());
    }

    @Test
    public void siFallaSeMantieneLaUltimaLista() throws Exception {
        server.enqueue(new MockResponse().setBody(json(opJson("c1", "COMPRA"))));
        lista.refrescar();
        esperar("lista", () -> lista.getEstado().lista != null);
        server.enqueue(new MockResponse().setResponseCode(500));
        lista.refrescar();
        esperar("desactualizado", () -> lista.getEstado().desactualizado);
        assertEquals(1, lista.getEstado().lista.size());
    }

    // ── Cotización precargada ────────────────────────────────────────────────

    @Test
    public void cotizacionPrecargadaVigenteYSinPedirDeMas() throws Exception {
        AtomicLong ahora = new AtomicLong(1_000_000);
        CotizacionDolarRepository repo = new CotizacionDolarRepository(() -> api, ahora::get);
        assertNull(repo.getVigente());

        server.enqueue(new MockResponse().setBody("{\"compra\":1495,\"venta\":1545,\"desactualizada\":false}"));
        repo.precargar();
        esperar("precargada", () -> repo.getVigente() != null);
        assertEquals(new BigDecimal("1545"), CambioDolares.precioPara(CambioDolares.Tipo.COMPRA, repo.getVigente()));

        repo.precargar(); // enseguida: no pide de nuevo
        Thread.sleep(150);
        assertEquals(1, server.getRequestCount());

        ahora.addAndGet(CotizacionDolarRepository.VIGENCIA_MS + 1);
        assertNull("vieja: mejor 'Buscando...' que un precio viejo", repo.getVigente());

        // 503: el backend ya no tiene ninguna
        server.enqueue(new MockResponse().setResponseCode(503));
        repo.precargar();
        esperar("503", () -> server.getRequestCount() == 2);
        Thread.sleep(100);
        assertNull(repo.getVigente());

        CotizacionDolar mala = new Gson().fromJson("{\"compra\":0,\"venta\":0}", CotizacionDolar.class);
        repo.guardar(mala);
        assertNull("una cotización inválida no se guarda", repo.getVigente());
        assertNotNull(new Gson().fromJson("{}", CotizacionDolar.class));
    }
}
