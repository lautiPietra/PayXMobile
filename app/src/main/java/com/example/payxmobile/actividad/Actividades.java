package com.example.payxmobile.actividad;

import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.transferencias.FormatoTransferencia;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Lógica pura del feed, igual a src/utils/actividad.js y Movimientos.jsx de la web.
 * Todo lo que depende de "hoy" o del día recibe un Clock y un ZoneId: el día de un movimiento
 * es el día LOCAL del teléfono, nunca el de UTC.
 */
public final class Actividades {

    public static final int POR_TANDA = 100;
    public static final int CANTIDAD_INICIO = 4;
    public static final List<Actividad.Tipo> TIPOS_ACTIVIDAD = Collections.unmodifiableList(Arrays.asList(Actividad.Tipo.values()));

    private Actividades() {}

    /**
     * Arma el feed ordenado por fecha, más reciente primero. El orden es ESTABLE: en un empate
     * se respeta el orden de llegada (el del backend). Cuando existan los otros tipos, se suman
     * como parámetros acá.
     */
    public static List<Actividad> construir(List<TransferenciaResponse> transferencias) {
        List<Actividad> items = new ArrayList<>(transferencias != null ? transferencias.size() : 0);
        if (transferencias != null) {
            for (TransferenciaResponse t : transferencias) {
                OffsetDateTime f = FormatoTransferencia.parsear(t.getFecha());
                items.add(Actividad.deTransferencia(t, f != null ? f.toInstant() : Instant.EPOCH));
            }
        }
        // List.sort es estable (TimSort)
        items.sort(Comparator.comparing((Actividad a) -> a.instante).reversed());
        return items;
    }

    /** Día calendario LOCAL al que pertenece la fecha (23:30 -03:00 del 24 es del 24 en Argentina). */
    public static LocalDate diaLocalDe(Actividad a, ZoneId zona) {
        return a.instante.atZone(zona).toLocalDate();
    }

    public static LocalDate diaLocalDe(String fechaIso, ZoneId zona) {
        OffsetDateTime f = FormatoTransferencia.parsear(fechaIso);
        return f == null ? null : f.atZoneSameInstant(zona).toLocalDate();
    }

    /**
     * Rango de días, AMBOS extremos incluidos. null = sin límite de ese lado. Si desde > hasta
     * no entra nada (como la web).
     */
    public static List<Actividad> filtrarPorFecha(List<Actividad> items, LocalDate desde, LocalDate hasta, ZoneId zona) {
        if (desde == null && hasta == null) return items;
        List<Actividad> resultado = new ArrayList<>();
        for (Actividad a : items) {
            LocalDate dia = diaLocalDe(a, zona);
            if (desde != null && dia.isBefore(desde)) continue;
            if (hasta != null && dia.isAfter(hasta)) continue;
            resultado.add(a);
        }
        return resultado;
    }

    public static boolean rangoInvalido(LocalDate desde, LocalDate hasta) {
        return desde != null && hasta != null && desde.isAfter(hasta);
    }

    // ── Tipos (la UI del filtro por tipo se conecta cuando existan los otros) ──

    public static Actividad.Tipo tipoDe(Actividad a) {
        return a.tipo;
    }

    /** null = todos los tipos. */
    public static List<Actividad> filtrarPorTipo(List<Actividad> items, Actividad.Tipo tipo) {
        if (tipo == null) return items;
        List<Actividad> resultado = new ArrayList<>();
        for (Actividad a : items) if (a.tipo == tipo) resultado.add(a);
        return resultado;
    }

    public static Map<Actividad.Tipo, Integer> contarPorTipo(List<Actividad> items) {
        Map<Actividad.Tipo, Integer> cuentas = new EnumMap<>(Actividad.Tipo.class);
        for (Actividad.Tipo t : TIPOS_ACTIVIDAD) cuentas.put(t, 0);
        for (Actividad a : items) cuentas.put(a.tipo, cuentas.get(a.tipo) + 1);
        return cuentas;
    }

    // ── Atajos de fecha ───────────────────────────────────────────────────────

    public static final class Atajo {
        public final String id;
        public final String etiqueta;
        public final LocalDate desde; // null = sin límite
        public final LocalDate hasta;

        Atajo(String id, String etiqueta, LocalDate desde, LocalDate hasta) {
            this.id = id;
            this.etiqueta = etiqueta;
            this.desde = desde;
            this.hasta = hasta;
        }

        public boolean coincide(LocalDate d, LocalDate h) {
            return java.util.Objects.equals(desde, d) && java.util.Objects.equals(hasta, h);
        }
    }

    /**
     * "Todo", "Hoy", "7 días" (hoy y los 6 anteriores), "30 días" y "Este mes". Se calculan con
     * el "hoy" del reloj en cada llamada, así siguen bien pasada la medianoche.
     */
    public static List<Atajo> atajos(Clock reloj, ZoneId zona) {
        LocalDate hoy = LocalDate.now(reloj.withZone(zona));
        return Arrays.asList(
                new Atajo("todo", "Todo", null, null),
                new Atajo("hoy", "Hoy", hoy, hoy),
                new Atajo("7", "7 días", hoy.minusDays(6), hoy),
                new Atajo("30", "30 días", hoy.minusDays(29), hoy),
                new Atajo("mes", "Este mes", hoy.withDayOfMonth(1), hoy));
    }

    /** El atajo cuyo rango coincide EXACTAMENTE con el actual ("Todo" sin rango), o null. */
    public static Atajo atajoActivo(List<Atajo> atajos, LocalDate desde, LocalDate hasta) {
        for (Atajo a : atajos) if (a.coincide(desde, hasta)) return a;
        return null;
    }

    public static LocalDate hoy(Clock reloj, ZoneId zona) {
        return LocalDate.now(reloj.withZone(zona));
    }
}
