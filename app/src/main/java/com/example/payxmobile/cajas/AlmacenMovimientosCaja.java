package com.example.payxmobile.cajas;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.payxmobile.utils.TokenCipher;

/**
 * Guarda los movimientos de cajas de cada usuario en SharedPreferences privadas de la app, CIFRADOS
 * con la clave del Android Keystore (la misma que protege el JWT). Si no se pueden descifrar (clave
 * perdida tras restaurar un backup en otro teléfono) se empieza de cero, sin romper nada.
 */
final class AlmacenMovimientosCaja implements MovimientosCajaSesion.Almacen {

    private static final String PREFS = "payx_movimientos_caja";

    private final SharedPreferences prefs;

    AlmacenMovimientosCaja(Context app) {
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String clave(String usuarioId) {
        return "u_" + usuarioId;
    }

    @Override
    public String leer(String usuarioId) {
        String guardado = prefs.getString(clave(usuarioId), null);
        if (guardado == null) return null;
        try {
            return TokenCipher.descifrar(guardado);
        } catch (Exception e) {
            prefs.edit().remove(clave(usuarioId)).apply();
            return null;
        }
    }

    @Override
    public void guardar(String usuarioId, String contenido) {
        try {
            prefs.edit().putString(clave(usuarioId), TokenCipher.cifrar(contenido)).apply();
        } catch (Exception e) {
            // Sin Keystore disponible no se guarda en claro: el movimiento se ve solo hasta cerrar la app
        }
    }
}
