package com.example.payxmobile.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/plazos-fijos/tasas: los plazos disponibles con su TNA, el monto mínimo y cuántos plazos
 * fijos activos puede tener cada usuario. Todo lo define el admin: la app NUNCA los hardcodea.
 */
public class TasasPlazoFijoResponse {
    private List<TasaPlazoFijo> tasas;
    private BigDecimal montoMinimo;
    private Integer maxActivos;

    public TasasPlazoFijoResponse() {}

    public TasasPlazoFijoResponse(List<TasaPlazoFijo> tasas, BigDecimal montoMinimo, Integer maxActivos) {
        this.tasas = tasas;
        this.montoMinimo = montoMinimo;
        this.maxActivos = maxActivos;
    }

    public List<TasaPlazoFijo> getTasas() { return tasas; }
    public BigDecimal getMontoMinimo() { return montoMinimo; }
    public Integer getMaxActivos() { return maxActivos; }
}
