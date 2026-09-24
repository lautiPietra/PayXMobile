package com.example.payxmobile.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;
import com.example.payxmobile.model.MensajeResponse;
import com.example.payxmobile.model.ReenviarCodigoRequest;
import com.example.payxmobile.model.VerificarCodigoRequest;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.utils.EsperaReenvio;
import com.example.payxmobile.utils.Validadores;
import com.google.android.material.textfield.TextInputEditText;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VerificarEmailActivity extends AppCompatActivity {

    private TextInputEditText etCodigo;
    private Button btnVerificar;
    private ProgressBar progressBar;
    private String email;
    private boolean enCurso = false;
    private EsperaReenvio esperaReenvio;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_verificar_email);

        email = getIntent().getStringExtra("email");
        if (email == null) {
            finish();
            return;
        }

        etCodigo = findViewById(R.id.etCodigo);
        btnVerificar = findViewById(R.id.btnVerificar);
        progressBar = findViewById(R.id.progressBar);

        TextView tvEmailDestino = findViewById(R.id.tvEmailDestino);
        tvEmailDestino.setText("Enviamos un código de 6 dígitos a " + email);

        btnVerificar.setOnClickListener(v -> verificar());

        // Tras tocar "Reenviar" queda bloqueado 1 minuto (evita mandar un mail por cada toque)
        TextView tvReenviar = findViewById(R.id.tvReenviar);
        esperaReenvio = new EsperaReenvio(this, EsperaReenvio.TIPO_VERIFICACION, email, tvReenviar);
        esperaReenvio.reanudar();
        tvReenviar.setOnClickListener(v -> reenviarCodigo());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (esperaReenvio != null) esperaReenvio.detener();
    }

    private void verificar() {
        String codigo = etCodigo.getText() != null ? etCodigo.getText().toString().trim() : "";

        String errorCodigo = Validadores.codigo(codigo);
        if (errorCodigo != null) {
            Toast.makeText(this, errorCodigo, Toast.LENGTH_SHORT).show();
            return;
        }
        if (enCurso) return;

        setLoading(true);

        RetrofitClient.getService(this)
                .verificarEmail(new VerificarCodigoRequest(email, codigo))
                .enqueue(new Callback<MensajeResponse>() {
                    @Override
                    public void onResponse(Call<MensajeResponse> call, Response<MensajeResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful()) {
                            Toast.makeText(VerificarEmailActivity.this,
                                    "Email verificado. ¡Ya podés iniciar sesión!", Toast.LENGTH_LONG).show();
                            Intent intent = new Intent(VerificarEmailActivity.this, LoginActivity.class);
                            intent.putExtra("email", email);
                            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(intent);
                            finish();
                        } else {
                            mostrarError(response);
                        }
                    }

                    @Override
                    public void onFailure(Call<MensajeResponse> call, Throwable t) {
                        setLoading(false);
                        Toast.makeText(VerificarEmailActivity.this,
                                ApiErrores.mensajeFallo(t), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void reenviarCodigo() {
        if (!esperaReenvio.puedeReenviar()) return;
        esperaReenvio.iniciar();
        RetrofitClient.getService(this)
                .reenviarCodigo(new ReenviarCodigoRequest(email))
                .enqueue(new Callback<MensajeResponse>() {
                    @Override
                    public void onResponse(Call<MensajeResponse> call, Response<MensajeResponse> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(VerificarEmailActivity.this,
                                    "Código reenviado. Revisá tu email", Toast.LENGTH_SHORT).show();
                        } else {
                            mostrarError(response);
                        }
                    }

                    @Override
                    public void onFailure(Call<MensajeResponse> call, Throwable t) {
                        Toast.makeText(VerificarEmailActivity.this,
                                ApiErrores.mensajeFallo(t), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        enCurso = loading;
        btnVerificar.setEnabled(!loading);
        btnVerificar.setText(loading ? "Verificando..." : "Verificar cuenta");
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void mostrarError(Response<?> response) {
        Toast.makeText(this, ApiErrores.mensaje(response), Toast.LENGTH_LONG).show();
    }
}
