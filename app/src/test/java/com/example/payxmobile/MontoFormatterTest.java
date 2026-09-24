package com.example.payxmobile;

import static org.junit.Assert.assertEquals;

import com.example.payxmobile.utils.MontoFormatter;
import com.example.payxmobile.utils.Saludo;

import org.junit.After;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * S2: mismos textos que toLocaleString('es-AR') de la web (valores generados con node),
 * con cualquier idioma del teléfono.
 */
public class MontoFormatterTest {

    private final Locale original = Locale.getDefault();

    @After
    public void restaurarLocale() {
        Locale.setDefault(original);
    }

    private static final String[][] FIAT = {
            {"0", "0,00"}, {"12.5", "12,50"}, {"999.99", "999,99"}, {"1234.5", "1.234,50"},
            {"12345.67", "12.345,67"}, {"1234567.5", "1.234.567,50"}, {"10000000", "10.000.000,00"},
            // redondeo mitad hacia arriba
            {"0.005", "0,01"}, {"1.005", "1,01"}, {"2.675", "2,68"},
            // saldo grande: con float se veía "97.567.744,00"
            {"97567743.55", "97.567.743,55"},
            {"5000.00", "5.000,00"},
    };

    private static final String[][] CRIPTO = {
            {"0", "0"}, {"0.5", "0,5"}, {"1", "1"}, {"1000", "1.000"}, {"1234.5", "1.234,5"},
            {"0.12345678", "0,12345678"}, {"12345.123456789", "12.345,12345679"},
            {"0.000000005", "0,00000001"},
            // así viaja el cero desde el backend (BigDecimal con escala 8)
            {"0E-8", "0"}, {"1.50000000", "1,5"},
    };

    @Test
    public void fiatYCriptoConCualquierIdiomaDelTelefono() {
        for (Locale idioma : new Locale[]{Locale.US, new Locale("es", "ES"), new Locale("es", "AR"),
                Locale.GERMANY, new Locale("ar", "EG")}) {
            Locale.setDefault(idioma);
            for (String[] c : FIAT) {
                assertEquals(idioma + " fiat " + c[0], c[1], MontoFormatter.fiat(new BigDecimal(c[0])));
            }
            for (String[] c : CRIPTO) {
                assertEquals(idioma + " cripto " + c[0], c[1], MontoFormatter.cripto(new BigDecimal(c[0])));
            }
        }
    }

    @Test
    public void equivalenteEnPesos() {
        // node: (0.5*127818114) -> "63.909.057,00"; (0.12345678*1517.34) -> "187,33"
        assertEquals("63.909.057,00",
                MontoFormatter.equivalentePesos(new BigDecimal("0.5"), new BigDecimal("127818114")));
        assertEquals("187,33",
                MontoFormatter.equivalentePesos(new BigDecimal("0.12345678"), new BigDecimal("1517.34")));
        assertEquals("0,00", MontoFormatter.equivalentePesos(new BigDecimal("0E-8"), new BigDecimal("2276.95")));
    }

    @Test
    public void sinPrecioEsGuionNoCero() {
        assertEquals(MontoFormatter.SIN_DATO, MontoFormatter.equivalentePesos(BigDecimal.ONE, null));
    }

    @Test
    public void nullSeMuestraComoCeroSoloDentroDeUnaRespuestaExitosa() {
        assertEquals("0,00", MontoFormatter.fiat(null));
        assertEquals("0", MontoFormatter.cripto(null));
    }

    @Test
    public void saludoComoLaWeb() {
        assertEquals("Ana", Saludo.primerNombre("Ana María Pérez"));
        assertEquals("Beto", Saludo.primerNombre("Beto"));
        assertEquals("Ana", Saludo.primerNombre("  Ana   Pérez "));
        assertEquals("de nuevo", Saludo.primerNombre(""));
        assertEquals("de nuevo", Saludo.primerNombre(null));
    }
}
