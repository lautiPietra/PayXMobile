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
import com.example.payxmobile.model.ErrorResponse;
import com.example.payxmobile.model.MensajeResponse;
import com.example.payxmobile.model.ReenviarCodigoRequest;
import com.example.payxmobile.model.VerificarCodigoRequest;
import com.example.payxmobile.network.RetrofitClient;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VerificarEmailActivity extends AppCompatActivity {

    private TextInputEditText etCodigo;
    private Button btnVerificar;
    private ProgressBar progressBar;
    private String email;

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

        findViewById(R.id.tvReenviar).setOnClickListener(v -> reenviarCodigo());
    }

    private void verificar() {
        String codigo = etCodigo.getText() != null ? etCodigo.getText().toString().trim() : "";

        if (codigo.length() != 6) {
            Toast.makeText(this, "Ingresá el código de 6 dígitos", Toast.LENGTH_SHORT).show();
            return;
        }

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
                                "Sin conexión con el servidor", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void reenviarCodigo() {
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
                                "Sin conexión con el servidor", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        btnVerificar.setEnabled(!loading);
        btnVerificar.setText(loading ? "Verificando..." : "Verificar cuenta");
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void mostrarError(Response<?> response) {
        try {
            ErrorResponse error = new Gson().fromJson(
                    response.errorBody().charStream(), ErrorResponse.class);
            Toast.makeText(this, error.getError(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error al verificar", Toast.LENGTH_SHORT).show();
        }
    }
}
