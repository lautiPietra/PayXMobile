package com.example.payxmobile.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.JwtFalso;
import com.example.payxmobile.model.CambiarPasswordRequest;
import com.example.payxmobile.model.GoogleLoginRequest;
import com.example.payxmobile.model.LoginRequest;
import com.example.payxmobile.model.LoginResponse;
import com.example.payxmobile.model.MensajeResponse;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.ByteString;
import retrofit2.Response;

/**
 * Auditoría del backend, punto 1 y test (f): el JWT lleva el claim "pv" (huella de la contraseña). Un token
 * de antes del deploy, o de antes de un cambio/reset de contraseña, recibe 401 en cualquier endpoint
 * autenticado. PUT /api/perfil/password responde 200 con el token nuevo en X-Renewed-Token.
 * El backend simulado aplica las mismas reglas que JwtFilter; la sesión falsa hace lo mismo que
 * SessionManager + SesionUtils (borrar el token e ir al login solo si sigue siendo el que se mandó).
 */
public class SesionAuditoriaTest {

    private static final long MIN = 60_000L;
    private static final String MSG_GOOGLE = "Esta cuenta se creo con Google: ingresa con el boton de Google, "
            + "o crea una contraseña desde \"Olvide mi contraseña\"";

    /** Como SessionManager + SesionUtils: "login" = se borró el token y se abrió el login. */
    private static final class SesionFalsa implements RetrofitClient.SesionHttp {
        volatile String token;
        final List<String> eventos = Collections.synchronizedList(new ArrayList<>());

        @Override public String token() { return token; }

        @Override
        public synchronized void noAutenticado(String enviado) {
            if (token == null || !token.equals(enviado)) return; // SesionUtils.noAutenticado
            token = null;                                        // SessionManager.clearSession
            eventos.add("login");                                // LoginActivity con CLEAR_TASK
        }

        @Override
        public synchronized void tokenRenovado(String enviado, String nuevo) {
            if (SessionManager.aceptaRenovacion(token, enviado, nuevo, System.currentTimeMillis())) token = nuevo;
        }
    }

    private MockWebServer server;
    private SesionFalsa sesion;
    private ApiService api;
    private volatile String huellaActual = "a1b2c3d4e5f60718"; // la del hash de la contraseña de HOY
    private volatile String renovarGetCon;                      // X-Renewed-Token a mandar en el próximo GET

    private static String token(String pv, long venceEnMs) {
        long exp = (System.currentTimeMillis() + venceEnMs) / 1000;
        return JwtFalso.conPayload("{\"sub\":\"8a6e0804-2bd0-4672-b79d-d97027f9071a\",\"rol\":\"USUARIO\""
                + (pv != null ? ",\"pv\":\"" + pv + "\"" : "") + ",\"iat\":" + (exp - 7200) + ",\"exp\":" + exp + "}");
    }

