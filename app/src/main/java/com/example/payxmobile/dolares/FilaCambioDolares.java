package com.example.payxmobile.dolares;

import com.example.payxmobile.model.OperacionCambioResponse;
import com.example.payxmobile.transferencias.FormatoTransferencia;
import com.example.payxmobile.utils.MontoFormatter;

import java.time.ZoneId;

/**
 * Qué muestra una compra/venta de dólares en el feed (igual que ActividadItem de la web):
 * "Compra de dólares" / "Pagaste $ X" / "+US$ Y" o "Venta de dólares" / "Recibiste $ X" / "-US$ Y".
 */
public final class FilaCambioDolares {

    public final String titulo;
    public final String detalle;
    /** Siempre 2 decimales: "+US$ 3,48". */
    public final String monto;
    public final boolean esCompra;
    public final String fechaCorta;

    private FilaCambioDolares(String titulo, String detalle, String monto, boolean esCompra, String fechaCorta) {
        this.titulo = titulo;
        this.detalle = detalle;
        this.monto = monto;
        this.esCompra = esCompra;
        this.fechaCorta = fechaCorta;
    }

    public static FilaCambioDolares de(OperacionCambioResponse c, ZoneId zona) {
        boolean compra = c.esCompra();
        return new FilaCambioDolares(
                compra ? "Compra de dólares" : "Venta de dólares",
                (compra ? "Pagaste $ " : "Recibiste $ ") + MontoFormatter.fiat(c.getMontoPesos()),
                (compra ? "+" : "-") + "US$ " + MontoFormatter.fiat(c.getMontoUsd()),
                compra,
                FormatoTransferencia.fechaCorta(c.getFecha(), zona));
    }
}
