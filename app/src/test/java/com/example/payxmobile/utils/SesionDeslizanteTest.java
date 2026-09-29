package com.example.payxmobile.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.JwtFalso;
import com.example.payxmobile.model.LoginRequest;
import com.example.payxmobile.model.LoginResponse;
import com.example.payxmobile.model.SinLeerResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import retrofit2.Response;

/**
 * Sesión contra un backend simulado que aplica las MISMAS reglas que JwtFilter/SecurityConfig:
 * - token ausente, ilegible o vencido -> 401 {"error":"No autenticado ..."}
 * - token al que le quedan menos de 30 min -> la respuesta trae X-Renewed-Token (uno nuevo de 2 h)
 * - /api/notificaciones/leer simula algo "de admin": 403 {"error":"No tenes permiso para hacer esto"}
 * El reloj del backend es simulado, así se pueden recorrer horas de uso en milisegundos.
 */
public class SesionDeslizanteTest {

    private static final long MIN = 60_000L;
    private static final long DURACION_TOKEN = 120 * MIN;
    private static final long UMBRAL = 30 * MIN;

    private MockWebServer server;
    private volatile long ahora; // reloj del backend simulado
    private final List<String> renovados = new ArrayList<>();

    /** Hace de SessionManager + SesionUtils, con la misma regla para aceptar la renovación. */
    private final class SesionFalsa implements RetrofitClient.SesionHttp {
        volatile String token;
        final List<String> cierres = new ArrayList<>();

        @Override public String token() { return token; }

        @Override
        public synchronized void noAutenticado(String tokenEnviado) {
            if (token == null || !token.equals(tokenEnviado)) return; // como SesionUtils.noAutenticado
            cierres.add(tokenEnviado);
            token = null;
        }

        @Override
        public synchronized void tokenRenovado(String tokenEnviado, String nuevo) {
            // El reloj real no sirve para validar un token del backend simulado: se usa el suyo
            if (SessionManager.aceptaRenovacion(token, tokenEnviado, nuevo, ahora)) token = nuevo;
        }
    }

    private SesionFalsa sesion;
    private ApiService api;

    private String tokenQueVenceEn(long ms) {
        return JwtFalso.conExp((ahora + ms) / 1000);
    }

