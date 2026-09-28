package com.example.payxmobile.servicios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.actividad.Actividad;
import com.example.payxmobile.actividad.Actividades;
import com.example.payxmobile.actividad.FiltroMoneda;
import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.actividad.VistaMovimientos;
import com.example.payxmobile.model.FacturaResponse;
import com.example.payxmobile.model.OperacionCambioResponse;
import com.example.payxmobile.model.ServicioConFacturaResponse;
import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.transferencias.TransferenciasRepository;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/** Estados visuales (S2), historial ordenado (S7) y feed de movimientos (S11, S13, S14). Sin red. */
public class VistaYFeedServiciosTest {

    private static final ZoneId BA = ServiciosContratoTest.BA;
    private static final LocalDate HOY = LocalDate.of(2026, 9, 28);
    private static final Gson GSON = new Gson();

    private static List<ServicioConFacturaResponse> catalogo() {
        return GSON.fromJson(ServiciosContratoTest.CATALOGO, new TypeToken<List<ServicioConFacturaResponse>>() {}.getType());
    }

    private static FacturaResponse f(String id, String codigo, String nombre, String periodo, String vence, String estado,
                                     String fechaPago) {
        return GSON.fromJson(ServiciosContratoTest.factura(id, codigo, nombre, periodo, "1000.50", vence, estado, fechaPago),
                FacturaResponse.class);
    }

    /** Historial realista DESORDENADO: 3 períodos, pagadas y pendientes. */
    private static List<FacturaResponse> historial() {
        return Arrays.asList(
                f("jul-luz", "LUZ", "Luz", "2026-07", "2026-07-15", "PAGADA", "2026-07-10T09:00:00-03:00"),
                f(ServiciosContratoTest.ID_LUZ, "LUZ", "Luz", "2026-09", "2026-09-15", "PENDIENTE", null),
                f("ago-gas", "GAS", "Gas", "2026-08", "2026-08-15", "PENDIENTE", null),
                f("a2222222-2222-2222-2222-222222222222", "GAS", "Gas", "2026-09", "2026-09-15", "PAGADA", "2026-09-10T11:30:00-03:00"),
                f("ago-luz", "LUZ", "Luz", "2026-08", "2026-08-15", "PAGADA", "2026-08-20T23:30:00-03:00"),
                f("jul-agua", "AGUA", "Agua", "2026-07", "2026-07-15", "PENDIENTE", null),
                f("ago-agua", "AGUA", "Agua", "2026-08", "2026-08-15", "PAGADA", "2026-08-05T08:00:00-03:00"));
    }

    // ── S2: estados visuales ────────────────────────────────────────────────────

    @Test
    public void s2_pendienteVencidaYPagada() {
        List<ServicioConFacturaResponse> c = new ArrayList<>(catalogo());
        c.set(2, GSON.fromJson(ServiciosContratoTest.servicio("AGUA", "Agua", "AySA", "Agua potable",
                "a3333333-3333-3333-3333-333333333333", "2210.00", "PENDIENTE", false, null), ServicioConFacturaResponse.class));
        VistaServicios v = VistaServicios.de(estado(c), null, HOY, BA);

        VistaServicios.Tarjeta vencida = v.tarjetas.get(0);
        assertEquals(VistaServicios.EstadoFactura.VENCIDA, vencida.estado);
        assertEquals("Venció el 15/09/2026", vencida.fecha);
        assertTrue("vencida: resaltada pero se puede pagar", vencida.puedePagar);

        VistaServicios.Tarjeta pagada = v.tarjetas.get(1);
        assertEquals(VistaServicios.EstadoFactura.PAGADA, pagada.estado);
        assertEquals("Pagada el 10/09/2026", pagada.fecha);
        assertFalse("pagada: sin botón de pagar", pagada.puedePagar);

        VistaServicios.Tarjeta pendiente = v.tarjetas.get(2);
        assertEquals(VistaServicios.EstadoFactura.PENDIENTE, pendiente.estado);
        assertEquals("Vence el 15/09/2026", pendiente.fecha);
        assertTrue(pendiente.puedePagar);
    }

