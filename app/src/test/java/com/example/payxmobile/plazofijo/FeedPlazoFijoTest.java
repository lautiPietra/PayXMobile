package com.example.payxmobile.plazofijo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.actividad.Actividad;
import com.example.payxmobile.actividad.Actividades;
import com.example.payxmobile.actividad.FeedCombinado;
import com.example.payxmobile.actividad.FiltroMoneda;
import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.actividad.VistaMovimientos;
import com.example.payxmobile.model.OperacionCambioResponse;
import com.example.payxmobile.model.OperacionCriptoResponse;
import com.example.payxmobile.model.PlazoFijoResponse;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P5 listado, P7 vencimiento (simulado), P10-P13 integración al feed de Inicio y "Mis
 * movimientos". Sin red ni Android.
 */
public class FeedPlazoFijoTest {

    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Gson GSON = new Gson();

    private static PlazoFijoResponse pf(String id, String estado, String inicio, String vence, String creacion) {
        return GSON.fromJson(ConstitucionPlazoFijoTest.plazoFijo(id, "1500.00", "35.50", 30, "43.77", "1543.77",
                estado, inicio, vence, creacion), PlazoFijoResponse.class);
    }

    /** Constituido el 27/09 a las 14:03 (hora argentina), vence el 27/10. */
    private static PlazoFijoResponse activo() {
        return pf("p1", "ACTIVO", "2026-09-27", "2026-10-27", "2026-09-27T14:03:22.123456-03:00");
    }

    /** El mismo plazo fijo, ya vencido y acreditado por el backend. */
    private static PlazoFijoResponse vencido() {
        return pf("p1", "VENCIDO", "2026-09-27", "2026-10-27", "2026-09-27T14:03:22.123456-03:00");
    }

    private static List<TransferenciaResponse> transferencias(String json) {
        return GSON.fromJson(json, new TypeToken<List<TransferenciaResponse>>() {}.getType());
    }

    private static List<String> keys(List<Actividad> feed) {
        List<String> k = new ArrayList<>();
        for (Actividad a : feed) k.add(a.key);
        return k;
    }

    // ── Contrato: el JSON real del backend se lee bien ─────────────────────────

    @Test
    public void contrato_jsonDelBackend() {
        PlazoFijoResponse p = GSON.fromJson(ConstitucionPlazoFijoTest.CREADO, PlazoFijoResponse.class);
        assertEquals("5a0f6c1e-2b3d-4e5f-8a9b-0c1d2e3f4a5b", p.getId());
        assertEquals(new BigDecimal("100000.00"), p.getMonto());
        assertEquals(new BigDecimal("35.50"), p.getTna());
        assertEquals(30, p.getPlazoDias());
        assertEquals(new BigDecimal("2917.81"), p.getInteresEstimado());
        assertEquals(new BigDecimal("102917.81"), p.getMontoTotal());
        assertTrue(p.esActivo());
        assertFalse(p.esVencido());
        assertEquals("2026-09-28", p.getFechaInicio());
        assertEquals("2026-10-28", p.getFechaVencimiento());
    }

    // ── P10 / P11: dos eventos, sin duplicar ni reemplazar ─────────────────────

    @Test
    public void p10_constituirAgregaSoloElAlta() {
        List<Actividad> feed = Actividades.construir(Collections.emptyList(), null, null,
                Collections.singletonList(activo()), BA);
        assertEquals(Collections.singletonList("pf-alta-p1"), keys(feed));
        Actividad alta = feed.get(0);
        assertEquals(Actividad.Tipo.PLAZO_FIJO, alta.tipo);
        assertEquals(Actividad.EventoPlazoFijo.ALTA, alta.eventoPlazoFijo);
        assertEquals("fecha = fechaCreacion (con hora)", "2026-09-27T14:03:22.123456-03:00", alta.fecha);

        FilaPlazoFijo f = FilaPlazoFijo.de(alta.plazoFijo, alta.eventoPlazoFijo, BA);
        assertEquals("Plazo fijo constituido", f.titulo);
        assertEquals("30 días · TNA 35.5%", f.detalle);
        assertEquals("sale plata: negativo y es el MONTO", "-$ 1.500,00", f.monto);
        assertFalse(f.positivo);
        assertEquals("con hora", "27/09 14:03", f.fecha);
    }

