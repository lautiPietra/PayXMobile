package com.example.payxmobile.cripto.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import com.example.payxmobile.R;
import com.example.payxmobile.cripto.TransicionPrecio;
import com.example.payxmobile.utils.MontoFormatter;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Muestra precios que cambian con una transición (TransicionPrecio): el número cuenta hasta el
 * nuevo valor, el verde/rojo aparece y se desvanece suave y el texto se mueve apenas. Cada
 * TextView tiene su propio estado, así cada cripto anima por separado.
 *
 * Llamar a {@link #mostrar} en cada render: si el valor no cambió no hace nada (no interrumpe una
 * animación en curso); si cambió la "clave" (otra cripto en el mismo texto) pone el valor directo.
 */
public final class AnimadorPrecios {

    private static final class Estado {
        String clave;
        BigDecimal valor;          // valor destino (el último recibido)
        BigDecimal mostrado;       // lo que se ve en este cuadro (para encadenar animaciones)
        ValueAnimator animador;
        ColorStateList colorOriginal;
    }

    private final float densidad;
    private final int verde, rojo;
    private final ArgbEvaluator mezcla = new ArgbEvaluator();
    private final Map<TextView, Estado> estados = new HashMap<>();

    public AnimadorPrecios(Context context) {
        this.densidad = context.getResources().getDisplayMetrics().density;
        this.verde = context.getColor(R.color.color_positivo);
        this.rojo = context.getColor(R.color.color_negativo);
    }

    /** "$ 128.127.714,00" */
    public static String pesos(BigDecimal v) {
        return "$ " + MontoFormatter.fiat(v);
    }

    public void mostrar(TextView tv, String clave, BigDecimal valor, Function<BigDecimal, String> formato) {
        if (tv == null) return;
        Estado e = estados.get(tv);
        if (e == null) {
            e = new Estado();
            e.colorOriginal = tv.getTextColors();
            estados.put(tv, e);
        }
        if (valor == null) {
            cancelar(tv, e);
            e.valor = e.mostrado = null;
            e.clave = clave;
            tv.setText(MontoFormatter.SIN_DATO);
            return;
        }
        boolean otraClave = clave == null ? e.clave != null : !clave.equals(e.clave);
        TransicionPrecio.Direccion dir = otraClave ? null : TransicionPrecio.direccion(e.valor, valor);
        if (dir == null) {
            // Primera vez, otra cripto o mismo precio: texto directo (salvo que ya esté animando hacia él)
            if (otraClave || e.animador == null) {
                cancelar(tv, e);
                tv.setText(formato.apply(valor));
                e.mostrado = valor;
            }
            e.valor = valor;
            e.clave = clave;
            return;
        }
        animar(tv, e, valor, dir, formato);
    }

    private void animar(TextView tv, Estado e, BigDecimal hasta, TransicionPrecio.Direccion dir,
                        Function<BigDecimal, String> formato) {
        // Si otra animación estaba en curso se parte de lo que se ve ahora (sin saltos)
        BigDecimal desde = e.mostrado != null ? e.mostrado : e.valor;
        if (e.animador != null) {
            ValueAnimator viejo = e.animador;
            e.animador = null;
            viejo.removeAllListeners();
            viejo.cancel();
        }
        int base = e.colorOriginal.getDefaultColor();
        int destino = dir == TransicionPrecio.Direccion.SUBE ? verde : rojo;
        e.valor = hasta;

        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(TransicionPrecio.DURACION_MS);
        a.setInterpolator(new LinearInterpolator()); // las curvas las pone TransicionPrecio
        final Estado estado = e;
        a.addUpdateListener(anim -> {
            long ms = (long) (anim.getAnimatedFraction() * TransicionPrecio.DURACION_MS);
            BigDecimal actual = TransicionPrecio.interpolar(desde, hasta, TransicionPrecio.progresoConteo(ms));
            estado.mostrado = actual;
            tv.setText(formato.apply(actual));
            tv.setTextColor((int) mezcla.evaluate(TransicionPrecio.intensidadColor(ms), base, destino));
            tv.setTranslationY(TransicionPrecio.desplazamientoDp(ms, dir) * densidad);
        });
        a.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // Último cuadro: SIEMPRE el texto exacto y el color original
                terminar(tv, estado, formato.apply(hasta), hasta);
                if (estado.animador == animation) estado.animador = null;
            }
        });
        e.animador = a;
        a.start();
    }

    private void terminar(TextView tv, Estado e, String texto, BigDecimal valor) {
        tv.setText(texto);
        tv.setTextColor(e.colorOriginal);
        tv.setTranslationY(0f);
        e.mostrado = valor;
    }

    private void cancelar(TextView tv, Estado e) {
        if (e.animador == null) return;
        ValueAnimator a = e.animador;
        e.animador = null;
        a.removeAllListeners();
        a.cancel();
        tv.setTextColor(e.colorOriginal);
        tv.setTranslationY(0f);
    }

    /** onDestroy: corta todo sin dejar colores a medias. */
    public void cancelarTodo() {
        for (Map.Entry<TextView, Estado> en : estados.entrySet()) cancelar(en.getKey(), en.getValue());
    }
}
