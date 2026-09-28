package com.example.payxmobile.cajas;

import com.example.payxmobile.transferencias.FormatoTransferencia;
import com.example.payxmobile.utils.MontoFormatter;

import java.time.ZoneId;

/**
 * Qué muestra un depósito/retiro de caja en el feed (extensión de la app, la web no lo tiene):
 * "Depósito en Viaje" / "Caja de ahorro" / "-$ 1.000,00" o "Retiro de Viaje" / "+$ 500,00".
 *
 * Signo desde el punto de vista del saldo PRINCIPAL, igual que constituir un plazo fijo: el depósito
 * SALE de la cuenta (negativo) y el retiro VUELVE (positivo).
 */
public final class FilaMovimientoCaja {

    public final String titulo;
    public final String detalle;
    public final String monto;
    /** true = entra plata al saldo principal (retiro). */
    public final boolean positivo;
    public final String fecha;
    public final String color;
    public final String icono;

    private FilaMovimientoCaja(String titulo, String detalle, String monto, boolean positivo, String fecha,
                               String color, String icono) {
        this.titulo = titulo;
        this.detalle = detalle;
        this.monto = monto;
        this.positivo = positivo;
        this.fecha = fecha;
        this.color = color;
        this.icono = icono;
    }

    public static FilaMovimientoCaja de(MovimientoCaja m, ZoneId zona) {
        boolean deposito = m.esDeposito();
        return new FilaMovimientoCaja(
                (deposito ? "Depósito en " : "Retiro de ") + m.nombreCaja,
                m.cajaEliminada ? "Caja eliminada: el saldo volvió a tu cuenta" : "Caja de ahorro",
                (deposito ? "-" : "+") + "$ " + MontoFormatter.fiat(m.monto),
                !deposito,
                FormatoTransferencia.fechaCorta(m.fecha, zona),
                TemasCaja.colorODefecto(m.color),
                TemasCaja.iconoODefecto(m.icono));
    }
}
