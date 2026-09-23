package com.example.payxmobile.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;

import java.text.NumberFormat;
import java.util.Locale;

public class TransferenciaActivity extends AppCompatActivity {

    public static final String EXTRA_MONEDA = "moneda";

    private static final String[] MOTIVOS = {
            "Varios", "Alquiler", "Servicios", "Préstamo", "Regalo", "Otro"
    };

    private final NumberFormat formatoMonto = NumberFormat.getNumberInstance(new Locale("es", "AR"));
    private double saldoDisponible = 97567742.00;
    private String simbolo = "$";

    private TextView tvMontoResumen, tvSaldoLuego, tvMotivo;
    private LinearLayout opcionAhora, opcionPendiente;
    private ImageView ivIconoAhora, ivIconoPendiente;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transferencia);

        formatoMonto.setMinimumFractionDigits(2);
        formatoMonto.setMaximumFractionDigits(2);

        boolean esDolares = "USD".equals(getIntent().getStringExtra(EXTRA_MONEDA));
        simbolo = esDolares ? "US$" : "$";

        ((TextView) findViewById(R.id.tvTitulo)).setText(esDolares ? "Transferir dólares" : "Transferir dinero");
        ((TextView) findViewById(R.id.tvSimboloResumen)).setText(simbolo);
        ((TextView) findViewById(R.id.tvSimboloInput)).setText(simbolo);

        tvMontoResumen = findViewById(R.id.tvMontoResumen);
        tvSaldoLuego = findViewById(R.id.tvSaldoLuego);
        tvMotivo = findViewById(R.id.tvMotivo);
        opcionAhora = findViewById(R.id.opcionAhora);
        opcionPendiente = findViewById(R.id.opcionPendiente);
        ivIconoAhora = findViewById(R.id.ivIconoAhora);
        ivIconoPendiente = findViewById(R.id.ivIconoPendiente);

        String saldoFormateado = simbolo + " " + formatoMonto.format(saldoDisponible);
        ((TextView) findViewById(R.id.tvSaldoActual)).setText(saldoFormateado);
        ((TextView) findViewById(R.id.tvSaldoDisponible)).setText(saldoFormateado);
        tvSaldoLuego.setText(saldoFormateado);

        EditText etMonto = findViewById(R.id.etMonto);
        etMonto.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                actualizarResumen(s.toString());
            }
        });

        findViewById(R.id.btnUsarTodo).setOnClickListener(v ->
                etMonto.setText(formatoMonto.format(saldoDisponible)));

        findViewById(R.id.filaMotivo).setOnClickListener(this::mostrarMotivos);

        opcionAhora.setOnClickListener(v -> seleccionarMetodoEnvio(true));
        opcionPendiente.setOnClickListener(v -> seleccionarMetodoEnvio(false));

        findViewById(R.id.btnVolver).setOnClickListener(v -> finish());
        findViewById(R.id.btnCerrar).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancelar).setOnClickListener(v -> finish());
        findViewById(R.id.btnContinuar).setOnClickListener(v ->
                Toast.makeText(this, "Esta función va a estar disponible próximamente.", Toast.LENGTH_SHORT).show());
    }

    private void actualizarResumen(String textoMonto) {
        double monto = 0;
        try {
            monto = Double.parseDouble(textoMonto.replace(",", "."));
        } catch (NumberFormatException ignored) {}

        tvMontoResumen.setText(formatoMonto.format(monto));
        double restante = Math.max(saldoDisponible - monto, 0);
        tvSaldoLuego.setText(simbolo + " " + formatoMonto.format(restante));
    }

    private void mostrarMotivos(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        for (String motivo : MOTIVOS) {
            popup.getMenu().add(motivo);
        }
        popup.setOnMenuItemClickListener(item -> {
            tvMotivo.setText(item.getTitle());
            tvMotivo.setTextColor(getColor(R.color.text_primary));
            return true;
        });
        popup.show();
    }

    private void seleccionarMetodoEnvio(boolean ahora) {
        opcionAhora.setBackgroundResource(ahora ? R.drawable.bg_opcion_envio_selected : R.drawable.bg_opcion_envio_normal);
        opcionPendiente.setBackgroundResource(ahora ? R.drawable.bg_opcion_envio_normal : R.drawable.bg_opcion_envio_selected);
        ivIconoAhora.setImageTintList(getColorStateList(ahora ? R.color.payx_orange : R.color.text_secondary));
        ivIconoPendiente.setImageTintList(getColorStateList(ahora ? R.color.text_secondary : R.color.payx_orange));
    }
}
