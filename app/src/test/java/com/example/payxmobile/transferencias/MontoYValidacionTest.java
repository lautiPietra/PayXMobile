package com.example.payxmobile.transferencias;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.model.PerfilResponse;
import com.google.gson.Gson;

import org.junit.Test;

import java.math.BigDecimal;

/** T1 (validaciones locales) y T5 (las 8 monedas: código, saldo propio y "Usar todo"). */
public class MontoYValidacionTest {

    private static final BigDecimal SALDO = new BigDecimal("3700.00");

    // ── Tipeo del monto (bloquea decimales/enteros de más) ────────────────────

    @Test
    public void fiatPermiteHasta2Decimales() {
        for (Moneda m : new Moneda[]{Moneda.PESOS, Moneda.USD}) {
            assertTrue(MontoInput.esTipeoValido("10", m));
            assertTrue(MontoInput.esTipeoValido("10,", m));
            assertTrue(MontoInput.esTipeoValido("10,5", m));
            assertTrue(MontoInput.esTipeoValido("10,55", m));
            assertTrue(MontoInput.esTipeoValido("10.55", m));
            // El bug del backend: 0,005 pesos crea plata. Se bloquea el tercer decimal.
            assertFalse(MontoInput.esTipeoValido("0,005", m));
            assertFalse(MontoInput.esTipeoValido("10,555", m));
        }
    }

    @Test
    public void criptoPermiteHasta8Decimales() {
        for (Moneda m : Moneda.values()) {
            if (!m.esCripto()) continue;
            assertTrue(MontoInput.esTipeoValido("0,00000001", m));
            assertFalse(MontoInput.esTipeoValido("0,000000001", m));
        }
    }

    @Test
    public void rechazaBasuraYMasDe13Enteros() {
        assertTrue(MontoInput.esTipeoValido("", Moneda.PESOS));
        assertTrue(MontoInput.esTipeoValido("1234567890123", Moneda.PESOS));
        assertFalse(MontoInput.esTipeoValido("12345678901234", Moneda.PESOS));
        assertFalse(MontoInput.esTipeoValido("1,2,3", Moneda.PESOS));
        assertFalse(MontoInput.esTipeoValido("-5", Moneda.PESOS));
        assertFalse(MontoInput.esTipeoValido("1e5", Moneda.PESOS));
        assertFalse(MontoInput.esTipeoValido("1.000,50", Moneda.PESOS));
    }

    @Test
    public void parseaDesdeElTextoSinArtefactosDeFloat() {
        assertEquals(new BigDecimal("0.1"), MontoInput.parsear("0,1"));
        assertEquals(new BigDecimal("0.1"), MontoInput.parsear("0.1"));
        assertEquals(new BigDecimal("10."), MontoInput.parsear("10,"));
        assertNull(MontoInput.parsear(""));
        assertNull(MontoInput.parsear(","));
        assertNull(MontoInput.parsear("abc"));
    }

    // ── Validador (textos de la web) ───────────────────────────────────────────

