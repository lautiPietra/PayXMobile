package com.example.payxmobile.cajas;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.model.CajaAhorroResponse;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;

import java.util.List;

/**
 * Las cajas de ahorro del usuario (GET /api/cajas-ahorro, la más vieja primero). Refresco al abrir,
 * tras cada operación y cada 10 s mientras la pantalla está visible (por si cambian desde la web).
 */
public final class CajasAhorroRepository {

    private static ListaRemota<CajaAhorroResponse> instancia;

    private CajasAhorroRepository() {}

    public static synchronized ListaRemota<CajaAhorroResponse> get(Context context) {
        if (instancia == null) {
            Context app = context.getApplicationContext();
            Handler main = new Handler(Looper.getMainLooper());
            instancia = new ListaRemota<>(() -> RetrofitClient.getService(app), ApiService::listarCajasAhorro,
                    CajaAhorroResponse::getId, (tarea, demora) -> {
                        main.postDelayed(tarea, demora);
                        return () -> main.removeCallbacks(tarea);
                    });
        }
        return instancia;
    }

    /** La caja con ese id en la lista (null si no está o no se cargó). */
    public static CajaAhorroResponse buscar(List<CajaAhorroResponse> lista, String id) {
        if (lista == null || id == null) return null;
        for (CajaAhorroResponse c : lista) if (id.equals(c.getId())) return c;
        return null;
    }
}