    private static String pvDe(String token) {
        try {
            JsonObject o = JsonParser.parseString(ByteString.decodeBase64(token.split("\\.")[1])
                    .string(StandardCharsets.UTF_8)).getAsJsonObject();
            return o.has("pv") ? o.get("pv").getAsString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static MockResponse json(int codigo, String body) {
        return new MockResponse().setResponseCode(codigo).setHeader("Content-Type", "application/json").setBody(body);
    }

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest r) {
                String path = r.getPath();
                if (path.startsWith("/api/auth/")) {
                    return json(401, "{\"error\":\"" + MSG_GOOGLE.replace("\"", "\\\"") + "\"}");
                }
                String auth = r.getHeader("Authorization");
                String t = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
                Long exp = JwtUtils.obtenerExpSegundos(t);
                // JwtFilter: firma/vencimiento Y que el token sea de la contraseña actual
                if (exp == null || exp * 1000L <= System.currentTimeMillis() || !huellaActual.equals(pvDe(t))) {
                    return json(401, "{\"error\":\"No autenticado (token ausente, invalido o vencido)\"}");
                }
                if (path.equals("/api/perfil/password") && "PUT".equals(r.getMethod())) {
                    huellaActual = "ffff000011112222"; // cambió el hash: todos los tokens anteriores dejan de valer
                    return json(200, "{\"mensaje\":\"Contrasena actualizada correctamente\"}")
                            .setHeader(RetrofitClient.HEADER_TOKEN_RENOVADO, token(huellaActual, 120 * MIN));
                }
                MockResponse ok = json(200, "{\"nombreUsuario\":\"ana\"}");
                if (renovarGetCon != null) ok.setHeader(RetrofitClient.HEADER_TOKEN_RENOVADO, renovarGetCon);
                return ok;
            }
        });
        server.start();
        sesion = new SesionFalsa();
        api = RetrofitClient.crear(server.url("/").toString(), sesion, false, true, null, 5);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    // ── 401 en un endpoint autenticado ────────────────────────────────────────

    @Test
    public void f_abrirLaAppConUnTokenDeAntesDelDeploy_vaAlLoginSinLoopNiTextoTecnico() throws Exception {
        // Vigente según su "exp" (Splash lo deja pasar), pero sin "pv": el backend nuevo lo rechaza
        String viejo = token(null, 90 * MIN);
        sesion.token = viejo;
        Response<PerfilResponse> r = api.obtenerPerfil().execute();
        assertEquals(401, r.code());
        assertNull("token borrado", sesion.token);
        assertEquals("una sola ida al login", Collections.singletonList("login"), sesion.eventos);
        assertEquals("sin reintentos", 1, server.getRequestCount());
        assertEquals("ni \"token ausente...\" en pantalla", ApiErrores.MSG_SESION_INVALIDA, ApiErrores.mensaje(r));
    }

    @Test
    public void f_variosPedidosCon401_unSoloCierre() throws Exception {
        sesion.token = token(null, 90 * MIN);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> codigos = new ArrayList<>();
            for (int i = 0; i < 4; i++) codigos.add(pool.submit(() -> api.obtenerPerfil().execute().code()));
            for (Future<Integer> c : codigos) assertEquals(401, (int) c.get(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(Collections.singletonList("login"), sesion.eventos);
        assertNull(sesion.token);
    }

    @Test
    public void f_otroDispositivoCambioLaContraseña_elProximoPedidoVaAlLogin() throws Exception {
        sesion.token = token(huellaActual, 90 * MIN);
        assertEquals(200, api.obtenerPerfil().execute().code());
        huellaActual = "0000999988887777"; // la cambió desde otro teléfono
        assertEquals(401, api.obtenerPerfil().execute().code());
        assertNull(sesion.token);
        assertEquals(Collections.singletonList("login"), sesion.eventos);
    }

    // ── 401 de /api/auth/**: login fallido, NO sesión vencida ────────────────

    @Test
    public void f_401DelLogin_noTocaElTokenYElMensajeLlegaTalCual() throws Exception {
        String vigente = token(huellaActual, 90 * MIN);
        sesion.token = vigente; // aunque hubiera una sesión guardada
        Response<LoginResponse> r = api.login(new LoginRequest("ana@gmail.com", "clave123")).execute();
        assertEquals(401, r.code());
        assertEquals("el texto del cuerpo, tal cual", MSG_GOOGLE, ApiErrores.mensaje(r));
        assertEquals("token intacto", vigente, sesion.token);
        assertTrue("sin ir al login", sesion.eventos.isEmpty());
        assertNull("a /api/auth/** no se manda el token", server.takeRequest().getHeader("Authorization"));

        Response<LoginResponse> g = api.loginConGoogle(new GoogleLoginRequest("id-token")).execute();
        assertEquals(401, g.code());
        assertEquals(vigente, sesion.token);
        assertTrue(sesion.eventos.isEmpty());
    }

    // ── X-Renewed-Token ───────────────────────────────────────────────────────

    @Test
    public void f_getConXRenewedToken_reemplazaElGuardado() throws Exception {
        String viejo = token(huellaActual, 10 * MIN);
        String nuevo = token(huellaActual, 120 * MIN);
        sesion.token = viejo;
        renovarGetCon = nuevo;
        assertEquals(200, api.obtenerPerfil().execute().code());
        assertEquals(nuevo, sesion.token);
        renovarGetCon = null;
        server.takeRequest();
        api.obtenerPerfil().execute();
        assertEquals("el siguiente pedido ya va con el nuevo", "Bearer " + nuevo, server.takeRequest().getHeader("Authorization"));
    }

    @Test
    public void f_cambiarLaContraseña_guardaElTokenNuevoYSigueLogueado() throws Exception {
        String anterior = token(huellaActual, 90 * MIN); // 1 h 30 de vida: no es la renovación por tiempo
        sesion.token = anterior;
        Response<MensajeResponse> r = api.cambiarPassword(new CambiarPasswordRequest("vieja1234", "nueva1234")).execute();
        assertEquals(200, r.code());
        assertEquals("PUT", server.takeRequest().getMethod());
        assertNotEquals("se guardó el de X-Renewed-Token", anterior, sesion.token);
        assertEquals(r.headers().get(RetrofitClient.HEADER_TOKEN_RENOVADO), sesion.token);

        // Sigue operando: el anterior ya no valdría (cambió la huella), el nuevo sí
        assertEquals(200, api.obtenerPerfil().execute().code());
        assertEquals("Bearer " + sesion.token, server.takeRequest().getHeader("Authorization"));
        assertTrue("nunca pasó por el login", sesion.eventos.isEmpty());
    }

    @Test
    public void f_sinElHeader_elTokenNoCambia() throws Exception {
        String vigente = token(huellaActual, 90 * MIN);
        sesion.token = vigente;
        Response<PerfilResponse> r = api.obtenerPerfil().execute();
        assertEquals(200, r.code());
        assertNull(r.headers().get(RetrofitClient.HEADER_TOKEN_RENOVADO));
        assertEquals(vigente, sesion.token);
    }

    // ── El token solo viaja al backend de PayX ────────────────────────────────

    @Test
    public void f_authorizationSoloALaUrlBaseDelBackend() throws Exception {
        MockWebServer otro = new MockWebServer(); // otro host (mismo "localhost", otro puerto = otro origen)
        otro.enqueue(new MockResponse().setBody("{}"));
        otro.enqueue(new MockResponse().setBody("{}"));
        otro.start();
        try {
            sesion.token = token(huellaActual, 90 * MIN);
            OkHttpClient cliente = RetrofitClient.crearCliente(HttpUrl.get(server.url("/").toString()), sesion,
                    false, true, null, 5);

            // Al backend, sí
            cliente.newCall(new Request.Builder().url(server.url("/api/perfil")).build()).execute().close();
            assertEquals("Bearer " + sesion.token, server.takeRequest().getHeader("Authorization"));

            // A otro host, nunca (aunque la ruta sea igual)
            cliente.newCall(new Request.Builder().url(otro.url("/api/perfil")).build()).execute().close();
            assertNull(otro.takeRequest().getHeader("Authorization"));

            // Ni si el backend redirige a otro host
            server.setDispatcher(new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest r) {
                    return new MockResponse().setResponseCode(302).setHeader("Location", otro.url("/robado"));
                }
            });
            cliente.newCall(new Request.Builder().url(server.url("/api/perfil")).build()).execute().close();
            assertEquals("Bearer " + sesion.token, server.takeRequest().getHeader("Authorization"));
            assertNull("el redirect no se lleva el token", otro.takeRequest().getHeader("Authorization"));
        } finally {
            otro.shutdown();
        }
    }

    @Test
    public void f_conUnaRutaBase_elTokenNoSaleDeEsaRuta() throws Exception {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest r) {
                return json(200, "{}");
            }
        });
        sesion.token = token(huellaActual, 90 * MIN);
        OkHttpClient cliente = RetrofitClient.crearCliente(HttpUrl.get(server.url("/payx/").toString()), sesion,
                false, true, null, 5);
        cliente.newCall(new Request.Builder().url(server.url("/payx/api/perfil")).build()).execute().close();
        assertEquals("Bearer " + sesion.token, server.takeRequest().getHeader("Authorization"));
        cliente.newCall(new Request.Builder().url(server.url("/otra-app/api/perfil")).build()).execute().close();
        assertNull(server.takeRequest().getHeader("Authorization"));
        cliente.newCall(new Request.Builder().url(server.url("/payx/api/auth/login")).build()).execute().close();
        assertNull("/api/auth/** es público también debajo de la ruta base", server.takeRequest().getHeader("Authorization"));
    }
}
