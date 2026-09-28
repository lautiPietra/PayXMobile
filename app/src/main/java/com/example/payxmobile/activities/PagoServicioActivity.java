package com.example.payxmobile.activities;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.payxmobile.R;
import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.model.FacturaResponse;
import com.example.payxmobile.model.ServicioConFacturaResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.saldos.EstadoSaldos;
import com.example.payxmobile.saldos.SaldosRepository;
import com.example.payxmobile.servicios.FacturaAPagar;
import com.example.payxmobile.servicios.PagoFactura;
import com.example.payxmobile.servicios.ServiciosRepository;
import com.example.payxmobile.servicios.ServiciosViewModel;
import com.example.payxmobile.servicios.ui.EstiloServicio;
import com.example.payxmobile.transferencias.FormatoTransferencia;
import com.example.payxmobile.transferencias.Refrescos;
import com.example.payxmobile.utils.MontoFormatter;
import com.example.payxmobile.utils.SesionUtils;
import com.google.gson.Gson;

import java.math.BigDecimal;
import java.time.ZoneId;

/**
 * Confirmación y pago de una factura (FacturaPagoModal.jsx). EXTRA_FACTURA: FacturaAPagar en JSON
 * (monto exacto del backend). Toda la lógica vive en {@link PagoFactura}.
 */
public class PagoServicioActivity extends AppCompatActivity {

    public static final String EXTRA_FACTURA = "factura";
    private static final String K_INCIERTO = "ps_incierto";

    private PagoFactura pago;
    private EstadoSaldos estadoSaldos;
    private final SaldosRepository.Observador observadorSaldos = e -> {
        estadoSaldos = e;
        if (e.sesionInvalida) {
            SesionUtils.sesionInvalida(this);
            return;
        }
        render();
    };

