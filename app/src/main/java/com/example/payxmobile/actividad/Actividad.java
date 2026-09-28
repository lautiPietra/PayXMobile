package com.example.payxmobile.actividad;

import com.example.payxmobile.cajas.MovimientoCaja;
import com.example.payxmobile.model.FacturaResponse;
import com.example.payxmobile.model.OperacionCambioResponse;
import com.example.payxmobile.model.OperacionCriptoResponse;
import com.example.payxmobile.model.PlazoFijoResponse;
import com.example.payxmobile.model.TransferenciaResponse;

import java.time.Instant;

/**
 * Un movimiento del feed (Inicio y "Mis movimientos"): transferencias, compras/ventas de dólares
 * y de cripto, y los dos eventos de un plazo fijo (como construirActividades de la web).
 */
public final class Actividad {

    public enum Tipo {
        TRANSFERENCIA("transferencias", "Transferencias"),
        PLAZO_FIJO("plazos-fijos", "Plazos fijos"),
        DOLARES("dolares", "Dólares"),
        CRIPTO("cripto", "Cripto"),
        /** Propio de la app (la web no muestra depósitos/retiros de cajas en Movimientos). */
        CAJA_AHORRO("cajas-ahorro", "Cajas de ahorro"),
        /** Propio de la app (la web no muestra pagos de servicios en Movimientos). */
        SERVICIO("servicios", "Servicios");

        /** Mismo id que TIPOS_ACTIVIDAD de la web. */
        public final String id;
        public final String etiqueta;

        Tipo(String id, String etiqueta) {
            this.id = id;
            this.etiqueta = etiqueta;
        }
    }

    /**
     * Un plazo fijo genera DOS movimientos distintos: el alta (sale la plata, fecha con hora) y, si
     * ya venció, la acreditación (entra montoTotal, fecha = día de vencimiento, SIN hora).
     */
    public enum EventoPlazoFijo { ALTA, VENCIMIENTO }

    /**
     * Única en todo el feed (como la web): "t-{id}" transferencias, "cd-{id}" dólares, "cc-{id}"
     * cripto, "pf-alta-{id}" y "pf-venc-{id}" plazos fijos; "ca-{id local}" cajas y "sv-{id}" servicios (solo la app).
     */
    public final String key;
    /** ISO-8601 con offset, tal como viene del backend; "yyyy-MM-dd" en la acreditación de un plazo fijo. */
    public final String fecha;
    /** El instante de "fecha", ya parseado (para ordenar y filtrar sin reparsear). */
    public final Instant instante;
    public final Tipo tipo;
    /** Dato original si es una transferencia; null para los demás tipos. */
    public final TransferenciaResponse transferencia;
    /** Dato original si es una compra/venta de dólares; null para los demás tipos. */
    public final OperacionCambioResponse cambioDolares;
    /** Dato original si es una compra/venta de cripto; null para los demás tipos. */
    public final OperacionCriptoResponse cambioCripto;
    /** Dato original si es un evento de plazo fijo; null para los demás tipos. */
    public final PlazoFijoResponse plazoFijo;
    /** Qué evento del plazo fijo es; null para los demás tipos. */
    public final EventoPlazoFijo eventoPlazoFijo;
    /** Depósito/retiro de una caja de ahorro visto en esta sesión; null para los demás tipos. */
    public final MovimientoCaja movimientoCaja;
    /** Factura PAGADA (del historial); null para los demás tipos. */
    public final FacturaResponse pagoServicio;

    Actividad(String key, String fecha, Instant instante, Tipo tipo, TransferenciaResponse transferencia,
              OperacionCambioResponse cambioDolares, OperacionCriptoResponse cambioCripto) {
        this(key, fecha, instante, tipo, transferencia, cambioDolares, cambioCripto, null, null, null, null);
    }

    private Actividad(String key, String fecha, Instant instante, Tipo tipo, TransferenciaResponse transferencia,
                      OperacionCambioResponse cambioDolares, OperacionCriptoResponse cambioCripto,
                      PlazoFijoResponse plazoFijo, EventoPlazoFijo eventoPlazoFijo, MovimientoCaja movimientoCaja,
                      FacturaResponse pagoServicio) {
        this.key = key;
        this.fecha = fecha;
        this.instante = instante;
        this.tipo = tipo;
        this.transferencia = transferencia;
        this.cambioDolares = cambioDolares;
        this.cambioCripto = cambioCripto;
        this.plazoFijo = plazoFijo;
        this.eventoPlazoFijo = eventoPlazoFijo;
        this.movimientoCaja = movimientoCaja;
        this.pagoServicio = pagoServicio;
    }

    public static Actividad deTransferencia(TransferenciaResponse t, Instant instante) {
        return new Actividad("t-" + t.getId(), t.getFecha(), instante, Tipo.TRANSFERENCIA, t, null, null);
    }

    public static Actividad deCambioDolares(OperacionCambioResponse c, Instant instante) {
        return new Actividad("cd-" + c.getId(), c.getFecha(), instante, Tipo.DOLARES, null, c, null);
    }

    public static Actividad deCambioCripto(OperacionCriptoResponse c, Instant instante) {
        return new Actividad("cc-" + c.getId(), c.getFecha(), instante, Tipo.CRIPTO, null, null, c);
    }

    /** Alta de un plazo fijo: fecha = fechaCreacion (con hora, desempata varios el mismo día). */
    public static Actividad dePlazoFijoAlta(PlazoFijoResponse p, Instant instante) {
        return new Actividad("pf-alta-" + p.getId(), p.getFechaCreacion(), instante, Tipo.PLAZO_FIJO,
                null, null, null, p, EventoPlazoFijo.ALTA, null, null);
    }

    /** Acreditación de un plazo fijo VENCIDO: fecha = fechaVencimiento (un día, sin hora). */
    public static Actividad dePlazoFijoVencimiento(PlazoFijoResponse p, Instant instante) {
        return new Actividad("pf-venc-" + p.getId(), p.getFechaVencimiento(), instante, Tipo.PLAZO_FIJO,
                null, null, null, p, EventoPlazoFijo.VENCIMIENTO, null, null);
    }

    /** Depósito/retiro de caja: key "ca-{id local}" (el backend no le da id a la operación). */
    public static Actividad deMovimientoCaja(MovimientoCaja m, Instant instante) {
        return new Actividad("ca-" + m.id, m.fecha, instante, Tipo.CAJA_AHORRO, null, null, null, null, null, m, null);
    }

    /** Pago de un servicio: una factura PAGADA del historial, fecha = fechaPago (con hora). */
    public static Actividad dePagoServicio(FacturaResponse f, Instant instante) {
        return new Actividad("sv-" + f.getId(), f.getFechaPago(), instante, Tipo.SERVICIO, null, null, null, null, null, null, f);
    }

    /** Para tests de extensibilidad: un movimiento de otro tipo, sin dato original. */
    static Actividad deOtroTipo(String key, String fecha, Instant instante, Tipo tipo) {
        return new Actividad(key, fecha, instante, tipo, null, null, null);
    }
}
