package com.example.payxmobile.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;
import com.example.payxmobile.model.ErrorResponse;
import com.example.payxmobile.model.MensajeResponse;
import com.example.payxmobile.model.ReenviarCodigoRequest;
import com.example.payxmobile.network.RetrofitClient;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OlvidePasswordActivity extends AppCompatActivity {

    private TextInputEditText etEmail;
    private Button btnEnviar;

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

        if (email.isEmpty()) {
            Toast.makeText(this, "Ingresá tu email", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);

        RetrofitClient.getService(this)
                .solicitarReset(new ReenviarCodigoRequest(email))
                .enqueue(new Callback<MensajeResponse>() {
                    @Override
                    public void onResponse(Call<MensajeResponse> call, Response<MensajeResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful()) {
                            Intent intent = new Intent(OlvidePasswordActivity.this, ResetPasswordActivity.class);
                            intent.putExtra("email", email);
                            startActivity(intent);
                        } else {
                            try {
                                ErrorResponse error = new Gson().fromJson(
                                        response.errorBody().charStream(), ErrorResponse.class);
                                Toast.makeText(OlvidePasswordActivity.this,
                                        error.getError(), Toast.LENGTH_LONG).show();
                            } catch (Exception e) {
                                Toast.makeText(OlvidePasswordActivity.this,
                                        "Error al enviar el código", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<MensajeResponse> call, Throwable t) {
                        setLoading(false);
                        Toast.makeText(OlvidePasswordActivity.this,
                                "Sin conexión con el servidor", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setLoading(boolean loading) {
        btnEnviar.setEnabled(!loading);
        btnEnviar.setText(loading ? "Enviando..." : "Enviar código");
    }
}
