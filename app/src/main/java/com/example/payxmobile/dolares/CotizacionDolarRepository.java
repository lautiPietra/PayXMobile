package com.example.payxmobile.dolares;

import android.content.Context;

import com.example.payxmobile.model.CotizacionDolar;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Última cotización del dólar conocida, en memoria. Inicio e Inversiones la precargan para que la
 * pantalla de compra/venta la muestre AL INSTANTE (y después la refresca sola). Una cotización de
 * más de {@link #VIGENCIA_MS} no se ofrece: mejor "Buscando..." que un precio viejo.
 */
public class CotizacionDolarRepository {

    /** El backend la cachea 60 s: precargar más seguido no trae nada nuevo. */
    public static final long MIN_ENTRE_PRECARGAS_MS = 30_000L;
    public static final long VIGENCIA_MS = 120_000L;

    private static CotizacionDolarRepository instancia;

    public static synchronized CotizacionDolarRepository get(Context context) {
        if (instancia == null) {
            Context app = context.getApplicationContext();
            instancia = new CotizacionDolarRepository(() -> RetrofitClient.getService(app), System::currentTimeMillis);
        }
        return instancia;
    }

    private final Supplier<ApiService> api;
    private final LongSupplier reloj;
    private CotizacionDolar ultima;
    private long ultimaEn;
    private long ultimoPedidoEn = Long.MIN_VALUE / 2;
    private boolean enVuelo;

    public CotizacionDolarRepository(Supplier<ApiService> api, LongSupplier reloj) {
        this.api = api;
        this.reloj = reloj;
    }

    /** La última buena si es reciente, o null. */
    public synchronized CotizacionDolar getVigente() {
        if (ultima == null || reloj.getAsLong() - ultimaEn > VIGENCIA_MS) return null;
        return ultima;
    }

    /** Guarda una cotización buena recibida por otra vía (la pantalla de compra/venta). */
    public synchronized void guardar(CotizacionDolar c) {
        if (c == null || CambioDolares.precioPara(CambioDolares.Tipo.COMPRA, c) == null
                || CambioDolares.precioPara(CambioDolares.Tipo.VENTA, c) == null) return;
        if (c != ultima) {
            ultima = c;
            ultimaEn = reloj.getAsLong();
        }
    }

    /** Pide la cotización en segundo plano si no se pidió hace poco. Nunca bloquea ni avisa errores. */
    public void precargar() {
        synchronized (this) {
            long ahora = reloj.getAsLong();
            if (enVuelo || ahora - ultimoPedidoEn < MIN_ENTRE_PRECARGAS_MS) return;
            enVuelo = true;
            ultimoPedidoEn = ahora;
        }
        api.get().obtenerCotizacionDolar().enqueue(new Callback<CotizacionDolar>() {
            @Override
            public void onResponse(Call<CotizacionDolar> call, Response<CotizacionDolar> response) {
                synchronized (CotizacionDolarRepository.this) {
                    enVuelo = false;
                    if (response.isSuccessful()) guardar(response.body());
                    else if (response.code() == 503) ultima = null; // el backend no tiene ninguna
                }
            }

            @Override
            public void onFailure(Call<CotizacionDolar> call, Throwable t) {
                synchronized (CotizacionDolarRepository.this) {
                    enVuelo = false;
                }
            }
        });
    }

    public synchronized void limpiar() {
        ultima = null;
        ultimoPedidoEn = Long.MIN_VALUE / 2;
    }
}
