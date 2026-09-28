package com.example.payxmobile.plazofijo;

import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.model.PlazoFijoResponse;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Avisa cuando una lista nueva trae algún plazo fijo que en la anterior estaba ACTIVO y ahora está
 * VENCIDO (el backend lo acreditó). La primera carga nunca avisa. Sin Android.
 */
public final class DetectorVencimientos implements ListaRemota.Observador<PlazoFijoResponse> {

    private final Runnable alVencer;
    private List<PlazoFijoResponse> anterior;

    public DetectorVencimientos(Runnable alVencer) {
        this.alVencer = alVencer;
    }

    @Override
    public void onCambio(ListaRemota.Estado<PlazoFijoResponse> estado) {
        List<PlazoFijoResponse> nueva = estado.lista;
        if (nueva == anterior) return; // mismo estado (ej. "cargando"): nada nuevo
        boolean vencio = hayVencidosNuevos(anterior, nueva);
        anterior = nueva;
        if (vencio) alVencer.run();
    }

    static boolean hayVencidosNuevos(List<PlazoFijoResponse> antes, List<PlazoFijoResponse> ahora) {
        if (antes == null || ahora == null) return false;
        Set<String> activosAntes = new HashSet<>();
        for (PlazoFijoResponse p : antes) if (p.esActivo()) activosAntes.add(p.getId());
        for (PlazoFijoResponse p : ahora) {
            if (p.esVencido() && activosAntes.contains(p.getId())) return true;
        }
        return false;
    }
}
