package com.example.payxmobile.estadisticas;

import com.example.payxmobile.model.CategoriaGastoResponse;
import com.example.payxmobile.model.EstadisticaGastosResponse;
import com.example.payxmobile.model.PuntoGastoDiarioResponse;
import com.example.payxmobile.utils.MontoFormatter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Todo lo que muestra "Estadísticas de gastos" sale de UNA respuesta (nunca se mezclan dos). Los
 * montos y porcentajes son los del backend: el cliente no los recalcula (salvo la comparación con
 * el período anterior y los datos derivados que también arma la web). Sin Android.
 */
public final class VistaEstadisticas {

    public static final String MSG_SIN_GASTOS = "No tuviste gastos en este período";

    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DIA_CORTO = DateTimeFormatter.ofPattern("dd/MM");

    /** Una fila de la leyenda (todas las categorías que manda el backend, en su orden). */
    public static final class Categoria {
        public final String codigo;
        public final String etiqueta;
        public final String monto;
        /** El porcentaje DEL BACKEND, formateado es-AR ("45,5%"). */
        public final String porcentaje;
        public final BigDecimal porcentajeValor;
        public final BigDecimal montoValor;
        public final boolean sinGasto;

        Categoria(CategoriaGastoResponse c) {
            codigo = c.getCodigo();
            etiqueta = c.getEtiqueta();
            montoValor = c.getMonto() != null ? c.getMonto() : BigDecimal.ZERO;
            monto = "$ " + MontoFormatter.fiat(montoValor);
            porcentajeValor = c.getPorcentaje() != null ? c.getPorcentaje() : BigDecimal.ZERO;
            porcentaje = ComparacionPeriodo.porcentajeTexto(porcentajeValor);
            sinGasto = montoValor.signum() <= 0;
        }
    }

    /** Un punto de la serie diaria (fecha y monto tal cual los mandó el backend). */
    public static final class Punto {
        public final LocalDate fecha;
        public final BigDecimal monto;

        Punto(LocalDate fecha, BigDecimal monto) {
            this.fecha = fecha;
            this.monto = monto;
        }
    }

    /** "Del 22/09/2026 al 28/09/2026" / "Desde siempre, hasta el 28/09/2026". */
    public final String rango;
    public final String total;
    public final BigDecimal totalValor;
    /** null = no se muestra (período "todo el tiempo"). */
    public final ComparacionPeriodo comparacion;
    /** "3 movimientos en este período". */
    public final String operaciones;
    public final boolean sinGastos;
    public final List<Categoria> categorias;
    /** Serie para el gráfico de tendencia; vacía = el gráfico se oculta (no es un error). */
    public final List<Punto> serie;
    /** "$ 1.234,50" por día del período, o null si no se puede calcular (todo el tiempo sin serie). */
    public final String promedioDiario;
    /** Día de mayor gasto ("28/09 · $ 5.000,00") o null si no hubo gastos en la serie. */
    public final String diaPico;

    private VistaEstadisticas(String rango, String total, BigDecimal totalValor, ComparacionPeriodo comparacion,
                              String operaciones, boolean sinGastos, List<Categoria> categorias, List<Punto> serie,
                              String promedioDiario, String diaPico) {
        this.rango = rango;
        this.total = total;
        this.totalValor = totalValor;
        this.comparacion = comparacion;
        this.operaciones = operaciones;
        this.sinGastos = sinGastos;
        this.categorias = categorias;
        this.serie = serie;
        this.promedioDiario = promedioDiario;
        this.diaPico = diaPico;
    }

    public static VistaEstadisticas de(EstadisticaGastosResponse r) {
        BigDecimal total = r.getTotalGastado();
        LocalDate desde = dia(r.getDesde()), hasta = dia(r.getHasta());
        String rango = desde != null && hasta != null ? "Del " + desde.format(DIA) + " al " + hasta.format(DIA)
                : hasta != null ? "Desde siempre, hasta el " + hasta.format(DIA) : "Desde siempre";

        List<Categoria> categorias = new ArrayList<>();
        if (r.getCategorias() != null) for (CategoriaGastoResponse c : r.getCategorias()) categorias.add(new Categoria(c));

        // Sin ningún gasto la serie trae todos los días en 0: tampoco se dibuja (no hay tendencia que ver)
        List<Punto> serie = new ArrayList<>();
        boolean algunGasto = false;
        Punto pico = null;
        if (r.getPorDia() != null) {
            for (PuntoGastoDiarioResponse p : r.getPorDia()) {
                LocalDate f = dia(p.getFecha());
                BigDecimal m = p.getMonto() != null ? p.getMonto() : BigDecimal.ZERO;
                if (f == null) continue;
                Punto punto = new Punto(f, m);
                serie.add(punto);
                if (m.signum() > 0) {
                    algunGasto = true;
                    if (pico == null || m.compareTo(pico.monto) > 0) pico = punto;
                }
            }
        }
        if (!algunGasto) serie.clear();

        // Días del período: desde..hasta; en "todo" los que cubre la serie (desde el primer gasto)
        long dias = desde != null && hasta != null ? ChronoUnit.DAYS.between(desde, hasta) + 1 : serie.size();
        String promedio = dias > 0 ? "$ " + MontoFormatter.fiat(total.divide(BigDecimal.valueOf(dias), 2, RoundingMode.HALF_UP)) : null;

        int n = r.getCantidadOperaciones();
        return new VistaEstadisticas(rango, "$ " + MontoFormatter.fiat(total), total,
                ComparacionPeriodo.de(total, r.getTotalPeriodoAnterior()),
                n + (n == 1 ? " movimiento en este período" : " movimientos en este período"),
                total.signum() <= 0, Collections.unmodifiableList(categorias), Collections.unmodifiableList(serie),
                promedio, pico != null ? pico.fecha.format(DIA_CORTO) + " · $ " + MontoFormatter.fiat(pico.monto) : null);
    }

    /** "yyyy-MM-dd" -> LocalDate (día, sin hora: nunca se corre en ningún huso). */
    static LocalDate dia(String iso) {
        if (iso == null || iso.length() < 10) return null;
        try {
            return LocalDate.parse(iso.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** La categoría con más gasto (el backend ya las manda ordenadas), o null sin gastos. */
    public Categoria principal() {
        for (Categoria c : categorias) if (!c.sinGasto) return c;
        return null;
    }

    /** "28/09" para las etiquetas del eje de la serie. */
    public static String diaCorto(LocalDate d) {
        return d.format(DIA_CORTO);
    }
}
