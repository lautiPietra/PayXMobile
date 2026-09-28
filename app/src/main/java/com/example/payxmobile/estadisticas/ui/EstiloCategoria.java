package com.example.payxmobile.estadisticas.ui;

import android.graphics.Color;

/** Color por categoría de gasto (los de utils/estadisticasTemas.js de la web). Uno desconocido: gris. */
public final class EstiloCategoria {

    private EstiloCategoria() {}

    public static int color(String codigo) {
        if (codigo == null) return Color.parseColor("#9ca3af");
        switch (codigo) {
            case "TRANSFERENCIAS": return Color.parseColor("#ff6b1a");
            case "DOLARES": return Color.parseColor("#0ea5e9");
            case "CRIPTO": return Color.parseColor("#8b5cf6");
            case "PLAZO_FIJO": return Color.parseColor("#16a34a");
            case "SERVICIOS": return Color.parseColor("#14b8a6");
            default: return Color.parseColor("#9ca3af");
        }
    }
}