    @Test
    public void s2_fechasSinCorrerseDeDia() {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String z : new String[]{"America/Argentina/Buenos_Aires", "Pacific/Kiritimati", "Pacific/Pago_Pago"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(z));
                assertEquals(z, "15/09/2026", FormatoServicio.diaTexto("2026-09-15"));
            }
        } finally {
            TimeZone.setDefault(original);
        }
        // Pagada a las 23:30 del 20/08 en Argentina (02:30Z del 21): es del 20 en Argentina
        assertEquals("20/08/2026", FormatoServicio.diaDeInstante("2026-08-21T02:30:00Z", BA));
        assertEquals("septiembre de 2026", FormatoServicio.periodo("2026-09"));
        assertEquals("basura", FormatoServicio.periodo("basura"));
    }

    // ── S7: historial completo y ordenado ──────────────────────────────────────

    @Test
    public void s7_todasLasFacturasLaMasRecientePrimero() {
        VistaServicios v = VistaServicios.de(estado(catalogo()), estado(historial()), HOY, BA);
        assertEquals("TODAS, no solo las del mes", 7, v.historial.size());
        List<String> ids = new ArrayList<>();
        for (VistaServicios.Fila fila : v.historial) ids.add(fila.factura.getId());
        // Por vencimiento desc; dentro del mismo período, la pagada más recientemente arriba
        assertEquals(Arrays.asList("a2222222-2222-2222-2222-222222222222", ServiciosContratoTest.ID_LUZ,
                "ago-luz", "ago-agua", "ago-gas", "jul-luz", "jul-agua"), ids);

        VistaServicios.Fila pagada = v.historial.get(0);
        assertEquals("Pagada", pagada.badge);
        assertEquals("septiembre de 2026 · Pagada el 10/09/2026", pagada.detalle);
        assertFalse(pagada.puedePagar);
        VistaServicios.Fila vencida = v.historial.get(4);
        assertEquals("Vencida", vencida.badge);
        assertEquals("agosto de 2026 · Venció el 15/08/2026", vencida.detalle);
        assertEquals("$ 1.000,50", vencida.monto);
    }

    @Test
    public void s7_anterioresSinPagarNoRepitenLasDelMes() {
        VistaServicios v = VistaServicios.de(estado(catalogo()), estado(historial()), HOY, BA);
        List<String> ids = new ArrayList<>();
        for (VistaServicios.Fila fila : v.anteriores) ids.add(fila.factura.getId());
        assertEquals("la que venció hace más primero; la de LUZ de septiembre ya es tarjeta",
                Arrays.asList("jul-agua", "ago-gas"), ids);
        assertTrue(v.anteriores.get(0).puedePagar);
        FacturaAPagar aPagar = FacturaAPagar.deAnterior(v.anteriores.get(0).factura);
        assertEquals("julio de 2026", aPagar.periodo);
        assertTrue(aPagar.vencida);
        assertEquals(new BigDecimal("1000.50"), aPagar.monto);
    }

    @Test
    public void s7_sinCatalogoNoSeInventanAnteriores() {
        VistaServicios v = VistaServicios.de(null, estado(historial()), HOY, BA);
        assertEquals(VistaServicios.Modo.CARGANDO, v.modo);
        assertTrue(v.anteriores.isEmpty());
        assertEquals(7, v.historial.size());
        assertEquals(VistaServicios.Modo.VACIO, VistaServicios.de(null, estado(Collections.emptyList()), HOY, BA).modoHistorial);
    }

    // ── S11: el feed usa TODO el historial pagado ───────────────────────────────

    @Test
    public void s11_feedConTodasLasPagadasHistoricas() {
        List<Actividad> feed = Actividades.construir(Collections.emptyList(), null, null, null, null, historial(), BA);
        List<String> keys = new ArrayList<>();
        for (Actividad a : feed) keys.add(a.key);
        assertEquals("solo las PAGADAS, por fechaPago", Arrays.asList(
                "sv-a2222222-2222-2222-2222-222222222222", "sv-ago-luz", "sv-ago-agua", "sv-jul-luz"), keys);
        Actividad a = feed.get(1);
        assertEquals(Actividad.Tipo.SERVICIO, a.tipo);
        FilaPagoServicio fila = FilaPagoServicio.de(a.pagoServicio, BA);
        assertEquals("Pago de Luz", fila.titulo);
        assertEquals("agosto de 2026", fila.detalle);
        assertEquals("sale plata", "-$ 1.000,50", fila.monto);
        assertEquals("fecha = fechaPago, en hora local", "20/08 23:30", fila.fecha);
        assertEquals("LUZ", fila.codigo);
        assertEquals("día local para los filtros", LocalDate.of(2026, 8, 20), Actividades.diaLocalDe(a, BA));
    }

    // ── S13: filtro "Servicios" ────────────────────────────────────────────────

    @Test
    public void s13_elFiltroServiciosCuentaBien() {
        List<Actividad> feed = Actividades.construir(transferencias(), null, null, null, null, historial(), BA);
        Map<FiltroMoneda, Integer> cuentas = FiltroMoneda.contar(feed);
        assertEquals(Integer.valueOf(4), cuentas.get(FiltroMoneda.SERVICIOS));
        assertEquals("un pago de servicio no cuenta en Pesos", Integer.valueOf(1), cuentas.get(FiltroMoneda.PESOS));
        assertEquals(Integer.valueOf(5), cuentas.get(FiltroMoneda.TODAS));
        for (Actividad a : FiltroMoneda.filtrar(feed, FiltroMoneda.SERVICIOS)) assertEquals(Actividad.Tipo.SERVICIO, a.tipo);
        assertEquals("Servicios", FiltroMoneda.SERVICIOS.etiqueta);
        assertEquals("servicios", Actividad.Tipo.SERVICIO.id);

        TransferenciasRepository.Estado estado = estadoTransferencias(transferencias());
        List<Actividad> sinPagos = Actividades.construir(estado.lista, null, null, null, null, Collections.emptyList(), BA);
        assertEquals(VistaMovimientos.MSG_SIN_RESULTADOS_TIPO,
                VistaMovimientos.de(estado, sinPagos, null, null, FiltroMoneda.SERVICIOS, 1, BA).sinResultados);
    }

    // ── S14: orden intercalado ──────────────────────────────────────────────────

    @Test
    public void s14_seIntercalaConLosDemasTipos() {
        List<OperacionCambioResponse> dolares = Collections.singletonList(new OperacionCambioResponse("d1", "COMPRA",
                BigDecimal.ONE, new BigDecimal("1545"), new BigDecimal("1545"), "2026-09-01T12:00:00Z"));
        List<Actividad> feed = Actividades.construir(transferencias(), dolares, null, null, null, historial(), BA);
        List<String> keys = new ArrayList<>();
        for (Actividad a : feed) keys.add(a.key);
        assertEquals(Arrays.asList("sv-a2222222-2222-2222-2222-222222222222", "cd-d1", "t-t1", "sv-ago-luz",
                "sv-ago-agua", "sv-jul-luz"), keys);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static List<TransferenciaResponse> transferencias() {
        return GSON.fromJson("[{\"id\":\"t1\",\"moneda\":\"PESOS\",\"monto\":5,\"estado\":\"COMPLETADA\","
                + "\"fecha\":\"2026-08-25T12:00:00Z\",\"direccion\":\"RECIBIDA\",\"contraparteNombre\":\"Ana\",\"esEmisor\":false}]",
                new TypeToken<List<TransferenciaResponse>>() {}.getType());
    }

    @SuppressWarnings("unchecked")
    private static <T> ListaRemota.Estado<T> estado(List<T> lista) {
        try {
            Constructor<?> c = ListaRemota.Estado.class.getDeclaredConstructor(List.class, String.class, boolean.class, boolean.class);
            c.setAccessible(true);
            return (ListaRemota.Estado<T>) c.newInstance(lista, null, false, false);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static TransferenciasRepository.Estado estadoTransferencias(List<TransferenciaResponse> lista) {
        try {
            Constructor<?> c = TransferenciasRepository.Estado.class.getDeclaredConstructor(List.class, String.class,
                    boolean.class, boolean.class);
            c.setAccessible(true);
            return (TransferenciasRepository.Estado) c.newInstance(lista, null, false, false);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