    @Test
    public void p11_alVencerApareceTambienLaAcreditacionYQuedanLosDos() {
        FeedCombinado combinado = new FeedCombinado(BA);
        List<TransferenciaResponse> t = Collections.emptyList();
        List<Actividad> antes = combinado.de(t, null, null, Collections.singletonList(activo()));
        assertEquals(Collections.singletonList("pf-alta-p1"), keys(antes));

        // El backend lo venció: la lista nueva trae el MISMO id con estado VENCIDO
        List<Actividad> despues = combinado.de(t, null, null, Collections.singletonList(vencido()));
        assertNotSame("lista nueva = feed nuevo", antes, despues);
        assertEquals(Arrays.asList("pf-venc-p1", "pf-alta-p1"), keys(despues));
        Actividad venc = despues.get(0);
        assertEquals(Actividad.EventoPlazoFijo.VENCIMIENTO, venc.eventoPlazoFijo);
        assertEquals("fecha = fechaVencimiento (sin hora)", "2026-10-27", venc.fecha);

        FilaPlazoFijo f = FilaPlazoFijo.de(venc.plazoFijo, venc.eventoPlazoFijo, BA);
        assertEquals("Plazo fijo acreditado", f.titulo);
        assertEquals("30 días · TNA 35.5%", f.detalle);
        assertEquals("entra plata: positivo y es el TOTAL", "+$ 1.543,77", f.monto);
        assertTrue(f.positivo);
        assertEquals("solo el día, sin hora", "27/10", f.fecha);

        // El alta sigue igual (misma key: la fila no se re-crea ni se mueve)
        assertEquals("-$ 1.500,00", FilaPlazoFijo.de(despues.get(1).plazoFijo, despues.get(1).eventoPlazoFijo, BA).monto);
        // Con las mismas listas se reusa el feed (no se re-arma en cada refresco sin cambios)
        List<PlazoFijoResponse> misma = Collections.singletonList(vencido());
        List<Actividad> armado = combinado.de(t, null, null, misma);
        assertSame(armado, combinado.de(t, null, null, misma));
    }

    @Test
    public void p11_laAcreditacionNoCorreDeDiaEnNingunaZona() {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String zona : new String[]{"America/Argentina/Buenos_Aires", "Pacific/Kiritimati", "Pacific/Pago_Pago", "UTC"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zona));
                ZoneId z = ZoneId.of(zona);
                List<Actividad> feed = Actividades.construir(Collections.emptyList(), null, null,
                        Collections.singletonList(vencido()), z);
                Actividad venc = feed.get(0);
                assertEquals(zona, LocalDate.of(2026, 10, 27), Actividades.diaLocalDe(venc, z));
                assertEquals(zona, "27/10", FilaPlazoFijo.de(venc.plazoFijo, venc.eventoPlazoFijo, z).fecha);
                // Filtro "Hoy" el día del vencimiento: entra
                assertEquals(1, Actividades.filtrarPorFecha(feed.subList(0, 1), LocalDate.of(2026, 10, 27),
                        LocalDate.of(2026, 10, 27), z).size());
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    // ── P12: el filtro "Plazos fijos" cuenta los dos eventos ───────────────────

    @Test
    public void p12_filtroPlazosFijosCuentaAmbosEventos() {
        List<Actividad> feed = Actividades.construir(transferencias("["
                        + "{\"id\":\"t1\",\"moneda\":\"PESOS\",\"monto\":5,\"estado\":\"COMPLETADA\",\"fecha\":\"2026-09-25T12:00:00Z\","
                        + "\"direccion\":\"RECIBIDA\",\"contraparteNombre\":\"Ana\",\"esEmisor\":false}]"),
                null, null, Arrays.asList(vencido(),
                        pf("p2", "ACTIVO", "2026-09-28", "2026-10-28", "2026-09-28T09:00:00-03:00")), BA);
        Map<FiltroMoneda, Integer> cuentas = FiltroMoneda.contar(feed);
        assertEquals("alta p1 + acreditación p1 + alta p2", Integer.valueOf(3), cuentas.get(FiltroMoneda.PLAZOS_FIJOS));
        assertEquals("un plazo fijo no es una transferencia en pesos", Integer.valueOf(1), cuentas.get(FiltroMoneda.PESOS));
        assertEquals(Integer.valueOf(4), cuentas.get(FiltroMoneda.TODAS));
        List<Actividad> solo = FiltroMoneda.filtrar(feed, FiltroMoneda.PLAZOS_FIJOS);
        assertEquals(3, solo.size());
        for (Actividad a : solo) assertEquals(Actividad.Tipo.PLAZO_FIJO, a.tipo);
        // Mismo tipo que la web para los dos eventos
        assertEquals(Integer.valueOf(3), Actividades.contarPorTipo(feed).get(Actividad.Tipo.PLAZO_FIJO));
        assertEquals("plazos-fijos", Actividad.Tipo.PLAZO_FIJO.id);
    }

