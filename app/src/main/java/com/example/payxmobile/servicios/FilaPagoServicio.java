package com.example.payxmobile.servicios;

import com.example.payxmobile.model.FacturaResponse;
import com.example.payxmobile.transferencias.FormatoTransferencia;
import com.example.payxmobile.utils.MontoFormatter;

import java.time.ZoneId;

/**
 * Qué muestra un pago de servicio en el feed (extensión de la app, la web no lo tiene):
 * "Pago de Luz" / "septiembre de 2026" / "-$ 8.123,45" (sale plata) / fechaPago "dd/MM HH:mm".
 */
public final class FilaPagoServicio {

    public final String titulo;
    public final String detalle;
    public final String monto;
    public final String fecha;
    public final String codigo;

    private FilaPagoServicio(String titulo, String detalle, String monto, String fecha, String codigo) {
        this.titulo = titulo;
        this.detalle = detalle;
        this.monto = monto;
        this.fecha = fecha;
        this.codigo = codigo;
    }

    public static FilaPagoServicio de(FacturaResponse f, ZoneId zona) {
        return new FilaPagoServicio(
                "Pago de " + f.getServicioNombre(),
                FormatoServicio.periodo(f.getPeriodo()),
                "-$ " + MontoFormatter.fiat(f.getMonto()),
                FormatoTransferencia.fechaCorta(f.getFechaPago(), zona),
                f.getServicioCodigo());
    }
}
