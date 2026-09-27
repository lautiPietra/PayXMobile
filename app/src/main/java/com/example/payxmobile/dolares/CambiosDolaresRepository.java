package com.example.payxmobile.dolares;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.model.OperacionCambioResponse;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;

/** Las compras/ventas de dólares del usuario (GET /api/cambio-dolares), para el feed de movimientos. */
public final class CambiosDolaresRepository {

    private static ListaRemota<OperacionCambioResponse> instancia;

    private CambiosDolaresRepository() {}

    public static synchronized ListaRemota<OperacionCambioResponse> get(Context context) {
        if (instancia == null) {
            Context app = context.getApplicationContext();
            Handler main = new Handler(Looper.getMainLooper());
            instancia = new ListaRemota<>(() -> RetrofitClient.getService(app), ApiService::listarCambiosDolares,
                    OperacionCambioResponse::getId, (tarea, demora) -> {
                        main.postDelayed(tarea, demora);
                        return () -> main.removeCallbacks(tarea);
                    });
        }
        return instancia;
    }
}
