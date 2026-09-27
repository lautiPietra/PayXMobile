package com.example.payxmobile.cripto;

import androidx.lifecycle.ViewModel;

/** Retiene la compra/venta de cripto (y un POST en vuelo) mientras la Activity se recrea al rotar. */
public class CriptoViewModel extends ViewModel {
    public OperacionCripto operacion;
}
