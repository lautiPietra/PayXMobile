package com.example.payxmobile.saldos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.model.CotizacionCripto;
import com.example.payxmobile.model.PerfilResponse;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** S1 (mapeo), S3 (cada pestaña su campo), S4 (equivalente), S5 (ojito), S6 (estados). */
public class VistaSaldoTest {

    /** 8 saldos distintos: si una pestaña muestra el campo de otra, algún assert falla. */
    static final String PERFIL_8_SALDOS = "{\"id\":\"x\",\"nombreCompleto\":\"Ana\","
            + "\"saldoPesos\":1234567.50,\"saldoUsd\":150.25,\"saldoBtc\":0.12345678,"
            + "\"saldoEth\":12345.12345679,\"saldoSolana\":3.5,\"saldoUsdt\":0.00000001,"
            + "\"saldoBnb\":1000,\"saldoXrp\":0E-8}";

    static final String COTIZACIONES = "[{\"simbolo\":\"BTC\",\"nombre\":\"Bitcoin\",\"precio\":127818114,\"desactualizada\":false},"
            + "{\"simbolo\":\"ETH\",\"nombre\":\"Ethereum\",\"precio\":4047980,\"desactualizada\":true},"
            + "{\"simbolo\":\"USDT\",\"nombre\":\"Tether\",\"precio\":1517.34,\"desactualizada\":false},"
            + "{\"simbolo\":\"BNB\",\"nombre\":\"BNB\",\"precio\":1180044,\"desactualizada\":false},"
            + "{\"simbolo\":\"XRP\",\"nombre\":\"XRP\",\"precio\":2276.95,\"desactualizada\":false}]"; // sin SOL

    private static final Gson GSON = new Gson();

    static EstadoSaldos estado(String perfilJson, String cotizacionesJson, String error, boolean desactualizado) {
        PerfilResponse perfil = perfilJson != null ? GSON.fromJson(perfilJson, PerfilResponse.class) : null;
        Map<String, CotizacionCripto> mapa = new LinkedHashMap<>();
        if (cotizacionesJson != null) {
            List<CotizacionCripto> lista = GSON.fromJson(cotizacionesJson, new TypeToken<List<CotizacionCripto>>() {}.getType());
            for (CotizacionCripto c : lista) mapa.put(c.getSimbolo(), c);
        }
        return new EstadoSaldos(perfil, error, desactualizado, mapa, false, false, false);
    }

    // ── S1 ──────────────────────────────────────────────────────────────────

    @Test
    public void s1_losOchoSaldosSeParseanExactosSinPasarPorDouble() {
        PerfilResponse p = GSON.fromJson(PERFIL_8_SALDOS, PerfilResponse.class);
        assertEquals(new BigDecimal("1234567.50"), p.getSaldoPesos());
        assertEquals(new BigDecimal("150.25"), p.getSaldoUsd());
        assertEquals(new BigDecimal("0.12345678"), p.getSaldoBtc());
        assertEquals(new BigDecimal("12345.12345679"), p.getSaldoEth());
        assertEquals(new BigDecimal("3.5"), p.getSaldoSolana());
        assertEquals(new BigDecimal("3.5"), p.getSaldoCripto("SOL")); // saldoSolana <-> SOL
        assertEquals(new BigDecimal("0.00000001"), p.getSaldoUsdt());
        assertEquals(new BigDecimal("1000"), p.getSaldoBnb());
        assertEquals(0, p.getSaldoXrp().signum()); // "0E-8"
        // un número que en double se deforma
        PerfilResponse grande = GSON.fromJson("{\"saldoPesos\":97567743.55}", PerfilResponse.class);
        assertEquals("97567743.55", grande.getSaldoPesos().toPlainString());
    }

