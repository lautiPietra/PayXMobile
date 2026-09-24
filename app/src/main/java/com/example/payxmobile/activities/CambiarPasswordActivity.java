package com.example.payxmobile.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;
import com.example.payxmobile.model.CambiarPasswordRequest;
import com.example.payxmobile.model.MensajeResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.utils.CamposUi;
import com.example.payxmobile.utils.Validadores;
import com.google.android.material.textfield.TextInputEditText;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CambiarPasswordActivity extends AppCompatActivity {

    private TextInputEditText etPasswordActual, etNuevaPassword, etConfirmarPassword;
    private Button btnCambiar;
    private boolean enCurso = false;

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
        // Las contraseñas no se recortan (igual que la web)
        String actual = getText(etPasswordActual);
        String nueva = getText(etNuevaPassword);
        String confirmar = getText(etConfirmarPassword);

        boolean hayError = CamposUi.error(etPasswordActual, Validadores.passwordObligatoria(actual));
        hayError |= CamposUi.error(etNuevaPassword, Validadores.passwordNueva(nueva));
        hayError |= CamposUi.error(etConfirmarPassword, Validadores.confirmacion(nueva, confirmar));
        if (hayError) {
            return;
        }
        if (enCurso) return;
        enCurso = true;

        btnCambiar.setEnabled(false);
        btnCambiar.setText("Cambiando...");

        RetrofitClient.getService(this)
                .cambiarPassword(new CambiarPasswordRequest(actual, nueva))
                .enqueue(new Callback<MensajeResponse>() {
                    @Override
                    public void onResponse(Call<MensajeResponse> call, Response<MensajeResponse> response) {
                        enCurso = false;
                        btnCambiar.setEnabled(true);
                        btnCambiar.setText("Cambiar contraseña");
                        if (response.isSuccessful()) {
                            // El JWT sigue siendo válido: la sesión no se cierra
                            etPasswordActual.setText("");
                            etNuevaPassword.setText("");
                            etConfirmarPassword.setText("");
                            Toast.makeText(CambiarPasswordActivity.this,
                                    "Contraseña actualizada correctamente", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Toast.makeText(CambiarPasswordActivity.this,
                                    ApiErrores.mensaje(response), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<MensajeResponse> call, Throwable t) {
                        enCurso = false;
                        btnCambiar.setEnabled(true);
                        btnCambiar.setText("Cambiar contraseña");
                        Toast.makeText(CambiarPasswordActivity.this,
                                ApiErrores.mensajeFallo(t), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString() : "";
    }
}
