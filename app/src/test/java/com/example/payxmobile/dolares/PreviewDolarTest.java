package com.example.payxmobile.dolares;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.JwtFalso;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * Auditoría del backend, punto 2 (c): lo que recibe el usuario al comprar o vender dólares se redondea
 * SIEMPRE hacia abajo (antes al más cercano: comprar y vender centavos daba ganancia gratis). La vista
 * previa "Recibís US$ X" tiene que usar el mismo DOWN, o promete un centavo que después no acredita.
 */
public class PreviewDolarTest {

    private static final BigDecimal VENTA = new BigDecimal("1530"); // lo que paga el usuario al comprar
    private static final BigDecimal COMPRA = new BigDecimal("1495"); // lo que recibe al vender

    @Test
    public void c_compra_10000a1530_son6_53_no6_54() {
        // 10.000 / 1.530 = 6,5359... HALF_UP prometía 6,54; el backend acredita 6,53
        assertEquals(new BigDecimal("6.53"), CambioDolares.preview(CambioDolares.Tipo.COMPRA, new BigDecimal("10000"), VENTA));
    }

    @Test
    public void c_compra_7_65a1530_da0_yNoSeManda() {
        // 7,65 / 1.530 = 0,005: HALF_UP daba US$ 0,01 (el doble de lo pagado); DOWN da 0,00
        assertEquals(new BigDecimal("0.00"), CambioDolares.preview(CambioDolares.Tipo.COMPRA, new BigDecimal("7.65"), VENTA));
        assertEquals("El monto es muy bajo: no alcanza para comprar ni un centavo de dólar.",
                CambioDolares.validar(CambioDolares.Tipo.COMPRA, "7,65", new BigDecimal("100000"), VENTA));
    }

    @Test
    public void c_venta_0_01x1495_son14_95() {
        assertEquals(new BigDecimal("14.95"), CambioDolares.preview(CambioDolares.Tipo.VENTA, new BigDecimal("0.01"), COMPRA));
        assertNull(CambioDolares.validar(CambioDolares.Tipo.VENTA, "0,01", new BigDecimal("50"), COMPRA));
    }

    @Test
    public void c_ventaQueDa0_esMuyBajoParaLaCotizacion() {
        assertEquals("El monto es muy bajo para esta cotización.",
                CambioDolares.validar(CambioDolares.Tipo.VENTA, "0,01", new BigDecimal("50"), new BigDecimal("0.99")));
    }

    // ── Con la pantalla real (cotización del backend simulado) ───────────────

    private MockWebServer server;
    private ApiService api;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        String token = JwtFalso.conExp(System.currentTimeMillis() / 1000 + 7200);
        api = RetrofitClient.crear(server.url("/").toString(), () -> token, () -> {}, false, false);
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private CambioDolares conCotizacion(CambioDolares.Tipo tipo) throws Exception {
        CambioDolares c = new CambioDolares(tipo, () -> api, () -> api, () -> {});
        server.enqueue(new MockResponse().setBody("{\"compra\":1495.00,\"venta\":1530.00,"
                + "\"fechaActualizacion\":\"2026-09-30T12:00:00Z\",\"desactualizada\":false}"));
        c.cargarCotizacion();
        long fin = System.currentTimeMillis() + 5000;
        while (c.getCotizacion() == null && System.currentTimeMillis() < fin) Thread.sleep(10);
        server.takeRequest(1, TimeUnit.SECONDS);
        return c;
    }

    @Test
    public void c_elRecibisDelFormularioYLoQueSeConfirmaUsanDown() throws Exception {
        CambioDolares compra = conCotizacion(CambioDolares.Tipo.COMPRA);
        assertTrue(compra.setMonto("10000"));
        assertEquals("Recibís US$ 6,53", new BigDecimal("6.53"), compra.getPreview());
        compra.continuar(new BigDecimal("100000"));
        assertEquals(CambioDolares.Paso.CONFIRMAR, compra.getPaso());

        CambioDolares chica = conCotizacion(CambioDolares.Tipo.COMPRA);
        assertTrue(chica.setMonto("7,65"));
        int pedidos = server.getRequestCount();
        chica.continuar(new BigDecimal("100000"));
        assertEquals(CambioDolares.MSG_MUY_BAJO_COMPRA, chica.getError());
        assertEquals("no pasa a confirmar", CambioDolares.Paso.FORMULARIO, chica.getPaso());
        assertEquals("ni un pedido", pedidos, server.getRequestCount());

        CambioDolares venta = conCotizacion(CambioDolares.Tipo.VENTA);
        assertTrue(venta.setMonto("0,01"));
        assertEquals(new BigDecimal("14.95"), venta.getPreview());
    }
}
