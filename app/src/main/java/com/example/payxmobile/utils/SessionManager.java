package com.example.payxmobile.utils;

import android.content.Context;
import android.content.SharedPreferences;


public class SessionManager {

    private static final String PREFS_NAME = "payx_session";
    // El token se guarda cifrado (TokenCipher). "token" era la clave en texto plano de versiones
    // anteriores: se borra si todavía existe.
    private static final String KEY_TOKEN_LEGACY = "token";
    private static final String KEY_TOKEN_CIFRADO = "token_cifrado";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_NOMBRE_COMPLETO = "nombre_completo";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_NOMBRE_USUARIO = "nombre_usuario";
    private static final String KEY_ROL = "rol";
    private static final String KEY_FOTO_PERFIL_URL = "foto_perfil_url";

    // Cache en memoria para no descifrar en cada request
    private static volatile String tokenEnMemoria;

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.contains(KEY_TOKEN_LEGACY)) {
            prefs.edit().remove(KEY_TOKEN_LEGACY).apply();
        }
    }

    public void saveSession(String token, String userId, String nombreCompleto,
                            String email, String nombreUsuario, String rol, String fotoPerfilUrl) {
        String cifrado;
        try {
            cifrado = TokenCipher.cifrar(token);
        } catch (Exception e) {
            // Sin Keystore no se guarda el token en disco: la sesión dura lo que dure el proceso
            cifrado = null;
        }
        tokenEnMemoria = token;
        prefs.edit()
                .putString(KEY_TOKEN_CIFRADO, cifrado)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_NOMBRE_COMPLETO, nombreCompleto)
                .putString(KEY_EMAIL, email)
                .putString(KEY_NOMBRE_USUARIO, nombreUsuario)
                .putString(KEY_ROL, rol)
                .putString(KEY_FOTO_PERFIL_URL, fotoPerfilUrl)
                .apply();
    }

    public void actualizarNombreUsuario(String nombreUsuario) {
        prefs.edit().putString(KEY_NOMBRE_USUARIO, nombreUsuario).apply();
    }

    public void actualizarFotoPerfilUrl(String url) {
        prefs.edit().putString(KEY_FOTO_PERFIL_URL, url).apply();
    }

    public void clearSession() {
        tokenEnMemoria = null;
        prefs.edit().clear().apply();
    }

    public boolean isLoggedIn() {
        return getToken() != null;
    }

    /** Hay token y su "exp" todavía no pasó. */
    public boolean tieneSesionVigente() {
        String token = getToken();
        return token != null && !JwtUtils.estaVencido(token, System.currentTimeMillis());
    }

    public String getToken() {
        if (tokenEnMemoria != null) return tokenEnMemoria;
        String cifrado = prefs.getString(KEY_TOKEN_CIFRADO, null);
        if (cifrado == null) return null;
        try {
            tokenEnMemoria = TokenCipher.descifrar(cifrado);
        } catch (Exception e) {
            // Clave del Keystore perdida (ej: backup restaurado en otro equipo): hay que loguearse
            prefs.edit().remove(KEY_TOKEN_CIFRADO).apply();
            return null;
        }
        return tokenEnMemoria;
    }

    public String getUserId() { return prefs.getString(KEY_USER_ID, null); }
    public String getNombreCompleto() { return prefs.getString(KEY_NOMBRE_COMPLETO, ""); }
    public String getEmail() { return prefs.getString(KEY_EMAIL, ""); }
    public String getNombreUsuario() { return prefs.getString(KEY_NOMBRE_USUARIO, ""); }
    public String getRol() { return prefs.getString(KEY_ROL, ""); }
    public String getFotoPerfilUrl() { return prefs.getString(KEY_FOTO_PERFIL_URL, null); }
}
