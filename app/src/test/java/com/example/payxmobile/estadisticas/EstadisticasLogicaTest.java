package com.example.payxmobile.estadisticas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.payxmobile.model.EstadisticaGastosResponse;
import com.google.gson.Gson;

import org.junit.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

/** Atajos (E1), mapeo (E2), comparación (E3), categorías (E4) y serie diaria (E5). Sin red. */
public class EstadisticasLogicaTest {

    /** Forma real de la respuesta para dias=7 (5 categorías siempre, ordenadas por monto). */
    static final String SIETE_DIAS = "{\"totalGastado\":15000.00,\"desde\":\"2026-09-22\",\"hasta\":\"2026-09-28\","
            + "\"categorias\":["
            + "{\"codigo\":\"TRANSFERENCIAS\",\"etiqueta\":\"Transferencias enviadas\",\"monto\":6825.00,\"porcentaje\":45.5},"
            + "{\"codigo\":\"PLAZO_FIJO\",\"etiqueta\":\"Plazos fijos\",\"monto\":5000.00,\"porcentaje\":33.3},"
            + "{\"codigo\":\"SERVICIOS\",\"etiqueta\":\"Pago de servicios\",\"monto\":3175.00,\"porcentaje\":21.2},"
            + "{\"codigo\":\"DOLARES\",\"etiqueta\":\"Compra de dólares\",\"monto\":0,\"porcentaje\":0},"
            + "{\"codigo\":\"CRIPTO\",\"etiqueta\":\"Compra de criptomonedas\",\"monto\":0,\"porcentaje\":0}],"
            + "\"porDia\":[{\"fecha\":\"2026-09-22\",\"monto\":0},{\"fecha\":\"2026-09-23\",\"monto\":6825.00},"
            + "{\"fecha\":\"2026-09-24\",\"monto\":0},{\"fecha\":\"2026-09-25\",\"monto\":0},{\"fecha\":\"2026-09-26\",\"monto\":5000.00},"
            + "{\"fecha\":\"2026-09-27\",\"monto\":0},{\"fecha\":\"2026-09-28\",\"monto\":3175.00}],"
            + "\"totalPeriodoAnterior\":10000.00,\"cantidadOperaciones\":3}";

    /** "Todo el tiempo" con más de 365 días de historial: desde y anterior null, serie vacía, hasta = hoy. */
    static final String TODO = "{\"totalGastado\":250000.00,\"desde\":null,\"hasta\":\"2026-09-28\","
            + "\"categorias\":[{\"codigo\":\"TRANSFERENCIAS\",\"etiqueta\":\"Transferencias enviadas\",\"monto\":250000.00,\"porcentaje\":100.0},"
            + "{\"codigo\":\"DOLARES\",\"etiqueta\":\"Compra de dólares\",\"monto\":0,\"porcentaje\":0}],"
            + "\"porDia\":[],\"totalPeriodoAnterior\":null,\"cantidadOperaciones\":120}";

    /** Sin gastos en 30 días: las 5 categorías en 0 y la serie con todos los días en 0. */
    static String sinGastos() {
        StringBuilder dias = new StringBuilder();
        for (int i = 0; i < 30; i++) {
            if (i > 0) dias.append(',');
            dias.append("{\"fecha\":\"").append(LocalDate.of(2026, 8, 30).plusDays(i)).append("\",\"monto\":0}");
        }
        return "{\"totalGastado\":0,\"desde\":\"2026-08-30\",\"hasta\":\"2026-09-28\",\"categorias\":["
                + "{\"codigo\":\"TRANSFERENCIAS\",\"etiqueta\":\"Transferencias enviadas\",\"monto\":0,\"porcentaje\":0},"
                + "{\"codigo\":\"DOLARES\",\"etiqueta\":\"Compra de dólares\",\"monto\":0,\"porcentaje\":0},"
                + "{\"codigo\":\"CRIPTO\",\"etiqueta\":\"Compra de criptomonedas\",\"monto\":0,\"porcentaje\":0},"
                + "{\"codigo\":\"PLAZO_FIJO\",\"etiqueta\":\"Plazos fijos\",\"monto\":0,\"porcentaje\":0},"
                + "{\"codigo\":\"SERVICIOS\",\"etiqueta\":\"Pago de servicios\",\"monto\":0,\"porcentaje\":0}],"
                + "\"porDia\":[" + dias + "],\"totalPeriodoAnterior\":0,\"cantidadOperaciones\":0}";
    }

