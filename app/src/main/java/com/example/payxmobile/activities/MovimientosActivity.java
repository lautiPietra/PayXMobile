package com.example.payxmobile.activities;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.payxmobile.R;
import com.example.payxmobile.actividad.Actividad;
import com.example.payxmobile.actividad.Actividades;
import com.example.payxmobile.actividad.FiltroMoneda;
import com.example.payxmobile.actividad.MovimientosViewModel;
import com.example.payxmobile.actividad.VistaMovimientos;
import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.transferencias.Refrescos;
import com.example.payxmobile.transferencias.TransferenciasRepository;
import com.example.payxmobile.transferencias.ui.DetalleTransferenciaSheet;
import com.example.payxmobile.transferencias.ui.FilaTransferenciaVista;
import com.example.payxmobile.utils.NavegacionInferior;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.CompositeDateValidator;
import com.google.android.material.datepicker.DateValidatorPointBackward;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * "Mis movimientos" (Movimientos.jsx de la web): todas las transferencias con filtro por moneda
 * (pesos / dólares / cripto), por fecha (día LOCAL, extremos incluidos) y paginación local de a 100. Lee del mismo
 * TransferenciasRepository que Inicio: un solo polling compartido, solo en primer plano.
 */
public class MovimientosActivity extends AppCompatActivity {

    private static final String K_DESDE = "mov_desde", K_HASTA = "mov_hasta", K_TANDAS = "mov_tandas",
            K_MONEDA = "mov_moneda", K_DETALLE = "mov_detalle";
    private static final DateTimeFormatter DD_MM_AAAA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private TransferenciasRepository repo;
    private final TransferenciasRepository.Observador observador = this::onEstado;
    private TransferenciasRepository.Estado estado;
    // El feed se arma una vez por cada lista nueva del repositorio; los filtros lo reusan
    private List<TransferenciaResponse> listaConstruida;
    private List<Actividad> actividades;

    private MovimientosViewModel filtros;
    private DetalleTransferenciaSheet detalle;
    private String detalleARestaurar;

