package com.example.payxmobile.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;
import com.example.payxmobile.model.MensajeResponse;
import com.example.payxmobile.model.ReenviarCodigoRequest;
import com.example.payxmobile.model.ResetPasswordRequest;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.utils.CamposUi;
import com.example.payxmobile.utils.EsperaReenvio;
import com.example.payxmobile.utils.SesionUtils;
import com.example.payxmobile.utils.Validadores;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.Map;

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
        CamposUi.codigoEspaciado(etCodigo, 20);
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

        boolean hayError = CamposUi.error(etCodigo, Validadores.codigo(codigo));
        hayError |= CamposUi.error(etNuevaPassword, Validadores.passwordNueva(nuevaPassword));
        hayError |= CamposUi.error(etConfirmarPassword, Validadores.confirmacion(nuevaPassword, confirmar));
        if (hayError) return;
        if (enCurso) return;

        setLoading(true);

        RetrofitClient.getService(this)
                .resetearPassword(new ResetPasswordRequest(email, codigo, nuevaPassword))
                .enqueue(new Callback<MensajeResponse>() {
                    @Override
                    public void onResponse(Call<MensajeResponse> call, Response<MensajeResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful()) {
                            // El reset cierra TODAS las sesiones de la cuenta: si en este teléfono quedaba un token
                            // guardado ya no sirve, se borra. Y como el código llegó a su casilla, el backend deja
                            // el email verificado: el login funciona directo, sin pedir verificación.
                            SesionUtils.limpiar(ResetPasswordActivity.this);
                            Toast.makeText(ResetPasswordActivity.this,
                                    "Contraseña actualizada. Iniciá sesión.", Toast.LENGTH_LONG).show();
                            Intent intent = new Intent(ResetPasswordActivity.this, LoginActivity.class);
                            intent.putExtra("email", email);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        } else {
                            mostrarError(ApiErrores.rechazo(response));
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
                            CamposUi.error(etCodigo, null); // el "demasiados intentos" era del código anterior
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

    /**
     * Errores del código o de la contraseña nueva debajo de su campo. Tras 5 errores el backend rechaza
     * ESE código aunque después se ingrese el correcto: se borra el campo y se resalta "Reenviar".
     */
    private void mostrarError(ApiErrores.Rechazo rechazo) {
        String errorCodigo = rechazo.campos.get("codigo");
        if (errorCodigo != null && ApiErrores.esCodigoAgotado(errorCodigo)) {
            etCodigo.setText("");
            esperaReenvio.destacar();
        }
        Map<String, EditText> campos = new HashMap<>();
        campos.put("codigo", etCodigo);
        campos.put("nuevaPassword", etNuevaPassword);
        if (!CamposUi.errores(rechazo.campos, campos)) {
            Toast.makeText(this, rechazo.mensaje, Toast.LENGTH_LONG).show();
        }
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