    private View seccionConfirmar, seccionResultado;
    private TextView tvSaldo, tvError, tvTituloResultado, tvTextoResultado, tvDetalleResultado;
    private ImageView ivIconoResultado;
    private Button btnConfirmar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pago_servicio);
        pago = obtenerPago(new ViewModelProvider(this).get(ServiciosViewModel.class), savedInstanceState);
        if (pago == null) {
            finish();
            return;
        }
        pago.setObservador(p -> render());

        seccionConfirmar = findViewById(R.id.seccionConfirmar);
        seccionResultado = findViewById(R.id.seccionResultado);
        tvSaldo = findViewById(R.id.tvSaldo);
        tvError = findViewById(R.id.tvError);
        tvTituloResultado = findViewById(R.id.tvTituloResultado);
        tvTextoResultado = findViewById(R.id.tvTextoResultado);
        tvDetalleResultado = findViewById(R.id.tvDetalleResultado);
        ivIconoResultado = findViewById(R.id.ivIconoResultado);
        btnConfirmar = findViewById(R.id.btnConfirmar);

        FacturaAPagar f = pago.getFactura();
        ImageView icono = findViewById(R.id.ivIcono);
        icono.setImageResource(EstiloServicio.icono(f.servicioCodigo));
        icono.setBackgroundTintList(getColorStateList(EstiloServicio.color(f.servicioCodigo)));
        ((TextView) findViewById(R.id.tvTitulo)).setText("Pagar " + f.nombre);
        ((TextView) findViewById(R.id.tvSubtitulo)).setText(f.periodo != null ? f.periodo
                : f.proveedor != null ? f.proveedor : "");
        ((TextView) findViewById(R.id.tvMonto)).setText("$ " + MontoFormatter.fiat(f.monto));
        findViewById(R.id.tvAvisoVencida).setVisibility(f.vencida ? View.VISIBLE : View.GONE);

        btnConfirmar.setOnClickListener(v -> pago.confirmar(saldoPesos()));
        findViewById(R.id.btnListo).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancelar).setOnClickListener(v -> intentarCerrar());
        findViewById(R.id.btnVolver).setOnClickListener(v -> intentarCerrar());
        findViewById(R.id.btnCerrar).setOnClickListener(v -> intentarCerrar());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                intentarCerrar();
            }
        });
        render();
    }

    private PagoFactura obtenerPago(ServiciosViewModel vm, Bundle guardado) {
        if (vm.pago != null) return vm.pago;
        String json = getIntent().getStringExtra(EXTRA_FACTURA);
        if (json == null) return null;
        FacturaAPagar f = new Gson().fromJson(json, FacturaAPagar.class);
        Context app = getApplicationContext();
        PagoFactura p = new PagoFactura(f, () -> RetrofitClient.getServiceSinReintentos(app),
                pagada -> mostrarPagada(app, pagada),
                () -> {
                    Refrescos.trasMoverPlata(app); // saldos, notificaciones e historial (feed)
                    ServiciosRepository.catalogo(app).refrescar();
                });
        if (guardado != null && guardado.getBoolean(K_INCIERTO)) p.restaurarIncierto();
        vm.pago = p;
        return p;
    }

    /** El pago aparece YA en la tarjeta del servicio, en el historial y en el feed (sin esperar al GET). */
    private static void mostrarPagada(Context app, FacturaResponse pagada) {
        ServiciosRepository.historial(app).actualizar(pagada);
        ListaRemota<ServicioConFacturaResponse> catalogo = ServiciosRepository.catalogo(app);
        ListaRemota.Estado<ServicioConFacturaResponse> e = catalogo.getEstado();
        if (e.lista == null) return;
        for (ServicioConFacturaResponse s : e.lista) {
            if (pagada.getId().equals(s.getFacturaId())) {
                catalogo.actualizar(s.conPago(pagada));
                return;
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (pago == null) return;
        // Si el proceso muere con el POST en vuelo no sabemos cómo terminó
        out.putBoolean(K_INCIERTO, pago.isEnviando() || pago.getPaso() == PagoFactura.Paso.INCIERTO);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (pago == null) return;
        SaldosRepository saldos = SaldosRepository.get(this);
        saldos.observar(observadorSaldos);
        saldos.refrescar();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (pago == null) return;
        SaldosRepository.get(this).dejarDeObservar(observadorSaldos);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pago != null) pago.setObservador(null);
    }

    private void intentarCerrar() {
        if (pago.isEnviando()) {
            Toast.makeText(this, "Esperá a que termine el pago", Toast.LENGTH_SHORT).show();
            return;
        }
        finish();
    }

    private BigDecimal saldoPesos() {
        return estadoSaldos != null && estadoSaldos.perfil != null ? estadoSaldos.perfil.getSaldoPesos() : null;
    }

    private void render() {
        if (isFinishing() || seccionConfirmar == null) return;
        boolean confirmar = pago.getPaso() == PagoFactura.Paso.CONFIRMAR;
        seccionConfirmar.setVisibility(confirmar ? View.VISIBLE : View.GONE);
        seccionResultado.setVisibility(confirmar ? View.GONE : View.VISIBLE);
        if (confirmar) renderConfirmar();
        else renderResultado();
    }

    private void renderConfirmar() {
        BigDecimal saldo = saldoPesos();
        tvSaldo.setText(saldo != null ? "$ " + MontoFormatter.fiat(saldo) : MontoFormatter.SIN_DATO);
        // Como la web: sin saldo suficiente se avisa ANTES y no se deja confirmar
        String previo = saldo != null ? PagoFactura.validar(pago.getFactura().monto, saldo) : null;
        String error = pago.getError() != null ? pago.getError() : previo;
        tvError.setVisibility(error != null ? View.VISIBLE : View.GONE);
        tvError.setText(error);
        boolean enviando = pago.isEnviando();
        btnConfirmar.setEnabled(!enviando && previo == null);
        btnConfirmar.setAlpha(btnConfirmar.isEnabled() ? 1f : 0.5f);
        btnConfirmar.setText(enviando ? "Pagando..." : "Confirmar pago");
    }

    private void renderResultado() {
        if (pago.getPaso() == PagoFactura.Paso.EXITO) {
            FacturaResponse r = pago.getResultado();
            ivIconoResultado.setImageResource(R.drawable.ic_check);
            ivIconoResultado.setImageTintList(getColorStateList(R.color.color_positivo));
            tvTituloResultado.setText("¡Pago realizado!");
            tvTextoResultado.setText("Pagaste $ " + MontoFormatter.fiat(r.getMonto()) + " de " + r.getServicioNombre());
            tvDetalleResultado.setVisibility(View.VISIBLE);
            tvDetalleResultado.setText("El " + FormatoTransferencia.fechaHora(r.getFechaPago(), ZoneId.systemDefault()));
        } else {
            ivIconoResultado.setImageResource(R.drawable.ic_clock);
            ivIconoResultado.setImageTintList(getColorStateList(R.color.color_servicio_luz));
            tvTituloResultado.setText("No sabemos si se pagó");
            tvTextoResultado.setText(pago.getError());
            tvDetalleResultado.setVisibility(View.GONE);
        }
    }
}
