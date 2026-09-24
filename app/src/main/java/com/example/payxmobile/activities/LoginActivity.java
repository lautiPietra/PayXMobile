package com.example.payxmobile.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.example.payxmobile.R;
import com.example.payxmobile.model.GoogleLoginRequest;
import com.example.payxmobile.model.LoginRequest;
import com.example.payxmobile.model.LoginResponse;
import com.example.payxmobile.model.MensajeResponse;
import com.example.payxmobile.model.ReenviarCodigoRequest;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.saldos.SaldosRepository;
import com.example.payxmobile.transferencias.TransferenciasRepository;
import com.example.payxmobile.utils.CamposUi;
import com.example.payxmobile.utils.SesionUtils;
import com.example.payxmobile.utils.SessionManager;
import com.example.payxmobile.utils.Validadores;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.textfield.TextInputEditText;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private Button btnLogin, btnGoogle, btnVerificarEmail;
    private SessionManager sessionManager;
    private CredentialManager credentialManager;

    // Evita requests duplicadas por doble tap (login normal, Google o reenvío de código)
    private boolean enCurso = false;
    // Email con el que el backend respondió "Debes verificar tu email..."
    private String emailSinVerificar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sessionManager = new SessionManager(this);
        credentialManager = CredentialManager.create(this);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogle = findViewById(R.id.btnGoogle);
        btnVerificarEmail = findViewById(R.id.btnVerificarEmail);

        String emailExtras = getIntent().getStringExtra("email");
        if (emailExtras != null) {
            etEmail.setText(emailExtras);
        }

        // Ej: "Tu sesión venció. Iniciá sesión de nuevo"
        String mensaje = getIntent().getStringExtra(SesionUtils.EXTRA_MENSAJE);
        if (mensaje != null && savedInstanceState == null) {
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
        }

        btnLogin.setOnClickListener(v -> iniciarSesion());
        btnVerificarEmail.setOnClickListener(v -> verificarEmailAhora());

        findViewById(R.id.tvRegistrate).setOnClickListener(v ->
                startActivity(new Intent(this, RegistroActivity.class))
        );

        findViewById(R.id.tvOlvidePassword).setOnClickListener(v ->
                startActivity(new Intent(this, OlvidePasswordActivity.class))
        );

        btnGoogle.setOnClickListener(v -> iniciarSesionConGoogle());
    }

    // ── Login con contraseña ─────────────────────────────────────────────────

    private void iniciarSesion() {
        if (enCurso) return;
        String email = getText(etEmail);
        // La contraseña NO se recorta: el backend la compara tal cual (igual que la web)
        String password = etPassword.getText() != null ? etPassword.getText().toString() : "";

        boolean hayError = CamposUi.error(etEmail, Validadores.email(email));
        hayError |= CamposUi.error(etPassword, Validadores.passwordObligatoria(password));
        if (hayError) return;

        btnVerificarEmail.setVisibility(View.GONE);
        setLoading(true);

        RetrofitClient.getService(this)
                .login(new LoginRequest(email, password))
                .enqueue(new Callback<LoginResponse>() {
                    @Override
                    public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null) {
                            guardarSesionYNavegar(response.body());
                        } else {
                            String mensaje = ApiErrores.mensaje(response);
                            Toast.makeText(LoginActivity.this, mensaje, Toast.LENGTH_LONG).show();
                            if (ApiErrores.esEmailSinVerificar(mensaje)) {
                                emailSinVerificar = email;
                                btnVerificarEmail.setVisibility(View.VISIBLE);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<LoginResponse> call, Throwable t) {
                        setLoading(false);
                        Toast.makeText(LoginActivity.this,
                                ApiErrores.mensajeFallo(t), Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** Igual que la web: reenvía el código y abre la verificación con ese email. */
    private void verificarEmailAhora() {
        if (enCurso || emailSinVerificar == null) return;
        setLoading(true);
        RetrofitClient.getService(this)
                .reenviarCodigo(new ReenviarCodigoRequest(emailSinVerificar))
                .enqueue(new Callback<MensajeResponse>() {
                    @Override
                    public void onResponse(Call<MensajeResponse> call, Response<MensajeResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful()) {
                            btnVerificarEmail.setVisibility(View.GONE);
                            Intent intent = new Intent(LoginActivity.this, VerificarEmailActivity.class);
                            intent.putExtra("email", emailSinVerificar);
                            startActivity(intent);
                        } else {
                            Toast.makeText(LoginActivity.this,
                                    ApiErrores.mensaje(response), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<MensajeResponse> call, Throwable t) {
                        setLoading(false);
                        Toast.makeText(LoginActivity.this,
                                ApiErrores.mensajeFallo(t), Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ── Login con Google ─────────────────────────────────────────────────────

    private void iniciarSesionConGoogle() {
        if (enCurso) return;
        setLoading(true);

        // serverClientId = Client ID WEB (el mismo de la web): el backend exige que sea el "aud" del ID token
        GetSignInWithGoogleOption opcionGoogle = new GetSignInWithGoogleOption
                .Builder(getString(R.string.google_web_client_id))
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(opcionGoogle)
                .build();

        credentialManager.getCredentialAsync(
                this,
                request,
                new CancellationSignal(),
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        procesarCredencialGoogle(result);
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        setLoading(false);
                        // Cancelar el selector no es un error
                        if (e instanceof GetCredentialCancellationException) return;
                        String mensaje = e instanceof NoCredentialException
                                ? "No hay una cuenta de Google disponible en este dispositivo. Agregá una en Ajustes > Cuentas e intentá de nuevo"
                                : "No se pudo iniciar sesión con Google. Verificá que el dispositivo tenga Google Play Services actualizado";
                        Toast.makeText(LoginActivity.this, mensaje, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    private void procesarCredencialGoogle(GetCredentialResponse result) {
        Credential credential = result.getCredential();

        if (!(credential instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
            setLoading(false);
            Toast.makeText(this, "Credencial de Google inválida", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            GoogleIdTokenCredential googleIdTokenCredential =
                    GoogleIdTokenCredential.createFrom(((CustomCredential) credential).getData());
            enviarTokenGoogleAlBackend(googleIdTokenCredential.getIdToken());
        } catch (Exception e) {
            // GoogleIdTokenCredential.createFrom (Kotlin) lanza GoogleIdTokenParsingException
            // en runtime sin declararla "throws" para Java, así que se captura genérico.
            setLoading(false);
            Toast.makeText(this, "No se pudo leer la credencial de Google", Toast.LENGTH_SHORT).show();
        }
    }

    private void enviarTokenGoogleAlBackend(String idToken) {
        RetrofitClient.getService(this)
                .loginConGoogle(new GoogleLoginRequest(idToken))
                .enqueue(new Callback<LoginResponse>() {
                    @Override
                    public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null) {
                            guardarSesionYNavegar(response.body());
                        } else {
                            Toast.makeText(LoginActivity.this,
                                    ApiErrores.mensaje(response), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<LoginResponse> call, Throwable t) {
                        setLoading(false);
                        Toast.makeText(LoginActivity.this,
                                ApiErrores.mensajeFallo(t), Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void guardarSesionYNavegar(LoginResponse body) {
        if (body.getToken() == null || body.getToken().isEmpty()) {
            Toast.makeText(this, ApiErrores.MSG_RESPUESTA_INESPERADA, Toast.LENGTH_LONG).show();
            return;
        }
        // Estado de saldos limpio para la cuenta que entra
        SaldosRepository.get(this).limpiar();
        TransferenciasRepository.get(this).limpiar();
        // No se llama a /api/notificaciones/registro-login: el backend ya crea la notificación INICIO_SES
        sessionManager.saveSession(
                body.getToken(),
                body.getId(),
                body.getNombreCompleto(),
                body.getEmail(),
                body.getNombreUsuario(),
                body.getRol(),
                body.getFotoPerfilUrl()
        );
        irAHome();
    }

    private void irAHome() {
        Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void setLoading(boolean loading) {
        enCurso = loading;
        btnLogin.setEnabled(!loading);
        btnGoogle.setEnabled(!loading);
        btnVerificarEmail.setEnabled(!loading);
        btnLogin.setText(loading ? "Ingresando..." : "Iniciar sesión");
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }
}
