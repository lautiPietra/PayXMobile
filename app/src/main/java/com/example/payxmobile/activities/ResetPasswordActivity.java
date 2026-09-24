package com.example.payxmobile.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;
import com.example.payxmobile.model.MensajeResponse;
import com.example.payxmobile.model.ReenviarCodigoRequest;
import com.example.payxmobile.model.ResetPasswordRequest;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.utils.EsperaReenvio;
import com.example.payxmobile.utils.Validadores;
import com.google.android.material.textfield.TextInputEditText;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ResetPasswordActivity extends AppCompatActivity {

    private TextInputEditText etCodigo, etNuevaPassword, etConfirmarPassword;
    private Button btnCambiarPassword;
    private String email;
    private boolean enCurso = false;
    private EsperaReenvio esperaReenvio;

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

        // Tras tocar "Reenviar" queda bloqueado 1 minuto (evita mandar un mail por cada toque)
        TextView tvReenviar = findViewById(R.id.tvReenviar);
        esperaReenvio = new EsperaReenvio(this, EsperaReenvio.TIPO_RESET, email, tvReenviar);
        esperaReenvio.reanudar();
        tvReenviar.setOnClickListener(v -> reenviarCodigo());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (esperaReenvio != null) esperaReenvio.detener();
    }

    private void cambiarPassword() {
        String codigo = getText(etCodigo);
        // Las contraseñas no se recortan (igual que la web)
        String nuevaPassword = sinRecortar(etNuevaPassword);
        String confirmar = sinRecortar(etConfirmarPassword);

        String error = Validadores.codigo(codigo);
        if (error == null) error = Validadores.passwordNueva(nuevaPassword);
        if (error == null) error = Validadores.confirmacion(nuevaPassword, confirmar);
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
            return;
        }
        if (enCurso) return;

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
                            Toast.makeText(ResetPasswordActivity.this,
                                    ApiErrores.mensaje(response), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<MensajeResponse> call, Throwable t) {
                        setLoading(false);
                        Toast.makeText(ResetPasswordActivity.this,
                                ApiErrores.mensajeFallo(t), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void reenviarCodigo() {
        if (!esperaReenvio.puedeReenviar()) return;
        esperaReenvio.iniciar();
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
                                    ApiErrores.mensaje(response), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<MensajeResponse> call, Throwable t) {
                        Toast.makeText(ResetPasswordActivity.this,
                                ApiErrores.mensajeFallo(t), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        enCurso = loading;
        btnCambiarPassword.setEnabled(!loading);
        btnCambiarPassword.setText(loading ? "Cambiando..." : "Cambiar contraseña");
    }

    private String sinRecortar(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString() : "";
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }
}
