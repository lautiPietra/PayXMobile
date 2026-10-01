package com.example.payxmobile.transferencias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.cajas.FormularioCaja;
import com.example.payxmobile.cajas.MovimientoCaja;
import com.example.payxmobile.cajas.OperacionMontoCaja;
import com.example.payxmobile.cripto.OperacionCripto;
import com.example.payxmobile.dolares.CambioDolares;

import org.junit.Test;

import java.math.BigDecimal;

/**
 * Auditoría del backend, punto 2: los saldos en pesos y dólares se guardan con 2 decimales y un monto con
 * más creaba plata de la nada; ahora el backend lo rechaza con 400. La app lo frena ANTES de mandar.
 * (a) validador de decimales y (b) formateo de montos sin notación científica.
 */
public class ValidadorMontoTest {

    private static final String HASTA_2 = "El monto puede tener hasta 2 decimales.";
    private static final String HASTA_8 = "El monto puede tener hasta 8 decimales.";

    // ── (a) Validador de decimales ───────────────────────────────────────────

    @Test
    public void a_hasta2Decimales() {
        for (String ok : new String[]{"100", "100.5", "100.55", "10.500", "100,55", "10,500"}) {
            assertNull(ok, ValidadorMonto.validar(ok, 2));
        }
        assertEquals(HASTA_2, ValidadorMonto.validar("0.005", 2));
        assertEquals(HASTA_2, ValidadorMonto.validar("100.001", 2));
        assertEquals(HASTA_2, ValidadorMonto.validar("0,005", 2));
    }

    @Test
    public void a_notacionCientificaRechazadaSiempre() {
        assertEquals(ValidadorMonto.MSG_MONTO_INVALIDO, ValidadorMonto.validar("1e-7", 2));
        assertEquals("tampoco donde los decimales alcanzarían", ValidadorMonto.MSG_MONTO_INVALIDO, ValidadorMonto.validar("1e-7", 8));
        assertEquals(ValidadorMonto.MSG_MONTO_INVALIDO, ValidadorMonto.validar("1E5", 2));
        assertNull("sin notación científica en la lectura", MontoInput.parsear("1e-7"));
        assertNull(MontoInput.parsear("+5"));
        assertNull(MontoInput.parsear("-5"));
        assertNull(MontoInput.parsear("1 000"));
    }

    @Test
    public void a_hasta8DecimalesEnCripto() {
        assertNull(ValidadorMonto.validar("0.00000001", 8));
        assertNull(ValidadorMonto.validar("0,00000001", 8));
        assertNull("los ceros de más no cuentan", ValidadorMonto.validar("0.0000000100", 8));
        assertEquals(HASTA_8, ValidadorMonto.validar("0.000000001", 8));
    }

    @Test
    public void a_ceroNegativoVacioYMasDe13Enteros() {
        for (String malo : new String[]{"", "   ", ",", ".", "0", "0,00", "-1", "abc", "1,2,3", "12345678901234", null}) {
            assertEquals(String.valueOf(malo), ValidadorMonto.MSG_MONTO_INVALIDO, ValidadorMonto.validar(malo, 2));
        }
        assertNull("13 enteros", ValidadorMonto.validar("1234567890123", 2));
        assertNull("sin entero", ValidadorMonto.validar(",5", 2));
        assertNull("separador al final", ValidadorMonto.validar("10,", 2));
    }

