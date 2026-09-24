package com.example.payxmobile.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;
import com.example.payxmobile.model.MensajeResponse;
import com.example.payxmobile.model.ReenviarCodigoRequest;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.utils.CamposUi;
import com.example.payxmobile.utils.Validadores;
import com.google.android.material.textfield.TextInputEditText;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OlvidePasswordActivity extends AppCompatActivity {

    private TextInputEditText etEmail;
    private Button btnEnviar;
    private boolean enCurso = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_olvide_password);

        etEmail = findViewById(R.id.etEmail);
        btnEnviar = findViewById(R.id.btnEnviar);

        btnEnviar.setOnClickListener(v -> solicitarReset());

        findViewById(R.id.tvVolver).setOnClickListener(v -> finish());
    }

    private void solicitarReset() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

        String errorEmail = Validadores.email(email);
        if (errorEmail != null) {
            CamposUi.error(etEmail, errorEmail);
            return;
        }
        if (enCurso) return;

        setLoading(true);

        RetrofitClient.getService(this)
                .solicitarReset(new ReenviarCodigoRequest(email))
                .enqueue(new Callback<MensajeResponse>() {
                    @Override
                    public void onResponse(Call<MensajeResponse> call, Response<MensajeResponse> response) {
                        setLoading(false);
                        // El backend responde 200 exista o no el email: se avanza igual a la pantalla del código
                        if (response.isSuccessful()) {
                            Intent intent = new Intent(OlvidePasswordActivity.this, ResetPasswordActivity.class);
                            intent.putExtra("email", email);
                            startActivity(intent);
                        } else {
                            Toast.makeText(OlvidePasswordActivity.this,
                                    ApiErrores.mensaje(response), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<MensajeResponse> call, Throwable t) {
                        setLoading(false);
                        Toast.makeText(OlvidePasswordActivity.this,
                                ApiErrores.mensajeFallo(t), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        enCurso = loading;
        btnEnviar.setEnabled(!loading);
        btnEnviar.setText(loading ? "Enviando..." : "Enviar código");
    }
}
