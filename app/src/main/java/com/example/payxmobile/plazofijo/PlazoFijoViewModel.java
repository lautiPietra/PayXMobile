package com.example.payxmobile.plazofijo;

import androidx.lifecycle.ViewModel;

/**
 * Retiene la constitución (formulario, tasas y una request en vuelo) mientras la Activity se
 * recrea por rotación: el POST no se pierde ni se repite.
 */
public class PlazoFijoViewModel extends ViewModel {
    public ConstitucionPlazoFijo constitucion;
}
