package com.example.payxmobile.activities;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;
import com.example.payxmobile.utils.SessionManager;

public class TarjetaVirtualActivity extends AppCompatActivity {

    private static final String NUMERO_ENMASCARADO = "•••• •••• •••• 9813";
    private static final String NUMERO_COMPLETO = "4521 8873 1092 9813";

    private boolean numeroVisible = false;
    private TextView tvNumeroTarjeta, tvVerNumero;
    private ImageView ivOjoTarjeta;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tarjeta_virtual);

        SessionManager sessionManager = new SessionManager(this);
        ((TextView) findViewById(R.id.tvTitularTarjeta))
                .setText(sessionManager.getNombreCompleto().toUpperCase());

        tvNumeroTarjeta = findViewById(R.id.tvNumeroTarjeta);
        tvVerNumero = findViewById(R.id.tvVerNumero);
        ivOjoTarjeta = findViewById(R.id.ivOjoTarjeta);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnVerNumero).setOnClickListener(v -> alternarNumero());
    }

    private void alternarNumero() {
        numeroVisible = !numeroVisible;
        tvNumeroTarjeta.setText(numeroVisible ? NUMERO_COMPLETO : NUMERO_ENMASCARADO);
        tvVerNumero.setText(numeroVisible ? "Ocultar número" : "Ver número completo");
        ivOjoTarjeta.setImageResource(numeroVisible ? R.drawable.ic_eye_off : R.drawable.ic_eye);
    }
}
