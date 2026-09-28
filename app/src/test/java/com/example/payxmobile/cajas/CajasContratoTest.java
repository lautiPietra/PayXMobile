package com.example.payxmobile.cajas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.JwtFalso;
import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.model.CajaAhorroRequest;
import com.example.payxmobile.model.CajaAhorroResponse;
import com.example.payxmobile.model.NotificacionResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.notificaciones.NotificacionesRepository;
import com.example.payxmobile.notificaciones.TitulosNotificacion;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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

/** Cajas de ahorro contra MockWebServer: contrato, errores, doble notificación y envío único (K1-K10). */
public class CajasContratoTest {

    static final String ID = "7c1e2d3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f";
    static final String BASE = "/api/cajas-ahorro";
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-28T13:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));

    static String caja(String id, String nombre, String color, String icono, String saldo, String meta) {
        return "{\"id\":\"" + id + "\",\"nombre\":\"" + nombre + "\",\"color\":\"" + color + "\",\"icono\":\"" + icono
                + "\",\"saldo\":" + saldo + ",\"montoObjetivo\":" + (meta == null ? "null" : meta)
                + ",\"fechaCreacion\":\"2026-09-20T10:00:00.123456-03:00\"}";
    }

    static CajaAhorroResponse cajaObj(String saldo, String meta) {
        return new Gson().fromJson(caja(ID, "Viaje", "#0ea5e9", "umbrella", saldo, meta), CajaAhorroResponse.class);
    }

    private MockWebServer server;
    private ApiService api;
    private final Map<String, ConcurrentLinkedQueue<MockResponse>> respuestas = new ConcurrentHashMap<>();
    private final List<RecordedRequest> pedidos = new CopyOnWriteArrayList<>();
    private final AtomicInteger refrescos = new AtomicInteger();
    private final AtomicReference<CajaAhorroResponse> devuelta = new AtomicReference<>();
    private MovimientosCajaSesion sesion;

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
        api = RetrofitClient.crear(server.url("/").toString(), () -> token, System::currentTimeMillis,
                () -> {}, false, false);
        sesion = new MovimientosCajaSesion();
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

    private static MockResponse error(int codigo, String mensaje) {
        return json(codigo, "{\"error\":\"" + mensaje + "\"}");
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

    private RecordedRequest ultimo(String metodo, String path) {
        RecordedRequest u = null;
        for (RecordedRequest r : pedidos) if (r.getMethod().equals(metodo) && r.getPath().equals(path)) u = r;
        return u;
    }

    private static JsonObject body(RecordedRequest r) {
        return JsonParser.parseString(r.getBody().clone().readUtf8()).getAsJsonObject();
    }

    private FormularioCaja formulario(CajaAhorroResponse existente) {
        return formulario(existente, api);
    }

    private FormularioCaja formulario(CajaAhorroResponse existente, ApiService s) {
        return new FormularioCaja(existente, () -> s, () -> s, devuelta::set, refrescos::incrementAndGet);
    }

    private OperacionMontoCaja operacion(MovimientoCaja.Tipo tipo, CajaAhorroResponse caja) {
        return operacion(tipo, caja, api);
    }

    private OperacionMontoCaja operacion(MovimientoCaja.Tipo tipo, CajaAhorroResponse caja, ApiService s) {
        return new OperacionMontoCaja(tipo, caja, () -> s, sesion, RELOJ, devuelta::set, refrescos::incrementAndGet);
    }

    private void guardar(FormularioCaja f) throws InterruptedException {
        f.guardar();
        esperar("fin del guardado", () -> !f.isEnviando());
    }

    private void confirmar(OperacionMontoCaja op, BigDecimal saldoPrincipal) throws InterruptedException {
        op.confirmar(saldoPrincipal);
        esperar("fin de la operación", () -> !op.isEnviando());
    }

    // ── K1: /limite antes de crear ─────────────────────────────────────────────

    @Test
    public void k1_conElMaximoAlcanzadoNoSeMandaElPost() throws Exception {
        responder("GET " + BASE + "/limite", json(200, "{\"maxPorUsuario\":3}"));
        FormularioCaja f = formulario(null);
        f.cargarLimite();
        esperar("límite", () -> f.getMaxCajas() != null);
        assertEquals("pidió /limite", 1, pedidosA("GET", BASE + "/limite"));
        assertEquals(Integer.valueOf(3), f.getMaxCajas());
        f.setCantidadCajas(2);
        assertFalse(f.isLimiteAlcanzado());
        f.setCantidadCajas(3);
        assertTrue("3/3: Crear deshabilitado", f.isLimiteAlcanzado());
        f.setNombre("Viaje");
        f.guardar();
        assertEquals("Ya tenés el máximo de 3 cajas de ahorro", f.getError());
        Thread.sleep(100);
        assertEquals(0, pedidosA("POST", BASE));
    }

    @Test
    public void k1_elLimiteNoEstaHardcodeado() throws Exception {
        responder("GET " + BASE + "/limite", json(200, "{\"maxPorUsuario\":7}"));
        FormularioCaja f = formulario(null);
        f.cargarLimite();
        esperar("límite", () -> f.getMaxCajas() != null);
        f.setCantidadCajas(6);
        assertFalse(f.isLimiteAlcanzado());
        f.setCantidadCajas(7);
        assertTrue(f.isLimiteAlcanzado());
        assertFalse("sin saber el máximo no se bloquea", FormularioCaja.limiteAlcanzado(null, 50));
    }

    @Test
    public void k1_el400DelTopeSeMuestraYVuelveAPedirElLimite() throws Exception {
        responder("POST " + BASE, error(400, "Ya tenes el maximo de 3 cajas de ahorro"));
        responder("GET " + BASE + "/limite", json(200, "{\"maxPorUsuario\":3}"));
        FormularioCaja f = formulario(null);
        f.setNombre("Viaje");
        guardar(f);
        assertEquals("Ya tenes el maximo de 3 cajas de ahorro", f.getError());
        assertEquals(FormularioCaja.Paso.EDITANDO, f.getPaso());
        esperar("re-pide /limite", () -> pedidosA("GET", BASE + "/limite") == 1);
        assertEquals("refresca la lista", 1, refrescos.get());
    }

    // ── K2: crear, whitelists y validación local ───────────────────────────────

    @Test
    public void k2_crearMandaElBodyExactoConMetaNullExplicita() throws Exception {
        responder("POST " + BASE, json(201, caja(ID, "Viaje", "#0ea5e9", "umbrella", "0.00", null)));
        FormularioCaja f = formulario(null);
        f.setNombre("  Viaje  ");
        assertTrue(f.elegirColor("#0ea5e9"));
        assertTrue(f.elegirIcono("umbrella"));
        guardar(f);
        assertEquals(FormularioCaja.Paso.EXITO, f.getPaso());
        RecordedRequest r = ultimo("POST", BASE);
        assertTrue(r.getHeader("Authorization").startsWith("Bearer "));
        assertEquals("{\"nombre\":\"Viaje\",\"color\":\"#0ea5e9\",\"icono\":\"umbrella\",\"montoObjetivo\":null}",
                r.getBody().readUtf8());
        assertEquals(ID, devuelta.get().getId());
        assertEquals("crear no mueve plata ni refresca de más", 0, refrescos.get());
    }

    @Test
    public void k2_soloSePuedenElegirValoresDeLasWhitelists() {
        FormularioCaja f = formulario(null);
        assertEquals(8, TemasCaja.COLORES.size());
        assertEquals(12, TemasCaja.ICONOS.size());
        for (String c : TemasCaja.COLORES) assertTrue(c, f.elegirColor(c));
        for (TemasCaja.Icono i : TemasCaja.ICONOS) assertTrue(i.clave, f.elegirIcono(i.clave));
        assertFalse(f.elegirColor("#FF6B1A")); // mayúsculas: el backend compara exacto
        assertFalse(f.elegirColor("#000000"));
        assertFalse(f.elegirColor("red"));
        assertFalse(f.elegirIcono("plane"));
        assertFalse(f.elegirIcono("book"));
        assertFalse(f.elegirIcono(null));
        assertEquals("quedó el último válido", "trending-up", f.getIcono());
        assertEquals(java.util.Arrays.asList("#ff6b1a", "#f59e0b", "#16a34a", "#0ea5e9", "#8b5cf6", "#ec4899",
                "#64748b", "#dc2626"), TemasCaja.COLORES);
    }

    @Test
    public void k2_forzarUnColorInvalidoDa400YSuTextoSeMuestra() throws Exception {
        // Directo contra la API (la UI no deja elegirlo): el texto del backend se lee bien
        responder("POST " + BASE, error(400, "El color elegido no es valido"));
        retrofit2.Response<CajaAhorroResponse> r = api.crearCajaAhorro(
                new CajaAhorroRequest("Viaje", "#123456", "piggy-bank", null)).execute();
        assertEquals(400, r.code());
        assertEquals("El color elegido no es valido", ApiErrores.mensaje(r));
        // Y por el formulario (si el backend cambiara la whitelist), también se muestra tal cual
        responder("POST " + BASE, error(400, "El icono elegido no es valido"));
        FormularioCaja f = formulario(null);
        f.setNombre("Viaje");
        guardar(f);
        assertEquals("El icono elegido no es valido", f.getError());
    }

    @Test
    public void k2_validacionLocalDelNombre() {
        String c = TemasCaja.COLORES.get(0), i = TemasCaja.ICONOS.get(0).clave;
        assertEquals(FormularioCaja.MSG_NOMBRE_VACIO, FormularioCaja.validar("", c, i, ""));
        assertEquals(FormularioCaja.MSG_NOMBRE_VACIO, FormularioCaja.validar("    ", c, i, ""));
        assertEquals(FormularioCaja.MSG_NOMBRE_VACIO, FormularioCaja.validar(null, c, i, ""));
        String cuarenta = new String(new char[40]).replace('\0', 'a');
        assertNull(FormularioCaja.validar(cuarenta, c, i, ""));
        assertNull("se valida recortado, como lo guarda el backend", FormularioCaja.validar("  " + cuarenta + "  ", c, i, ""));
        assertEquals(FormularioCaja.MSG_NOMBRE_LARGO, FormularioCaja.validar(cuarenta + "b", c, i, ""));
        assertEquals(FormularioCaja.MSG_COLOR, FormularioCaja.validar("x", "#123456", i, ""));
        assertEquals(FormularioCaja.MSG_ICONO, FormularioCaja.validar("x", c, "plane", ""));
    }

    @Test
    public void k2_sinTextoDelBackend403VacioEsElGenerico() throws Exception {
        // Un @Valid que falla en el backend cae en /error (con token): 403 sin body
        responder("POST " + BASE, new MockResponse().setResponseCode(403));
        FormularioCaja f = formulario(null);
        f.setNombre("Viaje");
        guardar(f);
        assertEquals(FormularioCaja.MSG_NO_SE_PUDO, f.getError());
        f.setNombre("");
        f.guardar();
        assertEquals("nombre vacío: validación local, no sale el POST", FormularioCaja.MSG_NOMBRE_VACIO, f.getError());
        assertEquals(1, pedidosA("POST", BASE));
    }

    // ── K3: meta opcional ──────────────────────────────────────────────────────

    @Test
    public void k3_metaOpcional() throws Exception {
        String c = TemasCaja.COLORES.get(0), i = TemasCaja.ICONOS.get(0).clave;
        assertNull(FormularioCaja.meta(""));
        assertNull(FormularioCaja.meta("   "));
        assertNull(FormularioCaja.validar("x", c, i, ""));
        assertEquals(FormularioCaja.MSG_META_CERO, FormularioCaja.validar("x", c, i, "0"));
        assertEquals(FormularioCaja.MSG_META_CERO, FormularioCaja.validar("x", c, i, "0,00"));
        assertEquals(FormularioCaja.MSG_META_INVALIDA, FormularioCaja.validar("x", c, i, ","));
        assertEquals(FormularioCaja.MSG_META_DECIMALES, FormularioCaja.validar("x", c, i, "10,001"));
        assertNull(FormularioCaja.validar("x", c, i, "0,01"));

        responder("POST " + BASE, json(201, caja(ID, "Viaje", "#ff6b1a", "piggy-bank", "0.00", "150000.50")));
        FormularioCaja f = formulario(null);
        f.setNombre("Viaje");
        assertTrue(f.setMeta("150000,50"));
        assertFalse("no deja tipear 3 decimales", f.setMeta("1,234"));
        guardar(f);
        JsonObject b = body(ultimo("POST", BASE));
        assertEquals(0, new BigDecimal("150000.50").compareTo(b.get("montoObjetivo").getAsBigDecimal()));
    }

    @Test
    public void k3_metaGrandeSinNotacionCientifica() {
        String json = new Gson().toJson(new CajaAhorroRequest("x", "#ff6b1a", "home", new BigDecimal("1E+3")));
        assertTrue(json, json.endsWith("\"montoObjetivo\":1000}"));
    }

    // ── K4: editar ────────────────────────────────────────────────────────────

    @Test
    public void k4_editarPrecargaYMandaMetaNullParaQuitarla() throws Exception {
        CajaAhorroResponse existente = cajaObj("2500.00", "10000.00");
        FormularioCaja f = formulario(existente);
        assertTrue(f.esEdicion());
        assertEquals("Viaje", f.getNombre());
        assertEquals("#0ea5e9", f.getColor());
        assertEquals("umbrella", f.getIcono());
        assertEquals("10000,00", f.getMetaTexto());

        responder("PUT " + BASE + "/" + ID, json(200, caja(ID, "Vacaciones", "#dc2626", "umbrella", "2500.00", null)));
        f.setNombre("Vacaciones");
        f.elegirColor("#dc2626");
        f.setMeta(""); // en blanco a propósito
        guardar(f);
        assertEquals(FormularioCaja.Paso.EXITO, f.getPaso());
        RecordedRequest r = ultimo("PUT", BASE + "/" + ID);
        String raw = r.getBody().readUtf8();
        assertTrue("null EXPLÍCITO, no omitido: " + raw, raw.contains("\"montoObjetivo\":null"));
        assertEquals(0, pedidosA("POST", BASE));
        assertNull(devuelta.get().getMontoObjetivo());
    }

    @Test
    public void k4_editarUnaCajaAjenaMuestraEl403() throws Exception {
        responder("PUT " + BASE + "/" + ID, error(403, "No tenes acceso a esta caja de ahorro"));
        FormularioCaja f = formulario(cajaObj("0.00", null));
        guardar(f);
        assertEquals("No tenes acceso a esta caja de ahorro", f.getError());
        assertEquals(FormularioCaja.Paso.EDITANDO, f.getPaso());
        assertEquals("editar no pide /limite", 0, pedidosA("GET", BASE + "/limite"));
    }

    @Test
    public void k4_editarNoSeBloqueaPorElTope() throws Exception {
        responder("PUT " + BASE + "/" + ID, json(200, caja(ID, "Viaje", "#0ea5e9", "umbrella", "0.00", null)));
        FormularioCaja f = formulario(cajaObj("0.00", null));
        f.setCantidadCajas(99);
        guardar(f);
        assertEquals(FormularioCaja.Paso.EXITO, f.getPaso());
    }

    // ── K5 / K6: depositar ─────────────────────────────────────────────────────

    @Test
    public void k5_depositarUsaElSaldoPrincipalYDevuelveLaCajaNueva() throws Exception {
        CajaAhorroResponse antes = cajaObj("2500.00", null);
        OperacionMontoCaja op = operacion(MovimientoCaja.Tipo.DEPOSITO, antes);
        BigDecimal principal = new BigDecimal("12345.67");
        op.usarTodo(op.disponible(principal));
        assertEquals("Usar todo = saldo PRINCIPAL exacto", "12345,67", op.getMontoTexto());

        responder("POST " + BASE + "/" + ID + "/depositar", json(200, caja(ID, "Viaje", "#0ea5e9", "umbrella", "14845.67", null)));
        confirmar(op, principal);
        assertEquals(OperacionMontoCaja.Paso.EXITO, op.getPaso());
        JsonObject b = body(ultimo("POST", BASE + "/" + ID + "/depositar"));
        assertEquals(1, b.size());
        assertEquals(0, new BigDecimal("12345.67").compareTo(b.get("monto").getAsBigDecimal()));
        assertEquals("saldo de la caja: el del backend", new BigDecimal("14845.67"), op.getResultado().getSaldo());
        assertEquals(new BigDecimal("14845.67"), devuelta.get().getSaldo());
        assertEquals("refresca saldos, cajas y campana", 1, refrescos.get());
        assertFalse(op.isMetaAlcanzada());

        // K12: queda en el feed de la sesión
        List<MovimientoCaja> feed = sesion.getLista();
        assertEquals(1, feed.size());
        assertEquals(MovimientoCaja.Tipo.DEPOSITO, feed.get(0).tipo);
        assertEquals(new BigDecimal("12345.67"), feed.get(0).monto);
        assertEquals("Viaje", feed.get(0).nombreCaja);
        assertEquals("hora del teléfono", "2026-09-28T10:00-03:00", feed.get(0).fecha);
    }

    @Test
    public void k5_saldoInsuficiente() throws Exception {
        OperacionMontoCaja op = operacion(MovimientoCaja.Tipo.DEPOSITO, cajaObj("0.00", null));
        op.setMonto("100,01");
        op.confirmar(new BigDecimal("100.00"));
        assertEquals("local, sin POST", OperacionMontoCaja.MSG_INSUFICIENTE_DEPOSITO, op.getError());
        op.setMonto("100");
        op.confirmar(null);
        assertEquals(OperacionMontoCaja.MSG_SIN_SALDO, op.getError());
        assertEquals(0, pedidosA("POST", BASE + "/" + ID + "/depositar"));

        // El saldo mostrado estaba viejo: el backend rechaza con su texto
        responder("POST " + BASE + "/" + ID + "/depositar", error(400, "No tenes saldo suficiente para depositar ese monto"));
        confirmar(op, new BigDecimal("500"));
        assertEquals("No tenes saldo suficiente para depositar ese monto", op.getError());
        assertEquals(OperacionMontoCaja.Paso.FORMULARIO, op.getPaso());
        assertEquals("refresca (el saldo estaba viejo)", 1, refrescos.get());
        assertTrue("un rechazo no va al feed", sesion.getLista().isEmpty());
    }

    @Test
    public void k6_depositoQueLlegaALaMetaYLaCampanaMuestraLasDos() throws Exception {
        OperacionMontoCaja op = operacion(MovimientoCaja.Tipo.DEPOSITO, cajaObj("9000.00", "10000.00"));
        op.setMonto("1500");
        responder("POST " + BASE + "/" + ID + "/depositar",
                json(200, caja(ID, "Viaje", "#0ea5e9", "umbrella", "10500.00", "10000.00")));
        confirmar(op, new BigDecimal("50000"));
        assertTrue(op.isMetaAlcanzada());

        // Lo que devuelve la campana después de ese depósito: DOS notificaciones
        responder("GET /api/notificaciones/sin-leer", json(200, "{\"cantidad\":2}"));
        responder("GET /api/notificaciones", json(200, "["
                + "{\"id\":31,\"plantillaCodigo\":\"CAJA_AHORRO_META_ALCANZADA\",\"mensaje\":\"Llegaste a la meta de $ 10.000,00 en Viaje\","
                + "\"leida\":false,\"fecha\":\"2026-09-28T10:00:01.000002-03:00\"},"
                + "{\"id\":30,\"plantillaCodigo\":\"CAJA_AHORRO_DEPOSITO\",\"mensaje\":\"Depositaste $ 1.500,00 en Viaje\","
                + "\"leida\":false,\"fecha\":\"2026-09-28T10:00:01.000001-03:00\"}]"));
        NotificacionesRepository campana = new NotificacionesRepository(() -> api, (t, d) -> () -> {});
        campana.abrirPanel();
        esperar("campana", () -> campana.getEstado().lista != null && campana.getEstado().sinLeer != null);
        List<NotificacionResponse> lista = campana.getEstado().lista;
        assertEquals("las DOS, no solo una", 2, lista.size());
        assertEquals("Meta alcanzada", TitulosNotificacion.de(lista.get(0).getPlantillaCodigo()));
        assertEquals("Depósito en caja de ahorro", TitulosNotificacion.de(lista.get(1).getPlantillaCodigo()));
        assertEquals(Long.valueOf(2), campana.getEstado().sinLeer);
    }

    @Test
    public void k6_metaAlcanzadaMismoCriterioQueElBackend() {
        assertTrue("llega justo", OperacionMontoCaja.alcanzaMeta(cajaObj("9000", "10000"), cajaObj("10000", "10000")));
        assertTrue("la pasa", OperacionMontoCaja.alcanzaMeta(cajaObj("0", "10000"), cajaObj("12000", "10000")));
        assertFalse("no llega", OperacionMontoCaja.alcanzaMeta(cajaObj("0", "10000"), cajaObj("9999.99", "10000")));
        assertFalse("ya estaba cumplida", OperacionMontoCaja.alcanzaMeta(cajaObj("10000", "10000"), cajaObj("11000", "10000")));
        assertFalse("sin meta", OperacionMontoCaja.alcanzaMeta(cajaObj("0", null), cajaObj("11000", null)));
    }

    // ── K7: retirar ───────────────────────────────────────────────────────────

    @Test
    public void k7_retirarUsaElSaldoDeLaCaja() throws Exception {
        OperacionMontoCaja op = operacion(MovimientoCaja.Tipo.RETIRO, cajaObj("2500.55", null));
        op.usarTodo(op.disponible(new BigDecimal("999999")));
        assertEquals("Usar todo = saldo de la CAJA exacto", "2500,55", op.getMontoTexto());
        op.setMonto("2500,56");
        op.confirmar(new BigDecimal("999999"));
        assertEquals(OperacionMontoCaja.MSG_INSUFICIENTE_RETIRO, op.getError());
        op.setMonto("500");
        op.confirmar(null);
        assertNotEquals("retirar no depende del saldo principal", OperacionMontoCaja.MSG_SIN_SALDO, op.getError());
        esperar("POST", () -> !op.isEnviando());
        // (sin respuesta encolada: 404 del mock -> rechazo; ahora el caso bueno)
        OperacionMontoCaja ok = operacion(MovimientoCaja.Tipo.RETIRO, cajaObj("2500.55", null));
        ok.setMonto("500");
        responder("POST " + BASE + "/" + ID + "/retirar", json(200, caja(ID, "Viaje", "#0ea5e9", "umbrella", "2000.55", null)));
        confirmar(ok, null);
        assertEquals(OperacionMontoCaja.Paso.EXITO, ok.getPaso());
        assertEquals(new BigDecimal("2000.55"), ok.getResultado().getSaldo());
        assertEquals(MovimientoCaja.Tipo.RETIRO, sesion.getLista().get(0).tipo);
    }

    private static void assertNotEquals(String msg, Object a, Object b) {
        assertFalse(msg, java.util.Objects.equals(a, b));
    }

    @Test
    public void k7_elBackendRechazaSiLaCajaNoAlcanza() throws Exception {
        responder("POST " + BASE + "/" + ID + "/retirar", error(400, "La caja no tiene suficiente saldo para retirar ese monto"));
        OperacionMontoCaja op = operacion(MovimientoCaja.Tipo.RETIRO, cajaObj("2500", null));
        op.setMonto("2000");
        confirmar(op, null);
        assertEquals("La caja no tiene suficiente saldo para retirar ese monto", op.getError());
        assertTrue(sesion.getLista().isEmpty());
    }

    // ── K8: eliminar ──────────────────────────────────────────────────────────

    private EliminacionCaja eliminacion(ApiService s, AtomicReference<CajaAhorroResponse> eliminada) {
        return new EliminacionCaja(() -> s, sesion, RELOJ, eliminada::set, refrescos::incrementAndGet);
    }

    @Test
    public void k8_eliminarConSaldoLoDevuelveYQuedaComoRetiro() throws Exception {
        responder("DELETE " + BASE + "/" + ID, new MockResponse().setResponseCode(204));
        AtomicReference<CajaAhorroResponse> eliminada = new AtomicReference<>();
        EliminacionCaja e = eliminacion(api, eliminada);
        assertTrue(EliminacionCaja.tieneSaldo(cajaObj("2500", null)));
        e.eliminar(cajaObj("2500.00", null));
        esperar("DELETE", () -> !e.isEnviando());
        assertEquals(EliminacionCaja.Estado.ELIMINADA, e.getEstado());
        assertEquals(ID, eliminada.get().getId());
        assertEquals(new BigDecimal("2500.00"), e.saldoDevuelto());
        assertEquals("refresca: el saldo principal sube", 1, refrescos.get());
        MovimientoCaja m = sesion.getLista().get(0);
        assertEquals(MovimientoCaja.Tipo.RETIRO, m.tipo);
        assertTrue(m.cajaEliminada);
        assertEquals(new BigDecimal("2500.00"), m.monto);
        e.consumir();
        assertEquals(EliminacionCaja.Estado.INACTIVO, e.getEstado());
    }

    @Test
    public void k8_eliminarVaciaNoAgregaNadaAlFeed() throws Exception {
        responder("DELETE " + BASE + "/" + ID, new MockResponse().setResponseCode(204));
        EliminacionCaja e = eliminacion(api, new AtomicReference<>());
        assertFalse(EliminacionCaja.tieneSaldo(cajaObj("0.00", null)));
        e.eliminar(cajaObj("0.00", null));
        esperar("DELETE", () -> !e.isEnviando());
        assertEquals(EliminacionCaja.Estado.ELIMINADA, e.getEstado());
        assertTrue(sesion.getLista().isEmpty());
    }

    @Test
    public void k8_errorAlEliminar() throws Exception {
        responder("DELETE " + BASE + "/" + ID, error(400, "La caja de ahorro no existe"));
        EliminacionCaja e = eliminacion(api, new AtomicReference<>());
        e.eliminar(cajaObj("10", null));
        esperar("DELETE", () -> !e.isEnviando());
        assertEquals(EliminacionCaja.Estado.ERROR, e.getEstado());
        assertEquals("La caja de ahorro no existe", e.getMensaje());
        assertTrue(sesion.getLista().isEmpty());
    }

    // ── K9: un solo pedido e incertidumbre ────────────────────────────────────

    @Test
    public void k9_dobleTapEsUnSoloPedidoEnCadaOperacion() throws Exception {
        MockResponse lenta = json(201, caja(ID, "Viaje", "#ff6b1a", "piggy-bank", "0.00", null)).setBodyDelay(300, TimeUnit.MILLISECONDS);
        responder("POST " + BASE, lenta);
        FormularioCaja f = formulario(null);
        f.setNombre("Viaje");
        f.guardar();
        f.guardar();
        f.guardar();
        esperar("crear", () -> !f.isEnviando());

        responder("POST " + BASE + "/" + ID + "/depositar",
                json(200, caja(ID, "Viaje", "#ff6b1a", "piggy-bank", "10.00", null)).setBodyDelay(300, TimeUnit.MILLISECONDS));
        OperacionMontoCaja d = operacion(MovimientoCaja.Tipo.DEPOSITO, cajaObj("0", null));
        d.setMonto("10");
        d.confirmar(new BigDecimal("100"));
        d.confirmar(new BigDecimal("100"));
        esperar("depositar", () -> !d.isEnviando());

        responder("POST " + BASE + "/" + ID + "/retirar",
                json(200, caja(ID, "Viaje", "#ff6b1a", "piggy-bank", "0.00", null)).setBodyDelay(300, TimeUnit.MILLISECONDS));
        OperacionMontoCaja r = operacion(MovimientoCaja.Tipo.RETIRO, cajaObj("10", null));
        r.setMonto("10");
        r.confirmar(null);
        r.confirmar(null);
        esperar("retirar", () -> !r.isEnviando());

        responder("DELETE " + BASE + "/" + ID, new MockResponse().setResponseCode(204).setBodyDelay(300, TimeUnit.MILLISECONDS));
        EliminacionCaja e = eliminacion(api, new AtomicReference<>());
        e.eliminar(cajaObj("0", null));
        e.eliminar(cajaObj("0", null));
        esperar("eliminar", () -> !e.isEnviando());

        Thread.sleep(100);
        assertEquals(1, pedidosA("POST", BASE));
        assertEquals(1, pedidosA("POST", BASE + "/" + ID + "/depositar"));
        assertEquals(1, pedidosA("POST", BASE + "/" + ID + "/retirar"));
        assertEquals(1, pedidosA("DELETE", BASE + "/" + ID));
        assertEquals("un depósito y un retiro, sin duplicados", 2, sesion.getLista().size());
    }

    private ApiService conTimeout() {
        return (ApiService) java.lang.reflect.Proxy.newProxyInstance(ApiService.class.getClassLoader(),
                new Class<?>[]{ApiService.class}, (proxy, metodo, args) -> {
                    Object r = metodo.invoke(api, args);
                    if (r instanceof retrofit2.Call) ((retrofit2.Call<?>) r).timeout().timeout(300, TimeUnit.MILLISECONDS);
                    return r;
                });
    }

    @Test
    public void k9_timeoutEsInciertoRefrescaYNoReintenta() throws Exception {
        ApiService lento = conTimeout();
        responder("POST " + BASE + "/" + ID + "/depositar", new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        OperacionMontoCaja d = operacion(MovimientoCaja.Tipo.DEPOSITO, cajaObj("0", null), lento);
        d.setMonto("10");
        confirmar(d, new BigDecimal("100"));
        assertEquals(OperacionMontoCaja.Paso.INCIERTO, d.getPaso());
        assertEquals(OperacionMontoCaja.MSG_INCIERTO, d.getError());
        assertEquals(1, refrescos.get());
        assertTrue("sin saber si se hizo, no va al feed", sesion.getLista().isEmpty());
        d.confirmar(new BigDecimal("100"));
        Thread.sleep(200);
        assertEquals(1, pedidosA("POST", BASE + "/" + ID + "/depositar"));

        responder("POST " + BASE, new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        FormularioCaja f = formulario(null, lento);
        f.setNombre("Viaje");
        guardar(f);
        assertEquals(FormularioCaja.Paso.INCIERTO, f.getPaso());
        assertEquals(FormularioCaja.MSG_INCIERTO_CREAR, f.getError());

        responder("DELETE " + BASE + "/" + ID, new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        EliminacionCaja e = eliminacion(lento, new AtomicReference<>());
        e.eliminar(cajaObj("50", null));
        esperar("DELETE", () -> !e.isEnviando());
        assertEquals(EliminacionCaja.Estado.INCIERTO, e.getEstado());
        assertEquals(EliminacionCaja.MSG_INCIERTO, e.getMensaje());
    }

    @Test
    public void k9_corteDespuesDeEnviarYDosCientosSinBody() throws Exception {
        responder("POST " + BASE + "/" + ID + "/retirar", new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        responder("POST " + BASE + "/" + ID + "/retirar", json(200, caja(ID, "Viaje", "#ff6b1a", "piggy-bank", "0.00", null)));
        OperacionMontoCaja r = operacion(MovimientoCaja.Tipo.RETIRO, cajaObj("10", null));
        r.setMonto("10");
        confirmar(r, null);
        assertEquals(OperacionMontoCaja.Paso.INCIERTO, r.getPaso());
        Thread.sleep(200);
        assertEquals("OkHttp no reintentó", 1, pedidosA("POST", BASE + "/" + ID + "/retirar"));

        responder("POST " + BASE + "/" + ID + "/depositar", new MockResponse().setResponseCode(200));
        OperacionMontoCaja d = operacion(MovimientoCaja.Tipo.DEPOSITO, cajaObj("0", null));
        d.setMonto("10");
        confirmar(d, new BigDecimal("100"));
        assertEquals("200 sin la caja: no sabemos el saldo nuevo", OperacionMontoCaja.Paso.INCIERTO, d.getPaso());
    }

    @Test
    public void k9_sinConexionAntesDeEnviarSePuedeReintentar() throws Exception {
        MockWebServer apagado = new MockWebServer();
        apagado.start();
        String url = apagado.url("/").toString();
        apagado.shutdown();
        ApiService caido = RetrofitClient.crear(url, () -> "x", System::currentTimeMillis, () -> {}, false, false);
        OperacionMontoCaja d = operacion(MovimientoCaja.Tipo.DEPOSITO, cajaObj("0", null), caido);
        d.setMonto("10");
        confirmar(d, new BigDecimal("100"));
        assertEquals(ApiErrores.MSG_SIN_CONEXION, d.getError());
        assertEquals(OperacionMontoCaja.Paso.FORMULARIO, d.getPaso());
        assertEquals(0, refrescos.get());
    }

    // ── La lista se actualiza al instante sin que un GET viejo lo deshaga ─────

    @Test
    public void listaActualizarYQuitarNoLosPisaUnGetViejo() throws Exception {
        String otra = "11111111-2222-3333-4444-555555555555";
        responder("GET " + BASE, json(200, "[" + caja(ID, "Viaje", "#ff6b1a", "home", "0.00", null) + ","
                + caja(otra, "Auto", "#16a34a", "target", "5.00", null) + "]"));
        ListaRemota<CajaAhorroResponse> repo = new ListaRemota<>(() -> api, ApiService::listarCajasAhorro,
                CajaAhorroResponse::getId, (t, d) -> () -> {});
        repo.refrescar();
        esperar("lista", () -> repo.getEstado().lista != null);

        // Un GET que salió ANTES del depósito y vuelve con el saldo viejo
        responder("GET " + BASE, json(200, "[" + caja(ID, "Viaje", "#ff6b1a", "home", "0.00", null) + ","
                + caja(otra, "Auto", "#16a34a", "target", "5.00", null) + "]").setBodyDelay(300, TimeUnit.MILLISECONDS));
        responder("GET " + BASE, json(200, "[" + caja(ID, "Viaje", "#ff6b1a", "home", "100.00", null) + "]"));
        repo.refrescar();
        repo.actualizar(new Gson().fromJson(caja(ID, "Viaje", "#ff6b1a", "home", "100.00", null), CajaAhorroResponse.class));
        assertEquals("reemplazada EN SU LUGAR", ID, repo.getEstado().lista.get(0).getId());
        assertEquals(new BigDecimal("100.00"), repo.getEstado().lista.get(0).getSaldo());
        repo.quitar(otra);
        assertEquals(1, repo.getEstado().lista.size());
        esperar("se volvió a pedir", () -> pedidosA("GET", BASE) == 3 && !repo.getEstado().cargando);
        assertEquals("el GET viejo no resucitó ni pisó nada", 1, repo.getEstado().lista.size());
        assertEquals(new BigDecimal("100.00"), repo.getEstado().lista.get(0).getSaldo());

        // Una caja nueva va AL FINAL (el backend ordena de la más vieja a la más nueva)
        repo.actualizar(new Gson().fromJson(caja(otra, "Nueva", "#16a34a", "target", "0.00", null), CajaAhorroResponse.class));
        assertEquals(otra, repo.getEstado().lista.get(1).getId());
    }
}
