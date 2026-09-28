package com.example.payxmobile.tarjeta;

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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/** GET /api/tarjeta contra MockWebServer: V1, V2, V5 y V6. Datos ficticios. */
public class RevelarTarjetaTest {

    private MockWebServer server;
    private ApiService api;
    private final AtomicLong ahora = new AtomicLong(1_000_000L);

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

    private RevelarTarjeta nueva() {
        return nueva(api);
    }

    private RevelarTarjeta nueva(ApiService s) {
        return new RevelarTarjeta(() -> s, ahora::get);
    }

    private static void esperar(String que, BooleanSupplier c) throws InterruptedException {
        long fin = System.currentTimeMillis() + 5000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) throw new AssertionError("Timeout: " + que);
            Thread.sleep(10);
        }
    }

    private static MockResponse ok() {
        return new MockResponse().setBody(FormatoTarjetaTest.JSON);
    }

    private void revelarYEsperar(RevelarTarjeta r) throws InterruptedException {
        r.revelar();
        esperar("GET", () -> r.getEstado() != RevelarTarjeta.Estado.CARGANDO);
    }

    // ── V2: solo cuando se pide ────────────────────────────────────────────────

    @Test
    public void v2_nadaSePideHastaQueElUsuarioLoPide() throws Exception {
        RevelarTarjeta r = nueva();
        Thread.sleep(150);
        assertEquals("crear la pantalla no pide nada", 0, server.getRequestCount());
        assertEquals(RevelarTarjeta.Estado.OCULTA, r.getEstado());
        assertNull(r.getVisible());

        server.enqueue(ok());
        revelarYEsperar(r);
        assertEquals(1, server.getRequestCount());
        RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("GET", req.getMethod());
        assertEquals("/api/tarjeta", req.getPath());
        assertTrue(req.getHeader("Authorization").startsWith("Bearer "));
    }

    @Test
    public void v1_v2_ocultarYMostrarNoRepiteElPedido_salirSi() throws Exception {
        server.enqueue(ok());
        RevelarTarjeta r = nueva();
        revelarYEsperar(r);
        assertTrue(r.isVisible());
        assertEquals("9004123456789012", r.getVisible().getNumero());
        assertEquals("007", r.getVisible().getCvv());
        assertEquals("ANA PRUEBA", r.getVisible().getTitular());
        assertEquals("2031-09-30", r.getVisible().getVencimiento());

        r.ocultar();
        assertNull("oculta: la UI no puede leer los datos", r.getVisible());
        r.revelar();
        assertTrue(r.isVisible());
        assertEquals("mostrar de nuevo no pide otra vez", 1, server.getRequestCount());

        // Salir de la pantalla: se olvida; al volver, nuevo GET
        r.olvidar();
        assertNull(r.getVisible());
        assertEquals(RevelarTarjeta.Estado.OCULTA, r.getEstado());
        server.enqueue(ok());
        revelarYEsperar(r);
        assertEquals(2, server.getRequestCount());
    }

    @Test
    public void v2_dobleTapEsUnSoloPedido() throws Exception {
        server.enqueue(ok().setBodyDelay(300, TimeUnit.MILLISECONDS));
        RevelarTarjeta r = nueva();
        r.revelar();
        r.revelar();
        r.revelar();
        esperar("GET", () -> r.getEstado() != RevelarTarjeta.Estado.CARGANDO);
        Thread.sleep(100);
        assertEquals(1, server.getRequestCount());
    }

    @Test
    public void unaRespuestaQueLlegaDespuesDeSalirSeDescarta() throws Exception {
        server.enqueue(ok().setBodyDelay(300, TimeUnit.MILLISECONDS));
        RevelarTarjeta r = nueva();
        r.revelar();
        r.olvidar(); // se fue de la pantalla con el pedido en vuelo
        Thread.sleep(600);
        assertNull("no se muestra al volver sin haberlo pedido", r.getVisible());
        assertEquals(RevelarTarjeta.Estado.OCULTA, r.getEstado());
    }

    // ── V5: 429 ───────────────────────────────────────────────────────────────

    @Test
    public void v5_rateLimitMensajeClaroYReintentoPasadoElMinuto() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429)
                .setBody("{\"error\":\"Demasiadas solicitudes. Intenta de nuevo en unos minutos\"}"));
        RevelarTarjeta r = nueva();
        revelarYEsperar(r);
        assertEquals(RevelarTarjeta.Estado.ERROR, r.getEstado());
        assertEquals(RevelarTarjeta.MSG_RATE_LIMIT, r.getError());
        assertEquals(60, r.segundosParaReintentar());

        ahora.addAndGet(30_000);
        assertEquals(30, r.segundosParaReintentar());
        r.revelar();
        Thread.sleep(100);
        assertEquals("bloqueado: no se insiste contra el límite", 1, server.getRequestCount());
        r.olvidar(); // salir y volver no saltea la espera
        r.revelar();
        Thread.sleep(100);
        assertEquals(1, server.getRequestCount());

        ahora.addAndGet(30_000);
        assertEquals(0, r.segundosParaReintentar());
        server.enqueue(ok());
        revelarYEsperar(r);
        assertTrue("pasado el minuto se puede", r.isVisible());
        assertEquals(2, server.getRequestCount());
    }

    // ── V6: 403, 5xx, sin conexión, respuesta rara ─────────────────────────────

    @Test
    public void v6_403SinBodyEsSesionInvalida() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(403));
        RevelarTarjeta r = nueva();
        revelarYEsperar(r);
        assertTrue("la pantalla cierra la sesión (una sola vez: SesionUtils ignora si ya no hay token)",
                r.isSesionInvalida());
        assertNull(r.getVisible());
    }

    @Test
    public void v6_servidorCaidoMensajeYReintentar() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503));
        RevelarTarjeta r = nueva();
        revelarYEsperar(r);
        assertEquals(RevelarTarjeta.Estado.ERROR, r.getEstado());
        assertEquals(ApiErrores.MSG_SERVIDOR, r.getError());
        assertFalse(r.isSesionInvalida());
        server.enqueue(ok());
        revelarYEsperar(r); // "Reintentar"
        assertTrue(r.isVisible());
    }

    @Test
    public void v6_sinConexionMensajeYReintentar() throws Exception {
        MockWebServer apagado = new MockWebServer();
        apagado.start();
        String url = apagado.url("/").toString();
        apagado.shutdown();
        ApiService caido = RetrofitClient.crear(url, () -> "x", System::currentTimeMillis, () -> {}, false);
        RevelarTarjeta r = nueva(caido);
        revelarYEsperar(r);
        assertEquals(ApiErrores.MSG_SIN_CONEXION, r.getError());
        assertEquals("se puede reintentar ya", 0, r.segundosParaReintentar());
    }

    @Test
    public void respuestaIncompletaNoSeMuestra() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"numero\":\"9004\",\"cvv\":\"1\"}"));
        RevelarTarjeta r = nueva();
        revelarYEsperar(r);
        assertEquals(ApiErrores.MSG_RESPUESTA_INESPERADA, r.getError());
        assertNull(r.getVisible());
        assertNotNull(r.getError());
    }
}
