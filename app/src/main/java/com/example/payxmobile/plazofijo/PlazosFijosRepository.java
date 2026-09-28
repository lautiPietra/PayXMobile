package com.example.payxmobile.plazofijo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.model.PlazoFijoResponse;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.notificaciones.NotificacionesRepository;
import com.example.payxmobile.saldos.SaldosRepository;

import java.util.List;

/**
 * Los plazos fijos del usuario (GET /api/plazos-fijos): "Mis plazos fijos", el tope de activos del
 * formulario y el feed de movimientos. Polling cada 10 s solo mientras alguna pantalla lo pide.
 *
 * El vencimiento lo hace el backend solo (acredita montoTotal y notifica): cuando un refresco
 * trae un plazo que pasó de ACTIVO a VENCIDO se refrescan saldos y campana en el acto, sin esperar
 * a su propio polling.
 */
public final class PlazosFijosRepository {

    private static ListaRemota<PlazoFijoResponse> instancia;

    private PlazosFijosRepository() {}

    public static synchronized ListaRemota<PlazoFijoResponse> get(Context context) {
        if (instancia == null) {
            Context app = context.getApplicationContext();
            Handler main = new Handler(Looper.getMainLooper());
            instancia = new ListaRemota<>(() -> RetrofitClient.getService(app), ApiService::listarPlazosFijos,
                    PlazoFijoResponse::getId, (tarea, demora) -> {
                        main.postDelayed(tarea, demora);
                        return () -> main.removeCallbacks(tarea);
                    });
            instancia.observar(new DetectorVencimientos(() -> {
                SaldosRepository.get(app).refrescar();
                NotificacionesRepository.get(app).refrescar();
            }));
        }
        return instancia;
    }

    /** Cuántos ACTIVOS hay en la lista (null si todavía no se cargó). */
    public static Integer activos(List<PlazoFijoResponse> lista) {
        if (lista == null) return null;
        int n = 0;
        for (PlazoFijoResponse p : lista) if (p.esActivo()) n++;
        return n;
    }
}
