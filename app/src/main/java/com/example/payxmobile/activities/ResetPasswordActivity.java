package com.example.payxmobile.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;
import com.example.payxmobile.model.ErrorResponse;
import com.example.payxmobile.model.MensajeResponse;
import com.example.payxmobile.model.ReenviarCodigoRequest;
import com.example.payxmobile.model.ResetPasswordRequest;
import com.example.payxmobile.network.RetrofitClient;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ResetPasswordActivity extends AppCompatActivity {

    private TextInputEditText etCodigo, etNuevaPassword, etConfirmarPassword;
    private Button btnCambiarPassword;
    private String email;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        email = getIntent().getStringExtra("email");
        if (email == null) {
            finish();
            return;
        }

        etCodigo = findViewById(R.id.etCodigo);
        etNuevaPassword = findViewById(R.id.etNuevaPassword);
        etConfirmarPassword = findViewById(R.id.etConfirmarPassword);
        btnCambiarPassword = findViewById(R.id.btnCambiarPassword);

        TextView tvEmailDestino = findViewById(R.id.tvEmailDestino);
        tvEmailDestino.setText("Ingresá el código que enviamos a " + email + " y tu nueva contraseña.");

        btnCambiarPassword.setOnClickListener(v -> cambiarPassword());

        findViewById(R.id.tvReenviar).setOnClickListener(v -> reenviarCodigo());
    }

    private void cambiarPassword() {
        String codigo = getText(etCodigo);
        String nuevaPassword = getText(etNuevaPassword);
        String confirmar = getText(etConfirmarPassword);

        if (codigo.length() != 6) {
            Toast.makeText(this, "Ingresá el código de 6 dígitos", Toast.LENGTH_SHORT).show();
            return;
        }

        if (nuevaPassword.length() < 8) {
            Toast.makeText(this, "La contraseña debe tener al menos 8 caracteres", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!nuevaPassword.equals(confirmar)) {
            Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        RetrofitClient.getService(this)
                .resetearPassword(new ResetPasswordRequest(email, codigo, nuevaPassword))
                .enqueue(new Callback<MensajeResponse>() {
                    @Override
                    public void onResponse(Call<MensajeResponse> call, Response<MensajeResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful()) {
                            Toast.makeText(ResetPasswordActivity.this,
                                    "Contraseña actualizada. Iniciá sesión.", Toast.LENGTH_LONG).show();
                            Intent intent = new Intent(ResetPasswordActivity.this, LoginActivity.class);
                            intent.putExtra("email", email);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(intent);
                            finish();
                        } else {
                            try {
                                ErrorResponse error = new Gson().fromJson(
                                        response.errorBody().charStream(), ErrorResponse.class);
                                Toast.makeText(ResetPasswordActivity.this,
                                        error.getError(), Toast.LENGTH_LONG).show();
                            } catch (Exception e) {
                                Toast.makeText(ResetPasswordActivity.this,
                                        "Error al cambiar la contraseña", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<MensajeResponse> call, Throwable t) {
                        setLoading(false);
                        Toast.makeText(ResetPasswordActivity.this,
                                "Sin conexión con el servidor", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void reenviarCodigo() {
        RetrofitClient.getService(this)
                .solicitarReset(new ReenviarCodigoRequest(email))
                .enqueue(new Callback<MensajeResponse>() {
                    @Override
                    public void onResponse(Call<MensajeResponse> call, Response<MensajeResponse> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(ResetPasswordActivity.this,
                                    "Código reenviado. Revisá tu email", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(ResetPasswordActivity.this,
                                    "No se pudo reenviar el código", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<MensajeResponse> call, Throwable t) {
                        Toast.makeText(ResetPasswordActivity.this,
                                "Sin conexión con el servidor", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        btnCambiarPassword.setEnabled(!loading);
        btnCambiarPassword.setText(loading ? "Cambiando..." : "Cambiar contraseña");
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }
}
