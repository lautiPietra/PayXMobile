package com.example.payxmobile.transferencias;

import android.content.Context;

import com.example.payxmobile.saldos.SaldosRepository;

/** Qué hay que actualizar después de cualquier operación que mueva (o pueda haber movido) plata. */
public final class Refrescos {

    private Refrescos() {}

    public static void trasMoverPlata(Context context) {
        SaldosRepository.get(context).refrescar();
        TransferenciasRepository.get(context).refrescar();
        // Notificaciones: la campana del Home recuenta las sin leer en onResume (al volver).
        // Cuando exista un repositorio de notificaciones, refrescarlo acá.
    }
}