    @Test
    public void s1_campoNullOAusenteEsCero() {
        PerfilResponse p = GSON.fromJson("{\"saldoPesos\":null}", PerfilResponse.class);
        assertEquals(BigDecimal.ZERO, p.getSaldoPesos());
        assertEquals(BigDecimal.ZERO, p.getSaldoCripto("BTC"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void s1_criptoDesconocidaNoMuestraOtroSaldo() {
        GSON.fromJson(PERFIL_8_SALDOS, PerfilResponse.class).getSaldoCripto("DOGE");
    }

    // ── S3 ──────────────────────────────────────────────────────────────────

    @Test
    public void s3_cadaPestaniaYCadaCriptoMuestraSuPropioCampo() {
        EstadoSaldos e = estado(PERFIL_8_SALDOS, COTIZACIONES, null, false);
        VistaSaldo pesos = VistaSaldo.de(e, VistaSaldo.PESOS, "BTC", true);
        assertEquals("$", pesos.prefijo);
        assertEquals("1.234.567,50", pesos.monto);
        assertNull(pesos.equivalente);

        VistaSaldo dolares = VistaSaldo.de(e, VistaSaldo.DOLARES, "BTC", true);
        assertEquals("US$", dolares.prefijo);
        assertEquals("150,25", dolares.monto);

        String[][] esperado = {
                {"BTC", "0,12345678 BTC"}, {"ETH", "12.345,12345679 ETH"}, {"SOL", "3,5 SOL"},
                {"USDT", "0,00000001 USDT"}, {"BNB", "1.000 BNB"}, {"XRP", "0 XRP"},
        };
        for (String[] c : esperado) {
            VistaSaldo v = VistaSaldo.de(e, VistaSaldo.CRIPTO, c[0], true);
            assertNull(v.prefijo);
            assertEquals(c[0], c[1], v.monto);
        }
    }

    // ── S4 ──────────────────────────────────────────────────────────────────

    @Test
    public void s4_equivalenteEsSaldoPorPrecioCon2Decimales() {
        EstadoSaldos e = estado(PERFIL_8_SALDOS, COTIZACIONES, null, false);
        // 0.12345678 * 127818114 = 15780012.78011292 -> web (node): "15.780.012,78"
        assertEquals("≈ $ 15.780.012,78", VistaSaldo.de(e, VistaSaldo.CRIPTO, "BTC", true).equivalente);
        assertEquals("≈ $ 1.180.044.000,00", VistaSaldo.de(e, VistaSaldo.CRIPTO, "BNB", true).equivalente);
        assertEquals("≈ $ 0,00", VistaSaldo.de(e, VistaSaldo.CRIPTO, "XRP", true).equivalente);
    }

    @Test
    public void s4_criptoSinCotizacionMuestraGuionNoCero() {
        EstadoSaldos e = estado(PERFIL_8_SALDOS, COTIZACIONES, null, false); // la lista no trae SOL
        assertEquals("≈ $ —", VistaSaldo.de(e, VistaSaldo.CRIPTO, "SOL", true).equivalente);
        EstadoSaldos sinNinguna = estado(PERFIL_8_SALDOS, null, null, false);
        assertEquals("≈ $ —", VistaSaldo.de(sinNinguna, VistaSaldo.CRIPTO, "BTC", true).equivalente);
    }

    @Test
    public void s4_cotizacionDesactualizadaSeAvisa() {
        EstadoSaldos e = estado(PERFIL_8_SALDOS, COTIZACIONES, null, false);
        assertTrue(VistaSaldo.de(e, VistaSaldo.CRIPTO, "ETH", true).cotizacionDesactualizada);
        assertFalse(VistaSaldo.de(e, VistaSaldo.CRIPTO, "BTC", true).cotizacionDesactualizada);
    }

    // ── S5 ──────────────────────────────────────────────────────────────────

    @Test
    public void s5_ocultoMuestraPuntosYEscondeElEquivalente() {
        EstadoSaldos e = estado(PERFIL_8_SALDOS, COTIZACIONES, null, false);
        for (int pestania : new int[]{VistaSaldo.PESOS, VistaSaldo.DOLARES, VistaSaldo.CRIPTO}) {
            VistaSaldo v = VistaSaldo.de(e, pestania, "BTC", false);
            assertEquals("••••••", v.monto);
            assertNull(v.equivalente);
            assertNull(v.valor);
            assertFalse(v.cotizacionDesactualizada);
        }
    }

    // ── S6 ──────────────────────────────────────────────────────────────────

    @Test
    public void s6_primeraCargaNoInventaUnCero() {
        VistaSaldo v = VistaSaldo.de(estado(null, null, null, false), VistaSaldo.PESOS, "BTC", true);
        assertEquals(VistaSaldo.Tipo.CARGANDO, v.tipo);
        assertNull(v.monto);
        assertEquals(VistaSaldo.Tipo.CARGANDO, VistaSaldo.de(null, VistaSaldo.CRIPTO, "BTC", true).tipo);
    }

    @Test
    public void s6_errorEnPrimeraCargaMuestraMensajeSinMonto() {
        VistaSaldo v = VistaSaldo.de(estado(null, null, "Sin conexión con el servidor", false),
                VistaSaldo.DOLARES, "BTC", true);
        assertEquals(VistaSaldo.Tipo.ERROR, v.tipo);
        assertEquals("Sin conexión con el servidor", v.error);
        assertNull(v.monto);
        assertNull(v.equivalente);
    }

    @Test
    public void s6_refrescoFallidoMantieneElValorYAvisa() {
        VistaSaldo v = VistaSaldo.de(estado(PERFIL_8_SALDOS, COTIZACIONES, null, true),
                VistaSaldo.PESOS, "BTC", true);
        assertEquals(VistaSaldo.Tipo.SALDO, v.tipo);
        assertEquals("1.234.567,50", v.monto);
        assertTrue(v.desactualizado);
    }
}
