package com.example.payxmobile.utils;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;

import com.example.payxmobile.activities.LoginActivity;
import com.example.payxmobile.asistente.ConversacionAsistente;
import com.example.payxmobile.cajas.CajasAhorroRepository;
import com.example.payxmobile.cajas.MovimientosCajaSesion;
import com.example.payxmobile.cripto.CambiosCriptoRepository;
import com.example.payxmobile.dolares.CambiosDolaresRepository;
import com.example.payxmobile.dolares.CotizacionDolarRepository;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.notificaciones.NotificacionesRepository;
import com.example.payxmobile.plazofijo.PlazosFijosRepository;
import com.example.payxmobile.saldos.SaldosRepository;
import com.example.payxmobile.servicios.ServiciosRepository;
import com.example.payxmobile.transferencias.TransferenciasRepository;

/** Cierre de sesión en un solo lugar (logout manual, sesión vencida o invalidada y reset de contraseña). */
public final class SesionUtils {

    public static final String EXTRA_MENSAJE = "mensaje_login";
    public static final String MSG_SESION_VENCIDA = "Tu sesión venció. Iniciá sesión de nuevo";
    public static final String MSG_SESION_INVALIDA = ApiErrores.MSG_SESION_INVALIDA;

    private SesionUtils() {}

    public static void cerrarSesion(Context context) {
        cerrar(context, null);
    }

    /** Al detectar un "exp" vencido sin llamar al backend (ej: volver a la app tras horas). Se ignora si ya no hay sesión. */
    public static void sesionVencida(Context context) {
        if (new SessionManager(context).getToken() == null) return;
        cerrar(context, MSG_SESION_VENCIDA);
    }

    /**
     * El interceptor HTTP recibió un 401 a un pedido autenticado (token ausente, inválido, vencido o cuenta
     * desactivada): se cierra la sesión sola y se vuelve al login. Se ignora si la sesión ya no es la que
     * mandó ese pedido (se cerró, entró otra cuenta o el token ya se renovó).
     */
    public static void noAutenticado(Context context, String tokenEnviado) {
        String actual = new SessionManager(context).getToken();
        if (actual == null || !actual.equals(tokenEnviado)) return;
        cerrar(context, mensajeSinSesion(actual));
    }

    /** Una pantalla vio un 401 (además del interceptor, que ya lo maneja). Se ignora si ya no hay sesión. */
    public static void sesionInvalida(Context context) {
        String actual = new SessionManager(context).getToken();
        if (actual == null) return;
        cerrar(context, mensajeSinSesion(actual));
    }

    /** "Venció" si el propio token dice que ya pasó su "exp"; si no (inválido, cuenta dada de baja), "ya no es válida". */
    static String mensajeSinSesion(String token) {
        return JwtUtils.estaVencido(token, System.currentTimeMillis()) ? MSG_SESION_VENCIDA : MSG_SESION_INVALIDA;
    }

    private static void cerrar(Context context, String mensaje) {
        Context app = context.getApplicationContext();
        limpiar(app);

        // CLEAR_TASK: el botón "atrás" no puede volver a pantallas autenticadas
        Intent intent = new Intent(app, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (mensaje != null) intent.putExtra(EXTRA_MENSAJE, mensaje);
        app.startActivity(intent);
    }

    /**
     * Borra la sesión guardada (token y datos del usuario) y todo lo que quedó en memoria de esa cuenta,
     * sin navegar. Lo usa el cierre de sesión y el reset de contraseña, que invalida los tokens de la cuenta.
     */
    public static void limpiar(Context context) {
        Context app = context.getApplicationContext();
        new SessionManager(app).clearSession();
        // Que la próxima cuenta no vea ni un instante los saldos de esta
        SaldosRepository.get(app).limpiar();
        TransferenciasRepository.get(app).limpiar();
        NotificacionesRepository.get(app).limpiar();
        CambiosDolaresRepository.get(app).limpiar();
        CambiosCriptoRepository.get(app).limpiar();
        PlazosFijosRepository.get(app).limpiar();
        CajasAhorroRepository.get(app).limpiar();
        MovimientosCajaSesion.get(app).cerrarSesion(); // lo guardado queda para cuando vuelva
        ServiciosRepository.catalogo(app).limpiar();
        ServiciosRepository.historial(app).limpiar();
        CotizacionDolarRepository.get(app).limpiar();
        // La charla con el asistente puede tener saldos y movimientos: la próxima cuenta no la tiene que ver
        ConversacionAsistente.get(app).cerrarSesion();

        // Olvida la cuenta de Google elegida: el próximo login vuelve a mostrar el selector
        CredentialManager.create(app).clearCredentialStateAsync(
                new ClearCredentialStateRequest(), null, ContextCompat.getMainExecutor(app),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override public void onResult(Void unused) {}
                    @Override public void onError(ClearCredentialException e) {}
                });
    }
}