    @Before
    public void setUp() throws Exception {
        ahora = 1_800_000_000_000L;
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest r) {
                String path = r.getPath();
                if (path.startsWith("/api/auth/")) {
                    return json(401, "{\"error\":\"Email o contraseña incorrectos\"}")
                            .setHeader(RetrofitClient.HEADER_TOKEN_RENOVADO, tokenQueVenceEn(DURACION_TOKEN));
                }
                String auth = r.getHeader("Authorization");
                String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
                Long exp = JwtUtils.obtenerExpSegundos(token);
                if (exp == null || exp * 1000L <= ahora) {
                    return json(401, "{\"error\":\"No autenticado (token ausente, invalido o vencido)\"}");
                }
                MockResponse respuesta = path.equals("/api/notificaciones/leer")
                        ? json(403, "{\"error\":\"No tenes permiso para hacer esto\"}")
                        : json(200, "{\"cantidad\":0}");
                if (exp * 1000L - ahora < UMBRAL) {
                    String nuevo = tokenQueVenceEn(DURACION_TOKEN);
                    synchronized (renovados) { renovados.add(nuevo); }
                    respuesta.setHeader(RetrofitClient.HEADER_TOKEN_RENOVADO, nuevo);
                }
                return respuesta;
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

    private static MockResponse json(int codigo, String body) {
        return new MockResponse().setResponseCode(codigo).setHeader("Content-Type", "application/json").setBody(body);
    }

    private Response<SinLeerResponse> pedir() throws Exception {
        return api.contarSinLeer().execute();
    }

    private long expDe(String token) {
        return JwtUtils.obtenerExpSegundos(token) * 1000L;
    }

    // ── 401: cierra la sesión sola ────────────────────────────────────────────

    @Test
    public void tokenBasura_401_cierraLaSesion() throws Exception {
        sesion.token = "basura.que.no.es.jwt";
        Response<SinLeerResponse> r = pedir();
        assertEquals(401, r.code());
        assertEquals("una vez, con el token que se mandó", 1, sesion.cierres.size());
        assertNull("token borrado", sesion.token);
    }

    @Test
    public void tokenVencidoAMano_401_cierraLaSesion() throws Exception {
        sesion.token = tokenQueVenceEn(-MIN);
        assertEquals(401, pedir().code());
        assertEquals(1, sesion.cierres.size());
        assertNull(sesion.token);
    }

    @Test
    public void sinSesion_noSeMandaAuthorizationNiSeCierraNada() throws Exception {
        sesion.token = null;
        assertEquals(401, pedir().code());
        assertNull(server.takeRequest().getHeader("Authorization"));
        assertTrue(sesion.cierres.isEmpty());
    }

    @Test
    public void unPedidoConUnTokenViejo_noCierraLaSesionNueva() throws Exception {
        // Se mandó con A; mientras volvía el 401 ya había otra sesión (o el token se renovó)
        String viejo = tokenQueVenceEn(-MIN);
        String actual = tokenQueVenceEn(DURACION_TOKEN);
        sesion.token = viejo;
        RetrofitClient.SesionHttp cambiaEnElMedio = new RetrofitClient.SesionHttp() {
            @Override public String token() { String t = sesion.token; sesion.token = actual; return t; }
            @Override public void noAutenticado(String enviado) { sesion.noAutenticado(enviado); }
            @Override public void tokenRenovado(String enviado, String nuevo) { sesion.tokenRenovado(enviado, nuevo); }
        };
        ApiService otra = RetrofitClient.crear(server.url("/").toString(), cambiaEnElMedio, false, true, null, 5);
        assertEquals(401, otra.contarSinLeer().execute().code());
        assertTrue(sesion.cierres.isEmpty());
        assertEquals(actual, sesion.token);
    }

    @Test
    public void login401_esCredencialIncorrecta_noSesion_yNoRenueva() throws Exception {
        String vigente = tokenQueVenceEn(DURACION_TOKEN);
        sesion.token = vigente;
        Response<LoginResponse> r = api.login(new LoginRequest("a@b.com", "mal")).execute();
        assertEquals(401, r.code());
        assertEquals("Email o contraseña incorrectos", ApiErrores.mensaje(r));
        assertTrue(sesion.cierres.isEmpty());
        assertEquals("/api/auth/** no toca la sesión", vigente, sesion.token);
    }

    // ── 403: error normal, la sesión sigue ────────────────────────────────────

    @Test
    public void sinPermiso_403_muestraElErrorYNoCierraLaSesion() throws Exception {
        String vigente = tokenQueVenceEn(DURACION_TOKEN);
        sesion.token = vigente;
        Response<Void> r = api.marcarTodasLeidas().execute();
        assertEquals(403, r.code());
        assertEquals("No tenes permiso para hacer esto", ApiErrores.mensaje(r));
        assertTrue(sesion.cierres.isEmpty());
        assertEquals(vigente, sesion.token);
        assertEquals("se puede seguir usando", 200, pedir().code());
    }

    // ── Sesión deslizante ─────────────────────────────────────────────────────

    @Test
    public void conMasDe30MinDeVida_noSeRenueva() throws Exception {
        String vigente = tokenQueVenceEn(31 * MIN);
        sesion.token = vigente;
        Response<SinLeerResponse> r = pedir();
        assertNull(r.headers().get(RetrofitClient.HEADER_TOKEN_RENOVADO));
        assertEquals(vigente, sesion.token);
    }

    @Test
    public void conMenosDe30Min_seReemplazaElTokenYElSiguientePedidoUsaElNuevo() throws Exception {
        String viejo = tokenQueVenceEn(10 * MIN);
        sesion.token = viejo;
        Response<SinLeerResponse> r = pedir();
        String header = r.headers().get(RetrofitClient.HEADER_TOKEN_RENOVADO);
        assertEquals(header, sesion.token);
        assertNotEquals(viejo, sesion.token);
        assertEquals("el nuevo dura 2 h desde ahora", ahora + DURACION_TOKEN, expDe(sesion.token));

        server.takeRequest();
        pedir();
        assertEquals("Bearer " + header, server.takeRequest().getHeader("Authorization"));
        assertTrue(sesion.cierres.isEmpty());
    }

    @Test
    public void usoActivoDurante5Horas_nuncaVenceNiDesloguea() throws Exception {
        sesion.token = tokenQueVenceEn(DURACION_TOKEN);
        long inicio = ahora;
        for (int i = 0; i < 20; i++) { // un pedido cada 15 min
            ahora += 15 * MIN;
            assertEquals("pedido " + i, 200, pedir().code());
        }
        assertEquals(5 * 60 * MIN, ahora - inicio);
        assertTrue(sesion.cierres.isEmpty());
        assertTrue("se renovó varias veces", renovados.size() >= 2);
        assertTrue("el token actual vence en el futuro del backend", expDe(sesion.token) > ahora);
    }

    @Test
    public void inactivoMasDe2Horas_alVolverVenceYPideLogin() throws Exception {
        sesion.token = tokenQueVenceEn(DURACION_TOKEN);
        assertEquals(200, pedir().code());
        ahora += DURACION_TOKEN + MIN; // 2 h 1 min sin mandar nada: no hubo con qué renovar
        assertEquals(401, pedir().code());
        assertEquals(1, sesion.cierres.size());
        assertNull(sesion.token);
        assertTrue(renovados.isEmpty());
    }

    @Test
    public void inactivo1h45_elPrimerPedidoRenuevaYNoDesloguea() throws Exception {
        sesion.token = tokenQueVenceEn(DURACION_TOKEN);
        ahora += 105 * MIN; // le quedan 15 min
        assertEquals(200, pedir().code());
        assertEquals(ahora + DURACION_TOKEN, expDe(sesion.token));
    }

    // ── Cuándo se acepta un token renovado ────────────────────────────────────

    @Test
    public void aceptaRenovacion_soloSiLaSesionEsLaMismaYElNuevoEsVigente() {
        long t = 1_800_000_000_000L;
        String a = JwtFalso.conExp(t / 1000 + 600);
        String nuevo = JwtFalso.conExp(t / 1000 + 7200);
        assertTrue(SessionManager.aceptaRenovacion(a, a, nuevo, t));
        assertFalse("se cerró sesión mientras volvía el pedido: no la revive",
                SessionManager.aceptaRenovacion(null, a, nuevo, t));
        assertFalse("otra cuenta / ya renovado", SessionManager.aceptaRenovacion(nuevo, a, JwtFalso.conExp(t / 1000 + 7300), t));
        assertFalse("no es un JWT", SessionManager.aceptaRenovacion(a, a, "basura", t));
        assertFalse("ya vencido", SessionManager.aceptaRenovacion(a, a, JwtFalso.conExp(t / 1000 - 1), t));
        assertFalse(SessionManager.aceptaRenovacion(a, a, null, t));
        assertFalse("mismo token", SessionManager.aceptaRenovacion(a, a, a, t));
    }
}