    private SwipeRefreshLayout swipeRefresh;
    private boolean pullEnCurso;
    private final Encabezado encabezado = new Encabezado();
    private final Filas filas = new Filas();
    private final Pie pie = new Pie();
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movimientos);
        repo = TransferenciasRepository.get(this);
        detalle = new DetalleTransferenciaSheet(this);
        filtros = new ViewModelProvider(this).get(MovimientosViewModel.class);
        if (savedInstanceState != null) {
            // Muerte del proceso: el ViewModel vuelve vacío, se restaura desde el Bundle
            if (filtros.getDesde() == null && filtros.getHasta() == null && filtros.getTandas() == 1
                    && filtros.getMoneda() == FiltroMoneda.TODAS) {
                String moneda = savedInstanceState.getString(K_MONEDA);
                filtros.restaurar(fecha(savedInstanceState.getString(K_DESDE)),
                        fecha(savedInstanceState.getString(K_HASTA)),
                        moneda != null ? FiltroMoneda.valueOf(moneda) : FiltroMoneda.TODAS,
                        savedInstanceState.getInt(K_TANDAS, 1));
            }
            detalleARestaurar = savedInstanceState.getString(K_DETALLE);
        }

        RecyclerView recycler = findViewById(R.id.recyclerMovimientos);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setItemAnimator(null); // refrescos en silencio: sin animaciones de más
        recycler.setAdapter(new ConcatAdapter(encabezado, filas, pie));

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.payx_orange);
        swipeRefresh.setOnRefreshListener(() -> {
            pullEnCurso = true;
            Refrescos.trasMoverPlata(this);
            onEstado(repo.getEstado());
        });
        configurarBottomNav();
    }

    @Override
    protected void onStart() {
        super.onStart();
        repo.observar(observador);
        repo.iniciarAutoRefresco(); // YA y cada 10 s en primer plano (compartido con Inicio)
    }

    @Override
    protected void onResume() {
        super.onResume();
        bottomNav.setSelectedItemId(R.id.nav_actividad);
    }

    @Override
    protected void onStop() {
        super.onStop();
        repo.detenerAutoRefresco();
        repo.dejarDeObservar(observador);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        detalle.cerrar();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        if (filtros.getDesde() != null) out.putString(K_DESDE, filtros.getDesde().toString());
        if (filtros.getHasta() != null) out.putString(K_HASTA, filtros.getHasta().toString());
        out.putInt(K_TANDAS, filtros.getTandas());
        out.putString(K_MONEDA, filtros.getMoneda().name());
        out.putString(K_DETALLE, detalle.idAbierto());
    }

    private void onEstado(TransferenciasRepository.Estado e) {
        if (isFinishing()) return;
        estado = e;
        if (pullEnCurso && !e.cargando) {
            pullEnCurso = false;
            swipeRefresh.setRefreshing(false);
        }
        if (e.lista != listaConstruida) {
            listaConstruida = e.lista;
            actividades = e.lista != null ? Actividades.construir(e.lista) : null;
        }
        render();
        if (detalleARestaurar != null && e.lista != null) {
            TransferenciaResponse t = e.buscar(detalleARestaurar);
            detalleARestaurar = null;
            if (t != null) detalle.abrir(t);
        }
    }

    private void render() {
        ZoneId zona = ZoneId.systemDefault();
        VistaMovimientos v = VistaMovimientos.de(estado, actividades, filtros.getDesde(), filtros.getHasta(),
                filtros.getMoneda(), filtros.getTandas(), zona);
        encabezado.mostrar(v);
        filas.submitList(v.visibles);
        pie.mostrar(v.mostrarMas);
    }

    private void cambiarFiltro(Runnable cambio) {
        cambio.run();
        render();
    }

    // ── Encabezado: título, estados y filtros ───────────────────────────────────

    private class Encabezado extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private View v;
        private VistaMovimientos pendiente;

        @Override
        public int getItemCount() {
            return 1;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.header_movimientos, parent, false);
            v.findViewById(R.id.btnBack).setOnClickListener(x -> finish());
            v.findViewById(R.id.btnReintentar).setOnClickListener(x -> repo.refrescar());
            v.findViewById(R.id.btnLimpiarFiltros).setOnClickListener(x -> cambiarFiltro(filtros::limpiar));
            v.findViewById(R.id.btnVerTodos).setOnClickListener(x -> cambiarFiltro(filtros::limpiar));
            v.findViewById(R.id.tvDesde).setOnClickListener(x -> elegirFecha(true));
            v.findViewById(R.id.tvHasta).setOnClickListener(x -> elegirFecha(false));
            return new RecyclerView.ViewHolder(v) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (pendiente != null) dibujar(pendiente);
        }

        void mostrar(VistaMovimientos vista) {
            pendiente = vista;
            if (v != null) dibujar(vista);
        }

        private void dibujar(VistaMovimientos vista) {
            v.findViewById(R.id.tvAvisoTope).setVisibility(vista.avisoTope ? View.VISIBLE : View.GONE);
            v.findViewById(R.id.tvDesactualizado).setVisibility(vista.desactualizado ? View.VISIBLE : View.GONE);

            boolean hayEstado = vista.modo != VistaMovimientos.Modo.LISTA;
            v.findViewById(R.id.layoutEstado).setVisibility(hayEstado ? View.VISIBLE : View.GONE);
            v.findViewById(R.id.progressMovimientos).setVisibility(
                    vista.modo == VistaMovimientos.Modo.CARGANDO ? View.VISIBLE : View.GONE);
            ((TextView) v.findViewById(R.id.tvEstado)).setText(
                    vista.modo == VistaMovimientos.Modo.CARGANDO ? VistaMovimientos.MSG_CARGANDO
                            : vista.modo == VistaMovimientos.Modo.ERROR ? vista.error
                            : VistaMovimientos.MSG_VACIO);
            v.findViewById(R.id.btnReintentar).setVisibility(
                    vista.modo == VistaMovimientos.Modo.ERROR ? View.VISIBLE : View.GONE);

            v.findViewById(R.id.layoutFiltros).setVisibility(vista.mostrarFiltros ? View.VISIBLE : View.GONE);
            dibujarMonedas(vista);
            dibujarAtajos();
            LocalDate desde = filtros.getDesde(), hasta = filtros.getHasta();
            ((TextView) v.findViewById(R.id.tvDesde)).setText(desde != null ? desde.format(DD_MM_AAAA) : "");
            ((TextView) v.findViewById(R.id.tvHasta)).setText(hasta != null ? hasta.format(DD_MM_AAAA) : "");
            v.findViewById(R.id.btnLimpiarFiltros).setVisibility(vista.hayFiltro ? View.VISIBLE : View.GONE);
            TextView tvErrorRango = v.findViewById(R.id.tvErrorRango);
            tvErrorRango.setVisibility(vista.errorRango != null ? View.VISIBLE : View.GONE);
            tvErrorRango.setText(vista.errorRango);
            TextView tvResumen = v.findViewById(R.id.tvResumen);
            tvResumen.setVisibility(vista.resumen != null ? View.VISIBLE : View.GONE);
            tvResumen.setText(vista.resumen);
            v.findViewById(R.id.layoutSinResultados).setVisibility(vista.sinResultados != null ? View.VISIBLE : View.GONE);
            ((TextView) v.findViewById(R.id.tvSinResultados)).setText(vista.sinResultados);
        }

        /** Pesos / Dólares / Cripto, con cuántas hay de cada una dentro del rango de fechas. */
        private void dibujarMonedas(VistaMovimientos vista) {
            LinearLayout fila = v.findViewById(R.id.filaMonedas);
            FiltroMoneda[] opciones = FiltroMoneda.values();
            if (fila.getChildCount() != opciones.length) {
                fila.removeAllViews();
                for (int i = 0; i < opciones.length; i++) fila.addView(crearChip());
            }
            for (int i = 0; i < opciones.length; i++) {
                FiltroMoneda m = opciones[i];
                TextView chip = (TextView) fila.getChildAt(i);
                Integer cuenta = vista.contadoresMoneda.get(m);
                chip.setText(cuenta != null ? m.etiqueta + "  " + cuenta : m.etiqueta);
                chip.setSelected(filtros.getMoneda() == m);
                if (filtros.getMoneda() == m) mostrarEntero(chip);
                chip.setOnClickListener(x -> cambiarFiltro(() -> filtros.setMoneda(m)));
            }
        }

        /** Los atajos se recalculan con el "hoy" de cada render (siguen bien pasada la medianoche). */
        private void dibujarAtajos() {
            LinearLayout fila = v.findViewById(R.id.filaAtajos);
            List<Actividades.Atajo> atajos = Actividades.atajos(Clock.systemDefaultZone(), ZoneId.systemDefault());
            Actividades.Atajo activo = Actividades.atajoActivo(atajos, filtros.getDesde(), filtros.getHasta());
            if (fila.getChildCount() != atajos.size()) {
                fila.removeAllViews();
                for (int i = 0; i < atajos.size(); i++) fila.addView(crearChip());
            }
            for (int i = 0; i < atajos.size(); i++) {
                Actividades.Atajo a = atajos.get(i);
                TextView chip = (TextView) fila.getChildAt(i);
                chip.setText(a.etiqueta);
                chip.setSelected(activo != null && activo.id.equals(a.id));
                if (activo != null && activo.id.equals(a.id)) mostrarEntero(chip);
                chip.setOnClickListener(x -> cambiarFiltro(() -> filtros.setRango(a.desde, a.hasta)));
            }
        }

        /** Desplaza la fila de botones para que el elegido no quede cortado contra el borde. */
        private void mostrarEntero(View chip) {
            chip.post(() -> {
                View fila = (View) chip.getParent().getParent();
                if (fila instanceof android.widget.HorizontalScrollView) {
                    android.widget.HorizontalScrollView scroll = (android.widget.HorizontalScrollView) fila;
                    int izquierda = chip.getLeft() - dp(8);
                    int derecha = chip.getRight() + dp(8) - scroll.getWidth();
                    if (scroll.getScrollX() > izquierda) scroll.smoothScrollTo(izquierda, 0);
                    else if (scroll.getScrollX() < derecha) scroll.smoothScrollTo(derecha, 0);
                }
            });
        }

        private TextView crearChip() {
            TextView chip = new TextView(MovimientosActivity.this);
            chip.setTextSize(12.5f);
            chip.setTypeface(null, Typeface.BOLD);
            chip.setBackgroundResource(R.drawable.bg_chip_naranja);
            chip.setTextColor(getColorStateList(R.color.coin_chip_text));
            chip.setPadding(dp(16), dp(9), dp(16), dp(9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            chip.setLayoutParams(lp);
            return chip;
        }
    }

    /**
     * Selector de fecha con las restricciones de la web: "Hasta" máx. hoy y mín. "Desde";
     * "Desde" máx. "Hasta" (o hoy).
     */
    private void elegirFecha(boolean esDesde) {
        LocalDate hoy = Actividades.hoy(Clock.systemDefaultZone(), ZoneId.systemDefault());
        LocalDate actual = esDesde ? filtros.getDesde() : filtros.getHasta();
        LocalDate max = esDesde ? (filtros.getHasta() != null ? filtros.getHasta() : hoy) : hoy;
        LocalDate min = esDesde ? null : filtros.getDesde();

        List<CalendarConstraints.DateValidator> reglas = new ArrayList<>();
        reglas.add(DateValidatorPointBackward.before(utc(max)));
        if (min != null) reglas.add(DateValidatorPointForward.from(utc(min)));
        CalendarConstraints restricciones = new CalendarConstraints.Builder()
                .setValidator(CompositeDateValidator.allOf(reglas))
                .setEnd(utc(max))
                .setOpenAt(utc(actual != null ? actual : max))
                .build();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(esDesde ? "Desde" : "Hasta")
                .setSelection(actual != null ? utc(actual) : null)
                .setCalendarConstraints(restricciones)
                .build();
        picker.addOnPositiveButtonClickListener(millis -> {
            // El picker trabaja en milisegundos UTC del inicio del día elegido
            LocalDate elegido = java.time.Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();
            cambiarFiltro(() -> {
                if (esDesde) filtros.setDesde(elegido);
                else filtros.setHasta(elegido);
            });
        });
        picker.show(getSupportFragmentManager(), "fecha");
    }

    private static long utc(LocalDate d) {
        return d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    private static LocalDate fecha(String iso) {
        return iso != null ? LocalDate.parse(iso) : null;
    }

    // ── Filas (DiffUtil por key: los refrescos no mueven el scroll) ─────────────

    private static final DiffUtil.ItemCallback<Actividad> DIFF = new DiffUtil.ItemCallback<Actividad>() {
        @Override
        public boolean areItemsTheSame(@NonNull Actividad a, @NonNull Actividad b) {
            return a.key.equals(b.key);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Actividad a, @NonNull Actividad b) {
            TransferenciaResponse x = a.transferencia, y = b.transferencia;
            if (x == null || y == null) return x == y;
            return Objects.equals(x.getEstado(), y.getEstado())
                    && Objects.equals(x.getConcepto(), y.getConcepto())
                    && Objects.equals(x.getFechaConfirmacion(), y.getFechaConfirmacion())
                    && x.getMonto().compareTo(y.getMonto()) == 0
                    && Objects.equals(x.getContraparteNombre(), y.getContraparteNombre());
        }
    };

    private class Filas extends ListAdapter<Actividad, RecyclerView.ViewHolder> {
        Filas() {
            super(DIFF);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View fila = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transferencia, parent, false);
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) fila.getLayoutParams();
            lp.leftMargin = dp(16);
            lp.rightMargin = dp(16);
            return new RecyclerView.ViewHolder(fila) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Actividad a = getItem(position);
            if (a.transferencia == null) return; // otros tipos: se dibujan cuando existan
            FilaTransferenciaVista.bind(holder.itemView, a.transferencia);
            holder.itemView.setOnClickListener(x -> detalle.abrir(a.transferencia));
        }
    }

    // ── Pie: "Mostrar más (n restantes)" ──────────────────────────────────────

    private class Pie extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private String texto;

        void mostrar(String nuevo) {
            boolean antes = texto != null, ahora = nuevo != null;
            texto = nuevo;
            if (antes && !ahora) notifyItemRemoved(0);
            else if (!antes && ahora) notifyItemInserted(0);
            else if (ahora) notifyItemChanged(0);
        }

        @Override
        public int getItemCount() {
            return texto != null ? 1 : 0;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View boton = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mostrar_mas, parent, false);
            boton.setOnClickListener(x -> cambiarFiltro(filtros::mostrarMas));
            return new RecyclerView.ViewHolder(boton) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ((TextView) holder.itemView).setText(texto);
        }
    }

    private void configurarBottomNav() {
        bottomNav = findViewById(R.id.bottomNav);
        NavegacionInferior.configurar(this, bottomNav, R.id.nav_actividad);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
