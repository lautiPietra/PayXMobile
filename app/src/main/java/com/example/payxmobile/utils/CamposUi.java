package com.example.payxmobile.utils;

import android.view.ViewParent;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;

/**
 * Muestra el error de validación debajo del campo (en su TextInputLayout). EditText.setError
 * dibuja un ícono que tapa el "ojito" de los campos de contraseña.
 */
public final class CamposUi {

    private CamposUi() {}

    /** @return true si hay error (mensaje != null) */
    public static boolean error(EditText campo, String mensaje) {
        TextInputLayout til = contenedor(campo);
        if (til != null) {
            til.setError(mensaje);
            til.setErrorEnabled(mensaje != null);
        } else {
            campo.setError(mensaje);
        }
        return mensaje != null;
    }

    private static TextInputLayout contenedor(EditText campo) {
        ViewParent p = campo.getParent();
        while (p != null) {
            if (p instanceof TextInputLayout) return (TextInputLayout) p;
            p = p.getParent();
        }
        return null;
    }
}
