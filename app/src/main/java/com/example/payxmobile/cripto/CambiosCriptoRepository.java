package com.example.payxmobile.cripto;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.model.OperacionCriptoResponse;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;

/** Las compras/ventas de cripto del usuario (GET /api/cripto), para el feed de movimientos. */
public final class CambiosCriptoRepository {

    private static ListaRemota<OperacionCriptoResponse> instancia;

    private CambiosCriptoRepository() {}

    public static synchronized ListaRemota<OperacionCriptoResponse> get(Context context) {
        if (instancia == null) {
            Context app = context.getApplicationContext();
            Handler main = new Handler(Looper.getMainLooper());
            instancia = new ListaRemota<>(() -> RetrofitClient.getService(app), ApiService::listarOperacionesCripto,
                    OperacionCriptoResponse::getId, (tarea, demora) -> {
                        main.postDelayed(tarea, demora);
                        return () -> main.removeCallbacks(tarea);
                    });
        }
        return instancia;
    }
}
