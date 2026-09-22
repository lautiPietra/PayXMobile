package com.example.payxmobile.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;
import com.example.payxmobile.model.CambiarPasswordRequest;
import com.example.payxmobile.model.ErrorResponse;
import com.example.payxmobile.model.MensajeResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CambiarPasswordActivity extends AppCompatActivity {

    private TextInputEditText etPasswordActual, etNuevaPassword, etConfirmarPassword;
    private Button btnCambiar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cambiar_password);

        etPasswordActual = findViewById(R.id.etPasswordActual);
        etNuevaPassword = findViewById(R.id.etNuevaPassword);
        etConfirmarPassword = findViewById(R.id.etConfirmarPassword);
        btnCambiar = findViewById(R.id.btnCambiar);

        btnCambiar.setOnClickListener(v -> cambiarPassword());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void cambiarPassword() {
        String actual = getText(etPasswordActual);
        String nueva = getText(etNuevaPassword);
        String confirmar = getText(etConfirmarPassword);

        if (actual.isEmpty() || nueva.isEmpty() || confirmar.isEmpty()) {
            Toast.makeText(this, "Completá todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }
        if (nueva.length() < 8) {
            Toast.makeText(this, "La nueva contraseña debe tener al menos 8 caracteres", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!nueva.equals(confirmar)) {
            Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show();
            return;
        }

        btnCambiar.setEnabled(false);
        btnCambiar.setText("Cambiando...");

        RetrofitClient.getService(this)
                .cambiarPassword(new CambiarPasswordRequest(actual, nueva))
                .enqueue(new Callback<MensajeResponse>() {
                    @Override
                    public void onResponse(Call<MensajeResponse> call, Response<MensajeResponse> response) {
                        btnCambiar.setEnabled(true);
                        btnCambiar.setText("Cambiar contraseña");
                        if (response.isSuccessful()) {
                            Toast.makeText(CambiarPasswordActivity.this,
                                    "Contraseña actualizada correctamente", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            try {
                                ErrorResponse error = new Gson().fromJson(
                                        response.errorBody().charStream(), ErrorResponse.class);
                                Toast.makeText(CambiarPasswordActivity.this,
                                        error.getError(), Toast.LENGTH_LONG).show();
                            } catch (Exception e) {
                                Toast.makeText(CambiarPasswordActivity.this,
                                        "Error al cambiar la contraseña", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<MensajeResponse> call, Throwable t) {
                        btnCambiar.setEnabled(true);
                        btnCambiar.setText("Cambiar contraseña");
                        Toast.makeText(CambiarPasswordActivity.this,
                                "Sin conexión con el servidor", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }
}
