package com.example.payxmobile.actividad;

import com.example.payxmobile.transferencias.Moneda;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Filtro de "Mis movimientos" por moneda de la transferencia: pesos, dólares o cualquiera de las
 * 6 cripto. Solo aplica a transferencias: con un filtro de moneda activo, los movimientos de
 * otros tipos (cuando existan) no entran.
 */
public enum FiltroMoneda {
    TODAS("Todas"),
    PESOS("Pesos"),
    DOLARES("Dólares"),
    CRIPTO("Cripto");

    public final String etiqueta;

    FiltroMoneda(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** ¿Este movimiento entra con este filtro? */
    public boolean acepta(Actividad a) {
        if (this == TODAS) return true;
        if (a.transferencia == null) return false;
        Moneda m = Moneda.desde(a.transferencia.getMoneda());
        if (m == null) return false;
        switch (this) {
            case PESOS: return m == Moneda.PESOS;
            case DOLARES: return m == Moneda.USD;
            default: return m.esCripto();
        }
    }

    public static List<Actividad> filtrar(List<Actividad> items, FiltroMoneda filtro) {
        if (filtro == null || filtro == TODAS) return items;
        List<Actividad> resultado = new ArrayList<>();
        for (Actividad a : items) if (filtro.acepta(a)) resultado.add(a);
        return resultado;
    }

    /** Cuántos hay de cada uno (TODAS = el total), para mostrarlo en cada botón. */
    public static Map<FiltroMoneda, Integer> contar(List<Actividad> items) {
        Map<FiltroMoneda, Integer> cuentas = new EnumMap<>(FiltroMoneda.class);
        for (FiltroMoneda f : values()) cuentas.put(f, 0);
        for (Actividad a : items) {
            for (FiltroMoneda f : values()) {
                if (f.acepta(a)) cuentas.put(f, cuentas.get(f) + 1);
            }
        }
        return cuentas;
    }
}
