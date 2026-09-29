package com.example.payxmobile.actividad;

import androidx.lifecycle.ViewModel;

import java.time.LocalDate;

/**
 * Filtros de "Mis movimientos" y qué página (de a 30) se está viendo. Sobreviven a la rotación
 * (ViewModel) y a los refrescos en segundo plano (no dependen de la lista).
 */
public class MovimientosViewModel extends ViewModel {

    private LocalDate desde;
    private LocalDate hasta;
    private FiltroMoneda moneda = FiltroMoneda.TODAS;
    private int pagina = 1;

    public LocalDate getDesde() { return desde; }
    public LocalDate getHasta() { return hasta; }
    public FiltroMoneda getMoneda() { return moneda; }
    public int getPagina() { return pagina; }

    /** Cualquier cambio de filtro vuelve a la página 1. */
    public void setDesde(LocalDate d) {
        desde = d;
        pagina = 1;
    }

    public void setHasta(LocalDate h) {
        hasta = h;
        pagina = 1;
    }

    public void setRango(LocalDate d, LocalDate h) {
        desde = d;
        hasta = h;
        pagina = 1;
    }

    public void setMoneda(FiltroMoneda m) {
        moneda = m != null ? m : FiltroMoneda.TODAS;
        pagina = 1;
    }

    /** "Limpiar filtros": fechas y moneda. */
    public void limpiar() {
        setRango(null, null);
        moneda = FiltroMoneda.TODAS;
    }

    /** Si pasa de la última, VistaMovimientos la ajusta a la última. */
    public void irAPagina(int p) {
        pagina = Math.max(1, p);
    }

    /** Restaura tras la muerte del proceso (sin tocar la página si no hay nada guardado). */
    public void restaurar(LocalDate d, LocalDate h, FiltroMoneda m, int p) {
        desde = d;
        hasta = h;
        moneda = m != null ? m : FiltroMoneda.TODAS;
        pagina = Math.max(1, p);
    }
}
