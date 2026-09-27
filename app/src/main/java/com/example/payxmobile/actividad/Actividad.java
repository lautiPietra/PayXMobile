package com.example.payxmobile.actividad;

import com.example.payxmobile.model.OperacionCambioResponse;
import com.example.payxmobile.model.OperacionCriptoResponse;
import com.example.payxmobile.model.TransferenciaResponse;

import java.time.Instant;

/**
 * Un movimiento del feed (Inicio y "Mis movimientos"): transferencias y compras/ventas de dólares
 * y de cripto.
 * Cuando se sumen plazos fijos se agrega su tipo y su dato original, sin cambiar la
 * lista ni los filtros (como construirActividades de la web).
 */
public final class Actividad {

    public enum Tipo {
        TRANSFERENCIA("transferencias", "Transferencias"),
        PLAZO_FIJO("plazos-fijos", "Plazos fijos"),
        DOLARES("dolares", "Dólares"),
        CRIPTO("cripto", "Cripto");

        /** Mismo id que TIPOS_ACTIVIDAD de la web. */
        public final String id;
        public final String etiqueta;

        Tipo(String id, String etiqueta) {
            this.id = id;
            this.etiqueta = etiqueta;
        }
    }

    /** Única en todo el feed: "t-{id}" transferencias, "cd-{id}" dólares, "cc-{id}" cripto (como la web). */
    public final String key;
    /** ISO-8601 con offset, tal como viene del backend. */
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

    Actividad(String key, String fecha, Instant instante, Tipo tipo, TransferenciaResponse transferencia,
              OperacionCambioResponse cambioDolares, OperacionCriptoResponse cambioCripto) {
        this.key = key;
        this.fecha = fecha;
        this.instante = instante;
        this.tipo = tipo;
        this.transferencia = transferencia;
        this.cambioDolares = cambioDolares;
        this.cambioCripto = cambioCripto;
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

    /** Para tests de extensibilidad: un movimiento de otro tipo, sin dato original. */
    static Actividad deOtroTipo(String key, String fecha, Instant instante, Tipo tipo) {
        return new Actividad(key, fecha, instante, tipo, null, null, null);
    }
}
