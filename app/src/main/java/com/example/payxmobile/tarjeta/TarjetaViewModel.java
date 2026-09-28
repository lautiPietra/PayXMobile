package com.example.payxmobile.tarjeta;

import androidx.lifecycle.ViewModel;

/**
 * Retiene lo revelado (y un pedido en vuelo) solo mientras la pantalla se recrea por rotación. Al
 * salir de la pantalla se olvida (ver TarjetaVirtualActivity.onStop); nunca va al Bundle.
 */
public class TarjetaViewModel extends ViewModel {
    public RevelarTarjeta revelar;
}