    static EstadisticaGastosResponse r(String json) {
        return new Gson().fromJson(json, EstadisticaGastosResponse.class);
    }

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    // ── E1: dias de cada atajo ─────────────────────────────────────────────────

    @Test
    public void e1_diasDeCadaAtajo() {
        LocalDate hoy = LocalDate.of(2026, 9, 28);
        assertEquals(7, PeriodoEstadistica.SIETE_DIAS.dias(hoy));
        assertEquals(30, PeriodoEstadistica.TREINTA_DIAS.dias(hoy));
        assertEquals("del 1 al 28 inclusive", 28, PeriodoEstadistica.ESTE_MES.dias(hoy));
        assertEquals("el 1 del mes: solo hoy", 1, PeriodoEstadistica.ESTE_MES.dias(LocalDate.of(2026, 10, 1)));
        assertEquals(31, PeriodoEstadistica.ESTE_MES.dias(LocalDate.of(2026, 12, 31)));
        assertEquals(90, PeriodoEstadistica.NOVENTA_DIAS.dias(hoy));
        assertEquals("todo = 0 EXPLÍCITO (sin parámetro el backend usa 30)", 0, PeriodoEstadistica.TODO.dias(hoy));
        assertEquals(PeriodoEstadistica.TREINTA_DIAS, PeriodoEstadistica.POR_DEFECTO);
    }

    // ── E2: mapeo, incluidos los null de "todo el tiempo" ──────────────────────

    @Test
    public void e2_mapeoCompleto() {
        EstadisticaGastosResponse s = r(SIETE_DIAS);
        assertEquals(bd("15000.00"), s.getTotalGastado());
        assertEquals("2026-09-22", s.getDesde());
        assertEquals("2026-09-28", s.getHasta());
        assertEquals(5, s.getCategorias().size());
        assertEquals("TRANSFERENCIAS", s.getCategorias().get(0).getCodigo());
        assertEquals("Transferencias enviadas", s.getCategorias().get(0).getEtiqueta());
        assertEquals(bd("45.5"), s.getCategorias().get(0).getPorcentaje());
        assertEquals(7, s.getPorDia().size());
        assertEquals("2026-09-23", s.getPorDia().get(1).getFecha());
        assertEquals(bd("10000.00"), s.getTotalPeriodoAnterior());
        assertEquals(3, s.getCantidadOperaciones());

        EstadisticaGastosResponse t = r(TODO);
        assertNull(t.getDesde());
        assertEquals("hasta NO es null en todo el tiempo (el backend manda hoy)", "2026-09-28", t.getHasta());
        assertNull(t.getTotalPeriodoAnterior());
        assertTrue(t.getPorDia().isEmpty());

        VistaEstadisticas v = VistaEstadisticas.de(t);
        assertEquals("Desde siempre, hasta el 28/09/2026", v.rango);
        assertEquals("Del 22/09/2026 al 28/09/2026", VistaEstadisticas.de(s).rango);
    }

    // ── E3: comparación con el período anterior ────────────────────────────────

    @Test
    public void e3_comparacion() {
        ComparacionPeriodo sube = ComparacionPeriodo.de(bd("15000"), bd("10000"));
        assertEquals(ComparacionPeriodo.Tipo.SUBE, sube.tipo);
        assertEquals(bd("50.0"), sube.porcentaje);
        assertEquals("Gastaste $ 5.000,00 más que en el período anterior (+50%)", sube.texto);

        ComparacionPeriodo baja = ComparacionPeriodo.de(bd("7500"), bd("10000"));
        assertEquals(ComparacionPeriodo.Tipo.BAJA, baja.tipo);
        assertEquals("Gastaste $ 2.500,00 menos que en el período anterior (-25%)", baja.texto);

        // 1/3 más: 33,3 % (BigDecimal, HALF_UP a 1 decimal)
        assertEquals(bd("33.3"), ComparacionPeriodo.de(bd("4000"), bd("3000")).porcentaje);
        assertEquals("12,5%", ComparacionPeriodo.porcentajeTexto(bd("12.5")));

        // Sin dividir por cero
        ComparacionPeriodo nuevo = ComparacionPeriodo.de(bd("500"), BigDecimal.ZERO);
        assertEquals(ComparacionPeriodo.Tipo.SIN_ANTERIOR, nuevo.tipo);
        assertEquals("No gastaste nada en el período anterior", nuevo.texto);
        assertNull(nuevo.porcentaje);
        assertEquals(ComparacionPeriodo.Tipo.IGUAL, ComparacionPeriodo.de(BigDecimal.ZERO, bd("0.00")).tipo);
        assertEquals(ComparacionPeriodo.Tipo.IGUAL, ComparacionPeriodo.de(bd("1000"), bd("1000.00")).tipo);
        assertEquals("una diferencia que redondea a 0,0 %", ComparacionPeriodo.Tipo.IGUAL,
                ComparacionPeriodo.de(bd("100000.01"), bd("100000")).tipo);

        assertNull("todo el tiempo: oculta", ComparacionPeriodo.de(bd("250000"), null));
        assertNull(VistaEstadisticas.de(r(TODO)).comparacion);
        assertEquals(ComparacionPeriodo.Tipo.SUBE, VistaEstadisticas.de(r(SIETE_DIAS)).comparacion.tipo);
    }

