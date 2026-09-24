package com.example.payxmobile.utils;

import android.app.Activity;
import android.content.Intent;

import com.example.payxmobile.R;
import com.example.payxmobile.activities.HomeActivity;
import com.example.payxmobile.activities.InversionesActivity;
import com.example.payxmobile.activities.MovimientosActivity;
import com.example.payxmobile.activities.PerfilActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Menú inferior (Inicio / Actividad / Inversiones / Perfil) en un solo lugar, con una regla fija
 * para la pila de pantallas:
 * - Inicio es siempre la base: tocarlo vuelve al Inicio existente y cierra todo lo de encima.
 * - Las otras pestañas REEMPLAZAN a la pestaña actual (nunca se apilan una sobre otra), así
 *   "atrás" desde cualquier pestaña vuelve al Inicio.
 * Antes, "Inicio" hacía finish() y volvía a la pantalla anterior (ej: Inversiones -> Actividad
 * -> Inicio terminaba en Inversiones).
 */
public final class NavegacionInferior {

    private NavegacionInferior() {}

    /** @param actual el item de esta pantalla (R.id.nav_inicio, nav_actividad, ...) */
    public static void configurar(Activity activity, BottomNavigationView nav, int actual) {
        nav.setSelectedItemId(actual);
        nav.setOnItemSelectedListener(item -> {
            int destino = item.getItemId();
            if (destino == actual) return true;
            ir(activity, destino, actual);
            return false; // la selección la marca la pantalla a la que se llega
        });
    }

    public static void ir(Activity activity, int destino, int actual) {
        if (destino == R.id.nav_inicio) {
            // Vuelve al Inicio que ya está en la pila y cierra todo lo que tiene encima
            Intent intent = new Intent(activity, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
            if (actual != R.id.nav_inicio) activity.finish();
            animar(activity);
            return;
        }
        Class<?> pantalla = destino == R.id.nav_actividad ? MovimientosActivity.class
                : destino == R.id.nav_inversiones ? InversionesActivity.class
                : destino == R.id.nav_perfil ? PerfilActivity.class
                : null;
        if (pantalla == null) return;
        activity.startActivity(new Intent(activity, pantalla));
        // Una pestaña reemplaza a la otra: la pila queda Inicio -> pestaña actual
        if (actual != R.id.nav_inicio) activity.finish();
        animar(activity);
    }

    private static void animar(Activity activity) {
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
