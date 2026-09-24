package com.example.payxmobile.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.payxmobile.R;
import com.example.payxmobile.model.CotizacionDolar;
import com.example.payxmobile.model.DestinatarioResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.saldos.EstadoSaldos;
import com.example.payxmobile.saldos.SaldosRepository;
import com.example.payxmobile.transferencias.ContactosFrecuentes;
import com.example.payxmobile.transferencias.EnvioTransferencia;
import com.example.payxmobile.transferencias.EnvioViewModel;
import com.example.payxmobile.transferencias.FormatoTransferencia;
import com.example.payxmobile.transferencias.Moneda;
import com.example.payxmobile.transferencias.MontoInput;
import com.example.payxmobile.transferencias.Refrescos;
import com.example.payxmobile.transferencias.TransferenciasRepository;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Enviar una transferencia en pesos, dólares o cripto, en 3 pasos (como TransferModal de la web).
 *
 * Acepta datos PRECARGADOS por Intent (lo va a usar el asistente de IA): EXTRA_DESTINATARIO,
 * EXTRA_MONTO (texto, ej. "1500.50"), EXTRA_MOTIVO, EXTRA_TIPO ("DIRECTA"/"PENDIENTE") y
 * EXTRA_MONEDA. La precarga solo completa el formulario: el usuario siempre tiene que tocar
 * "Continuar" y "Confirmar".
 */
public class TransferenciaActivity extends AppCompatActivity {

    public static final String EXTRA_MONEDA = "moneda";
    public static final String EXTRA_DESTINATARIO = "destinatario";
    public static final String EXTRA_MONTO = "monto";
    public static final String EXTRA_MOTIVO = "motivo";
    public static final String EXTRA_TIPO = "tipo";

    // Mismos motivos que la web
    static final String[] MOTIVOS = {
            "Alquiler",
            "Servicios (luz, agua, gas, internet)",
            "Comida y supermercado",
            "Transporte",
            "Salud",
            "Educación",
            "Entretenimiento",
            "Préstamo o devolución",
            "Regalo",
            "Otro",
    };
    private static final String SIN_MOTIVO = "Sin motivo";

    private static final String K_MONEDA = "t_moneda", K_DEST = "t_dest", K_MONTO = "t_monto",
            K_MOTIVO = "t_motivo", K_TIPO = "t_tipo", K_PASO = "t_paso", K_NOMBRE = "t_nombre",
            K_ALIAS = "t_alias", K_CVU = "t_cvu";

    private EnvioTransferencia envio;
    private boolean esPantallaCripto;
    private EstadoSaldos estadoSaldos;
    private List<ContactosFrecuentes.Contacto> contactos = Collections.emptyList();
    private BigDecimal ventaDolar; // null = sin cotización: se oculta el "≈ $"
    private boolean actualizandoCampos;

    private final SaldosRepository.Observador observadorSaldos = e -> {
        estadoSaldos = e;
        render();
    };
    private final TransferenciasRepository.Observador observadorTransferencias = e -> {
        contactos = e.lista != null ? ContactosFrecuentes.de(e.lista) : Collections.emptyList();
        renderSugerencias();
    };

