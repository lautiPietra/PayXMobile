package com.example.payxmobile.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.payxmobile.R;
import com.example.payxmobile.adapters.NotificacionesAdapter;
import com.example.payxmobile.notificaciones.NotificacionesRepository;
import com.example.payxmobile.utils.SesionUtils;

/**
 * Panel de notificaciones. Todo el estado vive en {@link NotificacionesRepository}: esta pantalla
 * solo lo dibuja. Mientras está visible, el polling de 10 s también trae la lista.
 */
public class NotificacionesActivity extends AppCompatActivity {

    private static final String MSG_DESACTUALIZADO = "No pudimos actualizar tus notificaciones. Te mostramos las últimas que cargamos";

    private NotificacionesRepository repo;
    private final NotificacionesRepository.Observador observador = this::render;
    private final NotificacionesAdapter adapter = new NotificacionesAdapter();

    private TextView btnMarcarLeidas, tvAviso, tvError;
    private RecyclerView recycler;
    private View emptyState, estadoError, progreso;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notificaciones);
        repo = NotificacionesRepository.get(this);

        recycler = findViewById(R.id.recyclerNotificaciones);
        emptyState = findViewById(R.id.emptyState);
        estadoError = findViewById(R.id.estadoError);
        progreso = findViewById(R.id.progreso);
        btnMarcarLeidas = findViewById(R.id.btnMarcarLeidas);
        tvAviso = findViewById(R.id.tvAviso);
        tvError = findViewById(R.id.tvError);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnReintentar).setOnClickListener(v -> repo.refrescar());
        btnMarcarLeidas.setOnClickListener(v -> repo.marcarTodasLeidas());
    }

    @Override
    protected void onStart() {
        super.onStart();
        repo.observar(observador);
        repo.abrirPanel();          // refresca YA (badge + lista)
        repo.iniciarAutoRefresco(); // y cada 10 s mientras se ve
    }

    @Override
    protected void onStop() {
        super.onStop();
        repo.detenerAutoRefresco();
        repo.cerrarPanel();
        repo.dejarDeObservar(observador);
    }

    private void render(NotificacionesRepository.Estado e) {
        if (isFinishing()) return;
        if (e.sesionInvalida) {
            SesionUtils.sesionInvalida(this);
            return;
        }
        boolean hayLista = e.lista != null && !e.lista.isEmpty();
        boolean errorSinDatos = e.lista == null && e.errorPrimeraCarga != null;

        boolean reintentando = errorSinDatos && e.cargandoLista;
        progreso.setVisibility(e.esPrimeraCarga() || reintentando ? View.VISIBLE : View.GONE);
        estadoError.setVisibility(errorSinDatos && !reintentando ? View.VISIBLE : View.GONE);
        tvError.setText(e.errorPrimeraCarga);
        emptyState.setVisibility(e.vacia() ? View.VISIBLE : View.GONE);
        recycler.setVisibility(hayLista ? View.VISIBLE : View.GONE);
        adapter.setItems(e.lista);

        btnMarcarLeidas.setVisibility(hayLista ? View.VISIBLE : View.GONE);
        btnMarcarLeidas.setEnabled(!e.marcando);
        btnMarcarLeidas.setText(e.marcando ? "Marcando..." : "Marcar todas como leídas");

        String aviso = e.errorMarcar != null ? e.errorMarcar : (e.desactualizado ? MSG_DESACTUALIZADO : null);
        tvAviso.setVisibility(aviso != null ? View.VISIBLE : View.GONE);
        tvAviso.setText(aviso);
    }
}
