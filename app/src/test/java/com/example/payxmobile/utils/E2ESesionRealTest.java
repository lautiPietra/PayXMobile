package com.example.payxmobile.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.example.payxmobile.model.AsistenteMensajeRequest;
import com.example.payxmobile.model.LoginRequest;
import com.example.payxmobile.model.LoginResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.google.gson.JsonParser;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okio.ByteString;
import retrofit2.Response;

/**
 * Sesión contra el backend LOCAL real (401 / 403 / 400 con campos / X-Renewed-Token). Solo corre con
 * PAYX_E2E=1 y PAYX_E2E_JWT_SECRET (el jwt.secret del backend); opcional PAYX_E2E_URL. Firma los tokens
 * acá igual que JwtUtil del backend (HS512 con esa clave), así se puede probar un token al que le quedan
 * 10 minutos sin esperar 1 h 50.
 * Desde la auditoría el token lleva el claim "pv" (huella de la contraseña): sin él, o con la de otra
 * contraseña, el backend responde 401. El usuario ("sub", ACTIVO y con rol USUARIO) y su "pv" salen de
 * PAYX_E2E_USER_ID + PAYX_E2E_PV o, si no están, de un login real con PAYX_E2E_EMAIL_A + PAYX_E2E_PASSWORD
 * (ese login le crea la notificación de inicio de sesión; nada más).
 * No crea ni modifica otros datos: el POST del asistente falla en el @Valid.
 */
public class E2ESesionRealTest {

    private static final String URL = env("PAYX_E2E_URL", "http://localhost:8080/");
    private static final long MIN = 60_000L;
    private static String secreto, usuarioId, huella;

    /** Como SessionManager + SesionUtils. */
    private static final class Sesion implements RetrofitClient.SesionHttp {
        volatile String token;
        final List<String> cierres = Collections.synchronizedList(new ArrayList<>());
        final List<String> renovaciones = Collections.synchronizedList(new ArrayList<>());

        @Override public String token() { return token; }

        @Override
        public synchronized void noAutenticado(String enviado) {
            if (token == null || !token.equals(enviado)) return;
            cierres.add(enviado);
            token = null;
        }

        @Override
        public synchronized void tokenRenovado(String enviado, String nuevo) {
            if (SessionManager.aceptaRenovacion(token, enviado, nuevo, System.currentTimeMillis())) {
                renovaciones.add(nuevo);
                token = nuevo;
            }
        }
    }

    /** Un GET de admin: con rol USUARIO el backend responde 403. La app no lo tiene en ApiService. */
    interface Admin {
        @retrofit2.http.GET("api/admin/usuarios")
        retrofit2.Call<okhttp3.ResponseBody> listarUsuarios();
    }

    private Sesion sesion;
    private ApiService api;
    private Admin admin;

    @BeforeClass
    public static void config() throws Exception {
        assumeTrue("Definí PAYX_E2E=1 para correr contra el backend real", System.getenv("PAYX_E2E") != null);
        secreto = requerida("PAYX_E2E_JWT_SECRET");
        String email = System.getenv("PAYX_E2E_EMAIL_A");
        String password = System.getenv("PAYX_E2E_PASSWORD");
        if (System.getenv("PAYX_E2E_PV") == null && email != null && password != null) {
            Response<LoginResponse> r = RetrofitClient.crear(URL, () -> null, () -> {}, false)
                    .login(new LoginRequest(email, password)).execute();
            assumeTrue("No se pudo loguear con PAYX_E2E_EMAIL_A: " + r.code(), r.isSuccessful() && r.body() != null);
            usuarioId = claim(r.body().getToken(), "sub");
            huella = claim(r.body().getToken(), "pv");
        } else {
            usuarioId = requerida("PAYX_E2E_USER_ID");
            huella = requerida("PAYX_E2E_PV");
        }
    }

    @Before
    public void setUp() {
        sesion = new Sesion();
        api = RetrofitClient.crear(URL, sesion, false, false, null, 30);
        OkHttpClient cliente = RetrofitClient.crearCliente(HttpUrl.get(URL), sesion, false, false, null, 30);
        admin = new retrofit2.Retrofit.Builder().baseUrl(URL).client(cliente).build().create(Admin.class);
    }

    // ── 401 ───────────────────────────────────────────────────────────────────

    @Test
    public void tokenBasura_401_cierraLaSesion() throws Exception {
        sesion.token = "esto.no.es-un-jwt";
        Response<?> r = api.obtenerPerfil().execute();
        assertEquals(401, r.code());
        assertEquals("sin el texto técnico del backend", ApiErrores.MSG_SESION_INVALIDA, ApiErrores.mensaje(r));
        assertEquals(1, sesion.cierres.size());
        assertNull(sesion.token);
    }

    @Test
    public void tokenDeAntesDelDeploy_sinHuella_401_cierraLaSesion() throws Exception {
        long ahora = System.currentTimeMillis();
        sesion.token = firmar(ahora, ahora + 120 * MIN, null); // firma y vencimiento válidos, pero sin "pv"
        assertEquals(401, api.obtenerPerfil().execute().code());
        assertEquals(1, sesion.cierres.size());
        assertNull(sesion.token);
    }

    @Test
    public void huellaDeOtraContraseña_401_comoTrasCambiarlaEnOtroDispositivo() throws Exception {
        long ahora = System.currentTimeMillis();
        sesion.token = firmar(ahora, ahora + 120 * MIN, "0123456789abcdef");
        assertEquals(401, api.obtenerPerfil().execute().code());
        assertEquals(1, sesion.cierres.size());
        assertNull(sesion.token);
    }