    // ── E4: categorías ──────────────────────────────────────────────────────────

    @Test
    public void e4_categoriasConLosPorcentajesDelBackend() {
        VistaEstadisticas v = VistaEstadisticas.de(r(SIETE_DIAS));
        assertFalse(v.sinGastos);
        assertEquals("$ 15.000,00", v.total);
        List<String> etiquetas = new ArrayList<>();
        for (VistaEstadisticas.Categoria c : v.categorias) etiquetas.add(c.etiqueta);
        assertEquals("en el orden del backend (mayor gasto primero)", Arrays.asList("Transferencias enviadas",
                "Plazos fijos", "Pago de servicios", "Compra de dólares", "Compra de criptomonedas"), etiquetas);
        VistaEstadisticas.Categoria t = v.categorias.get(0);
        assertEquals("$ 6.825,00", t.monto);
        assertEquals("el porcentaje del backend, sin recalcular", "45,5%", t.porcentaje);
        assertEquals("33,3%", v.categorias.get(1).porcentaje);
        assertTrue("las de $0 quedan atenuadas", v.categorias.get(3).sinGasto);
        assertEquals("0%", v.categorias.get(3).porcentaje);
        assertEquals("Transferencias enviadas", v.principal().etiqueta);
        assertEquals("3 movimientos en este período", v.operaciones);
    }

    @Test
    public void e4_sinGastosEsMensajeNoGraficoRoto() {
        VistaEstadisticas v = VistaEstadisticas.de(r(sinGastos()));
        assertTrue("el backend manda las 5 categorías en 0: se detecta por el total", v.sinGastos);
        assertEquals(5, v.categorias.size());
        assertNull(v.principal());
        assertEquals("$ 0,00", v.total);
        assertEquals("0 movimientos en este período", v.operaciones);
        assertEquals("No tuviste gastos en este período", VistaEstadisticas.MSG_SIN_GASTOS);
        assertEquals("1 movimiento en este período", VistaEstadisticas.de(r(SIETE_DIAS.replace(
                "\"cantidadOperaciones\":3", "\"cantidadOperaciones\":1"))).operaciones);
    }

    // ── E5: serie diaria ─────────────────────────────────────────────────────────

    @Test
    public void e5_serieConDatosCoincideEnFechaYMonto() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Pago_Pago")); // días, no instantes: no se corren
            VistaEstadisticas v = VistaEstadisticas.de(r(SIETE_DIAS));
            assertEquals(7, v.serie.size());
            assertEquals(LocalDate.of(2026, 9, 22), v.serie.get(0).fecha);
            assertEquals(LocalDate.of(2026, 9, 23), v.serie.get(1).fecha);
            assertEquals(bd("6825.00"), v.serie.get(1).monto);
            assertEquals(bd("3175.00"), v.serie.get(6).monto);
            assertEquals("23/09 · $ 6.825,00", v.diaPico);
            assertEquals("15.000 / 7 días", "$ 2.142,86", v.promedioDiario);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void e5_serieVaciaSeOcultaSinRomperElResto() {
        VistaEstadisticas todo = VistaEstadisticas.de(r(TODO));
        assertTrue("> 365 días: sin gráfico de tendencia", todo.serie.isEmpty());
        assertEquals("el desglose sigue", 2, todo.categorias.size());
        assertEquals("$ 250.000,00", todo.total);
        assertNull("sin serie en 'todo' no hay con qué promediar", todo.promedioDiario);
        assertNull(todo.diaPico);

        VistaEstadisticas cero = VistaEstadisticas.de(r(sinGastos()));
        assertTrue("todos los días en 0: tampoco se dibuja", cero.serie.isEmpty());
        assertEquals("$ 0,00", cero.promedioDiario);
    }
}
