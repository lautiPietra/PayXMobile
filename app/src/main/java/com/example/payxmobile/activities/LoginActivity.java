package com.example.payxmobile.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.example.payxmobile.R;
import com.example.payxmobile.model.ErrorResponse;
import com.example.payxmobile.model.GoogleLoginRequest;
import com.example.payxmobile.model.LoginRequest;
import com.example.payxmobile.model.LoginResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.utils.SessionManager;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private Button btnLogin;
    private SessionManager sessionManager;
    private CredentialManager credentialManager;
    private final Executor executorGoogle = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        sessionManager = new SessionManager(this);
        credentialManager = CredentialManager.create(this);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        Button btnGoogle = findViewById(R.id.btnGoogle);

        String emailExtras = getIntent().getStringExtra("email");
        if (emailExtras != null) {
            etEmail.setText(emailExtras);
        }

        btnLogin.setOnClickListener(v -> iniciarSesion());

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
        String email = getText(etEmail);
        String password = getText(etPassword);

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Completá todos los campos", Toast.LENGTH_SHORT).show();
            return;
        }

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
                            mostrarError(response);
                        }
                    }

                    @Override
                    public void onFailure(Call<LoginResponse> call, Throwable t) {
                        setLoading(false);
                        Toast.makeText(LoginActivity.this,
                                "Sin conexión con el servidor", Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ── Login con Google ─────────────────────────────────────────────────────

    private void iniciarSesionConGoogle() {
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
                executorGoogle,
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        runOnUiThread(() -> procesarCredencialGoogle(result));
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        runOnUiThread(() -> {
                            if (!(e instanceof GetCredentialCancellationException)) {
                                Toast.makeText(LoginActivity.this,
                                        "No se pudo iniciar sesión con Google", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
        );
    }

    private void procesarCredencialGoogle(GetCredentialResponse result) {
        Credential credential = result.getCredential();

        if (!(credential instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
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
            Toast.makeText(this, "No se pudo leer la credencial de Google", Toast.LENGTH_SHORT).show();
        }
    }

    private void enviarTokenGoogleAlBackend(String idToken) {
        setLoading(true);

        RetrofitClient.getService(this)
                .loginConGoogle(new GoogleLoginRequest(idToken))
                .enqueue(new Callback<LoginResponse>() {
                    @Override
                    public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null) {
                            guardarSesionYNavegar(response.body());
                        } else {
                            mostrarError(response);
                        }
                    }

                    @Override
                    public void onFailure(Call<LoginResponse> call, Throwable t) {
                        setLoading(false);
                        Toast.makeText(LoginActivity.this,
                                "Sin conexión con el servidor", Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void guardarSesionYNavegar(LoginResponse body) {
        sessionManager.saveSession(
                body.getToken(),
                body.getId(),
                body.getNombreCompleto(),
                body.getEmail(),
                body.getNombreUsuario(),
                body.getRol()
        );
        irAHome();
    }

    private void irAHome() {
        Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "Ingresando..." : "Iniciar sesión");
    }

    private void mostrarError(Response<?> response) {
        try {
            ErrorResponse error = new Gson().fromJson(
                    response.errorBody().charStream(), ErrorResponse.class);
            Toast.makeText(this, error.getError(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Error al iniciar sesión", Toast.LENGTH_SHORT).show();
        }
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }
}
