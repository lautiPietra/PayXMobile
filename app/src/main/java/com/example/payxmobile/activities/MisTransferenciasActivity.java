package com.example.payxmobile.activities;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.payxmobile.R;
import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.transferencias.DetalleTransferencia;
import com.example.payxmobile.transferencias.FilaTransferencia;
import com.example.payxmobile.transferencias.FormatoTransferencia;
import com.example.payxmobile.transferencias.Refrescos;
import com.example.payxmobile.transferencias.TransferenciasRepository;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mis transferencias (enviadas y recibidas) + detalle. Se actualiza cada 10 s en primer plano,
 * al volver y con pull-to-refresh; un detalle abierto se actualiza con la versión nueva.
 */
public class MisTransferenciasActivity extends AppCompatActivity {

    private static final int POR_PAGINA = 100;
    private static final String K_DETALLE_ID = "detalle_id";

    private TransferenciasRepository repo;
    private final TransferenciasRepository.Observador observador = this::onEstado;
    private TransferenciasRepository.Estado estado;

    private SwipeRefreshLayout swipeRefresh;
    private View layoutEstado, progressLista, btnReintentarLista, tvDesactualizado, tvAvisoTope;
    private TextView tvEstadoLista;
    private final Adaptador adaptador = new Adaptador();
    private int paginasVisibles = 1;
    private boolean pullEnCurso;

