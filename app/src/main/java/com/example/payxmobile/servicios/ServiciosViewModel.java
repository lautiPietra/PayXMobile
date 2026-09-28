package com.example.payxmobile.servicios;

import androidx.lifecycle.ViewModel;

/** Retiene el pago (y su request en vuelo) mientras la pantalla se recrea por rotación. */
public class ServiciosViewModel extends ViewModel {
    public PagoFactura pago;
}
