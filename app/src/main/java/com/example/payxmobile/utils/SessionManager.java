package com.example.payxmobile.utils;

import android.content.Context;
import android.content.SharedPreferences;


public class SessionManager {

    private static final String PREFS_NAME = "payx_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_NOMBRE_COMPLETO = "nombre_completo";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_NOMBRE_USUARIO = "nombre_usuario";
    private static final String KEY_ROL = "rol";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveSession(String token, String userId, String nombreCompleto,
                            String email, String nombreUsuario, String rol) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_NOMBRE_COMPLETO, nombreCompleto)
                .putString(KEY_EMAIL, email)
                .putString(KEY_NOMBRE_USUARIO, nombreUsuario)
                .putString(KEY_ROL, rol)
                .apply();
    }

    public void clearSession() {
        prefs.edit().clear().apply();
    }

    public boolean isLoggedIn() {
        return getToken() != null;
    }

    public String getToken() { return prefs.getString(KEY_TOKEN, null); }
    public String getUserId() { return prefs.getString(KEY_USER_ID, null); }
    public String getNombreCompleto() { return prefs.getString(KEY_NOMBRE_COMPLETO, ""); }
    public String getEmail() { return prefs.getString(KEY_EMAIL, ""); }
    public String getNombreUsuario() { return prefs.getString(KEY_NOMBRE_USUARIO, ""); }
    public String getRol() { return prefs.getString(KEY_ROL, ""); }
}
