package com.example.payxmobile.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * GET /api/estadisticas/gastos?dias=N. Verificado contra EstadisticaService (no contra el comentario
 * del DTO, que está desactualizado):
 * - categorias: SIEMPRE las 5 (TRANSFERENCIAS, DOLARES, CRIPTO, PLAZO_FIJO, SERVICIOS), también
 *   con monto 0, ordenadas de mayor a menor gasto. "Sin gastos" = totalGastado 0, no lista vacía.
 * - desde: null en "todo el tiempo"; hasta: SIEMPRE hoy (no es null en "todo el tiempo").
 * - porDia: con N <= 365 trae un punto por día (0 si no hubo gasto); vacía si el período (o, en
 *   "todo", desde el primer gasto) supera 365 días, o en "todo" sin ningún gasto.
 * - totalPeriodoAnterior: null en "todo el tiempo".
 * Fechas "yyyy-MM-dd" (días, sin hora).
 */
public class EstadisticaGastosResponse {
    private BigDecimal totalGastado;
    private String desde;
    private String hasta;
    private List<CategoriaGastoResponse> categorias;
    private List<PuntoGastoDiarioResponse> porDia;
    private BigDecimal totalPeriodoAnterior;
    private int cantidadOperaciones;

    public EstadisticaGastosResponse() {}

    public EstadisticaGastosResponse(BigDecimal totalGastado, String desde, String hasta,
                                     List<CategoriaGastoResponse> categorias, List<PuntoGastoDiarioResponse> porDia,
                                     BigDecimal totalPeriodoAnterior, int cantidadOperaciones) {
        this.totalGastado = totalGastado;
        this.desde = desde;
        this.hasta = hasta;
        this.categorias = categorias;
        this.porDia = porDia;
        this.totalPeriodoAnterior = totalPeriodoAnterior;
        this.cantidadOperaciones = cantidadOperaciones;
    }

    public BigDecimal getTotalGastado() { return totalGastado; }
    public String getDesde() { return desde; }
    public String getHasta() { return hasta; }
    public List<CategoriaGastoResponse> getCategorias() { return categorias; }
    public List<PuntoGastoDiarioResponse> getPorDia() { return porDia; }
    public BigDecimal getTotalPeriodoAnterior() { return totalPeriodoAnterior; }
    public int getCantidadOperaciones() { return cantidadOperaciones; }
}
