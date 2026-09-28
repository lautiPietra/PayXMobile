package com.example.payxmobile.plazofijo;

import com.example.payxmobile.actividad.Actividad;
import com.example.payxmobile.model.PlazoFijoResponse;
import com.example.payxmobile.transferencias.FormatoTransferencia;
import com.example.payxmobile.utils.MontoFormatter;

import java.time.ZoneId;

/**
 * Qué muestra un evento de plazo fijo en el feed (igual que ActividadItem de la web):
 * - ALTA: "Plazo fijo constituido" / "30 días · TNA 35.5%" / "-$ monto" / "dd/MM HH:mm" (fechaCreacion).
 * - VENCIMIENTO: "Plazo fijo acreditado" / igual / "+$ montoTotal" / "dd/MM" (fechaVencimiento, SIN hora).
 */
public final class FilaPlazoFijo {

    public final String titulo;
    public final String detalle;
    public final String monto;
    /** true = entra plata (acreditación, verde); false = sale (alta, rojo). */
    public final boolean positivo;
    public final String fecha;

    private FilaPlazoFijo(String titulo, String detalle, String monto, boolean positivo, String fecha) {
        this.titulo = titulo;
        this.detalle = detalle;
        this.monto = monto;
        this.positivo = positivo;
        this.fecha = fecha;
    }

    public static FilaPlazoFijo de(PlazoFijoResponse p, Actividad.EventoPlazoFijo evento, ZoneId zona) {
        String detalle = FormatoPlazoFijo.plazoYTna(p.getPlazoDias(), p.getTna());
        if (evento == Actividad.EventoPlazoFijo.VENCIMIENTO) {
            return new FilaPlazoFijo("Plazo fijo acreditado", detalle, "+$ " + MontoFormatter.fiat(p.getMontoTotal()),
                    true, FormatoPlazoFijo.diaMes(p.getFechaVencimiento()));
        }
        return new FilaPlazoFijo("Plazo fijo constituido", detalle, "-$ " + MontoFormatter.fiat(p.getMonto()),
                false, FormatoTransferencia.fechaCorta(p.getFechaCreacion(), zona));
    }
}
