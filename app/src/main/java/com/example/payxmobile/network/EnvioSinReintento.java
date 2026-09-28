package com.example.payxmobile.network;

import com.example.payxmobile.transferencias.FalloEnvio;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Clasifica el resultado de un pedido SIN idempotencia en el backend (cajas: crear, depositar, retirar,
 * eliminar; pagar una factura): nunca se reintenta solo, y si no hay respuesta y no es seguro que el pedido no salió,
 * es INCIERTO (pudo haberse hecho).
 */
public final class EnvioSinReintento {

    public interface Manejador<T> {
        /** 2xx (con body si se pidió). */
        void exito(T body);

        /** El backend lo rechazó: no se hizo nada. El mensaje ya es el texto para mostrar. */
        void rechazo(String mensaje, int codigo);

        /** Seguro que nunca llegó al servidor: se puede reintentar. */
        void noEnviado(String mensaje);

        /** Timeout, corte después de enviar, 2xx sin el body esperado: no se sabe si se hizo. */
        void incierto();
    }

    private EnvioSinReintento() {}

    public static <T> void enviar(Call<T> call, boolean requiereBody, Manejador<T> m) {
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(Call<T> c, Response<T> response) {
                if (response.isSuccessful() && (response.body() != null || !requiereBody)) {
                    m.exito(response.body());
                } else if (response.isSuccessful()) {
                    m.incierto();
                } else {
                    m.rechazo(ApiErrores.mensaje(response), response.code());
                }
            }

            @Override
            public void onFailure(Call<T> c, Throwable t) {
                if (FalloEnvio.seguroNoEnviado(t)) m.noEnviado(ApiErrores.mensajeFallo(t));
                else m.incierto();
            }
        });
    }

    /**
     * Sin {"error"} en el body (un @Valid que falla termina en /error, que exige token: 403 vacío)
     * no hay texto útil del backend: se usa el genérico de la operación, como la web.
     */
    public static String textoRechazo(String mensaje, String generico) {
        boolean sinTexto = mensaje.equals(ApiErrores.MSG_DATOS_INVALIDOS)
                || mensaje.equals(ApiErrores.MSG_RESPUESTA_INESPERADA);
        return sinTexto ? generico : mensaje;
    }
}
