package com.example.payxmobile.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;
import com.example.payxmobile.model.ActualizarPerfilRequest;
import com.example.payxmobile.model.ErrorResponse;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.utils.SessionManager;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PerfilActivity extends AppCompatActivity {

    private TextView tvInicialHeader, tvNombreHeader, tvUsernameHeader;
    private TextView tvNombreCompleto, tvEmail, tvDni, tvCvu;
    private TextInputEditText etNombreUsuario, etAlias, etTelefono;
    private Button btnGuardar;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil);

        sessionManager = new SessionManager(this);

        tvInicialHeader = findViewById(R.id.tvInicialHeader);
        tvNombreHeader = findViewById(R.id.tvNombreHeader);
        tvUsernameHeader = findViewById(R.id.tvUsernameHeader);
        tvNombreCompleto = findViewById(R.id.tvNombreCompleto);
        tvEmail = findViewById(R.id.tvEmail);
        tvDni = findViewById(R.id.tvDni);
        tvCvu = findViewById(R.id.tvCvu);
        etNombreUsuario = findViewById(R.id.etNombreUsuario);
        etAlias = findViewById(R.id.etAlias);
        etTelefono = findViewById(R.id.etTelefono);
        btnGuardar = findViewById(R.id.btnGuardar);

        cargarDatosLocales();
        cargarPerfilDesdeApi();

        btnGuardar.setOnClickListener(v -> guardarCambios());

        findViewById(R.id.cardCambiarPassword).setOnClickListener(v ->
                startActivity(new Intent(this, CambiarPasswordActivity.class))
        );

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnCerrarSesion).setOnClickListener(v -> {
            sessionManager.clearSession();
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    private void cargarDatosLocales() {
        String nombre = sessionManager.getNombreCompleto();
        String primerNombre = nombre.isEmpty() ? "U" : String.valueOf(nombre.charAt(0)).toUpperCase();
        tvInicialHeader.setText(primerNombre);
        tvNombreHeader.setText(nombre);
        tvUsernameHeader.setText("@" + sessionManager.getNombreUsuario());
        tvEmail.setText(sessionManager.getEmail());
        etNombreUsuario.setText(sessionManager.getNombreUsuario());
    }

    private void cargarPerfilDesdeApi() {
        RetrofitClient.getService(this).obtenerPerfil()
                .enqueue(new Callback<PerfilResponse>() {
                    @Override
                    public void onResponse(Call<PerfilResponse> call, Response<PerfilResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            PerfilResponse p = response.body();

                            String nombre = p.getNombreCompleto();
                            tvInicialHeader.setText(nombre != null && !nombre.isEmpty()
                                    ? String.valueOf(nombre.charAt(0)).toUpperCase() : "U");
                            tvNombreHeader.setText(nombre);
                            tvUsernameHeader.setText("@" + p.getNombreUsuario());

                            tvNombreCompleto.setText(nombre);
                            tvEmail.setText(p.getEmail());
                            tvDni.setText(p.getDni());
                            tvCvu.setText(p.getCvu());

                            etNombreUsuario.setText(p.getNombreUsuario());
                            etAlias.setText(p.getAlias());
                            etTelefono.setText(p.getTelefono());
                        }
                    }

                    @Override
                    public void onFailure(Call<PerfilResponse> call, Throwable t) {
                        Toast.makeText(PerfilActivity.this,
                                "Sin conexión con el servidor", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void guardarCambios() {
        String nuevoUsuario = getText(etNombreUsuario);
        String nuevoAlias = getText(etAlias);
        String nuevoTelefono = getText(etTelefono);

        if (nuevoUsuario.isEmpty() || nuevoAlias.isEmpty()) {
            Toast.makeText(this, "Nombre de usuario y alias son obligatorios", Toast.LENGTH_SHORT).show();
            return;
        }

        btnGuardar.setEnabled(false);
        btnGuardar.setText("Guardando...");

        RetrofitClient.getService(this)
                .actualizarPerfil(new ActualizarPerfilRequest(nuevoUsuario, nuevoTelefono, nuevoAlias))
                .enqueue(new Callback<PerfilResponse>() {
                    @Override
                    public void onResponse(Call<PerfilResponse> call, Response<PerfilResponse> response) {
                        btnGuardar.setEnabled(true);
                        btnGuardar.setText("Guardar cambios");
                        if (response.isSuccessful() && response.body() != null) {
                            PerfilResponse p = response.body();
                            sessionManager.saveSession(
                                    sessionManager.getToken(),
                                    sessionManager.getUserId(),
                                    p.getNombreCompleto(),
                                    p.getEmail(),
                                    p.getNombreUsuario(),
                                    sessionManager.getRol()
                            );
                            tvUsernameHeader.setText("@" + p.getNombreUsuario());
                            Toast.makeText(PerfilActivity.this,
                                    "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show();
                        } else {
                            try {
                                ErrorResponse error = new Gson().fromJson(
                                        response.errorBody().charStream(), ErrorResponse.class);
                                Toast.makeText(PerfilActivity.this, error.getError(), Toast.LENGTH_LONG).show();
                            } catch (Exception e) {
                                Toast.makeText(PerfilActivity.this, "Error al guardar", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<PerfilResponse> call, Throwable t) {
                        btnGuardar.setEnabled(true);
                        btnGuardar.setText("Guardar cambios");
                        Toast.makeText(PerfilActivity.this, "Sin conexión con el servidor", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }
}