    @Test
    public void p12_sinPlazosFijosElCartelHablaDeTipo() {
        TransferenciasRepository.Estado estado = estadoCon(transferencias("["
                + "{\"id\":\"t1\",\"moneda\":\"PESOS\",\"monto\":5,\"estado\":\"COMPLETADA\",\"fecha\":\"2026-09-25T12:00:00Z\","
                + "\"direccion\":\"RECIBIDA\",\"contraparteNombre\":\"Ana\",\"esEmisor\":false}]"));
        List<Actividad> feed = Actividades.construir(estado.lista, null, null, Collections.emptyList(), BA);
        VistaMovimientos v = VistaMovimientos.de(estado, feed, null, null, FiltroMoneda.PLAZOS_FIJOS, 1, BA);
        assertEquals(VistaMovimientos.MSG_SIN_RESULTADOS_TIPO, v.sinResultados);
        assertEquals(Integer.valueOf(0), v.contadoresMoneda.get(FiltroMoneda.PLAZOS_FIJOS));
    }

    // ── P13: orden intercalado con los demás tipos ─────────────────────────────

    @Test
    public void p13_seIntercalaPorFechaConTransferenciasDolaresYCripto() {
        List<TransferenciaResponse> t = transferencias("["
                + "{\"id\":\"t2\",\"moneda\":\"PESOS\",\"monto\":1,\"estado\":\"COMPLETADA\",\"fecha\":\"2026-10-27T12:00:00Z\","
                + "\"direccion\":\"ENVIADA\",\"contraparteNombre\":\"Bea\",\"esEmisor\":true},"
                // 26/10 22:00 en Argentina: es del día ANTERIOR al vencimiento, va después
                + "{\"id\":\"t1\",\"moneda\":\"PESOS\",\"monto\":1,\"estado\":\"COMPLETADA\",\"fecha\":\"2026-10-27T01:00:00Z\","
                + "\"direccion\":\"RECIBIDA\",\"contraparteNombre\":\"Ana\",\"esEmisor\":false}]");
        List<OperacionCambioResponse> d = Collections.singletonList(new OperacionCambioResponse("d1", "COMPRA",
                BigDecimal.ONE, new BigDecimal("1545"), new BigDecimal("1545"), "2026-09-27T18:00:00Z")); // 15:00 BA
        List<OperacionCriptoResponse> c = Collections.singletonList(new OperacionCriptoResponse("c1", "COMPRA", "BTC",
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, "2026-09-27T16:00:00Z")); // 13:00 BA
        List<Actividad> feed = Actividades.construir(t, d, c, Collections.singletonList(vencido()), BA);
        // pf-alta 14:03 BA queda entre cripto (13:00) y dólares (15:00)
        assertEquals(Arrays.asList("t-t2", "pf-venc-p1", "t-t1", "cd-d1", "pf-alta-p1", "cc-c1"), keys(feed));
    }

    @Test
    public void p13_dosPlazosElMismoDiaSeDesempatanPorHora() {
        PlazoFijoResponse temprano = pf("a", "ACTIVO", "2026-09-28", "2026-10-28", "2026-09-28T09:00:00-03:00");
        PlazoFijoResponse tarde = pf("b", "ACTIVO", "2026-09-28", "2026-10-28", "2026-09-28T18:30:00-03:00");
        List<Actividad> feed = Actividades.construir(Collections.emptyList(), null, null, Arrays.asList(temprano, tarde), BA);
        assertEquals(Arrays.asList("pf-alta-b", "pf-alta-a"), keys(feed));
    }

