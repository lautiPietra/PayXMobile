package com.example.payxmobile.cajas;

import androidx.lifecycle.ViewModel;

/**
 * Retiene lo que tiene que sobrevivir a una rotación en las pantallas de cajas: el formulario, el
 * depósito/retiro o la eliminación en curso (con su request en vuelo). Cada pantalla usa el suyo.
 */
public class CajasViewModel extends ViewModel {
    public FormularioCaja formulario;
    public OperacionMontoCaja operacion;
    public EliminacionCaja eliminacion;
}
