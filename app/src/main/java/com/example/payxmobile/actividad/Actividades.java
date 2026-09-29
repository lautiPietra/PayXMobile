package com.example.payxmobile.actividad;

import com.example.payxmobile.cajas.MovimientoCaja;
import com.example.payxmobile.model.FacturaResponse;
import com.example.payxmobile.model.OperacionCambioResponse;
import com.example.payxmobile.model.OperacionCriptoResponse;
import com.example.payxmobile.model.PlazoFijoResponse;
import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.plazofijo.FormatoPlazoFijo;
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

    public static final int POR_PAGINA = 30;
    public static final int CANTIDAD_INICIO = 4;
    public static final List<Actividad.Tipo> TIPOS_ACTIVIDAD = Collections.unmodifiableList(Arrays.asList(Actividad.Tipo.values()));

    private Actividades() {}

    /**
     * Arma el feed ordenado por fecha, más reciente primero. El orden es ESTABLE: en un empate
     * se respeta el orden de llegada (el del backend). Los demás tipos se suman en las otras
     * sobrecargas.
     */
    public static List<Actividad> construir(List<TransferenciaResponse> transferencias) {
        return construir(transferencias, null);
    }

    /** Transferencias + compras/ventas de dólares (null = todavía no se cargaron: no suman). */
    public static List<Actividad> construir(List<TransferenciaResponse> transferencias,
                                            List<OperacionCambioResponse> cambiosDolares) {
        return construir(transferencias, cambiosDolares, null);
    }

    /** Transferencias + compras/ventas de dólares + compras/ventas de cripto. */
    public static List<Actividad> construir(List<TransferenciaResponse> transferencias,
                                            List<OperacionCambioResponse> cambiosDolares,
                                            List<OperacionCriptoResponse> cambiosCripto) {
        return construir(transferencias, cambiosDolares, cambiosCripto, null, ZoneId.systemDefault());
    }

    /**
     * Todos los tipos. Cualquier lista null = todavía no se cargó: no suma (el resto se muestra
     * igual). Cada plazo fijo suma su alta y, si ya venció, TAMBIÉN su acreditación; esta se ordena
     * como el inicio de ESE día en la zona dada (como instanteDe de la web: medianoche UTC sería
     * el día anterior a la noche en Argentina).
     */
    public static List<Actividad> construir(List<TransferenciaResponse> transferencias,
                                            List<OperacionCambioResponse> cambiosDolares,
                                            List<OperacionCriptoResponse> cambiosCripto,
                                            List<PlazoFijoResponse> plazosFijos, ZoneId zona) {
        return construir(transferencias, cambiosDolares, cambiosCripto, plazosFijos, null, zona);
    }

    /**
     * Más los depósitos/retiros de cajas de ahorro vistos en esta sesión (el backend no tiene
     * historial de esas operaciones: ver MovimientosCajaSesion).
     */
    public static List<Actividad> construir(List<TransferenciaResponse> transferencias,
                                            List<OperacionCambioResponse> cambiosDolares,
                                            List<OperacionCriptoResponse> cambiosCripto,
                                            List<PlazoFijoResponse> plazosFijos,
                                            List<MovimientoCaja> movimientosCaja, ZoneId zona) {
        return construir(transferencias, cambiosDolares, cambiosCripto, plazosFijos, movimientosCaja, null, zona);
    }

    /**
     * Más los pagos de servicios: del historial COMPLETO de facturas (GET /api/facturas/historial,
     * persistente) entran solo las PAGADAS, con fecha = fechaPago. Las pendientes no son movimientos.
     */
    public static List<Actividad> construir(List<TransferenciaResponse> transferencias,
                                            List<OperacionCambioResponse> cambiosDolares,
                                            List<OperacionCriptoResponse> cambiosCripto,
                                            List<PlazoFijoResponse> plazosFijos,
                                            List<MovimientoCaja> movimientosCaja,
                                            List<FacturaResponse> facturas, ZoneId zona) {
        List<Actividad> items = new ArrayList<>(tam(transferencias) + tam(cambiosDolares) + tam(cambiosCripto)
                + 2 * tam(plazosFijos) + tam(movimientosCaja) + tam(facturas));
        if (transferencias != null) {
            for (TransferenciaResponse t : transferencias) {
                items.add(Actividad.deTransferencia(t, instanteDe(t.getFecha())));
            }
        }
        if (cambiosDolares != null) {
            for (OperacionCambioResponse c : cambiosDolares) {
                items.add(Actividad.deCambioDolares(c, instanteDe(c.getFecha())));
            }
        }
        if (cambiosCripto != null) {
            for (OperacionCriptoResponse c : cambiosCripto) {
                items.add(Actividad.deCambioCripto(c, instanteDe(c.getFecha())));
            }
        }
        if (plazosFijos != null) {
            for (PlazoFijoResponse p : plazosFijos) {
                items.add(Actividad.dePlazoFijoAlta(p, instanteDe(p.getFechaCreacion())));
                if (p.esVencido()) {
                    LocalDate dia = FormatoPlazoFijo.dia(p.getFechaVencimiento());
                    Instant instante = dia != null ? dia.atStartOfDay(zona).toInstant() : Instant.EPOCH;
                    items.add(Actividad.dePlazoFijoVencimiento(p, instante));
                }
            }
        }
        if (movimientosCaja != null) {
            for (MovimientoCaja m : movimientosCaja) {
                items.add(Actividad.deMovimientoCaja(m, instanteDe(m.fecha)));
            }
        }
        if (facturas != null) {
            for (FacturaResponse f : facturas) {
                if (!f.esPagada()) continue;
                OffsetDateTime pago = FormatoTransferencia.parsear(f.getFechaPago());
                if (pago != null) items.add(Actividad.dePagoServicio(f, pago.toInstant()));
            }
        }
        // List.sort es estable (TimSort)
        items.sort(Comparator.comparing((Actividad a) -> a.instante).reversed());
        return items;
    }

    private static int tam(List<?> l) {
        return l != null ? l.size() : 0;
    }

    private static Instant instanteDe(String fechaIso) {
        OffsetDateTime f = FormatoTransferencia.parsear(fechaIso);
        return f != null ? f.toInstant() : Instant.EPOCH;
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
     * "Todo", "Hoy", "7 días" (hoy y los 6 anteriores) y "30 días". Se calculan con el "hoy" del
     * reloj en cada llamada, así siguen bien pasada la medianoche. ("Este mes" se sacó a pedido:
     * ese rango se puede armar igual con "Desde"/"Hasta".)
     */
    public static List<Atajo> atajos(Clock reloj, ZoneId zona) {
        LocalDate hoy = LocalDate.now(reloj.withZone(zona));
        return Arrays.asList(
                new Atajo("todo", "Todo", null, null),
                new Atajo("hoy", "Hoy", hoy, hoy),
                new Atajo("7", "7 días", hoy.minusDays(6), hoy),
                new Atajo("30", "30 días", hoy.minusDays(29), hoy));
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
