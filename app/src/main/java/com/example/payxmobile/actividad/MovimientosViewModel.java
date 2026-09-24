package com.example.payxmobile.actividad;

import androidx.lifecycle.ViewModel;

import java.time.LocalDate;

/**
 * Filtros de "Mis movimientos" y cuántas tandas de 100 se muestran. Sobreviven a la rotación
 * (ViewModel) y a los refrescos en segundo plano (no dependen de la lista).
 */
public class MovimientosViewModel extends ViewModel {

    private LocalDate desde;
    private LocalDate hasta;
    private FiltroMoneda moneda = FiltroMoneda.TODAS;
    private int tandas = 1;

    public LocalDate getDesde() { return desde; }
    public LocalDate getHasta() { return hasta; }
    public FiltroMoneda getMoneda() { return moneda; }
    public int getTandas() { return tandas; }

    /** Cualquier cambio de filtro vuelve a la primera tanda de 100. */
    public void setDesde(LocalDate d) {
        desde = d;
        tandas = 1;
    }

    public void setHasta(LocalDate h) {
        hasta = h;
        tandas = 1;
    }

    public void setRango(LocalDate d, LocalDate h) {
        desde = d;
        hasta = h;
        tandas = 1;
    }

    public void setMoneda(FiltroMoneda m) {
        moneda = m != null ? m : FiltroMoneda.TODAS;
        tandas = 1;
    }

    /** "Limpiar filtros": fechas y moneda. */
    public void limpiar() {
        setRango(null, null);
        moneda = FiltroMoneda.TODAS;
    }

    public void mostrarMas() {
        tandas++;
    }

    /** Restaura tras la muerte del proceso (sin tocar las tandas si no hay nada guardado). */
    public void restaurar(LocalDate d, LocalDate h, FiltroMoneda m, int t) {
        desde = d;
        hasta = h;
        moneda = m != null ? m : FiltroMoneda.TODAS;
        tandas = Math.max(1, t);
    }
}
