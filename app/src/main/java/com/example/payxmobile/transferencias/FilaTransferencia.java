package com.example.payxmobile.transferencias;

import com.example.payxmobile.model.TransferenciaResponse;

import java.time.ZoneId;

/** Qué muestra una transferencia en el listado y en el detalle (igual que ActividadItem de la web). */
public final class FilaTransferencia {

    public enum Estilo { POSITIVO, NEGATIVO, CANCELADA }

    public enum Icono { CANCELADA, CRIPTO, RECIBIDA, ENVIADA }

    public final String titulo;
    /** "De {contraparte}" o "A {contraparte}". */
    public final String detalle;
    /** Con signo: "+$ 10,00", "-0,5 BTC"; cancelada sin signo. */
    public final String monto;
    public final Estilo estilo;
    public final Icono icono;
    public final boolean pendiente;
    public final String fechaCorta;

    private FilaTransferencia(String titulo, String detalle, String monto, Estilo estilo, Icono icono,
                              boolean pendiente, String fechaCorta) {
        this.titulo = titulo;
        this.detalle = detalle;
        this.monto = monto;
        this.estilo = estilo;
        this.icono = icono;
        this.pendiente = pendiente;
        this.fechaCorta = fechaCorta;
    }

    public static FilaTransferencia de(TransferenciaResponse t, ZoneId zona) {
        boolean cancelada = t.esCancelada();
        boolean recibida = t.esRecibida();
        Moneda moneda = Moneda.desde(t.getMoneda());
        boolean cripto = moneda != null && moneda.esCripto();

        String titulo = cancelada ? "Transferencia cancelada"
                : recibida ? "Transferencia recibida" : "Transferencia enviada";
        String detalle = (recibida ? "De " : "A ") + t.getContraparteNombre();
        // Una cancelada nunca movió plata: sin signo ni color de enviada/recibida
        String signo = cancelada ? "" : recibida ? "+" : "-";
        Estilo estilo = cancelada ? Estilo.CANCELADA : recibida ? Estilo.POSITIVO : Estilo.NEGATIVO;
        Icono icono = cancelada ? Icono.CANCELADA : cripto ? Icono.CRIPTO
                : recibida ? Icono.RECIBIDA : Icono.ENVIADA;
        return new FilaTransferencia(titulo, detalle,
                signo + FormatoTransferencia.montoListado(t.getMonto(), t.getMoneda()),
                estilo, icono, t.esPendiente(), FormatoTransferencia.fechaCorta(t.getFecha(), zona));
    }
}
