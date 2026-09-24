package com.example.payxmobile.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.CountDownTimer;
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
        long fin = System.currentTimeMillis() + ESPERA_MS;
        prefs.edit().putLong(clave, fin).apply();
        correr(fin);
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
