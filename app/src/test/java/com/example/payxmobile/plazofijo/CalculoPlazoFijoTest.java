package com.example.payxmobile.plazofijo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

/** P2: la fórmula del preview, contra cuentas hechas a mano y contra el redondeo del backend. */
public class CalculoPlazoFijoTest {

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    public void p2_combinacionesCalculadasAMano() {
        // 100.000 × 35,5 % × 30/365 = 106.500.000 / 36.500 = 2.917,808219... -> 2.917,81
        assertEquals(bd("2917.81"), CalculoPlazoFijo.interes(bd("100000"), bd("35.50"), 30));
        assertEquals(bd("102917.81"), CalculoPlazoFijo.total(bd("100000"), bd("35.50"), 30));
        // 1.000 × 40 % × 180/365 = 7.200.000 / 36.500 = 197,260273... -> 197,26
        assertEquals(bd("197.26"), CalculoPlazoFijo.interes(bd("1000"), bd("40.00"), 180));
        // 1.234,56 × 37 % × 90/365 = 4.111.084,8 / 36.500 = 112,632460... -> 112,63
        assertEquals(bd("112.63"), CalculoPlazoFijo.interes(bd("1234.56"), bd("37"), 90));
        // 365 días: exactamente la TNA. 50.000 × 42 % = 21.000
        assertEquals(bd("21000.00"), CalculoPlazoFijo.interes(bd("50000"), bd("42.00"), 365));
    }

    @Test
    public void p2_redondeoHalfUpComoElBackend() {
        // 4.562,5 × 1 × 1 / 36.500 = 0,125 exacto: HALF_UP -> 0,13 (HALF_EVEN daría 0,12)
        assertEquals(bd("0.13"), CalculoPlazoFijo.interes(bd("4562.5"), bd("1"), 1));
        // 1.460 × 1 × 1 / 36.500 = 0,04 exacto
        assertEquals(bd("0.04"), CalculoPlazoFijo.interes(bd("1460"), bd("1"), 1));
    }

    @Test
    public void p2_sinDoubles_montosGrandesExactos() {
        // 13 enteros: con double se perderían los centavos
        BigDecimal monto = bd("9999999999999.99");
        assertEquals(bd("291780821917.81"), CalculoPlazoFijo.interes(monto, bd("35.50"), 30));
        assertEquals(bd("10291780821917.80"), CalculoPlazoFijo.total(monto, bd("35.50"), 30));
    }

    @Test
    public void p2_sinDatosNoHayPreview() {
        assertNull(CalculoPlazoFijo.interes(null, bd("35.5"), 30));
        assertNull(CalculoPlazoFijo.interes(bd("0"), bd("35.5"), 30));
        assertNull(CalculoPlazoFijo.interes(bd("100"), null, 30));
        assertNull(CalculoPlazoFijo.interes(bd("100"), bd("35.5"), 0));
        assertNull(CalculoPlazoFijo.total(bd("100"), null, 30));
    }

    @Test
    public void vencimientoEstimadoEsHoyMasLosDias() {
        assertEquals(LocalDate.of(2026, 10, 28), CalculoPlazoFijo.vencimientoEstimado(LocalDate.of(2026, 9, 28), 30));
        assertEquals(LocalDate.of(2027, 9, 28), CalculoPlazoFijo.vencimientoEstimado(LocalDate.of(2026, 9, 28), 365));
    }

    @Test
    public void formatos() {
        assertEquals("35.5", FormatoPlazoFijo.tna(bd("35.50")));
        assertEquals("40", FormatoPlazoFijo.tna(bd("40.00")));
        assertEquals("20.25", FormatoPlazoFijo.tna(bd("20.25")));
        assertEquals("100", FormatoPlazoFijo.tna(bd("100.00")));
        assertEquals("90 días · TNA 38%", FormatoPlazoFijo.plazoYTna(90, bd("38.00")));
        assertEquals("28 de octubre de 2026", FormatoPlazoFijo.larga(LocalDate.of(2026, 10, 28)));
        assertEquals("", FormatoPlazoFijo.diaMesAnio("basura"));
        assertNull(FormatoPlazoFijo.dia(null));
    }
}
