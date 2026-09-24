package com.example.payxmobile.utils;

import android.content.Context;
import android.content.Intent;

import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.exceptions.ClearCredentialException;

import com.example.payxmobile.activities.LoginActivity;
import com.example.payxmobile.saldos.SaldosRepository;
import com.example.payxmobile.transferencias.TransferenciasRepository;

/** Cierre de sesión en un solo lugar (logout manual y sesión vencida). */
public final class SesionUtils {

    public static final String EXTRA_MENSAJE = "mensaje_login";
    public static final String MSG_SESION_VENCIDA = "Tu sesión venció. Iniciá sesión de nuevo";
    public static final String MSG_SESION_INVALIDA = "Tu sesión ya no es válida. Iniciá sesión de nuevo";

    private SesionUtils() {}

    public static void cerrarSesion(Context context) {
        cerrar(context, null);
    }

    /** Llamado por el interceptor HTTP o al detectar un "exp" vencido. Se ignora si ya no hay sesión. */
    public static void sesionVencida(Context context) {
        if (new SessionManager(context).getToken() == null) return;
        cerrar(context, MSG_SESION_VENCIDA);
    }

    /** 403 en un GET autenticado con token vigente (ej: cuenta desactivada). Se ignora si ya no hay sesión. */
    public static void sesionInvalida(Context context) {
        if (new SessionManager(context).getToken() == null) return;
        cerrar(context, MSG_SESION_INVALIDA);
    }

    private static void cerrar(Context context, String mensaje) {
        Context app = context.getApplicationContext();
        new SessionManager(app).clearSession();
        // Que la próxima cuenta no vea ni un instante los saldos de esta
        SaldosRepository.get(app).limpiar();
        TransferenciasRepository.get(app).limpiar();

        // Olvida la cuenta de Google elegida: el próximo login vuelve a mostrar el selector
        CredentialManager.create(app).clearCredentialStateAsync(
                new ClearCredentialStateRequest(), null, ContextCompat.getMainExecutor(app),
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override public void onResult(Void unused) {}
                    @Override public void onError(ClearCredentialException e) {}
                });

        // CLEAR_TASK: el botón "atrás" no puede volver a pantallas autenticadas
        Intent intent = new Intent(app, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (mensaje != null) intent.putExtra(EXTRA_MENSAJE, mensaje);
        app.startActivity(intent);
    }
}