    @Test
    public void a_cadaOperacionConSusDecimales() {
        BigDecimal mucho = new BigDecimal("1000000");
        // Transferencias: 2 en PESOS y USD, 8 en cada cripto
        assertEquals(HASTA_2, ValidadorTransferencia.validar("ana", "0,005", Moneda.PESOS, mucho));
        assertEquals(HASTA_2, ValidadorTransferencia.validar("ana", "0,005", Moneda.USD, mucho));
        for (Moneda cripto : new Moneda[]{Moneda.BTC, Moneda.ETH, Moneda.SOL, Moneda.USDT, Moneda.BNB, Moneda.XRP}) {
            assertNull(cripto.name(), ValidadorTransferencia.validar("ana", "0,00000001", cripto, mucho));
            assertEquals(cripto.name(), HASTA_8, ValidadorTransferencia.validar("ana", "0,000000001", cripto, mucho));
        }
        // Compra y venta de dólares: 2
        assertEquals(HASTA_2, CambioDolares.validar(CambioDolares.Tipo.COMPRA, "1000,005", mucho, new BigDecimal("1435")));
        assertEquals(HASTA_2, CambioDolares.validar(CambioDolares.Tipo.VENTA, "10,005", mucho, new BigDecimal("1385")));
        // Cripto: COMPRA en pesos (2), VENTA en cripto (8)
        BigDecimal precio = new BigDecimal("100000000");
        assertEquals(HASTA_2, OperacionCripto.validar(OperacionCripto.Tipo.COMPRA, Moneda.BTC, "0,005", mucho, precio));
        assertNull(OperacionCripto.validar(OperacionCripto.Tipo.VENTA, Moneda.BTC, "0,00000001", mucho, precio));
        assertEquals(HASTA_8, OperacionCripto.validar(OperacionCripto.Tipo.VENTA, Moneda.BTC, "0,000000001", mucho, precio));
        // Cajas de ahorro: depósito, retiro y meta, 2
        assertEquals(HASTA_2, OperacionMontoCaja.validar(MovimientoCaja.Tipo.DEPOSITO, "10,005", mucho));
        assertEquals(HASTA_2, OperacionMontoCaja.validar(MovimientoCaja.Tipo.RETIRO, "10,005", mucho));
        assertNull(OperacionMontoCaja.validar(MovimientoCaja.Tipo.RETIRO, "10,500", mucho));
        assertEquals(FormularioCaja.MSG_META_DECIMALES, FormularioCaja.validar("Viaje", "#ff6b1a", "target", "10,005"));
        assertNull("meta con ceros de más", FormularioCaja.validar("Viaje", "#ff6b1a", "target", "10,500"));
    }

    @Test
    public void a_elFiltroDeTipeoAvisaSoloCuandoSobranDecimales() {
        assertTrue(MontoInput.sobranDecimales("0,005", Moneda.PESOS));
        assertTrue(MontoInput.sobranDecimales("10.555", Moneda.USD));
        assertTrue(MontoInput.sobranDecimales("0,000000001", Moneda.BTC));
        assertFalse("se puede tipear", MontoInput.sobranDecimales("0,05", Moneda.PESOS));
        assertFalse("otro motivo: letras", MontoInput.sobranDecimales("1a", Moneda.PESOS));
        assertFalse("otro motivo: dos separadores", MontoInput.sobranDecimales("1.000,505", Moneda.PESOS));
        assertFalse("otro motivo: 14 enteros", MontoInput.sobranDecimales("12345678901234,555", Moneda.PESOS));
        assertFalse(MontoInput.sobranDecimales(null, Moneda.PESOS));
    }

    // ── (b) Formateo de montos ───────────────────────────────────────────────

    @Test
    public void b_montoATextoSinNotacionCientifica() {
        assertEquals("0.0000001", MontoInput.plano(new BigDecimal("0.0000001")));
        assertEquals("con toString() sería 1E-7", "1E-7", new BigDecimal("0.0000001").toString());
        assertEquals("100", MontoInput.plano(new BigDecimal("100.00")));
        assertEquals("100.5", MontoInput.plano(new BigDecimal("100.50")));
        assertEquals("1000", MontoInput.plano(new BigDecimal("1E+3")));
        assertEquals("0", MontoInput.plano(new BigDecimal("0.00")));
    }

    @Test
    public void b_usarTodoElSaldoConUnSaldoCriptoChico() {
        // "Usar todo" con 0,0000001 BTC: nunca "1.0E-7" (String.valueOf(double)) ni "1E-7"
        BigDecimal saldo = new BigDecimal("1E-7");
        assertEquals("1.0E-7", String.valueOf(saldo.doubleValue()));
        String texto = MontoInput.aTexto(saldo, Moneda.BTC);
        assertEquals("0,0000001", texto);
        assertEquals(0, saldo.compareTo(MontoInput.parsear(texto)));
        assertNull("y se puede mandar", ValidadorMonto.validar(texto, Moneda.BTC.decimales));
    }

    @Test
    public void b_conDoubleLaSumaTendria17Decimales() {
        // Por qué nunca double: 0.1 + 0.2 viajaría como un monto de 17 decimales y el backend lo rechaza
        assertEquals("0.30000000000000004", String.valueOf(0.1 + 0.2));
        BigDecimal suma = new BigDecimal("0.1").add(new BigDecimal("0.2"));
        assertEquals("0.3", MontoInput.plano(suma));
        assertNull(ValidadorMonto.validar(MontoInput.plano(suma), 2));
    }
}
