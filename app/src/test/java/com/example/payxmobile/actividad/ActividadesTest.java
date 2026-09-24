package com.example.payxmobile.actividad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.transferencias.TransferenciasRepository;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** M1, M4-M9, M11, M13: lógica pura del feed con reloj y zona fijos. */
public class ActividadesTest {

    private static final ZoneId BA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final ZoneId TOKIO = ZoneId.of("Asia/Tokyo");
    private static final Gson GSON = new Gson();

    static String t(String id, String moneda, String estado, String direccion, String fecha) {
        return "{\"id\":\"" + id + "\",\"moneda\":\"" + moneda + "\",\"monto\":1,\"estado\":\"" + estado
                + "\",\"fecha\":\"" + fecha + "\",\"direccion\":\"" + direccion
                + "\",\"contraparteNombre\":\"Ana\",\"contraparteAlias\":\"ana.payx\",\"esEmisor\":"
                + direccion.equals("ENVIADA") + "}";
    }

    static List<TransferenciaResponse> lista(String... jsons) {
        return GSON.fromJson("[" + String.join(",", jsons) + "]", new TypeToken<List<TransferenciaResponse>>() {}.getType());
    }

    static Clock reloj(String fechaHoraLocal, ZoneId zona) {
        return Clock.fixed(ZonedDateTime.of(java.time.LocalDateTime.parse(fechaHoraLocal), zona).toInstant(), zona);
    }

    // ── M1 ────────────────────────────────────────────────────────────────────

    @Test
    public void m1_construirIncluyeTodoYOrdenaDescendenteYEstable() {
        String[] monedas = {"PESOS", "USD", "BTC", "ETH", "SOL", "USDT", "BNB", "XRP"};
        String[] estados = {"PENDIENTE", "COMPLETADA", "CANCELADA"};
        List<String> jsons = new ArrayList<>();
        int i = 0;
        for (String m : monedas) {
            for (String e : estados) {
                String dir = i % 2 == 0 ? "ENVIADA" : "RECIBIDA";
                jsons.add(t("id" + i, m, e, dir, String.format("2026-09-%02dT10:00:00-03:00", 1 + (i % 20))));
                i++;
            }
        }
        // Dos con el MISMO instante escrito distinto: el orden de llegada se respeta
        jsons.add(t("empate-1", "PESOS", "COMPLETADA", "ENVIADA", "2026-09-30T12:00:00-03:00"));
        jsons.add(t("empate-2", "PESOS", "COMPLETADA", "RECIBIDA", "2026-09-30T15:00:00Z"));
        List<Actividad> items = Actividades.construir(lista(jsons.toArray(new String[0])));

        assertEquals(26, items.size());
        assertEquals("t-empate-1", items.get(0).key);
        assertEquals("t-empate-2", items.get(1).key);
        for (int k = 1; k < items.size(); k++) {
            assertFalse("orden descendente", items.get(k).instante.isAfter(items.get(k - 1).instante));
        }
        Set<String> keys = new HashSet<>();
        for (Actividad a : items) {
            assertTrue("key única", keys.add(a.key));
            assertTrue(a.key.startsWith("t-"));
            assertEquals(Actividad.Tipo.TRANSFERENCIA, a.tipo);
        }
        Set<String> vistas = new HashSet<>();
        for (Actividad a : items) vistas.add(a.transferencia.getMoneda() + a.transferencia.getEstado());
        assertEquals("8 monedas x 3 estados", 24, vistas.size());
    }

    @Test
    public void m1_ordenaAunqueElBackendLasMandeDesordenadas() {
        List<Actividad> items = Actividades.construir(lista(
                t("vieja", "PESOS", "COMPLETADA", "ENVIADA", "2026-09-01T10:00:00Z"),
                t("nueva", "PESOS", "COMPLETADA", "ENVIADA", "2026-09-24T10:00:00Z"),
                t("medio", "PESOS", "COMPLETADA", "ENVIADA", "2026-09-10T10:00:00Z")));
        assertEquals("t-nueva", items.get(0).key);
        assertEquals("t-medio", items.get(1).key);
        assertEquals("t-vieja", items.get(2).key);
    }

    // ── M5 ────────────────────────────────────────────────────────────────────

