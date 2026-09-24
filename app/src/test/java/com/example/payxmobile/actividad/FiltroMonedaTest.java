package com.example.payxmobile.actividad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.transferencias.TransferenciasRepository;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Filtro de "Mis movimientos" por moneda: pesos, dólares y cripto (las 6). */
public class FiltroMonedaTest {

    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");

    /** 2 en pesos, 1 en dólares y una de cada cripto (6), en días distintos. */
    private static List<TransferenciaResponse> mezcla() {
        String[] monedas = {"PESOS", "PESOS", "USD", "BTC", "ETH", "SOL", "USDT", "BNB", "XRP"};
        String[] jsons = new String[monedas.length];
        for (int i = 0; i < monedas.length; i++) {
            jsons[i] = ActividadesTest.t("m" + i, monedas[i], i % 3 == 0 ? "PENDIENTE" : "COMPLETADA",
                    i % 2 == 0 ? "ENVIADA" : "RECIBIDA", String.format("2026-09-%02dT12:00:00-03:00", 24 - i));
        }
        return ActividadesTest.lista(jsons);
    }

    private static TransferenciasRepository.Estado estado() {
        return ActividadesTest.estado(mezcla(), null);
    }

    @Test
    public void cadaFiltroTraeSoloSuMoneda() {
        List<Actividad> items = Actividades.construir(mezcla());
        assertEquals(9, FiltroMoneda.filtrar(items, FiltroMoneda.TODAS).size());
        assertEquals(2, FiltroMoneda.filtrar(items, FiltroMoneda.PESOS).size());
        assertEquals(1, FiltroMoneda.filtrar(items, FiltroMoneda.DOLARES).size());
        List<Actividad> cripto = FiltroMoneda.filtrar(items, FiltroMoneda.CRIPTO);
        assertEquals("las 6 cripto", 6, cripto.size());
        for (Actividad a : FiltroMoneda.filtrar(items, FiltroMoneda.DOLARES)) {
            assertEquals("USD", a.transferencia.getMoneda());
        }
        for (Actividad a : cripto) {
            assertTrue(a.transferencia.getMoneda(), a.transferencia.getMoneda().matches("BTC|ETH|SOL|USDT|BNB|XRP"));
        }
    }

    @Test
    public void contadoresDeCadaBoton() {
        Map<FiltroMoneda, Integer> c = FiltroMoneda.contar(Actividades.construir(mezcla()));
        assertEquals(Integer.valueOf(9), c.get(FiltroMoneda.TODAS));
        assertEquals(Integer.valueOf(2), c.get(FiltroMoneda.PESOS));
        assertEquals(Integer.valueOf(1), c.get(FiltroMoneda.DOLARES));
        assertEquals(Integer.valueOf(6), c.get(FiltroMoneda.CRIPTO));
    }

    @Test
    public void otrosTiposNoEntranConFiltroDeMoneda() {
        List<Actividad> items = new ArrayList<>(Actividades.construir(mezcla()));
        items.add(Actividad.deOtroTipo("cd-1", "2026-09-20T10:00:00Z", Instant.parse("2026-09-20T10:00:00Z"),
                Actividad.Tipo.DOLARES)); // una compra de dólares (futuro): no es una transferencia en USD
        assertEquals(10, FiltroMoneda.filtrar(items, FiltroMoneda.TODAS).size());
        assertEquals(1, FiltroMoneda.filtrar(items, FiltroMoneda.DOLARES).size());
    }

    @Test
    public void seCombinaConElRangoDeFechasYLosContadoresSonDelRango() {
        // Del 20 al 24: m0..m4 -> PESOS, PESOS, USD, BTC, ETH
        LocalDate desde = LocalDate.parse("2026-09-20"), hasta = LocalDate.parse("2026-09-24");
        VistaMovimientos v = VistaMovimientos.de(estado(), desde, hasta, FiltroMoneda.CRIPTO, 1, BA);
        assertEquals(2, v.visibles.size());
        assertEquals("2 de 9 movimientos", v.resumen);
        assertEquals(Integer.valueOf(5), v.contadoresMoneda.get(FiltroMoneda.TODAS));
        assertEquals(Integer.valueOf(2), v.contadoresMoneda.get(FiltroMoneda.PESOS));
        assertEquals(Integer.valueOf(1), v.contadoresMoneda.get(FiltroMoneda.DOLARES));
        assertEquals(Integer.valueOf(2), v.contadoresMoneda.get(FiltroMoneda.CRIPTO));
    }

    @Test
    public void soloMonedaYaCuentaComoFiltroActivo() {
        VistaMovimientos v = VistaMovimientos.de(estado(), null, null, FiltroMoneda.PESOS, 1, BA);
        assertTrue("muestra 'Limpiar filtros'", v.hayFiltro);
        assertEquals("2 de 9 movimientos", v.resumen);
        VistaMovimientos todas = VistaMovimientos.de(estado(), null, null, FiltroMoneda.TODAS, 1, BA);
        assertNull(todas.resumen);
    }

    @Test
    public void mensajesSinResultadosSegunElFiltro() {
        List<TransferenciaResponse> soloPesos = ActividadesTest.lista(
                ActividadesTest.t("p", "PESOS", "COMPLETADA", "ENVIADA", "2026-09-24T12:00:00-03:00"));
        TransferenciasRepository.Estado e = ActividadesTest.estado(soloPesos, null);
        assertEquals(VistaMovimientos.MSG_SIN_RESULTADOS_MONEDA,
                VistaMovimientos.de(e, null, null, FiltroMoneda.CRIPTO, 1, BA).sinResultados);
        LocalDate d = LocalDate.parse("2026-09-24");
        assertEquals(VistaMovimientos.MSG_SIN_RESULTADOS_MONEDA_Y_FECHA,
                VistaMovimientos.de(e, d, d, FiltroMoneda.DOLARES, 1, BA).sinResultados);
        LocalDate otro = LocalDate.parse("2026-09-01");
        assertEquals(VistaMovimientos.MSG_SIN_RESULTADOS,
                VistaMovimientos.de(e, otro, otro, FiltroMoneda.TODAS, 1, BA).sinResultados);
        assertNull(VistaMovimientos.de(e, null, null, FiltroMoneda.PESOS, 1, BA).sinResultados);
    }

    @Test
    public void cambiarLaMonedaVuelveALaPrimeraTandaYLimpiarLaResetea() {
        MovimientosViewModel vm = new MovimientosViewModel();
        vm.mostrarMas();
        vm.mostrarMas();
        assertEquals(3, vm.getTandas());
        vm.setMoneda(FiltroMoneda.CRIPTO);
        assertEquals(1, vm.getTandas());
        assertEquals(FiltroMoneda.CRIPTO, vm.getMoneda());
        vm.setRango(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-24"));
        vm.limpiar();
        assertEquals(FiltroMoneda.TODAS, vm.getMoneda());
        assertNull(vm.getDesde());
        assertNull(vm.getHasta());
    }
}