    private BottomSheetDialog hojaDetalle;
    private DetalleTransferencia detalle;
    private String detalleIdARestaurar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mis_transferencias);
        repo = TransferenciasRepository.get(this);

        swipeRefresh = findViewById(R.id.swipeRefresh);
        layoutEstado = findViewById(R.id.layoutEstado);
        progressLista = findViewById(R.id.progressLista);
        tvEstadoLista = findViewById(R.id.tvEstadoLista);
        btnReintentarLista = findViewById(R.id.btnReintentarLista);
        tvDesactualizado = findViewById(R.id.tvDesactualizado);
        tvAvisoTope = findViewById(R.id.tvAvisoTope);

        RecyclerView recycler = findViewById(R.id.recyclerTransferencias);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adaptador);

        swipeRefresh.setColorSchemeResources(R.color.payx_orange);
        swipeRefresh.setOnRefreshListener(() -> {
            pullEnCurso = true;
            repo.refrescar();
            Refrescos.trasMoverPlata(this); // también saldos (por si algo cambió del otro lado)
            onEstado(repo.getEstado());
        });
        btnReintentarLista.setOnClickListener(v -> repo.refrescar());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        if (savedInstanceState != null) detalleIdARestaurar = savedInstanceState.getString(K_DETALLE_ID);
    }

    @Override
    protected void onStart() {
        super.onStart();
        repo.observar(observador);
        repo.iniciarAutoRefresco(); // refresca YA y después cada 10 s mientras esté en primer plano
    }

    @Override
    protected void onStop() {
        super.onStop();
        repo.detenerAutoRefresco();
        repo.dejarDeObservar(observador);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (hojaDetalle != null && hojaDetalle.isShowing() && detalle != null) {
            out.putString(K_DETALLE_ID, detalle.getTransferencia().getId());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (hojaDetalle != null) hojaDetalle.dismiss();
    }

    private void onEstado(TransferenciasRepository.Estado e) {
        if (isFinishing()) return;
        estado = e;
        if (pullEnCurso && !e.cargando) {
            pullEnCurso = false;
            swipeRefresh.setRefreshing(false);
        }
        boolean hayLista = e.lista != null;
        tvDesactualizado.setVisibility(hayLista && e.desactualizado ? View.VISIBLE : View.GONE);
        tvAvisoTope.setVisibility(e.llegoAlTope() ? View.VISIBLE : View.GONE);

        if (!hayLista) {
            layoutEstado.setVisibility(View.VISIBLE);
            boolean error = e.errorPrimeraCarga != null;
            progressLista.setVisibility(error ? View.GONE : View.VISIBLE);
            tvEstadoLista.setText(error ? e.errorPrimeraCarga : "Cargando transferencias...");
            btnReintentarLista.setVisibility(error ? View.VISIBLE : View.GONE);
            adaptador.setLista(Collections.emptyList());
            return;
        }
        boolean vacia = e.lista.isEmpty();
        layoutEstado.setVisibility(vacia ? View.VISIBLE : View.GONE);
        progressLista.setVisibility(View.GONE);
        btnReintentarLista.setVisibility(View.GONE);
        tvEstadoLista.setText("Todavía no tenés transferencias");
        adaptador.setLista(e.lista);

        // Un detalle abierto se actualiza con la versión nueva (el otro lado o el vencimiento la cambiaron)
        if (detalle != null && hojaDetalle != null && hojaDetalle.isShowing()) {
            detalle.actualizarDesdeListado(e.buscar(detalle.getTransferencia().getId()));
        }
        if (detalleIdARestaurar != null) {
            TransferenciaResponse t = e.buscar(detalleIdARestaurar);
            detalleIdARestaurar = null;
            if (t != null) abrirDetalle(t);
        }
    }

    // ── Detalle ───────────────────────────────────────────────────────────────

    private void abrirDetalle(TransferenciaResponse t) {
        if (hojaDetalle != null) hojaDetalle.dismiss();
        Context app = getApplicationContext();
        detalle = new DetalleTransferencia(t, () -> RetrofitClient.getService(app),
                repo::reemplazar, () -> Refrescos.trasMoverPlata(app));

        View vista = LayoutInflater.from(this).inflate(R.layout.sheet_detalle_transferencia, null);
        hojaDetalle = new BottomSheetDialog(this);
        hojaDetalle.setContentView(vista);
        // El dismiss de una hoja anterior llega después: tiene que soltar SU detalle, no el nuevo
        final DetalleTransferencia deEstaHoja = detalle;
        hojaDetalle.setOnDismissListener(d -> deEstaHoja.setObservador(null));

        TextView btnCambiar = vista.findViewById(R.id.btnCambiarConcepto);
        View layoutEditar = vista.findViewById(R.id.layoutEditarConcepto);
        EditText etConcepto = vista.findViewById(R.id.etConcepto);
        btnCambiar.setOnClickListener(v -> {
            etConcepto.setText(detalle.getTransferencia().getConcepto());
            layoutEditar.setVisibility(View.VISIBLE);
            btnCambiar.setVisibility(View.GONE);
            vista.findViewById(R.id.tvConcepto).setVisibility(View.GONE);
            etConcepto.requestFocus();
        });
        vista.findViewById(R.id.btnCancelarConcepto).setOnClickListener(v -> cerrarEdicionConcepto(vista));
        vista.findViewById(R.id.btnGuardarConcepto).setOnClickListener(v ->
                detalle.guardarConcepto(etConcepto.getText().toString()));
        vista.findViewById(R.id.btnConfirmarPendiente).setOnClickListener(v -> detalle.confirmar());
        vista.findViewById(R.id.btnCancelarPendiente).setOnClickListener(v -> detalle.cancelar());

        final boolean[] estabaGuardando = {false};
        detalle.setObservador(d -> {
            // Terminó de guardar sin error: se cierra la edición
            if (estabaGuardando[0] && !d.isGuardando() && d.getError() == null) cerrarEdicionConcepto(vista);
            estabaGuardando[0] = d.isGuardando();
            renderDetalle(vista, d);
        });
        renderDetalle(vista, detalle);
        hojaDetalle.show();
        detalle.refrescar(); // GET /{id}: la versión más nueva
    }

    private void cerrarEdicionConcepto(View vista) {
        vista.findViewById(R.id.layoutEditarConcepto).setVisibility(View.GONE);
        vista.findViewById(R.id.tvConcepto).setVisibility(View.VISIBLE);
        vista.findViewById(R.id.btnCambiarConcepto).setVisibility(
                detalle != null && detalle.puedeCambiarConcepto() ? View.VISIBLE : View.GONE);
    }

    private void renderDetalle(View v, DetalleTransferencia d) {
        TransferenciaResponse t = d.getTransferencia();
        ZoneId zona = ZoneId.systemDefault();
        FilaTransferencia fila = FilaTransferencia.de(t, zona);

        TextView tvMonto = v.findViewById(R.id.tvMontoDetalle);
        tvMonto.setText(fila.monto);
        tvMonto.setTextColor(getColor(colorMonto(fila.estilo)));
        TextView tvTitulo = v.findViewById(R.id.tvTituloDetalle);
        tvTitulo.setText(fila.titulo);
        tvTitulo.setTextColor(getColor(t.esCancelada() ? R.color.text_secondary : R.color.text_primary));

        // Badge: Pendiente / Completada; la cancelada no lleva
        TextView badge = v.findViewById(R.id.tvBadgeEstado);
        if (t.esCancelada()) {
            badge.setVisibility(View.GONE);
        } else if (t.esPendiente()) {
            badge.setVisibility(View.VISIBLE);
            badge.setText("Pendiente");
            badge.setBackgroundResource(R.drawable.bg_badge_pendiente);
            badge.setTextColor(0xFFF2C27B);
            badge.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_clock_12, 0, 0, 0);
        } else {
            badge.setVisibility(View.VISIBLE);
            badge.setText("Completada");
            badge.setBackgroundResource(R.drawable.bg_pill_pagada);
            badge.setTextColor(getColor(R.color.color_positivo));
            badge.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        }

        ((TextView) v.findViewById(R.id.tvEtiquetaContraparte)).setText(t.esRecibida() ? "De" : "Para");
        ((TextView) v.findViewById(R.id.tvContraparte)).setText(
                t.getContraparteNombre() + " (" + t.getContraparteAlias() + ")");
        ((TextView) v.findViewById(R.id.tvFechaDetalle)).setText(FormatoTransferencia.fechaHora(t.getFecha(), zona));
        boolean confirmada = t.getFechaConfirmacion() != null;
        v.findViewById(R.id.filaConfirmada).setVisibility(confirmada ? View.VISIBLE : View.GONE);
        ((TextView) v.findViewById(R.id.tvFechaConfirmacion)).setText(
                confirmada ? FormatoTransferencia.fechaHora(t.getFechaConfirmacion(), zona) : "");

        String concepto = t.getConcepto();
        ((TextView) v.findViewById(R.id.tvConcepto)).setText(
                concepto == null || concepto.isEmpty() ? "Sin concepto" : concepto);
        boolean editando = v.findViewById(R.id.layoutEditarConcepto).getVisibility() == View.VISIBLE;
        v.findViewById(R.id.btnCambiarConcepto).setVisibility(d.puedeCambiarConcepto() && !editando ? View.VISIBLE : View.GONE);
        Button guardar = v.findViewById(R.id.btnGuardarConcepto);
        guardar.setEnabled(!d.isGuardando());
        guardar.setText(d.isGuardando() ? "Guardando..." : "Guardar");
        v.findViewById(R.id.btnCancelarConcepto).setEnabled(!d.isGuardando());

        TextView tvError = v.findViewById(R.id.tvErrorDetalle);
        tvError.setVisibility(d.getError() != null ? View.VISIBLE : View.GONE);
        tvError.setText(d.getError());

        v.findViewById(R.id.layoutPendiente).setVisibility(d.puedeConfirmarOCancelar() ? View.VISIBLE : View.GONE);
        ((TextView) v.findViewById(R.id.tvAvisoPendienteDetalle)).setText(
                "Esta transferencia todavía no se confirmó: la plata no se movió de tu cuenta. "
                        + "Si no la confirmás, se cancelará automáticamente el "
                        + FormatoTransferencia.vencimiento(t.getFecha(), zona) + ".");
        Button confirmar = v.findViewById(R.id.btnConfirmarPendiente);
        confirmar.setEnabled(!d.isProcesando());
        confirmar.setText(d.isProcesando() ? "Procesando..." : "Confirmar ahora");
        v.findViewById(R.id.btnCancelarPendiente).setEnabled(!d.isProcesando());
    }

    private static int colorMonto(FilaTransferencia.Estilo estilo) {
        switch (estilo) {
            case POSITIVO: return R.color.color_positivo;
            case NEGATIVO: return R.color.color_negativo;
            default: return R.color.text_secondary;
        }
    }

    // ── Listado con paginación local (de a 100, como la web) ────────────────────

    private class Adaptador extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TIPO_FILA = 0, TIPO_MAS = 1;
        private List<TransferenciaResponse> todas = new ArrayList<>();

        void setLista(List<TransferenciaResponse> lista) {
            todas = lista;
            notifyDataSetChanged();
        }

        private int visibles() {
            return Math.min(todas.size(), paginasVisibles * POR_PAGINA);
        }

        @Override
        public int getItemCount() {
            return visibles() + (visibles() < todas.size() ? 1 : 0);
        }

        @Override
        public int getItemViewType(int position) {
            return position < visibles() ? TIPO_FILA : TIPO_MAS;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int tipo) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (tipo == TIPO_MAS) {
                View boton = inf.inflate(R.layout.item_mostrar_mas, parent, false);
                boton.setOnClickListener(v -> {
                    paginasVisibles++;
                    notifyDataSetChanged();
                });
                return new RecyclerView.ViewHolder(boton) {};
            }
            return new Fila(inf.inflate(R.layout.item_transferencia, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof Fila) ((Fila) holder).bind(todas.get(position));
        }
    }

    private class Fila extends RecyclerView.ViewHolder {
        final ImageView ivIcono;
        final TextView tvTitulo, tvDetalle, tvBadge, tvMonto, tvFecha;

        Fila(View v) {
            super(v);
            ivIcono = v.findViewById(R.id.ivIcono);
            tvTitulo = v.findViewById(R.id.tvTitulo);
            tvDetalle = v.findViewById(R.id.tvDetalle);
            tvBadge = v.findViewById(R.id.tvBadgePendiente);
            tvMonto = v.findViewById(R.id.tvMonto);
            tvFecha = v.findViewById(R.id.tvFecha);
        }

        void bind(TransferenciaResponse t) {
            FilaTransferencia f = FilaTransferencia.de(t, ZoneId.systemDefault());
            tvTitulo.setText(f.titulo);
            tvTitulo.setTextColor(getColor(t.esCancelada() ? R.color.text_secondary : R.color.text_primary));
            tvDetalle.setText(f.detalle);
            tvBadge.setVisibility(f.pendiente ? View.VISIBLE : View.GONE);
            tvMonto.setText(f.monto);
            tvMonto.setTextColor(getColor(colorMonto(f.estilo)));
            tvFecha.setText(f.fechaCorta);
            switch (f.icono) {
                case CANCELADA:
                    ivIcono.setImageResource(R.drawable.ic_close);
                    ivIcono.setImageTintList(getColorStateList(R.color.text_secondary));
                    break;
                case CRIPTO:
                    ivIcono.setImageResource(R.drawable.ic_coins);
                    ivIcono.setImageTintList(getColorStateList(R.color.color_cripto));
                    break;
                case RECIBIDA:
                    ivIcono.setImageResource(R.drawable.ic_arrow_down_circle);
                    ivIcono.setImageTintList(getColorStateList(R.color.color_positivo));
                    break;
                default:
                    ivIcono.setImageResource(R.drawable.ic_arrow_up_circle);
                    ivIcono.setImageTintList(getColorStateList(R.color.color_negativo));
                    break;
            }
            itemView.setOnClickListener(v -> abrirDetalle(t));
        }
    }
}