    @Test
    public void m5_atajosConRelojFijo() {
        String[][] casos = {
                // hoy, 7 días desde, 30 días desde, mes desde
                {"2026-09-24", "2026-09-18", "2026-08-26", "2026-09-01"},
                {"2026-10-02", "2026-09-26", "2026-09-03", "2026-10-01"},
                {"2026-03-05", "2026-02-27", "2026-02-04", "2026-03-01"},
                {"2028-03-01", "2028-02-24", "2028-02-01", "2028-03-01"}, // bisiesto
                {"2027-01-03", "2026-12-28", "2026-12-05", "2027-01-01"}, // cruce de año
        };
        for (String[] c : casos) {
            LocalDate hoy = LocalDate.parse(c[0]);
            List<Actividades.Atajo> a = Actividades.atajos(reloj(c[0] + "T12:00:00", BA), BA);
            assertEquals("todo", a.get(0).id);
            assertNull(a.get(0).desde);
            assertNull(a.get(0).hasta);
            assertEquals(c[0] + " hoy", hoy, a.get(1).desde);
            assertEquals(hoy, a.get(1).hasta);
            assertEquals(c[0] + " 7 días", LocalDate.parse(c[1]), a.get(2).desde);
            assertEquals(hoy, a.get(2).hasta);
            assertEquals(c[0] + " 30 días", LocalDate.parse(c[2]), a.get(3).desde);
            assertEquals(hoy, a.get(3).hasta);
            assertEquals(c[0] + " mes", LocalDate.parse(c[3]), a.get(4).desde);
            assertEquals(hoy, a.get(4).hasta);
        }
    }

    @Test
    public void m5_hoyEsElDiaLocalNoElDeUtc() {
        // 24/09 a las 23:30 en Argentina = 25/09 02:30 UTC. "Hoy" tiene que ser el 24.
        Clock r = reloj("2026-09-24T23:30:00", BA);
        assertEquals(LocalDate.parse("2026-09-24"), Actividades.atajos(r, BA).get(1).desde);
        assertEquals(LocalDate.parse("2026-09-25"), Actividades.atajos(r, UTC).get(1).desde);
    }

