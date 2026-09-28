package com.example.payxmobile.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.payxmobile.R;
import com.example.payxmobile.actividad.ListaRemota;
import com.example.payxmobile.cajas.CajasAhorroRepository;
import com.example.payxmobile.cajas.CajasViewModel;
import com.example.payxmobile.cajas.EliminacionCaja;
import com.example.payxmobile.cajas.MovimientoCaja;
import com.example.payxmobile.cajas.MovimientosCajaSesion;
import com.example.payxmobile.cajas.VistaCajas;
import com.example.payxmobile.cajas.ui.EstiloCaja;
import com.example.payxmobile.model.CajaAhorroResponse;
import com.example.payxmobile.model.LimiteCajasResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.saldos.EstadoSaldos;
import com.example.payxmobile.saldos.SaldosRepository;
import com.example.payxmobile.transferencias.Refrescos;
import com.example.payxmobile.utils.MontoFormatter;
import com.example.payxmobile.utils.SesionUtils;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import java.time.Clock;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * "Cajas de ahorro" (CajasAhorro.jsx): las cajas con su saldo y meta, y las acciones Agregar,
 * Retirar, Editar y Eliminar. El máximo de cajas viene de GET /limite (lo configura el admin).
 */
public class CajasAhorroActivity extends AppCompatActivity {

    public static final String EXTRA_CAJA = "caja";

