package com.example.payxmobile.notificaciones.ui;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.example.payxmobile.R;
import com.example.payxmobile.activities.NotificacionesActivity;
import com.example.payxmobile.notificaciones.NotificacionesRepository;
import com.example.payxmobile.utils.SesionUtils;

/**
 * Campana con badge para cualquier pantalla principal (layout include_campana). Se engancha al
 * ciclo de vida de la Activity: observa y hace polling solo entre onStart y onStop.
 *
 * Uso: {@code CampanaNotificaciones.en(this)} después de setContentView. Si la campana está
 * dentro de una vista que se infla más tarde (ej. el encabezado de un RecyclerView), crear con
 * {@code new CampanaNotificaciones(this)} y llamar a {@link #vincular(View)} al inflarla.
 */
public final class CampanaNotificaciones implements DefaultLifecycleObserver {

    private final AppCompatActivity activity;
    private final NotificacionesRepository repo;
    private final NotificacionesRepository.Observador observador = this::render;
    private View boton;
    private TextView badge;
    private NotificacionesRepository.Estado ultimo;

    public static CampanaNotificaciones en(AppCompatActivity activity) {
        CampanaNotificaciones c = new CampanaNotificaciones(activity);
        c.vincular(activity.findViewById(android.R.id.content));
        return c;
    }

    public CampanaNotificaciones(AppCompatActivity activity) {
        this.activity = activity;
        this.repo = NotificacionesRepository.get(activity);
        activity.getLifecycle().addObserver(this);
    }

    public void vincular(View raiz) {
        boton = raiz.findViewById(R.id.btnNotificaciones);
        badge = raiz.findViewById(R.id.badgeNotificaciones);
        if (boton != null) {
            boton.setOnClickListener(v -> {
                activity.startActivity(new Intent(activity, NotificacionesActivity.class));
                activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
        }
        if (ultimo != null) render(ultimo);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        repo.observar(observador);
        repo.iniciarAutoRefresco();
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        repo.detenerAutoRefresco();
        repo.dejarDeObservar(observador);
    }

    private void render(NotificacionesRepository.Estado estado) {
        ultimo = estado;
        if (activity.isFinishing()) return;
        if (estado.sesionInvalida) {
            SesionUtils.sesionInvalida(activity);
            return;
        }
        if (badge == null) return;
        boolean mostrar = estado.mostrarBadge();
        badge.setVisibility(mostrar ? View.VISIBLE : View.GONE);
        badge.setText(mostrar ? estado.textoBadge() : "");
        if (boton != null) {
            boton.setContentDescription(mostrar
                    ? "Notificaciones, " + estado.sinLeer + " sin leer"
                    : "Notificaciones");
        }
    }
}