    @Test
    public void m5_atajoActivoSoloSiCoincideExacto() {
        List<Actividades.Atajo> a = Actividades.atajos(reloj("2026-09-24T12:00:00", BA), BA);
        assertEquals("todo", Actividades.atajoActivo(a, null, null).id);
        assertEquals("7", Actividades.atajoActivo(a, LocalDate.parse("2026-09-18"), LocalDate.parse("2026-09-24")).id);
        assertEquals("mes", Actividades.atajoActivo(a, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-24")).id);
        assertNull(Actividades.atajoActivo(a, LocalDate.parse("2026-09-18"), LocalDate.parse("2026-09-23")));
        assertNull(Actividades.atajoActivo(a, LocalDate.parse("2026-09-18"), null));
    }

    // ── M6 ────────────────────────────────────────────────────────────────────

    @Test
    public void m6_diaLocalSegunLaZona() {
        String f = "2026-09-24T23:30:00-03:00";
        assertEquals(LocalDate.parse("2026-09-24"), Actividades.diaLocalDe(f, BA));
        assertEquals(LocalDate.parse("2026-09-25"), Actividades.diaLocalDe(f, UTC));
        assertEquals(LocalDate.parse("2026-09-25"), Actividades.diaLocalDe(f, TOKIO));
    }

    @Test
    public void m6_extremosIncluidosAlSegundo() {
        List<Actividad> items = Actividades.construir(lista(
                t("antes", "PESOS", "COMPLETADA", "ENVIADA", "2026-09-19T23:59:59-03:00"),
                t("inicio", "PESOS", "COMPLETADA", "ENVIADA", "2026-09-20T00:00:00-03:00"),
                t("finDesde", "PESOS", "COMPLETADA", "ENVIADA", "2026-09-20T23:59:59-03:00"),
                t("inicioHasta", "PESOS", "COMPLETADA", "ENVIADA", "2026-09-22T00:00:00-03:00"),
                t("fin", "PESOS", "COMPLETADA", "ENVIADA", "2026-09-22T23:59:59-03:00"),
                t("despues", "PESOS", "COMPLETADA", "ENVIADA", "2026-09-23T00:00:00-03:00")));
        List<Actividad> r = Actividades.filtrarPorFecha(items, LocalDate.parse("2026-09-20"), LocalDate.parse("2026-09-22"), BA);
        Set<String> keys = new HashSet<>();
        for (Actividad a : r) keys.add(a.key);
        assertEquals(Set.of("t-inicio", "t-finDesde", "t-inicioHasta", "t-fin"), keys);
    }

    // ── M4 / M7 / M8 / M9: la vista ───────────────────────────────────────────

    static TransferenciasRepository.Estado estado(List<TransferenciaResponse> l, String error) {
        try {
            Constructor<TransferenciasRepository.Estado> c = TransferenciasRepository.Estado.class.getDeclaredConstructor(
                    List.class, String.class, boolean.class, boolean.class);
            c.setAccessible(true);
            return c.newInstance(l, error, false, false);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    static List<TransferenciaResponse> muchas(int n, String desde) {
        List<String> j = new ArrayList<>();
        Instant base = Instant.parse(desde);
        for (int i = 0; i < n; i++) {
            j.add(t("n" + i, i % 3 == 0 ? "BTC" : "PESOS", "COMPLETADA", i % 2 == 0 ? "ENVIADA" : "RECIBIDA",
                    base.minusSeconds(3600L * i).toString()));
        }
        return lista(j.toArray(new String[0]));
    }

    @Test
    public void m3_m4_estadosSinMentir() {
        assertEquals(VistaMovimientos.Modo.CARGANDO, VistaMovimientos.de(estado(null, null), null, null, 1, BA).modo);
        VistaMovimientos error = VistaMovimientos.de(estado(null, "Sin conexión"), null, null, 1, BA);
        assertEquals(VistaMovimientos.Modo.ERROR, error.modo);
        assertEquals("Sin conexión", error.error);
        VistaMovimientos vacio = VistaMovimientos.de(estado(new ArrayList<>(), null), null, null, 1, BA);
        assertEquals(VistaMovimientos.Modo.VACIO, vacio.modo);
        assertFalse("sin filtros cuando no hay nada", vacio.mostrarFiltros);
        // Inicio igual
        assertEquals(VistaMovimientos.Modo.ERROR, VistaMovimientos.inicio(estado(null, "x")).modo);
        assertEquals(VistaMovimientos.Modo.CARGANDO, VistaMovimientos.inicio(estado(null, null)).modo);
    }

    @Test
    public void m2_inicioMuestraLas4MasRecientes() {
        VistaMovimientos v = VistaMovimientos.inicio(estado(muchas(10, "2026-09-24T12:00:00Z"), null));
        assertEquals(4, v.visibles.size());
        assertEquals("t-n0", v.visibles.get(0).key);
        assertEquals("t-n3", v.visibles.get(3).key);
        assertEquals(2, VistaMovimientos.inicio(estado(muchas(2, "2026-09-24T12:00:00Z"), null)).visibles.size());
    }

    @Test
    public void m4_avisoDelTopeCon2000YNoCon1999() {
        assertTrue(VistaMovimientos.de(estado(muchas(2000, "2026-09-24T12:00:00Z"), null), null, null, 1, BA).avisoTope);
        assertFalse(VistaMovimientos.de(estado(muchas(1999, "2026-09-24T12:00:00Z"), null), null, null, 1, BA).avisoTope);
    }

    @Test
    public void m7_rangoInvalidoSoloMuestraElError() {
        VistaMovimientos v = VistaMovimientos.de(estado(muchas(5, "2026-09-24T12:00:00Z"), null),
                LocalDate.parse("2026-09-24"), LocalDate.parse("2026-09-20"), 1, BA);
        assertEquals(VistaMovimientos.MSG_RANGO_INVALIDO, v.errorRango);
        assertTrue(v.visibles.isEmpty());
        assertNull(v.resumen);
        assertNull(v.sinResultados);
        assertNull(v.mostrarMas);
        assertTrue(v.mostrarFiltros);
    }

    @Test
    public void m8_resumenYSinResultados() {
        List<TransferenciaResponse> l = muchas(48, "2026-09-24T12:00:00Z"); // una por hora, 2 días
        VistaMovimientos sinFiltro = VistaMovimientos.de(estado(l, null), null, null, 1, BA);
        assertNull("sin filtro no hay resumen", sinFiltro.resumen);
        assertFalse(sinFiltro.hayFiltro);

        LocalDate d = LocalDate.parse("2026-09-23");
        VistaMovimientos conFiltro = VistaMovimientos.de(estado(l, null), d, d, 1, BA);
        assertTrue(conFiltro.hayFiltro);
        assertEquals(conFiltro.visibles.size() + " de 48 movimientos", conFiltro.resumen);

        VistaMovimientos uno = VistaMovimientos.de(estado(muchas(1, "2026-09-24T12:00:00Z"), null), d, d, 1, BA);
        assertEquals("0 de 1 movimiento", uno.resumen);
        assertEquals(VistaMovimientos.MSG_SIN_RESULTADOS, uno.sinResultados);
        assertEquals("No hay movimientos en ese rango de fechas.", VistaMovimientos.MSG_SIN_RESULTADOS);
    }

    @Test
    public void m9_paginacionDeA100ConFormatoEsAr() {
        List<TransferenciaResponse> l = muchas(2000, "2026-09-24T12:00:00Z");
        VistaMovimientos v1 = VistaMovimientos.de(estado(l, null), null, null, 1, BA);
        assertEquals(100, v1.visibles.size());
        assertEquals("Mostrar más (1.900 restantes)", v1.mostrarMas);
        VistaMovimientos v2 = VistaMovimientos.de(estado(l, null), null, null, 2, BA);
        assertEquals(200, v2.visibles.size());
        assertEquals("Mostrar más (1.800 restantes)", v2.mostrarMas);
        VistaMovimientos todo = VistaMovimientos.de(estado(l, null), null, null, 20, BA);
        assertEquals(2000, todo.visibles.size());
        assertNull(todo.mostrarMas);
        assertEquals("Mostrar más (50 restantes)",
                VistaMovimientos.de(estado(muchas(150, "2026-09-24T12:00:00Z"), null), null, null, 1, BA).mostrarMas);
    }

    // ── M11 ───────────────────────────────────────────────────────────────────

    @Test
    public void m11_filtrarDosMilEsInstantaneo() {
        List<Actividad> items = Actividades.construir(muchas(2000, "2026-09-24T12:00:00Z"));
        LocalDate desde = LocalDate.parse("2026-09-01"), hasta = LocalDate.parse("2026-09-20");
        for (int i = 0; i < 20; i++) Actividades.filtrarPorFecha(items, desde, hasta, BA); // calentar JIT
        long inicio = System.nanoTime();
        int vueltas = 50;
        for (int i = 0; i < vueltas; i++) Actividades.filtrarPorFecha(items, desde, hasta, BA);
        double ms = (System.nanoTime() - inicio) / 1e6 / vueltas;
        System.out.println("M11 filtrarPorFecha(2000): " + String.format("%.2f", ms) + " ms");
        assertTrue("tarda " + ms + " ms", ms < 20);
        long t0 = System.nanoTime();
        VistaMovimientos.de(estado(muchas(2000, "2026-09-24T12:00:00Z"), null), desde, hasta, 1, BA);
        System.out.println("M11 vista completa (parseo + orden + filtro) de 2000: " + (System.nanoTime() - t0) / 1_000_000 + " ms");
    }

    // ── M13 ───────────────────────────────────────────────────────────────────

    @Test
    public void m13_tiposExtensibles() {
        List<Actividad> items = new ArrayList<>(Actividades.construir(muchas(3, "2026-09-24T12:00:00Z")));
        Instant i = Instant.parse("2026-09-20T10:00:00Z");
        items.add(Actividad.deOtroTipo("pf-alta-1", "2026-09-20T10:00:00Z", i, Actividad.Tipo.PLAZO_FIJO));
        items.add(Actividad.deOtroTipo("cd-1", "2026-09-20T10:00:00Z", i, Actividad.Tipo.DOLARES));
        items.add(Actividad.deOtroTipo("cc-1", "2026-09-20T10:00:00Z", i, Actividad.Tipo.CRIPTO));
        items.add(Actividad.deOtroTipo("cc-2", "2026-09-20T10:00:00Z", i, Actividad.Tipo.CRIPTO));

        assertEquals(Actividad.Tipo.PLAZO_FIJO, Actividades.tipoDe(items.get(3)));
        assertEquals(3, Actividades.filtrarPorTipo(items, Actividad.Tipo.TRANSFERENCIA).size());
        assertEquals(2, Actividades.filtrarPorTipo(items, Actividad.Tipo.CRIPTO).size());
        assertEquals(7, Actividades.filtrarPorTipo(items, null).size());
        Map<Actividad.Tipo, Integer> c = Actividades.contarPorTipo(items);
        assertEquals(Integer.valueOf(3), c.get(Actividad.Tipo.TRANSFERENCIA));
        assertEquals(Integer.valueOf(1), c.get(Actividad.Tipo.PLAZO_FIJO));
        assertEquals(Integer.valueOf(1), c.get(Actividad.Tipo.DOLARES));
        assertEquals(Integer.valueOf(2), c.get(Actividad.Tipo.CRIPTO));
        assertEquals("transferencias", Actividad.Tipo.TRANSFERENCIA.id);
        assertEquals(4, Actividades.TIPOS_ACTIVIDAD.size());
    }
}
