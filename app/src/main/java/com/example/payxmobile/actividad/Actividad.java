package com.example.payxmobile.actividad;

import com.example.payxmobile.model.TransferenciaResponse;

import java.time.Instant;

/**
 * Un movimiento del feed (Inicio y "Mis movimientos"). Hoy solo hay transferencias; cuando se
 * sumen plazos fijos y compra/venta de dólares y cripto se agrega su tipo y su dato original,
 * sin cambiar la lista ni los filtros (como construirActividades de la web).
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

    /** Única en todo el feed: "t-{id}" para transferencias (como la web). */
    public final String key;
    /** ISO-8601 con offset, tal como viene del backend. */
    public final String fecha;
    /** El instante de "fecha", ya parseado (para ordenar y filtrar sin reparsear). */
    public final Instant instante;
    public final Tipo tipo;
    /** Dato original si es una transferencia; null para los demás tipos. */
    public final TransferenciaResponse transferencia;

    Actividad(String key, String fecha, Instant instante, Tipo tipo, TransferenciaResponse transferencia) {
        this.key = key;
        this.fecha = fecha;
        this.instante = instante;
        this.tipo = tipo;
        this.transferencia = transferencia;
    }

    public static Actividad deTransferencia(TransferenciaResponse t, Instant instante) {
        return new Actividad("t-" + t.getId(), t.getFecha(), instante, Tipo.TRANSFERENCIA, t);
    }

    /** Para tests de extensibilidad: un movimiento de otro tipo, sin dato original. */
    static Actividad deOtroTipo(String key, String fecha, Instant instante, Tipo tipo) {
        return new Actividad(key, fecha, instante, tipo, null);
    }
}
