package com.example.payxmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.example.payxmobile.model.ActualizarPerfilRequest;
import com.example.payxmobile.model.CambiarPasswordRequest;
import com.example.payxmobile.model.GoogleLoginRequest;
import com.example.payxmobile.model.LoginRequest;
import com.example.payxmobile.model.LoginResponse;
import com.example.payxmobile.model.MensajeResponse;
import com.example.payxmobile.model.NotificacionResponse;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.model.ReenviarCodigoRequest;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.utils.ImagenPerfil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import retrofit2.Response;

/**
 * Contrato HTTP de la app contra un backend simulado: método, path, headers y body de cada
 * request, y cómo se interpreta cada respuesta (200 / 401 JSON / 403 vacío / 429 / 400 / 5xx / timeout).
 */
public class ApiContratoTest {

    private static final long AHORA_MS = 1_790_000_000_000L;

    private MockWebServer server;
    private String token;
    private final AtomicInteger sesionesVencidas = new AtomicInteger();

    private static final String LOGIN_OK = "{\"token\":\"%s\",\"id\":\"8a6e0804-2bd0-4672-b79d-d97027f9071a\","
            + "\"nombreCompleto\":\"Ana Pérez\",\"email\":\"ana@mail.com\",\"nombreUsuario\":\"anap\","
            + "\"rol\":\"USUARIO\",\"fotoPerfilUrl\":null}";

    private static final String PERFIL_GOOGLE = "{\"id\":\"8a6e0804-2bd0-4672-b79d-d97027f9071a\","
            + "\"email\":\"nuevo@gmail.com\",\"nombreCompleto\":\"Nuevo Google\",\"dni\":null,\"telefono\":null,"
            + "\"nombreUsuario\":\"nuevo123\",\"alias\":\"nuevo.payx.123\",\"cvu\":\"0000003100012345678901\","
            + "\"saldoPesos\":150000.10,\"saldoUsd\":0.01,\"saldoBtc\":0.00012345,\"saldoEth\":0,"
            + "\"saldoSolana\":0,\"saldoUsdt\":0,\"saldoBnb\":0,\"saldoXrp\":0,\"fotoPerfilUrl\":null,"
            + "\"tarjetaUltimosCuatro\":\"4321\",\"tarjetaVencimiento\":\"2031-05-31\"}";

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        token = null;
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private ApiService api() {
        return RetrofitClient.crear(server.url("/").toString(), () -> token, () -> AHORA_MS,
                sesionesVencidas::incrementAndGet, false);
    }

    private static JsonObject json(RecordedRequest r) {
        return JsonParser.parseString(r.getBody().readUtf8()).getAsJsonObject();
    }

    // ── A) Login ────────────────────────────────────────────────────────────

    @Test
    public void a1_loginMandaEmailYPasswordYMapeaTodaLaRespuesta() throws Exception {
        String jwt = JwtFalso.conExp(AHORA_MS / 1000 + 7200);
        server.enqueue(new MockResponse().setBody(String.format(LOGIN_OK, jwt)));

        Response<LoginResponse> r = api().login(new LoginRequest("ana@mail.com", " clave con espacios ")).execute();

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/api/auth/login", req.getPath());
        assertTrue(req.getHeader("Content-Type").startsWith("application/json"));
        assertNull("login no lleva Authorization", req.getHeader("Authorization"));
        JsonObject body = json(req);
        assertEquals("ana@mail.com", body.get("email").getAsString());
        assertEquals(" clave con espacios ", body.get("password").getAsString());

        LoginResponse l = r.body();
        assertNotNull(l);
        assertEquals(jwt, l.getToken());
        assertEquals("8a6e0804-2bd0-4672-b79d-d97027f9071a", l.getId());
        assertEquals("Ana Pérez", l.getNombreCompleto());
        assertEquals("ana@mail.com", l.getEmail());
        assertEquals("anap", l.getNombreUsuario());
        assertEquals("USUARIO", l.getRol());
        assertNull(l.getFotoPerfilUrl());
    }

