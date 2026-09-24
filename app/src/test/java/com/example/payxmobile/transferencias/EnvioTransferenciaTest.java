package com.example.payxmobile.transferencias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.JwtFalso;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

/** Contrato del flujo de envío en 3 pasos contra un backend simulado. */
public class EnvioTransferenciaTest {

    static final String DESTINATARIO_OK = "{\"nombreCompleto\":\"Bea Gómez\",\"alias\":\"bea.gomez.payx\",\"cvu\":\"0000003100098765432109\"}";

    static String creada(String moneda, String monto, String estado) {
        return "{\"id\":\"11111111-2222-3333-4444-555555555555\",\"moneda\":\"" + moneda + "\",\"monto\":" + monto
                + ",\"concepto\":null,\"estado\":\"" + estado + "\",\"fecha\":\"2026-09-24T13:15:30Z\","
                + "\"fechaConfirmacion\":null,\"direccion\":\"ENVIADA\",\"contraparteNombre\":\"Bea Gómez\","
                + "\"contraparteAlias\":\"bea.gomez.payx\",\"esEmisor\":true}";
    }

    private MockWebServer server;
    private EnvioTransferencia envio;
    private ApiService api, apiSinReintentos;
    private final AtomicInteger refrescos = new AtomicInteger();

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String token = JwtFalso.conExp(System.currentTimeMillis() / 1000 + 7200);
        String url = server.url("/").toString();
        api = RetrofitClient.crear(url, () -> token, System::currentTimeMillis, () -> {}, false, true);
        apiSinReintentos = RetrofitClient.crear(url, () -> token, System::currentTimeMillis, () -> {}, false, false);
        envio = nuevo(Moneda.PESOS);
    }

    private EnvioTransferencia nuevo(Moneda m) {
        return new EnvioTransferencia(m, () -> api, () -> apiSinReintentos, refrescos::incrementAndGet);
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

    private void hastaConfirmar(EnvioTransferencia e, String dest, String monto, BigDecimal saldo) throws Exception {
        server.enqueue(new MockResponse().setBody(DESTINATARIO_OK));
        e.setDestinatario(dest);
        e.setMonto(monto);
        e.continuar(saldo);
        esperar("paso CONFIRMAR", () -> e.getPaso() == EnvioTransferencia.Paso.CONFIRMAR);
        server.takeRequest(); // el GET /destinatario
    }

    private static JsonObject json(RecordedRequest r) {
        return JsonParser.parseString(r.getBody().readUtf8()).getAsJsonObject();
    }

    // ── T1 / T12: validaciones locales y precarga sin requests ─────────────────

    @Test
    public void t1_validacionesLocalesNoHacenNingunaRequest() throws Exception {
        BigDecimal saldo = new BigDecimal("3700.00");
        envio.continuar(saldo);
        assertEquals(ValidadorTransferencia.SIN_DESTINATARIO, envio.getError());
        envio.setDestinatario("bea.gomez.payx");
        envio.continuar(saldo);
        assertEquals(ValidadorTransferencia.MONTO_INVALIDO, envio.getError());
        envio.setMonto("0");
        envio.continuar(saldo);
        assertEquals(ValidadorTransferencia.MONTO_INVALIDO, envio.getError());
        envio.setMonto("3700,01");
        envio.continuar(saldo);
        assertEquals(ValidadorTransferencia.SALDO_INSUFICIENTE, envio.getError());
        // El tipeo de más decimales ni siquiera entra
        assertFalse(envio.setMonto("0,005"));
        assertEquals("3700,01", envio.getMontoTexto());
        Thread.sleep(100);
        assertEquals(0, server.getRequestCount());
        assertEquals(EnvioTransferencia.Paso.FORMULARIO, envio.getPaso());
    }

    @Test
    public void t12_precargaCompletaElFormularioYNoEnviaNada() throws Exception {
        EnvioTransferencia e = nuevo(Moneda.USD);
        e.precargar("@beagomez", "12.5", "Regalo", "PENDIENTE");
        assertEquals("@beagomez", e.getDestinatario());
        assertEquals("12,5", e.getMontoTexto());
        assertEquals("Regalo", e.getMotivo());
        assertTrue(e.esPendiente());
        assertEquals(EnvioTransferencia.Paso.FORMULARIO, e.getPaso());
        Thread.sleep(150);
        assertEquals("la precarga nunca ejecuta nada", 0, server.getRequestCount());
    }

    @Test
    public void t12_precargaConDemasiadosDecimalesNoRedondeaEnSilencio() {
        EnvioTransferencia e = nuevo(Moneda.PESOS);
        e.precargar("ana", "0.005", null, null);
        assertEquals("", e.getMontoTexto());
        assertEquals(ValidadorTransferencia.MONTO_INVALIDO, e.getError());
    }

    // ── T2: resolver destinatario ─────────────────────────────────────────────

    @Test
    public void t2_valorCodificadoYConTrim() throws Exception {
        String[][] casos = {
                {"  @beagomez  ", "/api/transferencias/destinatario?valor=%40beagomez"},
                {"bea.gomez.payx", "/api/transferencias/destinatario?valor=bea.gomez.payx"},
                {"0000003100098765432109", "/api/transferencias/destinatario?valor=0000003100098765432109"},
                {"ana maria", "/api/transferencias/destinatario?valor=ana+maria"},
        };
        for (String[] c : casos) {
            EnvioTransferencia e = nuevo(Moneda.PESOS);
            server.enqueue(new MockResponse().setBody(DESTINATARIO_OK));
            e.setDestinatario(c[0]);
            e.setMonto("10");
            e.continuar(new BigDecimal("100"));
            RecordedRequest r = server.takeRequest(2, TimeUnit.SECONDS);
            assertEquals("GET", r.getMethod());
            assertEquals(c[1], r.getPath());
            esperar("confirmar", () -> e.getPaso() == EnvioTransferencia.Paso.CONFIRMAR);
            assertEquals("Bea Gómez", e.getDestinatarioInfo().getNombreCompleto());
            assertEquals("0000003100098765432109", e.getDestinatarioInfo().getCvu());
        }
    }

    @Test
    public void t2_noSeLlamaAlTipear() throws Exception {
        for (String parcial : new String[]{"l", "la", "lau", "bea.gomez.payx"}) envio.setDestinatario(parcial);
        envio.setMonto("1");
        Thread.sleep(150);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void t2_cadaErrorDelBackendSeMuestraTextual() throws Exception {
        String[] errores = {
                "No encontramos un usuario con ese nombre de usuario",
                "No encontramos ninguna cuenta con ese alias",
                "No encontramos ninguna cuenta con ese CVU",
                "No podes transferirte a vos mismo",
                "El destinatario no puede recibir transferencias",
        };
        for (String msg : errores) {
            EnvioTransferencia e = nuevo(Moneda.PESOS);
            server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"" + msg + "\"}"));
            e.setDestinatario("x");
            e.setMonto("10");
            e.continuar(new BigDecimal("100"));
            esperar("error", () -> !e.isResolviendo() && e.getError() != null);
            assertEquals(msg, e.getError());
            assertEquals(EnvioTransferencia.Paso.FORMULARIO, e.getPaso());
        }
        EnvioTransferencia e = nuevo(Moneda.PESOS);
        server.enqueue(new MockResponse().setResponseCode(429)
                .setBody("{\"error\":\"Demasiadas solicitudes. Intenta de nuevo en unos minutos\"}"));
        e.setDestinatario("x");
        e.setMonto("10");
        e.continuar(new BigDecimal("100"));
        esperar("429", () -> !e.isResolviendo() && e.getError() != null);
        assertEquals("Demasiadas solicitudes. Intenta de nuevo en unos minutos", e.getError());
        // Sin {"error"} (403 vacío por validación): el texto genérico de la web
        EnvioTransferencia e2 = nuevo(Moneda.PESOS);
        server.enqueue(new MockResponse().setResponseCode(403));
        e2.setDestinatario("x");
        e2.setMonto("10");
        e2.continuar(new BigDecimal("100"));
        esperar("403", () -> !e2.isResolviendo() && e2.getError() != null);
        assertEquals(EnvioTransferencia.MSG_DESTINATARIO_NO_ENCONTRADO, e2.getError());
    }

    // ── T3 / T4 / T5: crear ───────────────────────────────────────────────────

    @Test
    public void t3_directaBodyExactoSinConceptoYSinArtefactosDeFloat() throws Exception {
        hastaConfirmar(envio, "  bea.gomez.payx ", "0,1", new BigDecimal("3700"));
        server.enqueue(new MockResponse().setResponseCode(201).setBody(creada("PESOS", "0.10000000", "COMPLETADA")));
        envio.confirmar();
        RecordedRequest r = server.takeRequest(2, TimeUnit.SECONDS);
        assertEquals("POST", r.getMethod());
        assertEquals("/api/transferencias", r.getPath());
        // Orden y contenido exactos; "concepto" omitido; 0.1 sin notación científica ni 0.1000000000000000055...
        assertEquals("{\"destinatario\":\"bea.gomez.payx\",\"moneda\":\"PESOS\",\"monto\":0.1,\"tipo\":\"DIRECTA\"}",
                r.getBody().readUtf8());
        esperar("éxito", () -> envio.getPaso() == EnvioTransferencia.Paso.EXITO);
        assertEquals("Bea Gómez", envio.nombreDestino());
        assertEquals("se refrescan saldos, listado y notificaciones", 1, refrescos.get());
    }

    @Test
    public void t3_montosChicosDeCriptoSinNotacionCientifica() throws Exception {
        EnvioTransferencia e = nuevo(Moneda.BTC);
        hastaConfirmar(e, "bea.gomez.payx", "0,00000001", new BigDecimal("0.00000389"));
        server.enqueue(new MockResponse().setResponseCode(201).setBody(creada("BTC", "0.00000001", "COMPLETADA")));
        e.setMotivo("Regalo");
        e.confirmar();
        JsonObject body = json(server.takeRequest(2, TimeUnit.SECONDS));
        assertEquals("BTC", body.get("moneda").getAsString());
        assertEquals("0.00000001", body.get("monto").getAsBigDecimal().toPlainString());
        assertEquals("Regalo", body.get("concepto").getAsString());
    }

    @Test
    public void t3_elBodyNuncaLlevaNotacionCientificaEnElTexto() throws Exception {
        EnvioTransferencia e = nuevo(Moneda.BTC);
        hastaConfirmar(e, "ana", "0,00000001", BigDecimal.ONE);
        server.enqueue(new MockResponse().setResponseCode(201).setBody(creada("BTC", "0.00000001", "COMPLETADA")));
        e.confirmar();
        String texto = server.takeRequest(2, TimeUnit.SECONDS).getBody().readUtf8();
        assertTrue(texto, texto.contains("\"monto\":0.00000001"));
        assertFalse(texto, texto.contains("E-"));
    }

    @Test
    public void t4_pendienteMandaTipoPendienteYElSaldoLuegoNoCambia() throws Exception {
        envio.setTipo(EnvioTransferencia.PENDIENTE);
        envio.setMonto("100");
        assertEquals(new BigDecimal("3700"), envio.saldoLuego(new BigDecimal("3700")));
        envio.setTipo(EnvioTransferencia.DIRECTA);
        assertEquals(new BigDecimal("3600"), envio.saldoLuego(new BigDecimal("3700")));
        envio.setTipo(EnvioTransferencia.PENDIENTE);

        hastaConfirmar(envio, "bea.gomez.payx", "100", new BigDecimal("3700"));
        server.enqueue(new MockResponse().setResponseCode(201).setBody(creada("PESOS", "100", "PENDIENTE")));
        envio.confirmar();
        assertEquals("PENDIENTE", json(server.takeRequest(2, TimeUnit.SECONDS)).get("tipo").getAsString());
        esperar("éxito", () -> envio.getPaso() == EnvioTransferencia.Paso.EXITO);
        assertEquals("PENDIENTE", envio.getResultado().getEstado());
    }

    @Test
    public void t5_cadaMonedaMandaSuCodigo() throws Exception {
        for (Moneda m : Moneda.values()) {
            EnvioTransferencia e = nuevo(m);
            hastaConfirmar(e, "ana", "1", new BigDecimal("5"));
            server.enqueue(new MockResponse().setResponseCode(201).setBody(creada(m.codigo(), "1", "COMPLETADA")));
            e.confirmar();
            assertEquals(m.codigo(), json(server.takeRequest(2, TimeUnit.SECONDS)).get("moneda").getAsString());
            esperar("éxito " + m, () -> e.getPaso() == EnvioTransferencia.Paso.EXITO);
        }
    }

    @Test
    public void t5_cambiarDeCriptoBorraMontoYError() {
        EnvioTransferencia e = nuevo(Moneda.BTC);
        e.setDestinatario("ana");
        e.setMonto("0,5");
        e.continuar(BigDecimal.ZERO); // error de saldo
        assertEquals(ValidadorTransferencia.SALDO_INSUFICIENTE, e.getError());
        e.setMoneda(Moneda.ETH);
        assertEquals("", e.getMontoTexto());
        assertNull(e.getError());
        assertEquals(Moneda.ETH, e.getMoneda());
    }

    // ── T7 / T10: errores al crear ─────────────────────────────────────────────

    @Test
    public void t7_saldoInsuficienteDelBackendConservaLosDatos() throws Exception {
        hastaConfirmar(envio, "bea.gomez.payx", "100", new BigDecimal("3700"));
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"error\":\"No tenes saldo suficiente para esta transferencia\"}"));
        envio.confirmar();
        esperar("error", () -> !envio.isEnviando() && envio.getError() != null);
        assertEquals("No tenes saldo suficiente para esta transferencia", envio.getError());
        assertEquals(EnvioTransferencia.Paso.CONFIRMAR, envio.getPaso());
        envio.editar();
        assertEquals("bea.gomez.payx", envio.getDestinatario().trim());
        assertEquals("100", envio.getMontoTexto());
        assertEquals(0, refrescos.get());
    }

    @Test
    public void t10_5xxY403VacioNoRompenYDejanReintentar() throws Exception {
        hastaConfirmar(envio, "ana", "10", new BigDecimal("100"));
        server.enqueue(new MockResponse().setResponseCode(500));
        envio.confirmar();
        esperar("500", () -> !envio.isEnviando() && envio.getError() != null);
        assertEquals(ApiErrores.MSG_SERVIDOR, envio.getError());
        assertEquals(EnvioTransferencia.Paso.CONFIRMAR, envio.getPaso());

        server.enqueue(new MockResponse().setResponseCode(403));
        envio.confirmar(); // un rechazo explícito sí deja reintentar
        esperar("403", () -> !envio.isEnviando() && server.getRequestCount() == 3);
        assertEquals(EnvioTransferencia.MSG_NO_SE_PUDO, envio.getError());
    }

    // ── T9: un solo pedido, sin reintentos, resultado incierto ─────────────────

    @Test
    public void t9_dobleTapEnConfirmarHaceUnaSolaRequest() throws Exception {
        hastaConfirmar(envio, "ana", "10", new BigDecimal("100"));
        server.enqueue(new MockResponse().setResponseCode(201).setBody(creada("PESOS", "10", "COMPLETADA"))
                .setHeadersDelay(300, TimeUnit.MILLISECONDS));
        envio.confirmar();
        envio.confirmar();
        envio.confirmar();
        esperar("éxito", () -> envio.getPaso() == EnvioTransferencia.Paso.EXITO);
        Thread.sleep(150);
        assertEquals("1 GET destinatario + 1 POST", 2, server.getRequestCount());
    }

    @Test
    public void t9_timeoutDespuesDeEnviarEsInciertoYNoReintenta() throws Exception {
        ApiService lento = conTimeoutCorto(apiSinReintentos);
        EnvioTransferencia e = new EnvioTransferencia(Moneda.PESOS, () -> api, () -> lento, refrescos::incrementAndGet);
        hastaConfirmar(e, "ana", "10", new BigDecimal("100"));
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        e.confirmar();
        esperar("incierto", () -> e.getPaso() == EnvioTransferencia.Paso.INCIERTO);
        assertEquals(FalloEnvio.MSG_INCIERTO, e.getError());
        assertEquals("refresca listado y saldos", 1, refrescos.get());
        e.confirmar(); // desde INCIERTO no se puede volver a mandar
        Thread.sleep(300);
        assertEquals("1 GET + 1 POST: sin reintentos automáticos", 2, server.getRequestCount());
    }

    @Test
    public void t9_corteDespuesDeEnviarEsInciertoYNoReintenta() throws Exception {
        hastaConfirmar(envio, "ana", "10", new BigDecimal("100"));
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        server.enqueue(new MockResponse().setResponseCode(201).setBody(creada("PESOS", "10", "COMPLETADA")));
        envio.confirmar();
        esperar("incierto", () -> envio.getPaso() == EnvioTransferencia.Paso.INCIERTO);
        Thread.sleep(300);
        assertEquals("1 GET + 1 POST (OkHttp no reintentó el POST)", 2, server.getRequestCount());
        assertEquals(FalloEnvio.MSG_INCIERTO, envio.getError());
        assertEquals(1, refrescos.get());
    }

    @Test
    public void t9_sinConexionAntesDeEnviarNoEsInciertoYSePuedeReintentar() throws Exception {
        hastaConfirmar(envio, "ana", "10", new BigDecimal("100"));
        String url = server.url("/").toString();
        server.shutdown(); // conexión rechazada: seguro que no salió
        ApiService caido = RetrofitClient.crear(url, () -> "x", System::currentTimeMillis, () -> {}, false, false);
        EnvioTransferencia e = new EnvioTransferencia(Moneda.PESOS, () -> caido, () -> caido, refrescos::incrementAndGet);
        e.setDestinatario("ana");
        e.setMonto("10");
        // Se fuerza el paso CONFIRMAR como si ya se hubiera resuelto
        e.restaurar(EnvioTransferencia.Paso.CONFIRMAR,
                new com.example.payxmobile.model.DestinatarioResponse("Ana", "ana.payx", "1"), null);
        e.confirmar();
        esperar("error", () -> !e.isEnviando() && e.getError() != null);
        assertEquals(ApiErrores.MSG_SIN_CONEXION, e.getError());
        assertEquals(EnvioTransferencia.Paso.CONFIRMAR, e.getPaso());
        assertEquals(0, refrescos.get());
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
