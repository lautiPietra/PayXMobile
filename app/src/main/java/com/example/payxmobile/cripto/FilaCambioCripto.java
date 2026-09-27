package com.example.payxmobile.cripto;

import com.example.payxmobile.model.OperacionCriptoResponse;
import com.example.payxmobile.transferencias.FormatoTransferencia;
import com.example.payxmobile.utils.MontoFormatter;

import java.time.ZoneId;

/**
 * Qué muestra una compra/venta de cripto en el feed (ActividadItem de la web): "Compra de BTC" /
 * "Pagaste $ X" / "+0,5 BTC" (hasta 8 decimales SIN ceros de relleno).
 */
public final class FilaCambioCripto {

    public final String titulo;
    public final String detalle;
    public final String monto;
    public final boolean esCompra;
    public final String fechaCorta;

    private FilaCambioCripto(String titulo, String detalle, String monto, boolean esCompra, String fechaCorta) {
        this.titulo = titulo;
        this.detalle = detalle;
        this.monto = monto;
        this.esCompra = esCompra;
        this.fechaCorta = fechaCorta;
    }

    public static FilaCambioCripto de(OperacionCriptoResponse c, ZoneId zona) {
        boolean compra = c.esCompra();
        String simbolo = c.getSimbolo();
        return new FilaCambioCripto(
                (compra ? "Compra de " : "Venta de ") + simbolo,
                (compra ? "Pagaste $ " : "Recibiste $ ") + MontoFormatter.fiat(c.getMontoPesos()),
                (compra ? "+" : "-") + MontoFormatter.cripto(c.getMontoCripto()) + " " + simbolo,
                compra,
                FormatoTransferencia.fechaCorta(c.getFecha(), zona));
    }
}
