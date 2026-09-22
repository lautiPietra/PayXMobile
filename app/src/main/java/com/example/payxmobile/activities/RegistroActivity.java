package com.example.payxmobile.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;
import com.example.payxmobile.model.ErrorResponse;
import com.example.payxmobile.model.RegistroRequest;
import com.example.payxmobile.model.RegistroResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegistroActivity extends AppCompatActivity {

    private TextInputEditText etNombreCompleto, etEmail, etTelefono,
            etNombreUsuario, etDni, etPassword;
    private Button btnRegistrarse;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registro);

        etNombreCompleto = findViewById(R.id.etNombreCompleto);
        etEmail = findViewById(R.id.etEmail);
        etTelefono = findViewById(R.id.etTelefono);
        etNombreUsuario = findViewById(R.id.etNombreUsuario);
        etDni = findViewById(R.id.etDni);
        etPassword = findViewById(R.id.etPassword);
        btnRegistrarse = findViewById(R.id.btnRegistrarse);

        btnRegistrarse.setOnClickListener(v -> registrar());

        findViewById(R.id.tvIniciarSesion).setOnClickListener(v -> finish());
    }

    private void registrar() {
        String nombre = getText(etNombreCompleto);
        String email = getText(etEmail);
        String telefono = getText(etTelefono);
        String usuario = getText(etNombreUsuario);
        String dni = getText(etDni);
        String password = getText(etPassword);

        if (nombre.isEmpty() || email.isEmpty() || usuario.isEmpty()
                || dni.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completá todos los campos obligatorios", Toast.LENGTH_SHORT).show();
            return;
        }

        if (password.length() < 8) {
            Toast.makeText(this, "La contraseña debe tener al menos 8 caracteres", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        RetrofitClient.getService(this)
                .registrar(new RegistroRequest(nombre, email, telefono, usuario, dni, password))
                .enqueue(new Callback<RegistroResponse>() {
                    @Override
                    public void onResponse(Call<RegistroResponse> call, Response<RegistroResponse> response) {
                        setLoading(false);
                        Log.d("REGISTRO", "HTTP " + response.code());
                        if (response.isSuccessful()) {
                            if (response.body() != null && response.body().getEmail() != null) {
                                Intent intent = new Intent(RegistroActivity.this, VerificarEmailActivity.class);
                                intent.putExtra("email", response.body().getEmail());
                                startActivity(intent);
                            } else {
                                // 201 pero body nulo: igual navegamos con el email ingresado
                                Intent intent = new Intent(RegistroActivity.this, VerificarEmailActivity.class);
                                intent.putExtra("email", getText(etEmail));
                                startActivity(intent);
                            }
                        } else {
                            mostrarError(response);
                        }
                    }

                    @Override
                    public void onFailure(Call<RegistroResponse> call, Throwable t) {
                        setLoading(false);
                        Log.e("REGISTRO", "onFailure: " + t.getMessage(), t);
                        Toast.makeText(RegistroActivity.this,
                                "Sin conexión con el servidor: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        btnRegistrarse.setEnabled(!loading);
        btnRegistrarse.setText(loading ? "Creando cuenta..." : "Crear cuenta");
    }

    private void mostrarError(Response<?> response) {
        try {
            String rawError = response.errorBody().string();
            Log.e("REGISTRO", "Error HTTP " + response.code() + ": " + rawError);
            ErrorResponse error = new Gson().fromJson(rawError, ErrorResponse.class);
            if (error != null && error.getError() != null) {
                Toast.makeText(this, error.getError(), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Error " + response.code() + ": " + rawError, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e("REGISTRO", "No se pudo parsear el error: " + e.getMessage());
            Toast.makeText(this, "Error HTTP " + response.code(), Toast.LENGTH_LONG).show();
        }
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }
}