    @Test
    public void a3_a5_a6_erroresDeLogin401SeMuestranConElTextoDelBackend() throws Exception {
        String[] errores = {"Credenciales invalidas", "Debes verificar tu email antes de iniciar sesion",
                "Tu cuenta ha sido desactivada. Contacta al soporte"};
        for (String e : errores) {
            server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"error\":\"" + e + "\"}"));
            Response<LoginResponse> r = api().login(new LoginRequest("ana@mail.com", "x")).execute();
            assertFalse(r.isSuccessful());
            assertEquals(e, ApiErrores.mensaje(r));
        }
        assertEquals("un 401 del login no es sesión vencida", 0, sesionesVencidas.get());
    }

    @Test
    public void a5_verificarAhoraLlamaAResendCode() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"mensaje\":\"Codigo reenviado. Revisa tu email\"}"));
        Response<MensajeResponse> r = api().reenviarCodigo(new ReenviarCodigoRequest("ana@mail.com")).execute();
        RecordedRequest req = server.takeRequest();
        assertEquals("/api/auth/resend-code", req.getPath());
        assertEquals("ana@mail.com", json(req).get("email").getAsString());
        assertTrue(r.isSuccessful());
    }

    @Test
    public void a8_rateLimit429() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429)
                .setBody("{\"error\":\"Demasiadas solicitudes. Intenta de nuevo en unos minutos\"}"));
        Response<LoginResponse> r = api().login(new LoginRequest("ana@mail.com", "x")).execute();
        assertEquals("Demasiadas solicitudes. Intenta de nuevo en unos minutos", ApiErrores.mensaje(r));
    }

    @Test
    public void a9_timeoutEsFalloAmigable() {
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
        ApiService lento = RetrofitClient.crear(server.url("/").toString(), () -> null, () -> AHORA_MS, () -> {}, false);
        try {
            // crear() usa 30 s de read timeout; acá se fuerza uno corto con la llamada cancelada por tiempo
            retrofit2.Call<LoginResponse> call = lento.login(new LoginRequest("a@b.co", "x"));
            call.timeout().timeout(300, TimeUnit.MILLISECONDS);
            call.execute();
            fail("debería haber fallado");
        } catch (IOException e) {
            String msg = ApiErrores.mensajeFallo(e);
            assertEquals(ApiErrores.MSG_TIMEOUT, msg);
        }
    }

    @Test
    public void a9_servidorCaidoEsSinConexion() throws Exception {
        String url = server.url("/").toString();
        server.shutdown();
        try {
            RetrofitClient.crear(url, () -> null, () -> AHORA_MS, () -> {}, false)
                    .login(new LoginRequest("a@b.co", "x")).execute();
            fail("debería haber fallado");
        } catch (IOException e) {
            assertEquals(ApiErrores.MSG_SIN_CONEXION, ApiErrores.mensajeFallo(e));
        }
    }

    @Test
    public void a11_jsonInvalidoEn200NoRompe() {
        server.enqueue(new MockResponse().setBody("<html>no es json"));
        try {
            api().login(new LoginRequest("a@b.co", "x")).execute();
            fail("debería haber fallado el parseo");
        } catch (Exception e) {
            assertEquals(ApiErrores.MSG_RESPUESTA_INESPERADA, ApiErrores.mensajeFallo(e));
        }
    }

    @Test
    public void a11_bodyVacioEn200EsRespuestaInesperadaNoSinConexion() {
        server.enqueue(new MockResponse().setBody(""));
        try {
            api().login(new LoginRequest("a@b.co", "x")).execute();
            fail("debería haber fallado el parseo");
        } catch (Exception e) {
            assertEquals(ApiErrores.MSG_RESPUESTA_INESPERADA, ApiErrores.mensajeFallo(e));
        }
    }

    @Test
    public void a11_bodyInesperadoEn200SinTokenSeDetecta() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"otra\":\"cosa\"}"));
        LoginResponse l = api().login(new LoginRequest("a@b.co", "x")).execute().body();
        assertNotNull(l);
        assertNull("LoginActivity no guarda sesión si falta el token", l.getToken());
    }

    @Test
    public void a12_requestsPosterioresLlevanBearer() throws Exception {
        token = JwtFalso.conExp(AHORA_MS / 1000 + 7200);
        server.enqueue(new MockResponse().setBody(PERFIL_GOOGLE));
        api().obtenerPerfil().execute();
        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertEquals("/api/perfil", req.getPath());
        assertEquals("Bearer " + token, req.getHeader("Authorization"));
    }

    @Test
    public void a12_rutasDeAuthNoLlevanTokenAunqueHayaSesion() throws Exception {
        token = JwtFalso.conExp(AHORA_MS / 1000 + 7200);
        server.enqueue(new MockResponse().setBody("{}"));
        api().loginConGoogle(new GoogleLoginRequest("x")).execute();
        assertNull(server.takeRequest().getHeader("Authorization"));
    }

    // ── C) Google ───────────────────────────────────────────────────────────

    @Test
    public void c1_googleMandaElIdTokenEnElCampoIdToken() throws Exception {
        String jwt = JwtFalso.conExp(AHORA_MS / 1000 + 7200);
        server.enqueue(new MockResponse().setBody(String.format(LOGIN_OK, jwt)));
        LoginResponse l = api().loginConGoogle(new GoogleLoginRequest("eyJ.id.token")).execute().body();
        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/api/auth/google", req.getPath());
        JsonObject body = json(req);
        assertEquals("eyJ.id.token", body.get("idToken").getAsString());
        assertEquals(1, body.size());
        assertEquals(jwt, l.getToken()); // misma forma de sesión que el login normal
    }

    @Test
    public void c4_erroresDeGoogle() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{\"error\":\"El token de Google no corresponde a esta aplicacion\"}"));
        Response<LoginResponse> r = api().loginConGoogle(new GoogleLoginRequest("x")).execute();
        assertEquals("El token de Google no corresponde a esta aplicacion", ApiErrores.mensaje(r));
    }

    // ── D) Sesión ───────────────────────────────────────────────────────────

    @Test
    public void d3_forbiddenVacioConTokenVigenteNoEsSesionVencida() throws Exception {
        token = JwtFalso.conExp(AHORA_MS / 1000 + 7200);
        server.enqueue(new MockResponse().setResponseCode(403));
        Response<PerfilResponse> r = api().actualizarPerfil(
                new ActualizarPerfilRequest("ana", "11 5555 1234", "alias.valido", null)).execute();
        assertEquals(403, r.code());
        assertEquals(ApiErrores.MSG_DATOS_INVALIDOS, ApiErrores.mensaje(r));
        assertEquals(0, sesionesVencidas.get());
    }

    @Test
    public void d3_forbiddenVacioConTokenVencidoDisparaSesionVencidaUnaVez() throws Exception {
        token = JwtFalso.conExp(AHORA_MS / 1000 - 1);
        server.enqueue(new MockResponse().setResponseCode(403));
        api().obtenerPerfil().execute();
        assertEquals(1, sesionesVencidas.get());
    }

    @Test
    public void d3_otrosErroresConTokenVencidoNoDisparanNada() throws Exception {
        token = JwtFalso.conExp(AHORA_MS / 1000 - 1);
        server.enqueue(new MockResponse().setResponseCode(500));
        api().obtenerPerfil().execute();
        assertEquals(0, sesionesVencidas.get());
    }

    // ── E) Notificaciones ───────────────────────────────────────────────────

    @Test
    public void e_notificacionesYMarcarLeidas() throws Exception {
        token = JwtFalso.conExp(AHORA_MS / 1000 + 7200);
        server.enqueue(new MockResponse().setBody("[{\"id\":7,\"plantillaCodigo\":\"INICIO_SES\","
                + "\"mensaje\":\"Iniciaste sesion el 24/09/2026 a las 10:15\",\"leida\":false,"
                + "\"fecha\":\"2026-09-24T10:15:00\"}]"));
        server.enqueue(new MockResponse().setResponseCode(200));
        server.enqueue(new MockResponse().setBody("{\"cantidad\":0}"));

        List<NotificacionResponse> lista = api().obtenerNotificaciones().execute().body();
        assertEquals(1, lista.size());
        assertEquals("INICIO_SES", lista.get(0).getPlantillaCodigo());
        assertTrue(lista.get(0).getMensaje().matches(".* \\d{2}/\\d{2}/\\d{4} a las \\d{2}:\\d{2}$"));

        api().marcarTodasLeidas().execute();
        assertEquals(0, api().contarSinLeer().execute().body().getCantidad());

        assertEquals("/api/notificaciones", server.takeRequest().getPath());
        RecordedRequest patch = server.takeRequest();
        assertEquals("PATCH", patch.getMethod());
        assertEquals("/api/notificaciones/leer", patch.getPath());
        assertEquals("/api/notificaciones/sin-leer", server.takeRequest().getPath());
    }

    // ── F) Perfil ───────────────────────────────────────────────────────────

    @Test
    public void f1_f2_perfilDeCuentaGoogleMapeaNullsYDecimales() throws Exception {
        token = JwtFalso.conExp(AHORA_MS / 1000 + 7200);
        server.enqueue(new MockResponse().setBody(PERFIL_GOOGLE));
        PerfilResponse p = api().obtenerPerfil().execute().body();
        assertNotNull(p);
        assertNull(p.getDni());
        assertNull(p.getTelefono());
        assertFalse(p.tieneDni());
        assertFalse(p.tieneTelefono());
        assertEquals(new BigDecimal("150000.10"), p.getSaldoPesos()); // sin pérdida de precisión
        assertEquals(new BigDecimal("0.00012345"), p.getSaldoBtc());
        assertEquals(BigDecimal.ZERO.compareTo(p.getSaldoXrp()), 0);
        assertEquals("0000003100012345678901", p.getCvu());
        assertEquals("nuevo.payx.123", p.getAlias());
        assertNull(p.getFotoPerfilUrl());
        assertEquals("4321", p.getTarjetaUltimosCuatro());
        assertEquals("2031-05-31", p.getTarjetaVencimiento());
        assertEquals("05/31", p.getTarjetaVencimientoMmAa());
    }

    @Test
    public void f4_f7_putPerfilSinDniCargadoOmiteElCampo() throws Exception {
        token = JwtFalso.conExp(AHORA_MS / 1000 + 7200);
        server.enqueue(new MockResponse().setBody(PERFIL_GOOGLE));
        api().actualizarPerfil(new ActualizarPerfilRequest("anap", "11 5555-1234", "ana.payx", null)).execute();
        RecordedRequest req = server.takeRequest();
        assertEquals("PUT", req.getMethod());
        assertEquals("/api/perfil", req.getPath());
        JsonObject body = json(req);
        assertEquals("anap", body.get("nombreUsuario").getAsString());
        assertEquals("ana.payx", body.get("alias").getAsString());
        assertEquals("11 5555-1234", body.get("telefono").getAsString());
        assertFalse("nunca se manda dni vacío ni null", body.has("dni"));
    }

    @Test
    public void f7_dniVacioTampocoSeManda() throws Exception {
        token = JwtFalso.conExp(AHORA_MS / 1000 + 7200);
        server.enqueue(new MockResponse().setBody(PERFIL_GOOGLE));
        api().actualizarPerfil(new ActualizarPerfilRequest("anap", "11 5555-1234", "ana.payx", "  ")).execute();
        assertFalse(json(server.takeRequest()).has("dni"));
    }

    @Test
    public void f7_dniNuevoSeMandaUnaVez() throws Exception {
        token = JwtFalso.conExp(AHORA_MS / 1000 + 7200);
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"El DNI ya esta registrado\"}"));
        Response<PerfilResponse> r = api().actualizarPerfil(
                new ActualizarPerfilRequest("anap", "11 5555-1234", "ana.payx", "30123456")).execute();
        assertEquals("30123456", json(server.takeRequest()).get("dni").getAsString());
        assertEquals("El DNI ya esta registrado", ApiErrores.mensaje(r));
    }

    @Test
    public void f6_duplicados400() throws Exception {
        token = JwtFalso.conExp(AHORA_MS / 1000 + 7200);
        for (String e : new String[]{"El alias ya esta en uso", "El nombre de usuario ya esta en uso"}) {
            server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"" + e + "\"}"));
            Response<PerfilResponse> r = api().actualizarPerfil(
                    new ActualizarPerfilRequest("anap", "11 5555-1234", "ana.payx", null)).execute();
            assertEquals(e, ApiErrores.mensaje(r));
        }
    }

    // ── G) Foto de perfil ───────────────────────────────────────────────────

    @Test
    public void g2_fotoEsMultipartConParteArchivoImageJpeg() throws Exception {
        token = JwtFalso.conExp(AHORA_MS / 1000 + 7200);
        String url = "https://res.cloudinary.com/demo/image/upload/v1727000000/payx/perfiles/8a6e.jpg";
        server.enqueue(new MockResponse().setBody(PERFIL_GOOGLE.replace("\"fotoPerfilUrl\":null",
                "\"fotoPerfilUrl\":\"" + url + "\"")));
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, (byte) 0xFF, (byte) 0xD9};

        PerfilResponse p = api().subirFotoPerfil(ImagenPerfil.crearParte(jpeg)).execute().body();

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/api/perfil/foto", req.getPath());
        assertEquals("Bearer " + token, req.getHeader("Authorization"));
        String contentType = req.getHeader("Content-Type");
        assertTrue(contentType, contentType.startsWith("multipart/form-data; boundary="));
        String body = req.getBody().readString(java.nio.charset.StandardCharsets.ISO_8859_1);
        assertTrue(body.contains("Content-Disposition: form-data; name=\"archivo\"; filename=\"perfil.jpg\""));
        assertTrue(body.contains("Content-Type: image/jpeg"));
        assertTrue(body.contains(new String(jpeg, java.nio.charset.StandardCharsets.ISO_8859_1)));
        assertEquals(url, p.getFotoPerfilUrl());
    }

    @Test
    public void g5_fotoRateLimitYErrorDeCloudinary() throws Exception {
        token = JwtFalso.conExp(AHORA_MS / 1000 + 7200);
        server.enqueue(new MockResponse().setResponseCode(429)
                .setBody("{\"error\":\"Demasiadas solicitudes. Intenta de nuevo en unos minutos\"}"));
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"error\":\"No se pudo subir la imagen. Intenta de nuevo\"}"));
        byte[] jpeg = {1, 2, 3};
        assertEquals("Demasiadas solicitudes. Intenta de nuevo en unos minutos",
                ApiErrores.mensaje(api().subirFotoPerfil(ImagenPerfil.crearParte(jpeg)).execute()));
        assertEquals("No se pudo subir la imagen. Intenta de nuevo",
                ApiErrores.mensaje(api().subirFotoPerfil(ImagenPerfil.crearParte(jpeg)).execute()));
    }

    @Test
    public void g5_cortePorDesconexionAMitadDeLaSubida() {
        token = JwtFalso.conExp(AHORA_MS / 1000 + 7200);
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_DURING_REQUEST_BODY));
        try {
            api().subirFotoPerfil(ImagenPerfil.crearParte(new byte[256 * 1024])).execute();
            fail("debería haber fallado");
        } catch (IOException e) {
            assertEquals(ApiErrores.MSG_SIN_CONEXION, ApiErrores.mensajeFallo(e));
        }
    }

    // ── H) Cambio de contraseña ─────────────────────────────────────────────

    @Test
    public void h_cambioDePasswordContratoYErrores() throws Exception {
        token = JwtFalso.conExp(AHORA_MS / 1000 + 7200);
        server.enqueue(new MockResponse().setBody("{\"mensaje\":\"Contrasena actualizada correctamente\"}"));
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"La contrasena actual es incorrecta\"}"));
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"error\":\"La nueva contrasena debe ser diferente a la actual\"}"));

        assertTrue(api().cambiarPassword(new CambiarPasswordRequest("vieja123", "nueva1234")).execute().isSuccessful());
        RecordedRequest req = server.takeRequest();
        assertEquals("PUT", req.getMethod());
        assertEquals("/api/perfil/password", req.getPath());
        JsonObject body = json(req);
        assertEquals("vieja123", body.get("passwordActual").getAsString());
        assertEquals("nueva1234", body.get("nuevaPassword").getAsString());

        assertEquals("La contrasena actual es incorrecta",
                ApiErrores.mensaje(api().cambiarPassword(new CambiarPasswordRequest("x", "nueva1234")).execute()));
        assertEquals("La nueva contrasena debe ser diferente a la actual",
                ApiErrores.mensaje(api().cambiarPassword(new CambiarPasswordRequest("a", "a")).execute()));
        assertEquals("un 400 no cierra la sesión", 0, sesionesVencidas.get());
    }

    @Test
    public void a12_ningunEndpointApuntaARegistroLogin() {
        for (java.lang.reflect.Method m : ApiService.class.getDeclaredMethods()) {
            for (java.lang.annotation.Annotation a : m.getAnnotations()) {
                assertFalse(m.getName(), a.toString().contains("registro-login"));
            }
        }
    }
}