    private ListaRemota<CajaAhorroResponse> repo;
    private ListaRemota.Estado<CajaAhorroResponse> estado;
    private final ListaRemota.Observador<CajaAhorroResponse> observador = e -> {
        estado = e;
        render();
    };
    private EstadoSaldos estadoSaldos;
    private final SaldosRepository.Observador observadorSaldos = e -> {
        estadoSaldos = e;
        if (e.sesionInvalida) {
            SesionUtils.sesionInvalida(this);
            return;
        }
        renderSaldo();
    };
    /** De GET /limite; null mientras no llegó. */
    private Integer maxCajas;
    private EliminacionCaja eliminacion;
    private AlertDialog dialogoResultado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cajas_ahorro);
        repo = CajasAhorroRepository.get(this);
        eliminacion = obtenerEliminacion(new ViewModelProvider(this).get(CajasViewModel.class));
        eliminacion.setObservador(e -> {
            render();
            mostrarResultadoEliminacion();
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnReintentar).setOnClickListener(v -> repo.refrescar());
        findViewById(R.id.btnNuevaCaja).setOnClickListener(v -> {
            VistaCajas vista = VistaCajas.de(estado, maxCajas, eliminacion.idEnviando());
            if (vista.avisoLimite != null) {
                Toast.makeText(this, vista.avisoLimite, Toast.LENGTH_SHORT).show();
            } else if (!vista.puedeCrear) {
                Toast.makeText(this, VistaCajas.MSG_CARGANDO, Toast.LENGTH_SHORT).show();
            } else {
                abrir(new Intent(this, NuevaCajaAhorroActivity.class));
            }
        });
        mostrarResultadoEliminacion(); // p. ej. terminó mientras se rotaba
    }

    private EliminacionCaja obtenerEliminacion(CajasViewModel vm) {
        if (vm.eliminacion == null) {
            Context app = getApplicationContext();
            vm.eliminacion = new EliminacionCaja(() -> RetrofitClient.getServiceSinReintentos(app),
                    MovimientosCajaSesion.get(app), Clock.systemDefaultZone(),
                    caja -> CajasAhorroRepository.get(app).quitar(caja.getId()),
                    () -> Refrescos.trasMoverPlata(app));
        }
        return vm.eliminacion;
    }

    @Override
    protected void onStart() {
        super.onStart();
        repo.observar(observador);
        repo.iniciarAutoRefresco(); // YA y cada 10 s
        SaldosRepository saldos = SaldosRepository.get(this);
        saldos.observar(observadorSaldos);
        saldos.refrescar();
        cargarLimite();
    }

    @Override
    protected void onStop() {
        super.onStop();
        repo.detenerAutoRefresco();
        repo.dejarDeObservar(observador);
        SaldosRepository.get(this).dejarDeObservar(observadorSaldos);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        eliminacion.setObservador(null);
        if (dialogoResultado != null) dialogoResultado.dismiss();
    }

    /** Siempre antes de decidir si se puede crear otra: el admin pudo cambiarlo. */
    private void cargarLimite() {
        RetrofitClient.getService(this).obtenerLimiteCajas().enqueue(new Callback<LimiteCajasResponse>() {
            @Override
            public void onResponse(Call<LimiteCajasResponse> call, Response<LimiteCajasResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getMaxPorUsuario() != null) {
                    maxCajas = response.body().getMaxPorUsuario();
                    render();
                }
            }

            @Override
            public void onFailure(Call<LimiteCajasResponse> call, Throwable t) {
                // Sin límite conocido no se inventa uno: decide el backend (y su 400 se muestra)
            }
        });
    }

    private void abrir(Intent intent) {
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private Intent conCaja(Class<?> destino, CajaAhorroResponse caja) {
        return new Intent(this, destino).putExtra(EXTRA_CAJA, new Gson().toJson(caja));
    }

    // ── Eliminar ─────────────────────────────────────────────────────────────

    /** Siempre se confirma; si tiene saldo se aclara que vuelve a la cuenta (así lo hace el backend). */
    private void confirmarEliminar(CajaAhorroResponse caja) {
        String mensaje = EliminacionCaja.tieneSaldo(caja)
                ? "Los $ " + MontoFormatter.fiat(caja.getSaldo()) + " que tiene se devuelven a tu cuenta principal."
                : "Esta acción no se puede deshacer.";
        new MaterialAlertDialogBuilder(this)
                .setTitle("¿Eliminar \"" + caja.getNombre() + "\"?")
                .setMessage(mensaje)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (d, w) -> eliminacion.eliminar(caja))
                .show();
    }

    private void mostrarResultadoEliminacion() {
        EliminacionCaja.Estado e = eliminacion.getEstado();
        if (e == EliminacionCaja.Estado.ELIMINADA) {
            String texto = "Caja eliminada";
            if (eliminacion.saldoDevuelto().signum() > 0) {
                texto += ". Volvieron $ " + MontoFormatter.fiat(eliminacion.saldoDevuelto()) + " a tu cuenta";
            }
            Toast.makeText(this, texto, Toast.LENGTH_LONG).show();
            eliminacion.consumir();
        } else if ((e == EliminacionCaja.Estado.ERROR || e == EliminacionCaja.Estado.INCIERTO)
                && (dialogoResultado == null || !dialogoResultado.isShowing())) {
            dialogoResultado = new MaterialAlertDialogBuilder(this)
                    .setTitle(e == EliminacionCaja.Estado.INCIERTO ? "No sabemos si se eliminó" : "No se pudo eliminar")
                    .setMessage(eliminacion.getMensaje())
                    .setPositiveButton("Entendido", null)
                    .setOnDismissListener(d -> eliminacion.consumir())
                    .show();
        }
    }

    // ── Dibujo ────────────────────────────────────────────────────────────────

    private void renderSaldo() {
        TextView tv = findViewById(R.id.tvSaldoPrincipal);
        tv.setText(estadoSaldos != null && estadoSaldos.perfil != null
                ? "$ " + MontoFormatter.fiat(estadoSaldos.perfil.getSaldoPesos()) : MontoFormatter.SIN_DATO);
    }

    private void render() {
        if (isFinishing()) return;
        VistaCajas v = VistaCajas.de(estado, maxCajas, eliminacion.idEnviando());

        TextView contador = findViewById(R.id.tvContador);
        contador.setText(v.subtitulo != null ? v.subtitulo : "");
        TextView aviso = findViewById(R.id.tvAvisoLimite);
        aviso.setVisibility(v.avisoLimite != null ? View.VISIBLE : View.GONE);
        if (v.avisoLimite != null) aviso.setText(v.avisoLimite + ". Eliminá una para crear otra.");
        findViewById(R.id.btnNuevaCaja).setAlpha(v.puedeCrear ? 1f : 0.5f);
        findViewById(R.id.tvDesactualizado).setVisibility(v.desactualizado ? View.VISIBLE : View.GONE);

        boolean lista = v.modo == VistaCajas.Modo.LISTA;
        findViewById(R.id.cardEstado).setVisibility(lista ? View.GONE : View.VISIBLE);
        findViewById(R.id.progressEstado).setVisibility(v.modo == VistaCajas.Modo.CARGANDO ? View.VISIBLE : View.GONE);
        findViewById(R.id.ivEstado).setVisibility(v.modo == VistaCajas.Modo.VACIO ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnReintentar).setVisibility(v.modo == VistaCajas.Modo.ERROR ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.tvEstado)).setText(
                v.modo == VistaCajas.Modo.CARGANDO ? VistaCajas.MSG_CARGANDO
                        : v.modo == VistaCajas.Modo.ERROR ? v.error
                        : VistaCajas.MSG_VACIO);

        // Se reusan las tarjetas: el refresco cada 10 s no parpadea
        LinearLayout contenedor = findViewById(R.id.listaCajas);
        LayoutInflater inflater = LayoutInflater.from(this);
        while (contenedor.getChildCount() > v.tarjetas.size()) contenedor.removeViewAt(contenedor.getChildCount() - 1);
        while (contenedor.getChildCount() < v.tarjetas.size()) {
            contenedor.addView(inflater.inflate(R.layout.item_caja_ahorro, contenedor, false));
        }
        for (int i = 0; i < v.tarjetas.size(); i++) bind(contenedor.getChildAt(i), v.tarjetas.get(i));
    }

    private void bind(View card, VistaCajas.Tarjeta t) {
        card.findViewById(R.id.fondoIcono).setBackgroundTintList(EstiloCaja.tinte(t.color));
        ((ImageView) card.findViewById(R.id.ivIcono)).setImageResource(EstiloCaja.icono(t.icono));
        ((TextView) card.findViewById(R.id.tvNombre)).setText(t.nombre);
        ((TextView) card.findViewById(R.id.tvSaldo)).setText(t.saldo);

        card.findViewById(R.id.layoutMeta).setVisibility(t.tieneMeta ? View.VISIBLE : View.GONE);
        if (t.tieneMeta) {
            ProgressBar barra = card.findViewById(R.id.barraMeta);
            barra.setProgress(t.progreso);
            barra.setProgressTintList(EstiloCaja.tinte(t.color));
            ((TextView) card.findViewById(R.id.tvTextoMeta)).setText(t.textoMeta);
            ((TextView) card.findViewById(R.id.tvPorcentaje)).setText(t.progreso + "%");
        }
        TextView estadoMeta = card.findViewById(R.id.tvEstadoMeta);
        estadoMeta.setText(t.estadoMeta);
        estadoMeta.setTextColor(getColor(t.metaCumplida ? R.color.color_positivo : R.color.text_hint));

        Button depositar = card.findViewById(R.id.btnDepositar);
        depositar.setEnabled(!t.eliminando);
        depositar.setAlpha(t.eliminando ? 0.5f : 1f);
        depositar.setText(t.eliminando ? "Eliminando..." : "Agregar");
        depositar.setOnClickListener(x -> abrir(conCaja(MontoCajaActivity.class, t.caja)
                .putExtra(MontoCajaActivity.EXTRA_TIPO, MovimientoCaja.Tipo.DEPOSITO.name())));
        Button retirar = card.findViewById(R.id.btnRetirar);
        retirar.setEnabled(t.puedeRetirar);
        retirar.setAlpha(t.puedeRetirar ? 1f : 0.4f);
        retirar.setOnClickListener(x -> abrir(conCaja(MontoCajaActivity.class, t.caja)
                .putExtra(MontoCajaActivity.EXTRA_TIPO, MovimientoCaja.Tipo.RETIRO.name())));

        View menu = card.findViewById(R.id.btnMenu);
        menu.setEnabled(!t.eliminando);
        menu.setOnClickListener(ancla -> {
            PopupMenu popup = new PopupMenu(this, ancla);
            popup.getMenu().add(0, 1, 0, "Editar");
            popup.getMenu().add(0, 2, 1, "Eliminar");
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) abrir(conCaja(NuevaCajaAhorroActivity.class, t.caja));
                else confirmarEliminar(t.caja);
                return true;
            });
            popup.show();
        });
    }
}
