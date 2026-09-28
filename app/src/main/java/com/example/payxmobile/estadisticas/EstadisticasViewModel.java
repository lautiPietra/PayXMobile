package com.example.payxmobile.estadisticas;

import androidx.lifecycle.ViewModel;

/** Retiene el período elegido y los datos mientras la pantalla se recrea por rotación (sin volver a pedir). */
public class EstadisticasViewModel extends ViewModel {
    public CargaEstadisticas carga;
}
