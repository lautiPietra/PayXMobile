package com.example.payxmobile.utils;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.ViewParent;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputLayout;

import java.util.Map;

/**
 * Muestra el error de validación debajo del campo (en su TextInputLayout). EditText.setError
 * dibuja un ícono que tapa el "ojito" de los campos de contraseña.
 */
public final class CamposUi {

    private CamposUi() {}

    /**
     * Cada error del backend debajo de su campo ({"alias": "El alias ya esta en uso"} -> etAlias), ver
     * ApiErrores.Rechazo. Debajo del campo el texto se lee entero; en un Toast (2 líneas) se cortaría.
     *
     * @return true si mostró alguno. Si no, el error es general y va por otro lado (Toast).
     */
    public static boolean errores(Map<String, String> errores, Map<String, EditText> campos) {
        boolean alguno = false;
        for (Map.Entry<String, String> e : errores.entrySet()) {
            EditText campo = campos.get(e.getKey());
            if (campo != null) {
                error(campo, e.getValue());
                alguno = true;
            }
        }
        return alguno;
    }

    /** @return true si hay error (mensaje != null) */
    public static boolean error(EditText campo, String mensaje) {
        TextInputLayout til = contenedor(campo);
        if (til != null) {
            // El ícono de error reemplazaría al "ojito": en una contraseña se deja solo el texto
            if (til.getEndIconMode() == TextInputLayout.END_ICON_PASSWORD_TOGGLE) til.setErrorIconDrawable(null);
            til.setError(mensaje);
            til.setErrorEnabled(mensaje != null);
        } else {
            campo.setError(mensaje);
        }
        return mensaje != null;
    }

    /**
     * Campo de código de verificación: los dígitos se escriben grandes y espaciados, pero el texto vacío (el
     * placeholder "Código de 6 dígitos") conserva la tipografía de los demás campos. Con el tamaño y el
     * espaciado fijos en el XML, el placeholder los heredaba y se agrandaba hasta cortarse ("Código de 6 dí…").
     */
    public static void codigoEspaciado(EditText campo, float spDigitos) {
        final float pxNormal = campo.getTextSize();
        final float pxDigitos = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, spDigitos,
                campo.getResources().getDisplayMetrics());
        campo.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                boolean hayTexto = s.length() > 0;
                campo.setLetterSpacing(hayTexto ? 0.4f : 0f);
                campo.setTextSize(TypedValue.COMPLEX_UNIT_PX, hayTexto ? pxDigitos : pxNormal);
            }
        });
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