    @Test
    public void mensajesDeValidacion() {
        assertEquals(ValidadorTransferencia.SIN_DESTINATARIO, ValidadorTransferencia.validar("", "10", Moneda.PESOS, SALDO));
        assertEquals(ValidadorTransferencia.SIN_DESTINATARIO, ValidadorTransferencia.validar("   ", "10", Moneda.PESOS, SALDO));
        assertEquals(ValidadorTransferencia.MONTO_INVALIDO, ValidadorTransferencia.validar("ana", "", Moneda.PESOS, SALDO));
        assertEquals(ValidadorTransferencia.MONTO_INVALIDO, ValidadorTransferencia.validar("ana", "0", Moneda.PESOS, SALDO));
        assertEquals(ValidadorTransferencia.MONTO_INVALIDO, ValidadorTransferencia.validar("ana", "0,00", Moneda.PESOS, SALDO));
        assertEquals(ValidadorTransferencia.MONTO_INVALIDO, ValidadorTransferencia.validar("ana", "-5", Moneda.PESOS, SALDO));
        assertEquals(ValidadorTransferencia.MONTO_INVALIDO, ValidadorTransferencia.validar("ana", "0,005", Moneda.PESOS, SALDO));
        assertEquals(ValidadorTransferencia.MONTO_INVALIDO, ValidadorTransferencia.validar("ana", "0,000000001", Moneda.BTC, BigDecimal.ONE));
        assertEquals(ValidadorTransferencia.MONTO_INVALIDO, ValidadorTransferencia.validar("ana", "12345678901234", Moneda.PESOS, SALDO));
        assertEquals(ValidadorTransferencia.SALDO_INSUFICIENTE, ValidadorTransferencia.validar("ana", "3700,01", Moneda.PESOS, SALDO));
        assertEquals("Ingresá a quién le querés transferir.", ValidadorTransferencia.SIN_DESTINATARIO);
        assertEquals("Ingresá un monto válido.", ValidadorTransferencia.MONTO_INVALIDO);
        assertEquals("No tenés saldo suficiente para esta transferencia.", ValidadorTransferencia.SALDO_INSUFICIENTE);
        // Exactamente el saldo: se puede
        assertNull(ValidadorTransferencia.validar("ana", "3700", Moneda.PESOS, SALDO));
        assertNull(ValidadorTransferencia.validar(" @ana ", "0,01", Moneda.PESOS, SALDO));
    }

    // ── T5: las 8 monedas ─────────────────────────────────────────────────────

    private static final PerfilResponse PERFIL = new Gson().fromJson(
            "{\"saldoPesos\":3700.00,\"saldoUsd\":150.25,\"saldoBtc\":0.00000389,\"saldoEth\":0.00012280,"
                    + "\"saldoSolana\":0.00170772,\"saldoUsdt\":12.5,\"saldoBnb\":1000,\"saldoXrp\":0E-8}",
            PerfilResponse.class);

    @Test
    public void cadaMonedaUsaSuCodigoYSuSaldoExacto() {
        Object[][] casos = {
                {Moneda.PESOS, "PESOS", "3700.00", "3700,00"},
                {Moneda.USD, "USD", "150.25", "150,25"},
                {Moneda.BTC, "BTC", "0.00000389", "0,00000389"},
                {Moneda.ETH, "ETH", "0.00012280", "0,0001228"},
                {Moneda.SOL, "SOL", "0.00170772", "0,00170772"}, // SOL <-> saldoSolana
                {Moneda.USDT, "USDT", "12.5", "12,5"},
                {Moneda.BNB, "BNB", "1000", "1000"},
                {Moneda.XRP, "XRP", "0E-8", "0"},
        };
        for (Object[] c : casos) {
            Moneda m = (Moneda) c[0];
            assertEquals(c[1], m.codigo());
            assertEquals(m + " saldo", 0, new BigDecimal((String) c[2]).compareTo(m.saldoEn(PERFIL)));
            // "Usar todo" completa SU saldo exacto sin perder decimales
            String texto = MontoInput.aTexto(m.saldoEn(PERFIL), m);
            assertEquals(m + " usar todo", c[3], texto);
            assertEquals(0, MontoInput.parsear(texto).compareTo(m.saldoEn(PERFIL)));
            // y valida contra SU saldo
            if (m.saldoEn(PERFIL).signum() > 0) {
                assertNull(ValidadorTransferencia.validar("ana", texto, m, m.saldoEn(PERFIL)));
            }
        }
        assertEquals(8, Moneda.values().length);
    }

    @Test
    public void usarTodoNuncaSuperaElSaldoFiatAunqueVengaConMasDecimales() {
        assertEquals("12,34", MontoInput.aTexto(new BigDecimal("12.3499"), Moneda.PESOS));
    }

    @Test
    public void monedaDesdeCodigo() {
        assertEquals(Moneda.PESOS, Moneda.desde("ARS"));
        assertEquals(Moneda.PESOS, Moneda.desde("PESOS"));
        assertEquals(Moneda.SOL, Moneda.desde("SOL"));
        assertNull(Moneda.desde("SOLANA"));
        assertNull(Moneda.desde(null));
    }
}