    @Test
    public void p13_inicioMuestraLasCuatroMasRecientesConPlazos() {
        TransferenciasRepository.Estado estado = estadoCon(Collections.emptyList());
        List<Actividad> feed = Actividades.construir(estado.lista, null, null, Arrays.asList(vencido(),
                pf("p2", "ACTIVO", "2026-10-01", "2026-10-31", "2026-10-01T10:00:00-03:00"),
                pf("p3", "ACTIVO", "2026-10-02", "2026-11-01", "2026-10-02T10:00:00-03:00"),
                pf("p4", "ACTIVO", "2026-10-03", "2026-11-02", "2026-10-03T10:00:00-03:00")), BA);
        VistaMovimientos v = VistaMovimientos.inicio(estado, feed);
        assertEquals(VistaMovimientos.Modo.LISTA, v.modo);
        assertEquals(5, v.total);
        assertEquals(Arrays.asList("pf-venc-p1", "pf-alta-p4", "pf-alta-p3", "pf-alta-p2"), keys(v.visibles));
    }

    @Test
    public void sinListaDePlazosElFeedSigueIgual() {
        List<TransferenciaResponse> t = transferencias("[{\"id\":\"t1\",\"moneda\":\"PESOS\",\"monto\":1,"
                + "\"estado\":\"COMPLETADA\",\"fecha\":\"2026-10-27T12:00:00Z\",\"direccion\":\"ENVIADA\","
                + "\"contraparteNombre\":\"Bea\",\"esEmisor\":true}]");
        assertEquals(keys(Actividades.construir(t, null, null)), keys(Actividades.construir(t, null, null, null, BA)));
        assertEquals(Collections.singletonList("t-t1"), keys(new FeedCombinado(BA).de(t, null, null, null)));
    }

    // ── P5: "Mis plazos fijos" ────────────────────────────────────────────────

    @Test
    public void p5_separaActivosYVencidosOrdenadosPorCreacion() {
        List<PlazoFijoResponse> lista = Arrays.asList(
                pf("viejo", "VENCIDO", "2026-06-01", "2026-07-01", "2026-06-01T10:00:00-03:00"),
                pf("mismoDiaTemprano", "ACTIVO", "2026-09-28", "2026-10-28", "2026-09-28T09:00:00-03:00"),
                pf("nuevo", "ACTIVO", "2026-09-28", "2026-10-28", "2026-09-28T18:30:00-03:00"),
                pf("medio", "VENCIDO", "2026-08-01", "2026-08-31", "2026-08-01T10:00:00-03:00"));
        VistaPlazosFijos v = VistaPlazosFijos.de(estado(lista), 5);
        assertEquals(VistaPlazosFijos.Modo.LISTA, v.modo);
        assertEquals(Arrays.asList("nuevo", "mismoDiaTemprano"), ids(v.activos));
        assertEquals(Arrays.asList("medio", "viejo"), ids(v.vencidos));
        assertEquals("2/5 activos", v.subtitulo);
        assertNull(v.avisoLimite);

        VistaPlazosFijos.Tarjeta a = v.activos.get(0);
        assertTrue(a.activo);
        assertEquals("Activo", a.badge);
        assertEquals("$ 1.500,00", a.monto);
        assertEquals("30 días · TNA 35.5%", a.plazo);
        assertEquals("+$ 43,77", a.interes);
        assertEquals("Vas a cobrar", a.etiquetaTotal);
        assertEquals("Se acredita el", a.etiquetaFecha);
        VistaPlazosFijos.Tarjeta venc = v.vencidos.get(0);
        assertFalse(venc.activo);
        assertEquals("Acreditado", venc.badge);
        assertEquals("Se acreditaron", venc.etiquetaTotal);
        assertEquals("el total acreditado", "$ 1.543,77", venc.total);
        assertEquals("Se acreditó el", venc.etiquetaFecha);
    }

