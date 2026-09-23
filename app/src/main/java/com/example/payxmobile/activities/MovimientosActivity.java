package com.example.payxmobile.activities;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Calendar;

public class MovimientosActivity extends AppCompatActivity {

    private static final String[] TIPOS = {
            "Todos los tipos", "Transferencias", "Plazos fijos", "Dólares", "Cripto"
    };
    private static final String[] RANGOS = {"Todo", "Hoy", "7 días", "30 días", "Este mes"};

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movimientos);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        configurarBottomNav();

        armarChips(findViewById(R.id.filaTipos), TIPOS);
        armarChips(findViewById(R.id.filaRangos), RANGOS);

        configurarSelectorFecha(findViewById(R.id.tvFechaDesde));
        configurarSelectorFecha(findViewById(R.id.tvFechaHasta));
    }

    @Override
    protected void onResume() {
        super.onResume();
        bottomNav.setSelectedItemId(R.id.nav_actividad);
    }

    private void armarChips(LinearLayout contenedor, String[] opciones) {
        for (int i = 0; i < opciones.length; i++) {
            TextView chip = new TextView(this);
            chip.setText(opciones[i]);
            chip.setTextSize(12.5f);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setBackgroundResource(R.drawable.bg_chip_naranja);
            chip.setTextColor(getColorStateList(R.color.coin_chip_text));
            chip.setSelected(i == 0);
            chip.setPadding(dpA(16), dpA(9), dpA(16), dpA(9));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMarginEnd(dpA(8));
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> {
                for (int j = 0; j < contenedor.getChildCount(); j++) {
                    contenedor.getChildAt(j).setSelected(false);
                }
                chip.setSelected(true);
            });

            contenedor.addView(chip);
        }
    }

    private void configurarSelectorFecha(TextView campo) {
        campo.setOnClickListener(v -> {
            Calendar hoy = Calendar.getInstance();
            new DatePickerDialog(this, (view, anio, mes, dia) -> {
                String fecha = String.format("%02d/%02d/%04d", dia, mes + 1, anio);
                campo.setText(fecha);
                campo.setTextColor(getColor(R.color.text_primary));
            }, hoy.get(Calendar.YEAR), hoy.get(Calendar.MONTH), hoy.get(Calendar.DAY_OF_MONTH)).show();
        });
    }

    private void configurarBottomNav() {
        bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_actividad);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_actividad) {
                return true;
            } else if (id == R.id.nav_inicio) {
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return false;
            } else if (id == R.id.nav_inversiones) {
                startActivity(new Intent(this, InversionesActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return false;
            } else if (id == R.id.nav_perfil) {
                startActivity(new Intent(this, PerfilActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                return false;
            }
            return false;
        });
    }

    private int dpA(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
