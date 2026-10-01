package com.example.payxmobile.transferencias.ui;

import android.text.InputFilter;
import android.text.Spanned;
import android.widget.EditText;

import com.example.payxmobile.transferencias.Moneda;
import com.example.payxmobile.transferencias.MontoInput;

import java.util.function.Supplier;

/**
 * Filtro de tipeo de los campos de monto: no deja escribir lo que {@link MontoInput#esTipeoValido} no
 * acepta (letras, dos separadores, más de 13 enteros o más decimales de los de la moneda). Si lo que
 * sobraba eran decimales, avisa para que el formulario muestre "El monto puede tener hasta N decimales."
 * en vez de ignorar la tecla sin explicación.
 */
public final class FiltroMonto implements InputFilter {

    private final EditText campo;
    private final Supplier<Moneda> moneda;
    private final Runnable alSobrarDecimales;

    private FiltroMonto(EditText campo, Supplier<Moneda> moneda, Runnable alSobrarDecimales) {
        this.campo = campo;
        this.moneda = moneda;
        this.alSobrarDecimales = alSobrarDecimales;
    }

    /** @param moneda la del monto que se tipea (puede cambiar: pesos al comprar cripto, la cripto al venderla) */
    public static void instalar(EditText campo, Supplier<Moneda> moneda, Runnable alSobrarDecimales) {
        campo.setFilters(new InputFilter[]{new FiltroMonto(campo, moneda, alSobrarDecimales)});
    }

    @Override
    public CharSequence filter(CharSequence fuente, int inicio, int fin, Spanned destino, int dInicio, int dFin) {
        String resultado = destino.subSequence(0, dInicio) + fuente.subSequence(inicio, fin).toString()
                + destino.subSequence(dFin, destino.length());
        Moneda m = moneda.get();
        if (MontoInput.esTipeoValido(resultado, m)) return null;
        // Fuera del filtro: el aviso redibuja el formulario y no conviene hacerlo a mitad de una edición
        if (MontoInput.sobranDecimales(resultado, m)) campo.post(alSobrarDecimales);
        return "";
    }
}