    private View seccionFormulario, seccionConfirmar, seccionExito, filaCriptos, filaMotivoConfirmar;
    private LinearLayout layoutCriptos, layoutSugerencias, opcionAhora, opcionPendiente;
    private EditText etDestinatario, etMonto;
    private TextView tvTitulo, tvMontoResumen, tvDestinoResumen, tvMotivoResumen, tvSaldoActual, tvSaldoLuego,
            tvAvisoPendienteResumen, tvSaldoDisponible, tvSimboloInput, tvEquivalenteUsd, tvMotivo,
            tvErrorFormulario, tvMontoConfirmar, tvEquivalenteConfirmar, tvNombreDestino, tvAliasDestino,
            tvCvuDestino, tvMotivoConfirmar, tvTipoEnvio, tvAvisoPendienteConfirmar, tvErrorConfirmar,
            tvTituloExito, tvTextoExito, tvAvisoExito;
    private ImageView ivIconoAhora, ivIconoPendiente, ivIconoExito;
    private Button btnContinuar, btnConfirmar, btnEditar, btnVerMisTransferencias;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transferencia);
        vincularVistas();

        Moneda monedaInicial = Moneda.desde(getIntent().getStringExtra(EXTRA_MONEDA));
        if (monedaInicial == null) monedaInicial = Moneda.PESOS;
        esPantallaCripto = monedaInicial.esCripto();

        EnvioViewModel vm = new ViewModelProvider(this).get(EnvioViewModel.class);
        envio = obtenerEnvio(vm, monedaInicial, savedInstanceState);
        envio.setObservador(e -> render());

        tvTitulo.setText(esPantallaCripto ? "Transferencia en cripto"
                : monedaInicial == Moneda.USD ? "Transferencia en dólares" : "Transferir dinero");
        filaCriptos.setVisibility(esPantallaCripto ? View.VISIBLE : View.GONE);
        if (esPantallaCripto) armarChipsCripto();

        configurarCampos();
        configurarBotones();
        render();
    }

    /** El flujo vive en el ViewModel (rotación). Si el proceso murió, se rearma desde el Bundle. */
    private EnvioTransferencia obtenerEnvio(EnvioViewModel vm, Moneda monedaInicial, Bundle guardado) {
        if (vm.envio != null) return vm.envio;
        Context app = getApplicationContext();
        Moneda moneda = monedaInicial;
        if (guardado != null && Moneda.desde(guardado.getString(K_MONEDA)) != null) {
            moneda = Moneda.desde(guardado.getString(K_MONEDA));
        }
        EnvioTransferencia nuevo = new EnvioTransferencia(moneda,
                () -> RetrofitClient.getService(app),
                () -> RetrofitClient.getServiceSinReintentos(app),
                () -> Refrescos.trasMoverPlata(app));
        if (guardado != null) {
            nuevo.setDestinatario(guardado.getString(K_DEST, ""));
            nuevo.setMonto(guardado.getString(K_MONTO, ""));
            nuevo.setMotivo(guardado.getString(K_MOTIVO));
            nuevo.setTipo(guardado.getString(K_TIPO));
            String paso = guardado.getString(K_PASO);
            DestinatarioResponse info = guardado.getString(K_NOMBRE) != null
                    ? new DestinatarioResponse(guardado.getString(K_NOMBRE), guardado.getString(K_ALIAS), guardado.getString(K_CVU))
                    : null;
            if (paso != null) nuevo.restaurar(EnvioTransferencia.Paso.valueOf(paso), info, guardado.getString(K_NOMBRE));
        } else {
            Intent i = getIntent();
            nuevo.precargar(i.getStringExtra(EXTRA_DESTINATARIO), i.getStringExtra(EXTRA_MONTO),
                    i.getStringExtra(EXTRA_MOTIVO), i.getStringExtra(EXTRA_TIPO));
        }
        vm.envio = nuevo;
        return nuevo;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(K_MONEDA, envio.getMoneda().codigo());
        out.putString(K_DEST, envio.getDestinatario());
        out.putString(K_MONTO, envio.getMontoTexto());
        out.putString(K_MOTIVO, envio.getMotivo());
        out.putString(K_TIPO, envio.getTipo());
        out.putString(K_PASO, envio.getPaso().name());
        DestinatarioResponse info = envio.getDestinatarioInfo();
        if (envio.getPaso() == EnvioTransferencia.Paso.EXITO) {
            out.putString(K_NOMBRE, envio.nombreDestino());
        } else if (info != null) {
            out.putString(K_NOMBRE, info.getNombreCompleto());
            out.putString(K_ALIAS, info.getAlias());
            out.putString(K_CVU, info.getCvu());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        SaldosRepository saldos = SaldosRepository.get(this);
        saldos.observar(observadorSaldos);
        saldos.refrescar();
        TransferenciasRepository transferencias = TransferenciasRepository.get(this);
        transferencias.observar(observadorTransferencias);
        transferencias.refrescar();
        if (envio.getMoneda() == Moneda.USD) cargarCotizacionDolar();
    }

    @Override
    protected void onStop() {
        super.onStop();
        SaldosRepository.get(this).dejarDeObservar(observadorSaldos);
        TransferenciasRepository.get(this).dejarDeObservar(observadorTransferencias);
    }

    // ── Vistas ────────────────────────────────────────────────────────────────

    private void vincularVistas() {
        seccionFormulario = findViewById(R.id.seccionFormulario);
        seccionConfirmar = findViewById(R.id.seccionConfirmar);
        seccionExito = findViewById(R.id.seccionExito);
        filaCriptos = findViewById(R.id.filaCriptos);
        layoutCriptos = findViewById(R.id.layoutCriptos);
        layoutSugerencias = findViewById(R.id.layoutSugerencias);
        opcionAhora = findViewById(R.id.opcionAhora);
        opcionPendiente = findViewById(R.id.opcionPendiente);
        etDestinatario = findViewById(R.id.etDestinatario);
        etMonto = findViewById(R.id.etMonto);
        tvTitulo = findViewById(R.id.tvTitulo);
        tvMontoResumen = findViewById(R.id.tvMontoResumen);
        tvDestinoResumen = findViewById(R.id.tvDestinoResumen);
        tvMotivoResumen = findViewById(R.id.tvMotivoResumen);
        tvSaldoActual = findViewById(R.id.tvSaldoActual);
        tvSaldoLuego = findViewById(R.id.tvSaldoLuego);
        tvAvisoPendienteResumen = findViewById(R.id.tvAvisoPendienteResumen);
        tvSaldoDisponible = findViewById(R.id.tvSaldoDisponible);
        tvSimboloInput = findViewById(R.id.tvSimboloInput);
        tvEquivalenteUsd = findViewById(R.id.tvEquivalenteUsd);
        tvMotivo = findViewById(R.id.tvMotivo);
        tvErrorFormulario = findViewById(R.id.tvErrorFormulario);
        tvMontoConfirmar = findViewById(R.id.tvMontoConfirmar);
        tvEquivalenteConfirmar = findViewById(R.id.tvEquivalenteConfirmar);
        tvNombreDestino = findViewById(R.id.tvNombreDestino);
        tvAliasDestino = findViewById(R.id.tvAliasDestino);
        tvCvuDestino = findViewById(R.id.tvCvuDestino);
        filaMotivoConfirmar = findViewById(R.id.filaMotivoConfirmar);
        tvMotivoConfirmar = findViewById(R.id.tvMotivoConfirmar);
        tvTipoEnvio = findViewById(R.id.tvTipoEnvio);
        tvAvisoPendienteConfirmar = findViewById(R.id.tvAvisoPendienteConfirmar);
        tvErrorConfirmar = findViewById(R.id.tvErrorConfirmar);
        tvTituloExito = findViewById(R.id.tvTituloExito);
        tvTextoExito = findViewById(R.id.tvTextoExito);
        tvAvisoExito = findViewById(R.id.tvAvisoExito);
        ivIconoAhora = findViewById(R.id.ivIconoAhora);
        ivIconoPendiente = findViewById(R.id.ivIconoPendiente);
        ivIconoExito = findViewById(R.id.ivIconoExito);
        btnContinuar = findViewById(R.id.btnContinuar);
        btnConfirmar = findViewById(R.id.btnConfirmar);
        btnEditar = findViewById(R.id.btnEditar);
        btnVerMisTransferencias = findViewById(R.id.btnVerMisTransferencias);
    }

    private void configurarCampos() {
        actualizandoCampos = true;
        etDestinatario.setText(envio.getDestinatario());
        etMonto.setText(envio.getMontoTexto());
        actualizandoCampos = false;

        etDestinatario.addTextChangedListener(new Observar(s -> {
            envio.setDestinatario(s);
            render();
        }));
        etDestinatario.setOnFocusChangeListener((v, foco) -> renderSugerencias());

        // Bloquea el tipeo de más decimales (2 en pesos/dólares, 8 en cripto) o más de 13 enteros
        etMonto.setFilters(new InputFilter[]{(fuente, inicio, fin, destino, dInicio, dFin) -> {
            String resultado = destino.subSequence(0, dInicio) + fuente.subSequence(inicio, fin).toString()
                    + destino.subSequence(dFin, destino.length());
            return MontoInput.esTipeoValido(resultado, envio.getMoneda()) ? null : "";
        }});
        etMonto.addTextChangedListener(new Observar(s -> {
            envio.setMonto(s);
            render();
        }));
    }

    private void configurarBotones() {
        findViewById(R.id.btnUsarTodo).setOnClickListener(v -> {
            BigDecimal saldo = saldoDisponible();
            if (saldo == null) {
                Toast.makeText(this, "Todavía no pudimos cargar tu saldo", Toast.LENGTH_SHORT).show();
                return;
            }
            envio.usarTodo(saldo);
            ponerTextoMonto(envio.getMontoTexto());
        });
        findViewById(R.id.filaMotivo).setOnClickListener(this::mostrarMotivos);
        opcionAhora.setOnClickListener(v -> {
            envio.setTipo(EnvioTransferencia.DIRECTA);
            render();
        });
        opcionPendiente.setOnClickListener(v -> {
            envio.setTipo(EnvioTransferencia.PENDIENTE);
            render();
        });

        btnContinuar.setOnClickListener(v -> {
            if (estadoSaldos == null || estadoSaldos.perfil == null) {
                Toast.makeText(this, "Todavía no pudimos cargar tu saldo. Probá de nuevo en unos segundos", Toast.LENGTH_SHORT).show();
                SaldosRepository.get(this).refrescar();
                return;
            }
            layoutSugerencias.setVisibility(View.GONE);
            envio.continuar(saldoDisponible());
        });
        btnConfirmar.setOnClickListener(v -> envio.confirmar());
        btnEditar.setOnClickListener(v -> envio.editar());
        btnVerMisTransferencias.setOnClickListener(v -> {
            startActivity(new Intent(this, MisTransferenciasActivity.class));
            finish();
        });
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
    }

    /** Mientras se crea la transferencia no se deja salir: el resultado se perdería de vista. */
    private void intentarCerrar() {
        if (envio.isEnviando()) {
            Toast.makeText(this, "Esperá a que termine la transferencia", Toast.LENGTH_SHORT).show();
            return;
        }
        finish();
    }

    private void armarChipsCripto() {
        layoutCriptos.removeAllViews();
        for (Moneda m : Moneda.values()) {
            if (!m.esCripto()) continue;
            TextView chip = new TextView(this);
            chip.setText(m.simbolo);
            chip.setTextSize(12.5f);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setBackgroundResource(R.drawable.bg_coin_chip);
            chip.setTextColor(getColorStateList(R.color.coin_chip_text));
            int h = dp(16), vv = dp(8);
            chip.setPadding(h, vv, h, vv);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);
            chip.setTag(m);
            chip.setOnClickListener(v -> {
                envio.setMoneda(m);
                ponerTextoMonto(envio.getMontoTexto()); // la web borra el monto al cambiar de cripto
                render();
            });
            layoutCriptos.addView(chip);
        }
    }

    private void mostrarMotivos(View ancla) {
        PopupMenu popup = new PopupMenu(this, ancla);
        popup.getMenu().add(SIN_MOTIVO);
        for (String motivo : MOTIVOS) popup.getMenu().add(motivo);
        popup.setOnMenuItemClickListener(item -> {
            String elegido = item.getTitle().toString();
            envio.setMotivo(SIN_MOTIVO.equals(elegido) ? null : elegido);
            render();
            return true;
        });
        popup.show();
    }

    private void cargarCotizacionDolar() {
        RetrofitClient.getService(this).obtenerCotizacionDolar().enqueue(new Callback<CotizacionDolar>() {
            @Override
            public void onResponse(Call<CotizacionDolar> call, Response<CotizacionDolar> response) {
                // 503 u otro error: sin cotización -> se oculta el equivalente (nunca "0")
                ventaDolar = response.isSuccessful() && response.body() != null ? response.body().getVenta() : null;
                render();
            }

            @Override
            public void onFailure(Call<CotizacionDolar> call, Throwable t) {
                ventaDolar = null;
                render();
            }
        });
    }

    // ── Render ────────────────────────────────────────────────────────────────

    private BigDecimal saldoDisponible() {
        if (estadoSaldos == null || estadoSaldos.perfil == null) return null;
        return envio.getMoneda().saldoEn(estadoSaldos.perfil);
    }

    private void render() {
        EnvioTransferencia.Paso paso = envio.getPaso();
        seccionFormulario.setVisibility(paso == EnvioTransferencia.Paso.FORMULARIO ? View.VISIBLE : View.GONE);
        boolean confirmarOIncierto = paso == EnvioTransferencia.Paso.CONFIRMAR || paso == EnvioTransferencia.Paso.INCIERTO;
        seccionConfirmar.setVisibility(confirmarOIncierto ? View.VISIBLE : View.GONE);
        seccionExito.setVisibility(paso == EnvioTransferencia.Paso.EXITO ? View.VISIBLE : View.GONE);

        if (paso == EnvioTransferencia.Paso.FORMULARIO) renderFormulario();
        else if (confirmarOIncierto) renderConfirmar(paso == EnvioTransferencia.Paso.INCIERTO);
        else renderExito();
    }

    private void renderFormulario() {
        Moneda moneda = envio.getMoneda();
        BigDecimal saldo = saldoDisponible();
        BigDecimal monto = envio.getMonto();

        for (int i = 0; i < layoutCriptos.getChildCount(); i++) {
            View chip = layoutCriptos.getChildAt(i);
            chip.setSelected(chip.getTag() == moneda);
        }
        tvSimboloInput.setText(moneda.simbolo);
        etMonto.setHint(moneda.esCripto() ? "0,00000000" : "0,00");

        String saldoTexto = saldo != null ? FormatoTransferencia.montoFormulario(saldo, moneda) : "—";
        tvSaldoDisponible.setText(saldoTexto);
        tvSaldoActual.setText(saldoTexto);
        tvMontoResumen.setText(FormatoTransferencia.montoFormulario(monto != null ? monto : BigDecimal.ZERO, moneda));

        String dest = envio.getDestinatario().trim();
        tvDestinoResumen.setVisibility(dest.isEmpty() ? View.GONE : View.VISIBLE);
        tvDestinoResumen.setText("a " + dest);
        tvMotivoResumen.setVisibility(envio.getMotivo() != null ? View.VISIBLE : View.GONE);
        tvMotivoResumen.setText("Motivo: " + envio.getMotivo());
        tvMotivo.setText(envio.getMotivo() != null ? envio.getMotivo() : "Seleccioná un motivo");
        tvMotivo.setTextColor(getColor(envio.getMotivo() != null ? R.color.text_primary : R.color.text_hint));

        BigDecimal luego = envio.saldoLuego(saldo);
        tvSaldoLuego.setText(luego != null ? FormatoTransferencia.montoFormulario(luego, moneda) : "—");
        tvSaldoLuego.setTextColor(getColor(luego != null && luego.signum() < 0 ? R.color.color_negativo : R.color.text_primary));

        boolean pendiente = envio.esPendiente();
        tvAvisoPendienteResumen.setVisibility(pendiente ? View.VISIBLE : View.GONE);
        opcionAhora.setBackgroundResource(pendiente ? R.drawable.bg_opcion_envio_normal : R.drawable.bg_opcion_envio_selected);
        opcionPendiente.setBackgroundResource(pendiente ? R.drawable.bg_opcion_envio_selected : R.drawable.bg_opcion_envio_normal);
        ivIconoAhora.setImageTintList(getColorStateList(pendiente ? R.color.text_secondary : R.color.payx_orange));
        ivIconoPendiente.setImageTintList(getColorStateList(pendiente ? R.color.payx_orange : R.color.text_secondary));

        String equivalente = moneda == Moneda.USD && monto != null
                ? FormatoTransferencia.equivalenteUsd(monto, ventaDolar) : null;
        tvEquivalenteUsd.setVisibility(equivalente != null ? View.VISIBLE : View.GONE);
        tvEquivalenteUsd.setText(equivalente);

        mostrarError(tvErrorFormulario, envio.getError());
        btnContinuar.setEnabled(!envio.isResolviendo());
        btnContinuar.setText(envio.isResolviendo() ? "Buscando destinatario..." : "Continuar");
        renderSugerencias();
    }

    private void renderConfirmar(boolean incierto) {
        Moneda moneda = envio.getMoneda();
        BigDecimal monto = envio.getMontoAEnviar();
        DestinatarioResponse info = envio.getDestinatarioInfo();
        tvMontoConfirmar.setText(monto != null ? FormatoTransferencia.montoFormulario(monto, moneda) : "");
        String equivalente = moneda == Moneda.USD ? FormatoTransferencia.equivalenteUsd(monto, ventaDolar) : null;
        tvEquivalenteConfirmar.setVisibility(equivalente != null ? View.VISIBLE : View.GONE);
        tvEquivalenteConfirmar.setText(equivalente);

        tvNombreDestino.setText(info != null ? info.getNombreCompleto() : envio.getDestinatario().trim());
        tvAliasDestino.setText(info != null && info.getAlias() != null ? info.getAlias() : "—");
        tvCvuDestino.setText(info != null && info.getCvu() != null ? info.getCvu() : "—");
        filaMotivoConfirmar.setVisibility(envio.getMotivo() != null ? View.VISIBLE : View.GONE);
        tvMotivoConfirmar.setText(envio.getMotivo());
        tvTipoEnvio.setText(envio.esPendiente() ? "Pendiente (a confirmar después)" : "Directa (inmediata)");
        tvAvisoPendienteConfirmar.setVisibility(envio.esPendiente() ? View.VISIBLE : View.GONE);

        mostrarError(tvErrorConfirmar, envio.getError());
        // Resultado incierto: nada de reintentar desde acá, se revisa el listado
        btnConfirmar.setVisibility(incierto ? View.GONE : View.VISIBLE);
        btnEditar.setVisibility(incierto ? View.GONE : View.VISIBLE);
        btnVerMisTransferencias.setVisibility(incierto ? View.VISIBLE : View.GONE);
        btnConfirmar.setEnabled(!envio.isEnviando());
        btnEditar.setEnabled(!envio.isEnviando());
        btnConfirmar.setText(envio.isEnviando() ? "Procesando..."
                : envio.esPendiente() ? "Confirmar y dejar pendiente" : "Confirmar transferencia");
    }

    private void renderExito() {
        String monto = FormatoTransferencia.montoFormulario(envio.getMontoAEnviar() != null
                ? envio.getMontoAEnviar() : BigDecimal.ZERO, envio.getMoneda());
        boolean pendiente = envio.esPendiente();
        ivIconoExito.setImageResource(pendiente ? R.drawable.ic_clock : R.drawable.ic_check);
        tvTituloExito.setText(pendiente ? "Transferencia pendiente creada" : "¡Transferencia realizada!");
        tvTextoExito.setText(pendiente
                ? "Dejaste pendiente una transferencia de " + monto + " a " + envio.nombreDestino()
                + ". No se descontó nada todavía: podés confirmarla o cancelarla cuando quieras"
                : "Le transferiste " + monto + " a " + envio.nombreDestino());
        tvAvisoExito.setVisibility(pendiente ? View.VISIBLE : View.GONE);
    }

    private void renderSugerencias() {
        if (envio == null || layoutSugerencias == null) return;
        boolean mostrar = envio.getPaso() == EnvioTransferencia.Paso.FORMULARIO && etDestinatario.hasFocus();
        List<ContactosFrecuentes.Contacto> lista = mostrar
                ? ContactosFrecuentes.sugerencias(contactos, envio.getDestinatario()) : Collections.emptyList();
        // Si lo escrito ya es exactamente un alias sugerido, no hace falta seguir mostrándolo
        if (lista.size() == 1 && lista.get(0).alias.equalsIgnoreCase(envio.getDestinatario().trim())) {
            lista = Collections.emptyList();
        }
        layoutSugerencias.removeAllViews();
        layoutSugerencias.setVisibility(lista.isEmpty() ? View.GONE : View.VISIBLE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ContactosFrecuentes.Contacto c : lista) {
            View item = inflater.inflate(R.layout.item_sugerencia_contacto, layoutSugerencias, false);
            ((TextView) item.findViewById(R.id.tvAliasSugerencia)).setText(c.alias);
            ((TextView) item.findViewById(R.id.tvNombreSugerencia)).setText(c.nombre);
            item.setOnClickListener(v -> {
                actualizandoCampos = true;
                etDestinatario.setText(c.alias);
                etDestinatario.setSelection(c.alias.length());
                actualizandoCampos = false;
                envio.setDestinatario(c.alias);
                layoutSugerencias.setVisibility(View.GONE);
                render();
            });
            layoutSugerencias.addView(item);
        }
    }

    private void mostrarError(TextView tv, String error) {
        tv.setVisibility(error != null ? View.VISIBLE : View.GONE);
        tv.setText(error);
    }

    private void ponerTextoMonto(String texto) {
        actualizandoCampos = true;
        etMonto.setText(texto);
        etMonto.setSelection(texto.length());
        actualizandoCampos = false;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    /** TextWatcher que ignora los cambios hechos por código (setText al restaurar o al usar todo). */
    private class Observar implements TextWatcher {
        private final java.util.function.Consumer<String> alCambiar;

        Observar(java.util.function.Consumer<String> alCambiar) {
            this.alCambiar = alCambiar;
        }

        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

        @Override
        public void afterTextChanged(Editable s) {
            if (!actualizandoCampos) alCambiar.accept(s.toString());
        }
    }
}
