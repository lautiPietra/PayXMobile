package com.example.payxmobile.transferencias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.JwtFalso;
import com.example.payxmobile.model.CrearCambioDolaresRequest;
import com.example.payxmobile.model.CrearOperacionCriptoRequest;
import com.example.payxmobile.model.CrearTransferenciaRequest;
import com.example.payxmobile.model.MontoCajaAhorroRequest;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.google.gson.Gson;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Auditoría del backend, test (g): los montos que se mandan viajan exactos, siempre desde BigDecimal. Con
 * double, 10,20 podría salir 10.199999999999999 (17 decimales) y el backend lo rechazaría con 400.
 */
public class MontoJsonTest {

    private final Gson gson = RetrofitClient.gson();

    @Test
    public void g_transferenciaDe10_20PesosViajaComo10_2() {
        String json = gson.toJson(new CrearTransferenciaRequest("ana", "PESOS", MontoInput.parsear("10,20"), null, "DIRECTA"));
        assertTrue(json, json.contains("\"monto\":10.2,") || json.endsWith("\"monto\":10.2}"));
        assertFalse(json, json.contains("10.19"));
        assertEquals("con double sí aparecería", "10.199999999999999", new BigDecimal(10.2).toString().substring(0, 18));
    }

    @Test
    public void g_otrosMontosTambienPlanosYExactos() {
        assertTrue(gson.toJson(new CrearCambioDolaresRequest("COMPRA", MontoInput.parsear("1000,50"))).contains("\"monto\":1000.5"));
        assertTrue(gson.toJson(new MontoCajaAhorroRequest(MontoInput.parsear("0,10"))).contains("\"monto\":0.1"));
        // Venta de cripto: 8 decimales, nunca "1E-8"
        String cripto = gson.toJson(new CrearOperacionCriptoRequest("VENTA", "BTC", MontoInput.parsear("0,00000001")));
        assertTrue(cripto, cripto.contains("\"monto\":0.00000001"));
        assertTrue(gson.toJson(new CrearTransferenciaRequest("ana", "PESOS", new BigDecimal("100.00"), null, "DIRECTA"))
                .contains("\"monto\":100,"));
    }

    // ── El cuerpo real del POST, armado por el flujo de la pantalla ───────────

    private MockWebServer server;
    private ApiService api;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        String token = JwtFalso.conExp(System.currentTimeMillis() / 1000 + 7200);
        api = RetrofitClient.crear(server.url("/").toString(), () -> token, () -> {}, false, false);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void g_elPostDeLaTransferenciaLlevaMonto10_2() throws Exception {
        EnvioTransferencia envio = new EnvioTransferencia(Moneda.PESOS, () -> api, () -> api, () -> {});
        server.enqueue(new MockResponse().setBody(EnvioTransferenciaTest.DESTINATARIO_OK));
        server.enqueue(new MockResponse().setResponseCode(201).setBody(EnvioTransferenciaTest.creada("PESOS", "10.20", "COMPLETADA")));
        envio.setDestinatario("bea.gomez.payx");
        assertTrue(envio.setMonto("10,20"));
        envio.continuar(new BigDecimal("3700.00"));
        esperar(() -> envio.getPaso() == EnvioTransferencia.Paso.CONFIRMAR);
        envio.confirmar();
        esperar(() -> envio.getPaso() == EnvioTransferencia.Paso.EXITO);

        server.takeRequest(1, TimeUnit.SECONDS); // GET /destinatario
        RecordedRequest post = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("POST", post.getMethod());
        String cuerpo = post.getBody().readUtf8();
        assertTrue(cuerpo, cuerpo.contains("\"monto\":10.2,"));
        assertTrue(cuerpo, cuerpo.contains("\"moneda\":\"PESOS\""));
        assertFalse(cuerpo, cuerpo.contains("10.19"));
    }

    private static void esperar(java.util.function.BooleanSupplier c) throws InterruptedException {
        long fin = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) throw new AssertionError("Timeout");
            Thread.sleep(10);
        }
    }
}
