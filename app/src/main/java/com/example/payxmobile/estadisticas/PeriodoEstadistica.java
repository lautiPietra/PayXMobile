package com.example.payxmobile.estadisticas;

import java.time.LocalDate;

/**
 * Atajos de período. El backend recibe "dias" = ventana hacia atrás desde HOY con ambos extremos
 * incluidos (7 = hoy y los 6 anteriores, como los atajos de Movimientos); 0 = todo el tiempo.
 * "dias" se manda SIEMPRE: sin el parámetro el backend usa 30, no "todo".
 */
public enum PeriodoEstadistica {
    SIETE_DIAS("7 días"),
    TREINTA_DIAS("30 días"),
    ESTE_MES("Este mes"),
    NOVENTA_DIAS("90 días"),
    TODO("Todo el tiempo");

    public static final PeriodoEstadistica POR_DEFECTO = TREINTA_DIAS;

    public final String etiqueta;

    PeriodoEstadistica(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** El "dias" a mandar. "Este mes": del 1 hasta hoy inclusive (el 28/09 son 28 días). */
    public int dias(LocalDate hoy) {
        switch (this) {
            case SIETE_DIAS: return 7;
            case TREINTA_DIAS: return 30;
            case ESTE_MES: return hoy.getDayOfMonth();
            case NOVENTA_DIAS: return 90;
            default: return 0;
        }
    }

    public boolean esTodo() {
        return this == TODO;
    }
}
