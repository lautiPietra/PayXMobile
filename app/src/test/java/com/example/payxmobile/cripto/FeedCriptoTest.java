package com.example.payxmobile.cripto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.example.payxmobile.actividad.Actividad;
import com.example.payxmobile.actividad.Actividades;
import com.example.payxmobile.actividad.FeedCombinado;
import com.example.payxmobile.actividad.FiltroMoneda;
import com.example.payxmobile.model.OperacionCambioResponse;
import com.example.payxmobile.model.OperacionCriptoResponse;
import com.example.payxmobile.model.TransferenciaResponse;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** C11 cripto en el feed y el filtro "Cripto", C12 orden intercalado, C13 formato sin ceros de relleno. */
public class FeedCriptoTest {

    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");

    private static OperacionCriptoResponse cc(String id, String tipo, String simbolo, String cant, String pesos, String fecha) {
        return new OperacionCriptoResponse(id, tipo, simbolo, new BigDecimal(cant), new BigDecimal(pesos),
                new BigDecimal("128127714"), fecha);
    }

    private static OperacionCambioResponse cd(String id, String fecha) {
        return new OperacionCambioResponse(id, "COMPRA", new BigDecimal("1"), new BigDecimal("1545"),
                new BigDecimal("1545"), fecha);
    }

    private static List<TransferenciaResponse> transferencias() {
        return new Gson().fromJson("["
                + "{\"id\":\"t2\",\"moneda\":\"BTC\",\"monto\":0.5,\"estado\":\"COMPLETADA\",\"fecha\":\"2026-09-27T12:00:00Z\","
                + "\"direccion\":\"ENVIADA\",\"contraparteNombre\":\"Bea\",\"esEmisor\":true},"
                + "{\"id\":\"t1\",\"moneda\":\"PESOS\",\"monto\":5,\"estado\":\"COMPLETADA\",\"fecha\":\"2026-09-25T12:00:00Z\","
                + "\"direccion\":\"RECIBIDA\",\"contraparteNombre\":\"Ana\",\"esEmisor\":false}]",
                new TypeToken<List<TransferenciaResponse>>() {}.getType());
    }

    private static final List<OperacionCriptoResponse> CRIPTO = Arrays.asList(
            cc("c3", "VENTA", "XRP", "0.12345678", "286.71", "2026-09-27T10:00:00-03:00"),  // 13:00Z
            cc("c2", "COMPRA", "ETH", "0.5", "2028553.50", "2026-09-26T12:00:00Z"),
            cc("c1", "COMPRA", "BTC", "1", "128127714", "2026-09-24T12:00:00Z"));

    @Test
    public void c12_seIntercalaConTransferenciasYDolaresPorFecha() {
        List<Actividad> feed = Actividades.construir(transferencias(),
                Collections.singletonList(cd("d1", "2026-09-26T18:00:00Z")), CRIPTO);
        String[] esperado = {"cc-c3", "t-t2", "cd-d1", "cc-c2", "t-t1", "cc-c1"};
        assertEquals(esperado.length, feed.size());
        for (int i = 0; i < esperado.length; i++) assertEquals("posición " + i, esperado[i], feed.get(i).key);
        assertEquals(Actividad.Tipo.CRIPTO, feed.get(0).tipo);
        assertSame(CRIPTO.get(0), feed.get(0).cambioCripto);
        assertNull(feed.get(0).transferencia);
        assertNull(feed.get(0).cambioDolares);
    }

    @Test
    public void c11_filtroCriptoAgrupaLasSeisYSuContador() {
        List<Actividad> feed = Actividades.construir(transferencias(),
                Collections.singletonList(cd("d1", "2026-09-26T18:00:00Z")), CRIPTO);
        List<Actividad> cripto = FiltroMoneda.filtrar(feed, FiltroMoneda.CRIPTO);
        // 3 operaciones (BTC, ETH, XRP juntas) + la transferencia en BTC
        assertEquals(4, cripto.size());
        Map<FiltroMoneda, Integer> c = FiltroMoneda.contar(feed);
        assertEquals(Integer.valueOf(4), c.get(FiltroMoneda.CRIPTO));
        assertEquals(Integer.valueOf(1), c.get(FiltroMoneda.DOLARES));
        assertEquals(Integer.valueOf(1), c.get(FiltroMoneda.PESOS));
        assertEquals(Integer.valueOf(6), c.get(FiltroMoneda.TODAS));
    }

    @Test
    public void c13_formatoSinCerosDeRelleno() {
        assertEquals("+1 BTC", FilaCambioCripto.de(cc("a", "COMPRA", "BTC", "1.00000000", "1", "2026-09-27T12:00:00Z"), BA).monto);
        assertEquals("+0,5 ETH", FilaCambioCripto.de(cc("b", "COMPRA", "ETH", "0.50000000", "1", "2026-09-27T12:00:00Z"), BA).monto);
        assertEquals("-0,12345678 XRP", FilaCambioCripto.de(cc("c", "VENTA", "XRP", "0.12345678", "1", "2026-09-27T12:00:00Z"), BA).monto);
        assertEquals("-1.234,5 SOL", FilaCambioCripto.de(cc("d", "VENTA", "SOL", "1234.5", "1", "2026-09-27T12:00:00Z"), BA).monto);
    }

    @Test
    public void filaComoLaWeb() {
        FilaCambioCripto compra = FilaCambioCripto.de(cc("a", "COMPRA", "BTC", "0.0000078", "1000.00", "2026-09-27T02:39:10.5-03:00"), BA);
        assertEquals("Compra de BTC", compra.titulo);
        assertEquals("Pagaste $ 1.000,00", compra.detalle);
        assertEquals("+0,0000078 BTC", compra.monto);
        assertEquals(true, compra.esCompra);
        FilaCambioCripto venta = FilaCambioCripto.de(cc("b", "VENTA", "USDT", "30", "45475.8", "2026-09-27T02:39:10-03:00"), BA);
        assertEquals("Venta de USDT", venta.titulo);
        assertEquals("Recibiste $ 45.475,80", venta.detalle);
        assertEquals("-30 USDT", venta.monto);
    }

    @Test
    public void feedCombinadoSeReusaMientrasNingunaListaCambie() {
        FeedCombinado f = new FeedCombinado();
        List<TransferenciaResponse> t = transferencias();
        assertNull("sin transferencias todavía no hay feed", f.de(null, null, CRIPTO));
        List<Actividad> a = f.de(t, null, CRIPTO);
        assertSame(a, f.de(t, null, CRIPTO));
        List<Actividad> b = f.de(t, null, CRIPTO.subList(0, 1));
        assertEquals(3, b.size());
    }
}
