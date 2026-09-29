package com.example.payxmobile.asistente;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.model.AsistenteMensajeRequest.Turno;
import com.example.payxmobile.model.AsistenteRespuestaResponse.AccionSugerida;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/** El chat del asistente contra MockWebServer: los casos de prueba del módulo que no necesitan pantalla. */
public class ConversacionAsistenteTest {

    private static final String USUARIO_A = "11111111-1111-1111-1111-111111111111";
    private static final String USUARIO_B = "22222222-2222-2222-2222-222222222222";

    private MockWebServer server;
    private ApiService api;
    private AlmacenMemoria almacen;

    /** Hace de SharedPreferences: sobrevive a "matar la app" (crear otra ConversacionAsistente). */
    static final class AlmacenMemoria implements ConversacionAsistente.Almacen {
        String contenido;
        int borrados;

        @Override public String leer() { return contenido; }
        @Override public void guardar(String c) { contenido = c; }
        @Override public void borrar() { contenido = null; borrados++; }
    }

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        api = RetrofitClient.crear(server.url("/").toString(), () -> "jwt", () -> {},
                false, false, 5);
        almacen = new AlmacenMemoria();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private ConversacionAsistente nueva(String usuario) {
        ConversacionAsistente c = new ConversacionAsistente(() -> api, almacen);
        c.usarUsuario(usuario);
        return c;
    }

