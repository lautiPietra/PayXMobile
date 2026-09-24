package com.example.payxmobile.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;
import com.example.payxmobile.utils.NavegacionInferior;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class InversionesActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inversiones);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        configurarBottomNav();

        configurarAccion(findViewById(R.id.accionPlazoFijo), R.drawable.ic_piggy_bank, "Plazos fijos",
                PlazoFijoActivity.class, null);
        configurarAccion(findViewById(R.id.accionMisPlazoFijo), R.drawable.ic_file_text, "Mis plazos fijos",
                MisPlazosFijosActivity.class, null);
        configurarAccion(findViewById(R.id.accionCajaAhorro), R.drawable.ic_target, "Cajas de ahorro",
                CajasAhorroActivity.class, null);
        configurarAccion(findViewById(R.id.accionComprarDolares), R.drawable.ic_arrow_down_circle, "Comprar dólares",
                OperacionDolaresActivity.class, "COMPRAR");
        configurarAccion(findViewById(R.id.accionVenderDolares), R.drawable.ic_arrow_up_circle, "Vender dólares",
                OperacionDolaresActivity.class, "VENDER");
        configurarAccion(findViewById(R.id.accionComprarCripto), R.drawable.ic_coins, "Comprar criptomonedas",
                OperacionCriptoActivity.class, "COMPRAR");
        configurarAccion(findViewById(R.id.accionVenderCripto), R.drawable.ic_coins, "Vender criptomonedas",
                OperacionCriptoActivity.class, "VENDER");
    }

    private void configurarAccion(View item, int iconoRes, String label, Class<?> destino, String tipo) {
        ((ImageView) item.findViewById(R.id.ivIconoAccion)).setImageResource(iconoRes);
        ((TextView) item.findViewById(R.id.tvLabelAccion)).setText(label);
        item.setOnClickListener(v -> {
            Intent intent = new Intent(this, destino);
            if (tipo != null) {
                intent.putExtra("tipo", tipo);
            }
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        bottomNav.setSelectedItemId(R.id.nav_inversiones);
    }

    private void configurarBottomNav() {
        bottomNav = findViewById(R.id.bottomNav);
        NavegacionInferior.configurar(this, bottomNav, R.id.nav_inversiones);
    }
}
