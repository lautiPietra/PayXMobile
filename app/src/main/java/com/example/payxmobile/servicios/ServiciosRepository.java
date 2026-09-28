package com.example.payxmobile.servicios;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.model.FacturaResponse;
import com.example.payxmobile.model.ServicioConFacturaResponse;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;

/**
 * Las dos listas del módulo, sin polling (las facturas del período siguiente no aparecen "de golpe"):
 * se refrescan al abrir la pantalla y tras pagar.
 * - catálogo: GET /api/facturas (los 6 servicios con su factura del mes; id = código del servicio).
 * - historial: GET /api/facturas/historial (todas las facturas; también alimenta el feed de movimientos).
 */
public final class ServiciosRepository {

    private static ListaRemota<ServicioConFacturaResponse> catalogo;
    private static ListaRemota<FacturaResponse> historial;

    private ServiciosRepository() {}

    public static synchronized ListaRemota<ServicioConFacturaResponse> catalogo(Context context) {
        if (catalogo == null) {
            Context app = context.getApplicationContext();
            catalogo = new ListaRemota<>(() -> RetrofitClient.getService(app), ApiService::listarServicios,
                    ServicioConFacturaResponse::getServicioCodigo, programador());
        }
        return catalogo;
    }

    public static synchronized ListaRemota<FacturaResponse> historial(Context context) {
        if (historial == null) {
            Context app = context.getApplicationContext();
            historial = new ListaRemota<>(() -> RetrofitClient.getService(app), ApiService::historialFacturas,
                    FacturaResponse::getId, programador());
        }
        return historial;
    }

    private static com.example.payxmobile.saldos.SaldosRepository.Programador programador() {
        Handler main = new Handler(Looper.getMainLooper());
        return (tarea, demora) -> {
            main.postDelayed(tarea, demora);
            return () -> main.removeCallbacks(tarea);
        };
    }
}