    private static MockResponse respuesta(String texto) {
        return new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"texto\":\"" + texto + "\",\"accionSugerida\":null}");
    }

    private static void esperar(String que, BooleanSupplier condicion) throws InterruptedException {
        long fin = System.currentTimeMillis() + 5000;
        while (!condicion.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) throw new AssertionError("Timeout esperando: " + que);
            Thread.sleep(10);
        }
    }

    private JsonObject cuerpo(RecordedRequest r) {
        return JsonParser.parseString(r.getBody().readUtf8()).getAsJsonObject();
    }

    private void enviarYEsperar(ConversacionAsistente c, String texto) throws InterruptedException {
        assertTrue(c.enviar(texto));
        esperar("respuesta de " + texto, () -> !c.isEnviando());
    }

    // ── 1. Mensaje simple ─────────────────────────────────────────────────────

    @Test
    public void mensajeSimple_muestraLaRespuestaYMandaElContratoDelBackend() throws Exception {
        server.enqueue(respuesta("Tenés $ 10.000,00 en pesos."));
        ConversacionAsistente c = nueva(USUARIO_A);

        assertTrue(c.enviar("  ¿cuál es mi saldo?  "));
        assertTrue("mientras espera: escribiendo...", c.isEnviando());
        assertEquals("el mensaje del usuario se ve al instante", 1, c.getMensajes().size());
        esperar("respuesta", () -> !c.isEnviando());

        RecordedRequest r = server.takeRequest(1, TimeUnit.SECONDS);
        assertEquals("POST", r.getMethod());
        assertEquals("/api/asistente/mensaje", r.getPath());
        assertEquals("Bearer jwt", r.getHeader("Authorization"));
        JsonObject body = cuerpo(r);
        assertEquals("¿cuál es mi saldo?", body.get("mensaje").getAsString());
        assertEquals("primer mensaje: historial vacío (el saludo no viaja)", 0, body.getAsJsonArray("historial").size());

        List<MensajeChat> m = c.getMensajes();
        assertEquals(2, m.size());
        assertEquals(MensajeChat.USUARIO, m.get(0).rol);
        assertEquals(MensajeChat.ASISTENTE, m.get(1).rol);
        assertEquals("Tenés $ 10.000,00 en pesos.", m.get(1).texto);
        assertNull(m.get(1).accion);
        assertNull(c.getError());
    }

    // ── 2. Historial encadenado ───────────────────────────────────────────────

    @Test
    public void mensajesEncadenados_elHistorialLlevaLosTurnosAnterioresConSusRoles() throws Exception {
        server.enqueue(respuesta("R1"));
        server.enqueue(respuesta("R2"));
        server.enqueue(respuesta("R3"));
        ConversacionAsistente c = nueva(USUARIO_A);

        enviarYEsperar(c, "P1");
        enviarYEsperar(c, "P2");
        enviarYEsperar(c, "P3");

        JsonArray h1 = cuerpo(server.takeRequest()).getAsJsonArray("historial");
        JsonObject b2 = cuerpo(server.takeRequest());
        JsonObject b3 = cuerpo(server.takeRequest());
        assertEquals(0, h1.size());

        assertEquals("P2", b2.get("mensaje").getAsString());
        JsonArray h2 = b2.getAsJsonArray("historial");
        assertEquals(2, h2.size());
        assertTurno(h2, 0, "USUARIO", "P1");
        assertTurno(h2, 1, "ASISTENTE", "R1");

        assertEquals("P3", b3.get("mensaje").getAsString());
        JsonArray h3 = b3.getAsJsonArray("historial");
        assertEquals(4, h3.size());
        assertTurno(h3, 0, "USUARIO", "P1");
        assertTurno(h3, 1, "ASISTENTE", "R1");
        assertTurno(h3, 2, "USUARIO", "P2");
        assertTurno(h3, 3, "ASISTENTE", "R2");
        assertEquals(6, c.getMensajes().size());
    }

    private static void assertTurno(JsonArray h, int i, String rol, String texto) {
        JsonObject t = h.get(i).getAsJsonObject();
        assertEquals(rol, t.get("rol").getAsString());
        assertEquals(texto, t.get("texto").getAsString());
        assertEquals("solo rol y texto", 2, t.size());
    }

    // ── 3. Conversación larga: 30 turnos y 4000 caracteres ────────────────────

    @Test
    public void conversacionLarga_seRecortaALosUltimos30YCadaTextoA4000() throws Exception {
        // 40 turnos guardados (20 idas y vueltas), varios de 5000 caracteres
        List<MensajeChat> guardados = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            guardados.add(MensajeChat.delUsuario("pregunta " + i + (i % 3 == 0 ? repetir('x', 5000) : "")));
            guardados.add(MensajeChat.delAsistente("respuesta " + i + (i % 4 == 0 ? repetir('y', 6000) : ""), null));
        }
        JsonObject guardado = new JsonObject();
        guardado.addProperty("usuarioId", USUARIO_A);
        guardado.addProperty("abierto", true);
        guardado.add("mensajes", new com.google.gson.Gson().toJsonTree(guardados));
        almacen.contenido = guardado.toString();
        ConversacionAsistente c = nueva(USUARIO_A);
        assertEquals(40, c.getMensajes().size());

        server.enqueue(respuesta("ok"));
        enviarYEsperar(c, "la 41");
        JsonArray h = cuerpo(server.takeRequest()).getAsJsonArray("historial");

        assertEquals(30, h.size());
        // Los ÚLTIMOS 30: arranca en la pregunta 5 (los turnos 0..9 quedan afuera) y termina en la respuesta 19
        assertTrue(h.get(0).getAsJsonObject().get("texto").getAsString().startsWith("pregunta 5"));
        assertEquals("USUARIO", h.get(0).getAsJsonObject().get("rol").getAsString());
        assertTrue(h.get(29).getAsJsonObject().get("texto").getAsString().startsWith("respuesta 19"));
        for (int i = 0; i < h.size(); i++) {
            String texto = h.get(i).getAsJsonObject().get("texto").getAsString();
            assertTrue("turno " + i + " con " + texto.length(), texto.length() <= 4000);
        }
        // La pregunta 6 medía 5010: se trunca a exactamente 4000
        assertEquals(4000, h.get(2).getAsJsonObject().get("texto").getAsString().length());
        // En pantalla no se recorta nada
        assertEquals(42, c.getMensajes().size());
    }

    @Test
    public void recortar_noPartiUnEmojiYSalteaFallidosYVacios() {
        String texto = repetir('a', 3999) + "😀" + "resto";
        String truncado = HistorialAsistente.truncar(texto);
        assertEquals(3999, truncado.length());

        List<MensajeChat> m = new ArrayList<>();
        m.add(MensajeChat.delUsuario("hola"));
        m.add(MensajeChat.delUsuario("no llegó").comoFallido());
        m.add(MensajeChat.delAsistente("   ", null));
        m.add(MensajeChat.delAsistente("chau", null));
        List<Turno> h = HistorialAsistente.recortar(m);
        assertEquals(2, h.size());
        assertEquals("hola", h.get(0).getTexto());
        assertEquals("chau", h.get(1).getTexto());
    }

    // ── 4. Transferencia sugerida ─────────────────────────────────────────────

    @Test
    public void transferenciaSugerida_seGuardaConLaRespuestaYArmaElFormulario() throws Exception {
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(
                "{\"texto\":\"Te dejé lista la transferencia.\",\"accionSugerida\":{\"tipo\":\"TRANSFERENCIA\","
                        + "\"destinatario\":\"juan.perez.payx\",\"nombreResuelto\":\"Juan Pérez\",\"monto\":500.00,"
                        + "\"moneda\":\"PESOS\",\"motivo\":\"Regalo\",\"tipoTransferencia\":\"DIRECTA\"}}"));
        ConversacionAsistente c = nueva(USUARIO_A);
        enviarYEsperar(c, "transferile 500 pesos a juan.perez.payx");

        MensajeChat m = c.getMensajes().get(1);
        assertTrue(m.tieneTransferencia());
        assertEquals("Juan Pérez", m.accion.getNombreResuelto());
        assertEquals("$ 500,00", TransferenciaSugerida.montoFormateado(m.accion));

        TransferenciaSugerida t = TransferenciaSugerida.de(m.accion);
        assertNotNull(t);
        assertEquals("PESOS", t.moneda);
        assertEquals("juan.perez.payx", t.destinatario);
        assertEquals("con los decimales de la moneda, como lo tipearía el usuario", "500,00", t.monto);
        assertEquals("Regalo", t.motivo);
        assertEquals("DIRECTA", t.tipo);

        // La tarjeta sobrevive a cerrar la app (queda en la charla guardada)
        ConversacionAsistente reabierta = nueva(USUARIO_A);
        assertTrue(reabierta.getMensajes().get(1).tieneTransferencia());
        assertEquals(0, new BigDecimal("500.00").compareTo(reabierta.getMensajes().get(1).accion.getMonto()));
    }

    @Test
    public void transferenciaSugerida_formatoPorMoneda() {
        assertEquals("$ 1.500,00", TransferenciaSugerida.montoFormateado(accion("1500", "ARS")));
        assertEquals("US$ 20,50", TransferenciaSugerida.montoFormateado(accion("20.5", "USD")));
        assertEquals("0,005 BTC", TransferenciaSugerida.montoFormateado(accion("0.00500000", "BTC")));
        assertEquals("PESOS", TransferenciaSugerida.de(accion("1", "ARS")).moneda);
        assertEquals("0,005", TransferenciaSugerida.de(accion("0.00500000", "BTC")).monto);
        assertEquals("500,00", TransferenciaSugerida.de(accion("500.0", "PESOS")).monto);
        assertEquals("con más decimales no se redondea (el formulario lo marca)", "10.123", TransferenciaSugerida.de(accion("10.123", "USD")).monto);
        assertEquals("sin notación científica", "1000,00", TransferenciaSugerida.de(accion("1E+3", "USD")).monto);
        assertNull("moneda desconocida: no hay formulario", TransferenciaSugerida.de(accion("1", "DOGE")));
        assertNull(TransferenciaSugerida.de(new AccionSugerida("OTRA", "x", "X", BigDecimal.ONE, "PESOS", null, null)));
    }

    private static AccionSugerida accion(String monto, String moneda) {
        return new AccionSugerida("TRANSFERENCIA", "alias.x", "Alguien", new BigDecimal(monto), moneda, null, "DIRECTA");
    }

    // ── 5. Cerrar sesión ──────────────────────────────────────────────────────

    @Test
    public void cerrarSesion_borraMemoriaYDiscoYLaProximaSesionArrancaVacia() throws Exception {
        server.enqueue(respuesta("Tenés $ 10.000,00."));
        ConversacionAsistente c = nueva(USUARIO_A);
        c.setAbierto(true);
        enviarYEsperar(c, "¿cuál es mi saldo?");
        c.setBorrador("a medio escribir");
        assertNotNull(almacen.contenido);

        c.cerrarSesion();
        assertTrue(c.getMensajes().isEmpty());
        assertFalse(c.isAbierto());
        assertEquals("", c.getBorrador());
        assertNull("nada en disco", almacen.contenido);

        // Mismo usuario u otro, en el mismo proceso o después de reabrir la app: vacío
        c.usarUsuario(USUARIO_A);
        assertTrue(c.getMensajes().isEmpty());
        assertTrue(nueva(USUARIO_B).getMensajes().isEmpty());
    }

    @Test
    public void cerrarSesion_conUnaRespuestaEnCamino_laDescarta() throws Exception {
        server.enqueue(respuesta("secreto").setBodyDelay(300, TimeUnit.MILLISECONDS));
        ConversacionAsistente c = nueva(USUARIO_A);
        assertTrue(c.enviar("¿cuál es mi saldo?"));
        c.cerrarSesion();
        Thread.sleep(600);
        assertTrue(c.getMensajes().isEmpty());
        assertFalse(c.isEnviando());
        assertNull(almacen.contenido);
    }

    // ── 6. Reabrir la app con la sesión activa ────────────────────────────────

    @Test
    public void reabrirLaApp_conLaMismaSesion_recuperaLaCharlaYSiEstabaAbierta() throws Exception {
        server.enqueue(respuesta("R1"));
        ConversacionAsistente c = nueva(USUARIO_A);
        c.setAbierto(true);
        enviarYEsperar(c, "P1");

        ConversacionAsistente reabierta = nueva(USUARIO_A); // proceso nuevo, mismo almacén
        assertEquals(2, reabierta.getMensajes().size());
        assertEquals("P1", reabierta.getMensajes().get(0).texto);
        assertEquals("R1", reabierta.getMensajes().get(1).texto);
        assertTrue(reabierta.isAbierto());
    }

    @Test
    public void loGuardadoDeOtroUsuario_seDescartaYSeBorra() throws Exception {
        server.enqueue(respuesta("R1"));
        ConversacionAsistente c = nueva(USUARIO_A);
        enviarYEsperar(c, "P1");

        ConversacionAsistente deOtro = nueva(USUARIO_B);
        assertTrue(deOtro.getMensajes().isEmpty());
        assertNull(almacen.contenido);
    }

    @Test
    public void cambioDeUsuarioEnElMismoProceso_empiezaDeCero() throws Exception {
        server.enqueue(respuesta("R1"));
        ConversacionAsistente c = nueva(USUARIO_A);
        enviarYEsperar(c, "P1");
        c.usarUsuario(USUARIO_B);
        assertTrue(c.getMensajes().isEmpty());
    }

    // ── 7. Mensaje vacío o de más de 1000 caracteres ──────────────────────────

    @Test
    public void mensajeVacioOLargo_noSeManda() throws Exception {
        ConversacionAsistente c = nueva(USUARIO_A);
        assertFalse(c.enviar(""));
        assertFalse(c.enviar("   \n  "));
        assertFalse(c.enviar(null));
        assertFalse(c.enviar(repetir('a', 1001)));
        assertTrue(c.getMensajes().isEmpty());
        assertEquals(0, server.getRequestCount());

        server.enqueue(respuesta("ok"));
        assertTrue("1000 justos sí", c.enviar(repetir('a', 1000)));
        esperar("respuesta", () -> !c.isEnviando());
        assertEquals(1000, cuerpo(server.takeRequest()).get("mensaje").getAsString().length());
    }

    @Test
    public void conUnPedidoEnCamino_noSeMandaOtro() throws Exception {
        server.enqueue(respuesta("R1").setBodyDelay(300, TimeUnit.MILLISECONDS));
        ConversacionAsistente c = nueva(USUARIO_A);
        assertTrue(c.enviar("P1"));
        assertFalse(c.enviar("P2"));
        esperar("respuesta", () -> !c.isEnviando());
        assertEquals(1, server.getRequestCount());
    }

    // ── 8. Sin conexión / errores del backend ─────────────────────────────────

    @Test
    public void sinConexion_errorLegibleYElMensajeQuedaEnPantallaParaReintentar() throws Exception {
        ConversacionAsistente c = nueva(USUARIO_A);
        server.shutdown(); // backend apagado
        assertTrue(c.enviar("¿cuál es mi saldo?"));
        esperar("fallo", () -> !c.isEnviando());

        assertEquals(ApiErrores.MSG_SIN_CONEXION, c.getError());
        assertEquals(1, c.getMensajes().size());
        MensajeChat fallido = c.getMensajes().get(0);
        assertEquals("¿cuál es mi saldo?", fallido.texto);
        assertTrue(fallido.fallido);

        // Vuelve el backend: reintentar manda el mismo texto, sin duplicarlo en el historial
        server = new MockWebServer();
        server.start();
        api = RetrofitClient.crear(server.url("/").toString(), () -> "jwt", () -> {},
                false, false, 5);
        server.enqueue(respuesta("Tenés $ 10,00."));
        assertTrue(c.reintentar(fallido));
        esperar("respuesta", () -> !c.isEnviando());
        JsonObject body = cuerpo(server.takeRequest());
        assertEquals("¿cuál es mi saldo?", body.get("mensaje").getAsString());
        assertEquals(0, body.getAsJsonArray("historial").size());
        assertNull(c.getError());
        assertEquals(2, c.getMensajes().size());
        assertFalse(c.getMensajes().get(0).fallido);
    }

    @Test
    public void mensajeFallido_noViajaEnElHistorialDelSiguiente() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(respuesta("R2"));
        ConversacionAsistente c = nueva(USUARIO_A);
        enviarYEsperar(c, "P1");
        assertEquals(ApiErrores.MSG_SERVIDOR, c.getError());
        enviarYEsperar(c, "P2");
        server.takeRequest();
        assertEquals(0, cuerpo(server.takeRequest()).getAsJsonArray("historial").size());
        assertEquals("P1 fallido + P2 + R2", 3, c.getMensajes().size());
    }

    @Test
    public void errorDelBackend_muestraSuTextoOElGenerico() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"error\":\"El asistente no está disponible en este momento\"}"));
        // @Valid que falla: 400 con el detalle por campo
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"Datos invalidos\","
                + "\"campos\":{\"historial\":\"La conversacion es demasiado larga\"}}"));
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"Datos invalidos\",\"campos\":{}}"));
        server.enqueue(new MockResponse().setResponseCode(429));
        ConversacionAsistente c = nueva(USUARIO_A);

        enviarYEsperar(c, "hola");
        assertEquals("El asistente no está disponible en este momento", c.getError());
        enviarYEsperar(c, "hola");
        assertEquals("La conversacion es demasiado larga", c.getError());
        enviarYEsperar(c, "hola");
        assertEquals("sin detalle: el genérico", ConversacionAsistente.MSG_ERROR_GENERICO, c.getError());
        enviarYEsperar(c, "hola");
        assertEquals(ApiErrores.MSG_RATE_LIMIT, c.getError());
        assertEquals("los mensajes siguen en pantalla", 4, c.getMensajes().size());
    }

    @Test
    public void timeout_errorLegible() throws Exception {
        api = RetrofitClient.crear(server.url("/").toString(), () -> "jwt", () -> {},
                false, false, 1);
        server.enqueue(respuesta("tarde").setBodyDelay(3, TimeUnit.SECONDS));
        ConversacionAsistente c = nueva(USUARIO_A);
        enviarYEsperar(c, "hola");
        assertEquals(ApiErrores.MSG_TIMEOUT, c.getError());
    }

    // ── Nueva charla ──────────────────────────────────────────────────────────

    @Test
    public void nuevaCharla_vaciaTodoYDescartaUnaRespuestaPendiente() throws Exception {
        server.enqueue(respuesta("R1"));
        server.enqueue(respuesta("tardía").setBodyDelay(300, TimeUnit.MILLISECONDS));
        ConversacionAsistente c = nueva(USUARIO_A);
        c.setAbierto(true);
        enviarYEsperar(c, "P1");
        assertTrue(c.enviar("P2"));
        c.nuevaCharla();
        assertTrue(c.getMensajes().isEmpty());
        assertFalse(c.isEnviando());
        assertTrue("el panel sigue abierto", c.isAbierto());
        Thread.sleep(600);
        assertTrue(c.getMensajes().isEmpty());
        assertTrue("guardada vacía", nueva(USUARIO_A).getMensajes().isEmpty());
    }

    @Test
    public void observadores_seEnteranDeCadaCambio() throws Exception {
        server.enqueue(respuesta("R1"));
        ConversacionAsistente c = nueva(USUARIO_A);
        List<Boolean> enviando = new java.util.concurrent.CopyOnWriteArrayList<>();
        c.observar(conv -> enviando.add(conv.isEnviando()));
        enviarYEsperar(c, "P1");
        esperar("dos avisos", () -> enviando.size() >= 2);
        assertSame(Boolean.TRUE, enviando.get(0));
        assertSame(Boolean.FALSE, enviando.get(enviando.size() - 1));
    }

    private static String repetir(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }
}
