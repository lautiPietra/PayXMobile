package com.example.payxmobile.asistente.ui;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.example.payxmobile.MainActivity;
import com.example.payxmobile.R;
import com.example.payxmobile.activities.LoginActivity;
import com.example.payxmobile.activities.OlvidePasswordActivity;
import com.example.payxmobile.activities.RegistroActivity;
import com.example.payxmobile.activities.ResetPasswordActivity;
import com.example.payxmobile.activities.SplashActivity;
import com.example.payxmobile.activities.VerificarEmailActivity;
import com.example.payxmobile.utils.SessionManager;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Pone el chat del asistente en TODAS las pantallas privadas (como App.jsx de la web), sin que cada
 * pantalla tenga que acordarse: una pantalla nueva lo trae sola. Nunca en las pantallas públicas
 * (login, registro, recuperar contraseña...) ni sin sesión.
 */
public final class InstaladorChatAsistente implements Application.ActivityLifecycleCallbacks {

    private static final Set<Class<?>> PUBLICAS = new HashSet<>(Arrays.asList(
            SplashActivity.class,
            LoginActivity.class,
            RegistroActivity.class,
            VerificarEmailActivity.class,
            OlvidePasswordActivity.class,
            ResetPasswordActivity.class,
            MainActivity.class));

    static boolean esPrivada(Class<?> pantalla) {
        return !PUBLICAS.contains(pantalla);
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        // En onStart (no en onCreate) porque recién ahí la pantalla ya hizo setContentView
        if (!esPrivada(activity.getClass())) return;
        if (!new SessionManager(activity).isLoggedIn()) return;
        ChatAsistenteView chat = ChatAsistenteView.instalar(activity);
        if (chat != null) chat.alIniciar();
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        Object chat = activity.findViewById(android.R.id.content) != null
                ? activity.findViewById(android.R.id.content).getTag(R.id.chatAsistenteRaiz) : null;
        if (chat instanceof ChatAsistenteView) ((ChatAsistenteView) chat).alDetener();
    }

    @Override public void onActivityCreated(@NonNull Activity activity, Bundle guardado) {}
    @Override public void onActivityResumed(@NonNull Activity activity) {}
    @Override public void onActivityPaused(@NonNull Activity activity) {}
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle out) {}
    @Override public void onActivityDestroyed(@NonNull Activity activity) {}
}
