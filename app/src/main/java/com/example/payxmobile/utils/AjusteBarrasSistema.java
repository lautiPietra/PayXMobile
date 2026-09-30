package com.example.payxmobile.utils;

import android.app.Activity;
import android.app.Application;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.payxmobile.R;

/**
 * Desde Android 15 (y con targetSdk 35+ ya no se puede desactivar) las pantallas se dibujan detrás de
 * la barra de estado (hora, batería, notificaciones) y de la barra de navegación, así que el contenido
 * quedaba encimado. Esto le deja a TODAS las pantallas el margen de esas barras, sin que cada una
 * tenga que acordarse: el contenido (y el chat del asistente, que cuelga del mismo lugar) arranca
 * debajo de la barra de estado y termina arriba de la de navegación.
 */
public final class AjusteBarrasSistema implements Application.ActivityLifecycleCallbacks {

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        // En onStart (no en onCreate) porque recién ahí la pantalla ya hizo setContentView
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return;
        ViewGroup contenido = activity.findViewById(android.R.id.content);
        if (contenido == null || contenido.getTag(R.id.ajusteBarrasSistema) != null) return;
        contenido.setTag(R.id.ajusteBarrasSistema, Boolean.TRUE);

        // Lo que queda detrás de las barras toma el mismo fondo que la pantalla, así no se nota el corte
        if (contenido.getChildCount() > 0) {
            Drawable fondo = contenido.getChildAt(0).getBackground();
            if (fondo instanceof ColorDrawable) contenido.setBackgroundColor(((ColorDrawable) fondo).getColor());
        }

        ViewCompat.setOnApplyWindowInsetsListener(contenido, (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            int abajo = barras.bottom;
            // Con "adjustResize" (p. ej. el chat del asistente abierto) el teclado achica la pantalla
            if (usaAdjustResize(activity)) {
                abajo = Math.max(abajo, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
            }
            v.setPadding(barras.left, barras.top, barras.right, abajo);
            // Consumidos: si no, la barra de navegación inferior sumaría su propio margen encima de este
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(contenido);
    }

    private static boolean usaAdjustResize(Activity activity) {
        int modo = activity.getWindow().getAttributes().softInputMode
                & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST;
        return modo == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
    }

    @Override public void onActivityCreated(@NonNull Activity activity, Bundle guardado) {}
    @Override public void onActivityResumed(@NonNull Activity activity) {}
    @Override public void onActivityPaused(@NonNull Activity activity) {}
    @Override public void onActivityStopped(@NonNull Activity activity) {}
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle out) {}
    @Override public void onActivityDestroyed(@NonNull Activity activity) {}
}
