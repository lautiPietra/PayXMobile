package com.example.payxmobile.utils;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;

/**
 * Bloquea el botón "Reenviar" durante 1 minuto después de tocarlo, para no mandar un mail por
 * cada toque. El fin de la espera se guarda por tipo + email, así que salir y volver a entrar a la
 * pantalla (o cerrar la app) no lo saltea.
 */
public class EsperaReenvio {

    public static final long ESPERA_MS = 60_000L;
    public static final String TIPO_VERIFICACION = "verificacion";
    public static final String TIPO_RESET = "reset";

    private static final String PREFS = "payx_espera_reenvio";

    private final SharedPreferences prefs;
    private final String clave;
    private final TextView boton;
    private final String textoNormal;
    private CountDownTimer timer;

    public EsperaReenvio(Context context, String tipo, String email, TextView boton) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.clave = tipo + ":" + email.trim().toLowerCase();
        this.boton = boton;
        this.textoNormal = boton.getText().toString();
    }

    /** Llamar en onCreate: si quedaba una espera pendiente, el botón arranca bloqueado. */
    public void reanudar() {
        long fin = prefs.getLong(clave, 0);
        if (segundosRestantes(fin, System.currentTimeMillis()) > 0) {
            correr(fin);
        } else {
            habilitar();
        }
    }

    public boolean puedeReenviar() {
        return segundosRestantes(prefs.getLong(clave, 0), System.currentTimeMillis()) == 0;
    }

    /** Llamar al tocar "Reenviar", antes de mandar la request. */
    public void iniciar() {
        apagarDestacado();
        long fin = System.currentTimeMillis() + ESPERA_MS;
        prefs.edit().putLong(clave, fin).apply();
        correr(fin);
    }

    /**
     * El código actual ya no sirve (5 intentos fallidos: el backend lo rechaza aunque después se ingrese el
     * correcto). Se resalta "Reenviar" con un borde y un pulso para que pidan uno nuevo; se apaga al reenviar.
     */
    public void destacar() {
        float d = boton.getResources().getDisplayMetrics().density;
        GradientDrawable borde = new GradientDrawable();
        borde.setCornerRadius(16 * d);
        borde.setStroke(Math.round(1.5f * d), boton.getCurrentTextColor());
        boton.setBackground(borde);
        int h = Math.round(12 * d), v = Math.round(6 * d);
        boton.setPadding(h, v, h, v);
        ObjectAnimator pulso = ObjectAnimator.ofPropertyValuesHolder(boton,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.15f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.15f, 1f));
        pulso.setDuration(700);
        pulso.setRepeatCount(2);
        pulso.start();
    }

    private void apagarDestacado() {
        boton.setBackground(null);
        boton.setPadding(0, 0, 0, 0);
    }

    /** Llamar en onDestroy. */
    public void detener() {
        if (timer != null) timer.cancel();
    }

    private void correr(long fin) {
        detener();
        boton.setEnabled(false);
        boton.setAlpha(0.5f);
        long restante = fin - System.currentTimeMillis();
        boton.setText(textoEspera(segundosRestantes(fin, System.currentTimeMillis())));
        timer = new CountDownTimer(restante, 1000) {
            @Override
            public void onTick(long msHastaFin) {
                boton.setText(textoEspera(segundosRestantes(fin, System.currentTimeMillis())));
            }

            @Override
            public void onFinish() {
                habilitar();
            }
        }.start();
    }

    private void habilitar() {
        boton.setEnabled(true);
        boton.setAlpha(1f);
        boton.setText(textoNormal);
    }

    private String textoEspera(long segundos) {
        return textoNormal + " (" + segundos + "s)";
    }

    /** Segundos que faltan (redondeado hacia arriba), 0 si ya se puede reenviar. */
    public static long segundosRestantes(long finMs, long ahoraMs) {
        long ms = finMs - ahoraMs;
        return ms <= 0 ? 0 : (ms + 999) / 1000;
    }
}