    @Test
    public void tokenVencido_401_cierraLaSesion() throws Exception {
        sesion.token = firmar(System.currentTimeMillis() - 125 * MIN, System.currentTimeMillis() - 5 * MIN);
        assertEquals(401, api.obtenerPerfil().execute().code());
        assertEquals(1, sesion.cierres.size());
        assertNull(sesion.token);
    }

    @Test
    public void firmaInvalida_401_cierraLaSesion() throws Exception {
        String bueno = firmar(System.currentTimeMillis(), System.currentTimeMillis() + 120 * MIN);
        sesion.token = bueno.substring(0, bueno.length() - 4) + "AAAA";
        assertEquals(401, api.obtenerPerfil().execute().code());
        assertEquals(1, sesion.cierres.size());
    }

    // ── 403 ───────────────────────────────────────────────────────────────────

    @Test
    public void usuarioComunEnAdmin_403_muestraElErrorYLaSesionSigue() throws Exception {
        String vigente = firmar(System.currentTimeMillis(), System.currentTimeMillis() + 120 * MIN);
        sesion.token = vigente;
        Response<okhttp3.ResponseBody> r = admin.listarUsuarios().execute();
        assertEquals(403, r.code());
        String mensaje = ApiErrores.mensaje(r);
        assertFalse("hay un texto para mostrar: " + mensaje, mensaje.isEmpty());
        assertTrue(sesion.cierres.isEmpty());
        assertEquals(vigente, sesion.token);
        assertEquals("se puede seguir usando", 200, api.obtenerPerfil().execute().code());
    }

    // ── 400 con campos ────────────────────────────────────────────────────────

    @Test
    public void validacion_400_traeCamposYSeLeen() throws Exception {
        sesion.token = firmar(System.currentTimeMillis(), System.currentTimeMillis() + 120 * MIN);
        // Mensaje vacío: @NotBlank lo rechaza antes de llamar a Anthropic (no cuesta nada, no guarda nada)
        Response<?> r = api.enviarMensajeAsistente(new AsistenteMensajeRequest("", Collections.emptyList())).execute();
        assertEquals(400, r.code());
        String raw = ApiErrores.leerBody(r);
        assertEquals("El mensaje no puede estar vacio", ApiErrores.campos(raw).get("mensaje"));
        assertEquals("El mensaje no puede estar vacio", ApiErrores.mensaje(400, raw));
        assertTrue(sesion.cierres.isEmpty());
    }

    // ── Sesión deslizante ─────────────────────────────────────────────────────

    @Test
    public void conMenosDe30Min_elBackendMandaUnTokenNuevoYLaAppLoUsa() throws Exception {
        long ahora = System.currentTimeMillis();
        String viejo = firmar(ahora - 110 * MIN, ahora + 10 * MIN);
        sesion.token = viejo;
        Response<?> r = api.contarSinLeer().execute();
        assertEquals(200, r.code());
        String header = r.headers().get(RetrofitClient.HEADER_TOKEN_RENOVADO);
        assertNotNull("el backend renovó", header);
        assertEquals("la app lo reemplazó", header, sesion.token);
        assertNotEquals(viejo, sesion.token);
        long exp = JwtUtils.obtenerExpSegundos(sesion.token) * 1000L;
        assertTrue("vence ~2 h desde ahora: " + (exp - ahora) / MIN + " min", exp - ahora > 115 * MIN);

        // El siguiente pedido ya va con el nuevo, que tiene 2 h: no se vuelve a renovar
        Response<?> r2 = api.contarSinLeer().execute();
        assertEquals(200, r2.code());
        assertNull(r2.headers().get(RetrofitClient.HEADER_TOKEN_RENOVADO));
        assertEquals(1, sesion.renovaciones.size());
        assertTrue(sesion.cierres.isEmpty());
    }

    @Test
    public void conMasDe30Min_noHayRenovacion() throws Exception {
        long ahora = System.currentTimeMillis();
        String vigente = firmar(ahora - 60 * MIN, ahora + 60 * MIN);
        sesion.token = vigente;
        Response<?> r = api.contarSinLeer().execute();
        assertEquals(200, r.code());
        assertNull(r.headers().get(RetrofitClient.HEADER_TOKEN_RENOVADO));
        assertEquals(vigente, sesion.token);
    }

    // ── Firma igual a JwtUtil del backend ─────────────────────────────────────

    private static String firmar(long emitido, long vence) throws Exception {
        return firmar(emitido, vence, huella);
    }

    /** @param pv huella de la contraseña (claim "pv"); null = token de antes de que existiera */
    private static String firmar(long emitido, long vence, String pv) throws Exception {
        String header = "{\"alg\":\"HS512\"}";
        String payload = "{\"sub\":\"" + usuarioId + "\",\"rol\":\"USUARIO\""
                + (pv != null ? ",\"pv\":\"" + pv + "\"" : "")
                + ",\"iat\":" + emitido / 1000 + ",\"exp\":" + vence / 1000 + "}";
        String firmado = b64(header.getBytes(StandardCharsets.UTF_8)) + "." + b64(payload.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return firmado + "." + b64(mac.doFinal(firmado.getBytes(StandardCharsets.UTF_8)));
    }

    private static String b64(byte[] bytes) {
        return ByteString.of(bytes).base64Url().replace("=", "");
    }

    private static String claim(String token, String nombre) {
        ByteString payload = ByteString.decodeBase64(token.split("\\.")[1]);
        return JsonParser.parseString(payload.string(StandardCharsets.UTF_8)).getAsJsonObject().get(nombre).getAsString();
    }

    private static String env(String nombre, String porDefecto) {
        String v = System.getenv(nombre);
        return v != null && !v.isEmpty() ? v : porDefecto;
    }

    private static String requerida(String nombre) {
        String v = System.getenv(nombre);
        assumeTrue("Falta la variable de entorno " + nombre, v != null && !v.isEmpty());
        return v;
    }
}
