package com.example.payxmobile.cripto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Curvas del cambio de precio en el ticker (sin Android, testeable). En los 3 s que dura:
 * - el número CUENTA del precio viejo al nuevo en {@link #CONTEO_MS}, frenando al final;
 * - el color verde/rojo APARECE en {@link #APARICION_MS}, se mantiene y se DESVANECE en
 *   {@link #DESVANECIMIENTO_MS} hasta el color original (nunca de golpe);
 * - el texto se corre unos dp hacia arriba (sube) o abajo (baja) y vuelve a su lugar.
 */
public final class TransicionPrecio {

    public enum Direccion { SUBE, BAJA }

    public static final long DURACION_MS = 3_000L;
    public static final long CONTEO_MS = 700L;
    public static final long APARICION_MS = 300L;
    public static final long DESVANECIMIENTO_MS = 900L;
    public static final long DESPLAZAMIENTO_MS = 450L;
    /** Cuántos dp se corre el texto al empezar. */
    public static final float DESPLAZAMIENTO_DP = 5f;

    private TransicionPrecio() {}

    /** null si no hay con qué comparar o si es el mismo precio (100 == 100.00). */
    public static Direccion direccion(BigDecimal antes, BigDecimal ahora) {
        if (antes == null || ahora == null) return null;
        int cmp = ahora.compareTo(antes);
        return cmp > 0 ? Direccion.SUBE : cmp < 0 ? Direccion.BAJA : null;
    }

    /** Avance del conteo (0..1) a los {@code ms}: desacelera (ease-out cúbico) y termina en 1. */
    public static float progresoConteo(long ms) {
        if (ms <= 0) return 0f;
        if (ms >= CONTEO_MS) return 1f;
        float t = ms / (float) CONTEO_MS;
        float r = 1f - t;
        return 1f - r * r * r;
    }

    /** Valor intermedio del número: exacto al final (sin decimales de más que el destino). */
    public static BigDecimal interpolar(BigDecimal desde, BigDecimal hasta, float progreso) {
        if (desde == null || progreso >= 1f) return hasta;
        if (progreso <= 0f) return desde;
        int escala = Math.max(hasta.scale(), 2);
        return desde.add(hasta.subtract(desde).multiply(BigDecimal.valueOf(progreso)))
                .setScale(escala, RoundingMode.HALF_UP);
    }

    /**
     * Intensidad del color verde/rojo (0 = color original, 1 = color pleno): sube suave en
     * APARICION_MS, se mantiene y baja suave en los últimos DESVANECIMIENTO_MS.
     */
    public static float intensidadColor(long ms) {
        if (ms <= 0 || ms >= DURACION_MS) return 0f;
        if (ms < APARICION_MS) return suave(ms / (float) APARICION_MS);
        long inicioSalida = DURACION_MS - DESVANECIMIENTO_MS;
        if (ms <= inicioSalida) return 1f;
        return 1f - suave((ms - inicioSalida) / (float) DESVANECIMIENTO_MS);
    }

    /** Desplazamiento vertical en dp (negativo = arriba). Arranca corrido y vuelve a 0 suave. */
    public static float desplazamientoDp(long ms, Direccion dir) {
        if (dir == null || ms >= DESPLAZAMIENTO_MS) return 0f;
        float restante = 1f - suave(Math.max(ms, 0) / (float) DESPLAZAMIENTO_MS);
        float signo = dir == Direccion.SUBE ? -1f : 1f;
        return signo * DESPLAZAMIENTO_DP * restante;
    }

    /** Ease-in-out (smoothstep): sin saltos al empezar ni al terminar. */
    static float suave(float t) {
        float x = Math.max(0f, Math.min(1f, t));
        return x * x * (3f - 2f * x);
    }
}
