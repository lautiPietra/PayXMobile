package com.example.payxmobile.asistente;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.payxmobile.utils.TokenCipher;

/**
 * Guarda la charla en SharedPreferences privadas de la app, CIFRADA con la clave del Android Keystore
 * (la misma del JWT): puede tener saldos y movimientos del usuario. Si no se puede cifrar, no se
 * guarda en claro: la charla dura lo que dure el proceso.
 */
final class AlmacenConversacion implements ConversacionAsistente.Almacen {

    private static final String PREFS = "payx_asistente";
    private static final String CLAVE = "conversacion";

    private final SharedPreferences prefs;

    AlmacenConversacion(Context app) {
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public String leer() {
        String guardado = prefs.getString(CLAVE, null);
        if (guardado == null) return null;
        try {
            return TokenCipher.descifrar(guardado);
        } catch (Exception e) {
            borrar();
            return null;
        }
    }

    @Override
    public void guardar(String contenido) {
        try {
            prefs.edit().putString(CLAVE, TokenCipher.cifrar(contenido)).apply();
        } catch (Exception e) {
            borrar(); // que no quede una versión vieja que no coincide con lo que se ve
        }
    }

    @Override
    public void borrar() {
        // commit (no apply): al cerrar sesión tiene que quedar borrado en disco ya, aunque el proceso muera
        prefs.edit().remove(CLAVE).commit();
    }
}