    @Test
    public void p5_fechasSonDiasSinHoraEnCualquierZona() {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String zona : new String[]{"America/Argentina/Buenos_Aires", "Pacific/Kiritimati", "Pacific/Pago_Pago"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zona));
                VistaPlazosFijos.Tarjeta t = VistaPlazosFijos.de(estado(Collections.singletonList(activo())), null).activos.get(0);
                assertEquals(zona, "27/09/2026", t.constituido);
                assertEquals(zona, "27/10/2026", t.vencimiento);
            }
        } finally {
            TimeZone.setDefault(original);
        }
        // fechaCreacion sí tiene hora (la usa el alta del feed), en hora local
        assertEquals("27/09 14:03", FilaPlazoFijo.de(activo(), Actividad.EventoPlazoFijo.ALTA, BA).fecha);
        assertEquals("27/09 17:03", FilaPlazoFijo.de(activo(), Actividad.EventoPlazoFijo.ALTA, ZoneId.of("UTC")).fecha);
    }

    @Test
    public void p5_topeYEstadosDeCarga() {
        List<PlazoFijoResponse> dos = Arrays.asList(activo(),
                pf("p2", "ACTIVO", "2026-09-28", "2026-10-28", "2026-09-28T09:00:00-03:00"));
        VistaPlazosFijos lleno = VistaPlazosFijos.de(estado(dos), 2);
        assertEquals("2/2 activos", lleno.subtitulo);
        assertEquals("Ya tenés el máximo de 2 plazos fijos activos", lleno.avisoLimite);
        assertEquals("sin máximo todavía", "2 activos", VistaPlazosFijos.de(estado(dos), null).subtitulo);
        assertNull(VistaPlazosFijos.de(estado(dos), null).avisoLimite);

        assertEquals(VistaPlazosFijos.Modo.VACIO, VistaPlazosFijos.de(estado(Collections.emptyList()), 5).modo);
        assertEquals(VistaPlazosFijos.Modo.CARGANDO, VistaPlazosFijos.de(null, 5).modo);
        assertEquals(VistaPlazosFijos.Modo.CARGANDO, VistaPlazosFijos.de(estado(null), 5).modo);
        assertEquals(Integer.valueOf(2), PlazosFijosRepository.activos(dos));
        assertNull(PlazosFijosRepository.activos(null));
    }

    // ── P7 (simulado): el backend vence uno ────────────────────────────────────

    @Test
    public void p7_detectaElVencimientoYRefrescaSaldosYCampanaUnaVez() {
        AtomicInteger avisos = new AtomicInteger();
        DetectorVencimientos d = new DetectorVencimientos(avisos::incrementAndGet);
        List<PlazoFijoResponse> antes = Collections.singletonList(activo());
        d.onCambio(estado(null));
        d.onCambio(estado(antes));
        assertEquals("primera carga: nada", 0, avisos.get());
        d.onCambio(estado(antes));
        assertEquals(0, avisos.get());
        List<PlazoFijoResponse> despues = Collections.singletonList(vencido());
        d.onCambio(estado(despues));
        assertEquals("ACTIVO -> VENCIDO", 1, avisos.get());
        d.onCambio(estado(despues));
        d.onCambio(estado(Collections.singletonList(vencido())));
        assertEquals("ya vencido: no vuelve a avisar", 1, avisos.get());
        // Uno que aparece ya vencido (nunca lo vimos activo) no es un vencimiento "en vivo"
        d.onCambio(estado(Arrays.asList(vencido(), pf("x", "VENCIDO", "2026-01-01", "2026-01-31", "2026-01-01T10:00:00-03:00"))));
        assertEquals(1, avisos.get());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static List<String> ids(List<VistaPlazosFijos.Tarjeta> tarjetas) {
        List<String> r = new ArrayList<>();
        for (VistaPlazosFijos.Tarjeta t : tarjetas) r.add(t.id);
        return r;
    }

    /** ListaRemota.Estado tiene constructor de paquete: se arma por reflexión (solo tests). */
    @SuppressWarnings("unchecked")
    private static ListaRemota.Estado<PlazoFijoResponse> estado(List<PlazoFijoResponse> lista) {
        try {
            Constructor<?> c = ListaRemota.Estado.class.getDeclaredConstructor(List.class, String.class, boolean.class, boolean.class);
            c.setAccessible(true);
            return (ListaRemota.Estado<PlazoFijoResponse>) c.newInstance(lista, null, false, false);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static TransferenciasRepository.Estado estadoCon(List<TransferenciaResponse> lista) {
        try {
            for (Constructor<?> c : TransferenciasRepository.Estado.class.getDeclaredConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length > 0 && p[0] == List.class) {
                    c.setAccessible(true);
                    Object[] args = new Object[p.length];
                    args[0] = lista;
                    for (int i = 1; i < p.length; i++) args[i] = p[i] == boolean.class ? false : null;
                    return (TransferenciasRepository.Estado) c.newInstance(args);
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
        throw new AssertionError("sin constructor de Estado");
    }
}
