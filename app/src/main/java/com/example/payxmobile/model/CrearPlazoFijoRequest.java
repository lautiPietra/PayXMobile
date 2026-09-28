package com.example.payxmobile.model;

import java.math.BigDecimal;

/**
 * POST /api/plazos-fijos. monto: hasta 13 enteros y 2 decimales. plazoDias: uno EXACTO de los que
 * devolvió /tasas.
 */
public class CrearPlazoFijoRequest {
    private final BigDecimal monto;
    private final int plazoDias;

    public CrearPlazoFijoRequest(BigDecimal monto, int plazoDias) {
        this.monto = monto;
        this.plazoDias = plazoDias;
    }

    public BigDecimal getMonto() { return monto; }
    public int getPlazoDias() { return plazoDias; }
}
