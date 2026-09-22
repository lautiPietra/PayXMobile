package com.example.payxmobile.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.payxmobile.R;
import com.example.payxmobile.adapters.NotificacionesAdapter;
import com.example.payxmobile.model.NotificacionResponse;
import com.example.payxmobile.network.RetrofitClient;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificacionesActivity extends AppCompatActivity {

    private NotificacionesAdapter adapter;
    private List<NotificacionResponse> listaActual;
    private TextView btnMarcarLeidas;
    private RecyclerView recycler;
    private LinearLayout emptyState;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notificaciones);

        recycler = findViewById(R.id.recyclerNotificaciones);
        emptyState = findViewById(R.id.emptyState);
        btnMarcarLeidas = findViewById(R.id.btnMarcarLeidas);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        btnMarcarLeidas.setOnClickListener(v -> marcarLeidasYLimpiar());

        cargarNotificaciones();
    }

    private void cargarNotificaciones() {
        RetrofitClient.getService(this).obtenerNotificaciones()
                .enqueue(new Callback<List<NotificacionResponse>>() {
                    @Override
                    public void onResponse(Call<List<NotificacionResponse>> call,
                                           Response<List<NotificacionResponse>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            listaActual = response.body();
                            mostrarLista(listaActual);
                        }
                    }

                    @Override
                    public void onFailure(Call<List<NotificacionResponse>> call, Throwable t) {
                        Toast.makeText(NotificacionesActivity.this,
                                "Sin conexión con el servidor", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void mostrarLista(List<NotificacionResponse> lista) {
        if (lista.isEmpty()) {
            recycler.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            btnMarcarLeidas.setVisibility(View.GONE);
        } else {
            recycler.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            btnMarcarLeidas.setVisibility(View.VISIBLE);
            recycler.setLayoutManager(new LinearLayoutManager(this));
            adapter = new NotificacionesAdapter(lista);
            recycler.setAdapter(adapter);
        }
    }

    private void marcarLeidasYLimpiar() {
        // Limpiar UI de inmediato sin esperar la respuesta
        listaActual.clear();
        recycler.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        btnMarcarLeidas.setVisibility(View.GONE);

        // Llamada al backend en segundo plano
        RetrofitClient.getService(this).marcarTodasLeidas().enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {}
            @Override
            public void onFailure(Call<Void> call, Throwable t) {}
        });
    }
}
