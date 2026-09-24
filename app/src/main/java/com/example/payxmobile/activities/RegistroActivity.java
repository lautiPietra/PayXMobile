package com.example.payxmobile.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;
import com.example.payxmobile.model.RegistroRequest;
import com.example.payxmobile.model.RegistroResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.utils.CamposUi;
import com.example.payxmobile.utils.Validadores;
import com.google.android.material.textfield.TextInputEditText;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegistroActivity extends AppCompatActivity {

    private TextInputEditText etNombreCompleto, etEmail, etTelefono,
            etNombreUsuario, etDni, etPassword;
    private Button btnRegistrarse;
    private boolean enCurso = false;

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
        if (enCurso) return;
        String nombre = getText(etNombreCompleto);
        String email = getText(etEmail);
        String telefono = getText(etTelefono);
        String usuario = getText(etNombreUsuario);
        String dni = getText(etDni);
        // La contraseña no se recorta (igual que la web)
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        // Mismas reglas que RegistroRequest del backend: si no se cumplen responde 403 vacío
        boolean hayError = CamposUi.error(etNombreCompleto, Validadores.nombreCompleto(nombre));
        hayError |= CamposUi.error(etEmail, Validadores.email(email));
        hayError |= CamposUi.error(etTelefono, Validadores.telefono(telefono));
        hayError |= CamposUi.error(etNombreUsuario, Validadores.nombreUsuario(usuario));
        hayError |= CamposUi.error(etDni, Validadores.dni(dni));
        hayError |= CamposUi.error(etPassword, Validadores.passwordNueva(password));
        if (hayError) {
            return;
        }

        setLoading(true);

        RetrofitClient.getService(this)
                .registrar(new RegistroRequest(nombre, email, telefono, usuario, dni, password))
                .enqueue(new Callback<RegistroResponse>() {
                    @Override
                    public void onResponse(Call<RegistroResponse> call, Response<RegistroResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful()) {
                            // Si el 201 viene sin body, se usa el email ingresado
                            String emailDestino = response.body() != null && response.body().getEmail() != null
                                    ? response.body().getEmail() : email;
                            Intent intent = new Intent(RegistroActivity.this, VerificarEmailActivity.class);
                            intent.putExtra("email", emailDestino);
                            startActivity(intent);
                        } else {
                            Toast.makeText(RegistroActivity.this,
                                    ApiErrores.mensaje(response), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<RegistroResponse> call, Throwable t) {
                        setLoading(false);
                        Toast.makeText(RegistroActivity.this,
                                ApiErrores.mensajeFallo(t), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        enCurso = loading;
        btnRegistrarse.setEnabled(!loading);
        btnRegistrarse.setText(loading ? "Creando cuenta..." : "Crear cuenta");
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }
}
