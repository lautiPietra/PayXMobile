package com.example.payxmobile.transferencias;

import android.content.Context;

import com.example.payxmobile.cajas.CajasAhorroRepository;
import com.example.payxmobile.cripto.CambiosCriptoRepository;
import com.example.payxmobile.dolares.CambiosDolaresRepository;
import com.example.payxmobile.notificaciones.NotificacionesRepository;
import com.example.payxmobile.plazofijo.PlazosFijosRepository;
import com.example.payxmobile.saldos.SaldosRepository;
import com.example.payxmobile.servicios.ServiciosRepository;

/**
 * Qué hay que actualizar después de cualquier operación que mueva (o pueda haber movido) plata:
 * transferencias (crear/confirmar/cancelar), compra/venta de dólares y cripto, plazos fijos, cajas de
 * ahorro (depósitos/retiros/eliminación) y pago de servicios.
 */
public final class Refrescos {

    private Refrescos() {}

    public static void trasMoverPlata(Context context) {
        SaldosRepository.get(context).refrescar();
        TransferenciasRepository.get(context).refrescar();
        CambiosDolaresRepository.get(context).refrescar();
        CambiosCriptoRepository.get(context).refrescar();
        PlazosFijosRepository.get(context).refrescar();
        CajasAhorroRepository.get(context).refrescar();
        // Historial de facturas: alimenta el feed (los pagos de servicios); cualquier pago lo cambia
        ServiciosRepository.historial(context).refrescar();
        // La operación crea una notificación en el backend: badge (y lista, si ya se cargó) al instante
        NotificacionesRepository.get(context).refrescar();
    }
}
