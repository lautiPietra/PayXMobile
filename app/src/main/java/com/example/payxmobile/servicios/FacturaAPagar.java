package com.example.payxmobile.servicios;

import com.example.payxmobile.model.FacturaResponse;
import com.example.payxmobile.model.ServicioConFacturaResponse;

import java.math.BigDecimal;

/**
 * Lo que necesita la confirmación de pago, venga de una tarjeta del mes (ServicioConFacturaResponse) o
 * de una factura anterior sin pagar del historial (FacturaResponse), como hace la web para reusar el
 * mismo modal. Todo sale de la respuesta del backend (monto incluido).
 */
public final class FacturaAPagar {

    public final String facturaId;
    public final String servicioCodigo;
    public final String nombre;
    /** "" para una factura anterior (el historial no trae el proveedor). */
    public final String proveedor;
    public final BigDecimal monto;
    /** "septiembre de 2026" para una anterior; null para la del mes. */
    public final String periodo;
    public final boolean vencida;

    public FacturaAPagar(String facturaId, String servicioCodigo, String nombre, String proveedor, BigDecimal monto,
                         String periodo, boolean vencida) {
        this.facturaId = facturaId;
        this.servicioCodigo = servicioCodigo;
        this.nombre = nombre;
        this.proveedor = proveedor;
        this.monto = monto;
        this.periodo = periodo;
        this.vencida = vencida;
    }

    public static FacturaAPagar de(ServicioConFacturaResponse s) {
        return new FacturaAPagar(s.getFacturaId(), s.getServicioCodigo(), s.getNombre(), s.getProveedor(),
                s.getMonto(), null, s.isVencida());
    }

    /** Una factura de un mes anterior que quedó sin pagar (siempre vencida). */
    public static FacturaAPagar deAnterior(FacturaResponse f) {
        return new FacturaAPagar(f.getId(), f.getServicioCodigo(), f.getServicioNombre(), "", f.getMonto(),
                FormatoServicio.periodo(f.getPeriodo()), true);
    }
}
