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

public class TransferenciaCriptoActivity extends AppCompatActivity {

    private static final String[] CODIGOS_MONEDA = {"BTC", "ETH", "SOL", "USDT", "BNB", "XRP"};
    private static final String[] NOMBRES_MONEDA = {
            "Bitcoin (BTC)", "Ethereum (ETH)", "Solana (SOL)", "Tether (USDT)", "BNB (BNB)", "XRP (XRP)"
    };

    private static final String[] MOTIVOS = {
            "Varios", "Alquiler", "Servicios", "Préstamo", "Regalo", "Otro"
    };

    private final NumberFormat formatoMonto = NumberFormat.getNumberInstance(new Locale("es", "AR"));
    private final double saldoDisponible = 0;
    private String codigoMoneda = CODIGOS_MONEDA[0];

    private TextView tvMonedaSeleccionada, tvMonedaResumen, tvMonedaInput;
    private TextView tvSaldoActual, tvSaldoDisponible, tvMontoResumen, tvSaldoLuego, tvMotivo;
    private LinearLayout opcionAhora, opcionPendiente;
    private ImageView ivIconoAhora, ivIconoPendiente;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transferencia_cripto);

        formatoMonto.setMinimumFractionDigits(2);
        formatoMonto.setMaximumFractionDigits(2);

        tvMonedaSeleccionada = findViewById(R.id.tvMonedaSeleccionada);
        tvMonedaResumen = findViewById(R.id.tvMonedaResumen);
        tvMonedaInput = findViewById(R.id.tvMonedaInput);
        tvSaldoActual = findViewById(R.id.tvSaldoActual);
        tvSaldoDisponible = findViewById(R.id.tvSaldoDisponible);
        tvMontoResumen = findViewById(R.id.tvMontoResumen);
        tvSaldoLuego = findViewById(R.id.tvSaldoLuego);
        tvMotivo = findViewById(R.id.tvMotivo);
        opcionAhora = findViewById(R.id.opcionAhora);
        opcionPendiente = findViewById(R.id.opcionPendiente);
        ivIconoAhora = findViewById(R.id.ivIconoAhora);
        ivIconoPendiente = findViewById(R.id.ivIconoPendiente);

        actualizarMoneda();

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

        findViewById(R.id.filaMoneda).setOnClickListener(this::mostrarMonedas);
        findViewById(R.id.filaMotivo).setOnClickListener(this::mostrarMotivos);

        opcionAhora.setOnClickListener(v -> seleccionarMetodoEnvio(true));
        opcionPendiente.setOnClickListener(v -> seleccionarMetodoEnvio(false));

        findViewById(R.id.btnVolver).setOnClickListener(v -> finish());
        findViewById(R.id.btnCerrar).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancelar).setOnClickListener(v -> finish());
        findViewById(R.id.btnContinuar).setOnClickListener(v ->
                Toast.makeText(this, "Esta función va a estar disponible próximamente.", Toast.LENGTH_SHORT).show());
    }

    private void mostrarMonedas(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        for (String nombre : NOMBRES_MONEDA) {
            popup.getMenu().add(nombre);
        }
        popup.setOnMenuItemClickListener(item -> {
            String nombre = item.getTitle().toString();
            for (int i = 0; i < NOMBRES_MONEDA.length; i++) {
                if (NOMBRES_MONEDA[i].equals(nombre)) {
                    codigoMoneda = CODIGOS_MONEDA[i];
                    break;
                }
            }
            actualizarMoneda();
            return true;
        });
        popup.show();
    }

    private void actualizarMoneda() {
        int indice = 0;
        for (int i = 0; i < CODIGOS_MONEDA.length; i++) {
            if (CODIGOS_MONEDA[i].equals(codigoMoneda)) indice = i;
        }
        tvMonedaSeleccionada.setText(NOMBRES_MONEDA[indice]);
        tvMonedaResumen.setText(codigoMoneda);
        tvMonedaInput.setText(codigoMoneda);

        String saldoFormateado = formatoMonto.format(saldoDisponible) + " " + codigoMoneda;
        tvSaldoActual.setText(saldoFormateado);
        tvSaldoDisponible.setText(saldoFormateado);
        tvSaldoLuego.setText(saldoFormateado);
    }

    private void actualizarResumen(String textoMonto) {
        double monto = 0;
        try {
            monto = Double.parseDouble(textoMonto.replace(",", "."));
        } catch (NumberFormatException ignored) {}

        tvMontoResumen.setText(formatoMonto.format(monto));
        double restante = Math.max(saldoDisponible - monto, 0);
        tvSaldoLuego.setText(formatoMonto.format(restante) + " " + codigoMoneda);
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
