package com.example.payxmobile.model;

import java.math.BigDecimal;

/** Un plazo ofrecido (GET /api/plazos-fijos/tasas): días y su TNA en %. Configurable desde el panel de admin. */
public class TasaPlazoFijo {
    private int dias;
    private BigDecimal tna;

    public TasaPlazoFijo() {}

    public TasaPlazoFijo(int dias, BigDecimal tna) {
        this.dias = dias;
        this.tna = tna;
    }

    public int getDias() { return dias; }
    public BigDecimal getTna() { return tna; }
}
