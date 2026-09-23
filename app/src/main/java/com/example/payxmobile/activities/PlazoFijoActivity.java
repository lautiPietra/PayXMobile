package com.example.payxmobile.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class PlazoFijoActivity extends AppCompatActivity {

    private static final int[] DIAS_PLAZO = {30, 60, 90, 180};
    private static final double[] TNA_PLAZO = {35.5, 36.5, 38.0, 40.0};

    private final NumberFormat formatoMonto = NumberFormat.getNumberInstance(new Locale("es", "AR"));
    private final double saldoDisponible = 97567742.00;
    private int indicePlazo = 0;

    private TextView tvMontoResumen, tvInteres, tvTotal, tvFechaVencimiento, tvPlazoBadge, tvPlazoSeleccionado, tvNota;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plazo_fijo);

        formatoMonto.setMinimumFractionDigits(2);
        formatoMonto.setMaximumFractionDigits(2);

        tvMontoResumen = findViewById(R.id.tvMontoResumen);
        tvInteres = findViewById(R.id.tvInteres);
        tvTotal = findViewById(R.id.tvTotal);
        tvFechaVencimiento = findViewById(R.id.tvFechaVencimiento);
        tvPlazoBadge = findViewById(R.id.tvPlazoBadge);
        tvPlazoSeleccionado = findViewById(R.id.tvPlazoSeleccionado);
        tvNota = findViewById(R.id.tvNota);

        ((TextView) findViewById(R.id.tvSaldoDisponible))
                .setText("$ " + formatoMonto.format(saldoDisponible));

        EditText etMonto = findViewById(R.id.etMonto);
        etMonto.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                actualizarResumen();
            }
        });

        findViewById(R.id.btnUsarTodo).setOnClickListener(v ->
                etMonto.setText(formatoMonto.format(saldoDisponible)));

        findViewById(R.id.filaPlazo).setOnClickListener(this::mostrarPlazos);

        findViewById(R.id.btnVerMisPlazos).setOnClickListener(v -> {
            startActivity(new Intent(this, MisPlazosFijosActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        findViewById(R.id.btnVolver).setOnClickListener(v -> finish());
        findViewById(R.id.btnCerrar).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancelar).setOnClickListener(v -> finish());
        findViewById(R.id.btnConstituir).setOnClickListener(v ->
                Toast.makeText(this, "Esta función va a estar disponible próximamente.", Toast.LENGTH_SHORT).show());

        actualizarResumen();
    }

    private void mostrarPlazos(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        for (int i = 0; i < DIAS_PLAZO.length; i++) {
            popup.getMenu().add(0, i, i, textoPlazo(i));
        }
        popup.setOnMenuItemClickListener(item -> {
            indicePlazo = item.getItemId();
            actualizarResumen();
            return true;
        });
        popup.show();
    }

    private String textoPlazo(int indice) {
        return DIAS_PLAZO[indice] + " días · TNA " + formatoTna(TNA_PLAZO[indice]) + "%";
    }

    private String formatoTna(double tna) {
        return tna == Math.floor(tna) ? String.valueOf((int) tna) : String.valueOf(tna);
    }

    private void actualizarResumen() {
        double monto = 0;
        try {
            monto = Double.parseDouble(((EditText) findViewById(R.id.etMonto)).getText().toString().replace(",", "."));
        } catch (NumberFormatException ignored) {}

        int dias = DIAS_PLAZO[indicePlazo];
        double tna = TNA_PLAZO[indicePlazo];
        double interes = monto * (tna / 100.0) * (dias / 365.0);
        double total = monto + interes;

        Calendar vencimiento = Calendar.getInstance();
        vencimiento.add(Calendar.DAY_OF_YEAR, dias);
        SimpleDateFormat formatoFecha = new SimpleDateFormat("d 'de' MMMM 'de' yyyy", new Locale("es", "AR"));
        String fechaTexto = formatoFecha.format(vencimiento.getTime());

        String plazoTexto = textoPlazo(indicePlazo);

        tvMontoResumen.setText(formatoMonto.format(monto));
        tvPlazoBadge.setText(plazoTexto);
        tvPlazoSeleccionado.setText(plazoTexto);
        tvInteres.setText("+$ " + formatoMonto.format(interes));
        tvTotal.setText("$ " + formatoMonto.format(total));
        tvFechaVencimiento.setText(fechaTexto);
        tvNota.setText("El dinero queda inmovilizado hasta el " + fechaTexto
                + ". Ese día se acredita solo, junto con el interés.");
    }
}
