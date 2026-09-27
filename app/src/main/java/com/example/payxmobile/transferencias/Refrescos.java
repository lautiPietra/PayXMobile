package com.example.payxmobile.transferencias;

import android.content.Context;

import com.example.payxmobile.cripto.CambiosCriptoRepository;
import com.example.payxmobile.dolares.CambiosDolaresRepository;
import com.example.payxmobile.notificaciones.NotificacionesRepository;
import com.example.payxmobile.saldos.SaldosRepository;

/**
 * Qué hay que actualizar después de cualquier operación que mueva (o pueda haber movido) plata:
 * transferencias (crear/confirmar/cancelar), compra/venta de dólares, y los próximos módulos.
 */
public final class Refrescos {

    private Refrescos() {}

    public static void trasMoverPlata(Context context) {
        SaldosRepository.get(context).refrescar();
        TransferenciasRepository.get(context).refrescar();
        CambiosDolaresRepository.get(context).refrescar();
        CambiosCriptoRepository.get(context).refrescar();
        // La operación crea una notificación en el backend: badge (y lista, si ya se cargó) al instante
        NotificacionesRepository.get(context).refrescar();
    }
}
