package com.example.payxmobile.transferencias;

import androidx.lifecycle.ViewModel;

/**
 * Retiene el flujo de envío (y una request en vuelo) mientras la Activity se recrea por
 * rotación o al plegar/desplegar el teléfono: el POST no se pierde ni se repite.
 */
public class EnvioViewModel extends ViewModel {
    public EnvioTransferencia envio;
}
