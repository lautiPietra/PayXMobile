package com.example.payxmobile.dolares;

import androidx.lifecycle.ViewModel;

/**
 * Retiene la compra/venta (formulario, cotización y una request en vuelo) mientras la Activity se
 * recrea por rotación: el POST no se pierde ni se repite.
 */
public class DolaresViewModel extends ViewModel {
    public CambioDolares cambio;
}
