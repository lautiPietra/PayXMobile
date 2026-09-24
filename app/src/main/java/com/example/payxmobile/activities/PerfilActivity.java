package com.example.payxmobile.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;
import com.example.payxmobile.model.ActualizarPerfilRequest;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.network.ApiErrores;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.utils.Avatar;
import com.example.payxmobile.utils.CamposUi;
import com.example.payxmobile.utils.ImagenPerfil;
import com.example.payxmobile.utils.SesionUtils;
import com.example.payxmobile.utils.SessionManager;
import com.example.payxmobile.utils.Validadores;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PerfilActivity extends AppCompatActivity {

    private static final String PENDIENTE = "Pendiente de completar";

    private TextView tvInicialHeader, tvNombreHeader, tvUsernameHeader;
    private TextView tvNombreCompleto, tvEmail, tvDni, tvCvu;
    private TextView tvSubiendoFoto, tvErrorFoto, tvAvisoDatosPendientes;
    private TextView tvEstadoPerfil;
    private View layoutEstadoPerfil, progressPerfil, btnReintentarPerfil;
    private ImageView ivFotoPerfil;
    private ImageButton btnEditarFoto;
    private TextInputLayout tilDni;
    private TextInputEditText etNombreUsuario, etAlias, etTelefono, etDni;
    private Button btnGuardar;
    private SessionManager sessionManager;

    private PerfilResponse perfil;
    private boolean guardando = false;
    private boolean subiendoFoto = false;

    private final ExecutorService executorImagen = Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<PickVisualMediaRequest> selectorFoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil);

        sessionManager = new SessionManager(this);

        tvInicialHeader = findViewById(R.id.tvInicialHeader);
        tvNombreHeader = findViewById(R.id.tvNombreHeader);
        tvUsernameHeader = findViewById(R.id.tvUsernameHeader);
        tvNombreCompleto = findViewById(R.id.tvNombreCompleto);
        tvEmail = findViewById(R.id.tvEmail);
        tvDni = findViewById(R.id.tvDni);
        tvCvu = findViewById(R.id.tvCvu);
        ivFotoPerfil = findViewById(R.id.ivFotoPerfil);
        btnEditarFoto = findViewById(R.id.btnEditarFoto);
        tvSubiendoFoto = findViewById(R.id.tvSubiendoFoto);
        tvErrorFoto = findViewById(R.id.tvErrorFoto);
        tvAvisoDatosPendientes = findViewById(R.id.tvAvisoDatosPendientes);
        layoutEstadoPerfil = findViewById(R.id.layoutEstadoPerfil);
        progressPerfil = findViewById(R.id.progressPerfil);
        tvEstadoPerfil = findViewById(R.id.tvEstadoPerfil);
        btnReintentarPerfil = findViewById(R.id.btnReintentarPerfil);
        tilDni = findViewById(R.id.tilDni);
        etDni = findViewById(R.id.etDni);
        etNombreUsuario = findViewById(R.id.etNombreUsuario);
        etAlias = findViewById(R.id.etAlias);
        etTelefono = findViewById(R.id.etTelefono);
        btnGuardar = findViewById(R.id.btnGuardar);

        // Photo Picker: no pide permiso de almacenamiento. Si se cancela, uri == null y no se hace nada.
        selectorFoto = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) subirFoto(uri);
        });

        cargarDatosLocales();
        cargarPerfilDesdeApi();

        btnGuardar.setOnClickListener(v -> guardarCambios());
        btnReintentarPerfil.setOnClickListener(v -> cargarPerfilDesdeApi());
        btnEditarFoto.setOnClickListener(v -> elegirFoto());
        ivFotoPerfil.setOnClickListener(v -> elegirFoto());
        tvInicialHeader.setOnClickListener(v -> elegirFoto());

        findViewById(R.id.cardCambiarPassword).setOnClickListener(v ->
                startActivity(new Intent(this, CambiarPasswordActivity.class))
        );

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnCerrarSesion).setOnClickListener(v -> confirmarCerrarSesion());
    }

    // Mismo diálogo que el menú del Home
    private void confirmarCerrarSesion() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Seguro que querés cerrar sesión?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Cerrar sesión", (dialog, which) -> SesionUtils.cerrarSesion(this))
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorImagen.shutdownNow();
    }

    private void cargarDatosLocales() {
        String nombre = sessionManager.getNombreCompleto();
        Avatar.mostrar(ivFotoPerfil, tvInicialHeader, nombre, sessionManager.getFotoPerfilUrl());
        tvNombreHeader.setText(nombre);
        tvUsernameHeader.setText("@" + sessionManager.getNombreUsuario());
        tvEmail.setText(sessionManager.getEmail());
        etNombreUsuario.setText(sessionManager.getNombreUsuario());
    }

    // ── Carga del perfil ────────────────────────────────────────────────────

    private void cargarPerfilDesdeApi() {
        mostrarEstado(true, "Cargando perfil...", false);
        RetrofitClient.getService(this).obtenerPerfil()
                .enqueue(new Callback<PerfilResponse>() {
                    @Override
                    public void onResponse(Call<PerfilResponse> call, Response<PerfilResponse> response) {
                        if (isFinishing()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            layoutEstadoPerfil.setVisibility(View.GONE);
                            mostrarPerfil(response.body(), true);
                        } else {
                            mostrarEstado(false, ApiErrores.mensaje(response), true);
                        }
                    }

                    @Override
                    public void onFailure(Call<PerfilResponse> call, Throwable t) {
                        if (isFinishing()) return;
                        mostrarEstado(false, ApiErrores.mensajeFallo(t), true);
                    }
                });
    }

    private void mostrarEstado(boolean cargando, String texto, boolean reintentar) {
        layoutEstadoPerfil.setVisibility(View.VISIBLE);
        progressPerfil.setVisibility(cargando ? View.VISIBLE : View.GONE);
        tvEstadoPerfil.setText(texto);
        btnReintentarPerfil.setVisibility(reintentar ? View.VISIBLE : View.GONE);
    }

    /** @param rellenarFormulario false tras subir la foto, para no pisar lo que el usuario esté editando */
    private void mostrarPerfil(PerfilResponse p, boolean rellenarFormulario) {
        perfil = p;
        String nombre = p.getNombreCompleto();

        Avatar.mostrar(ivFotoPerfil, tvInicialHeader, nombre, p.getFotoPerfilUrl());
        tvNombreHeader.setText(nombre);
        tvUsernameHeader.setText("@" + p.getNombreUsuario());

        tvNombreCompleto.setText(nombre);
        tvEmail.setText(p.getEmail());
        tvDni.setText(p.tieneDni() ? p.getDni() : PENDIENTE);
        tvCvu.setText(p.getCvu());

        // El DNI se carga una sola vez: el campo solo aparece si la cuenta no lo tiene
        tilDni.setVisibility(p.tieneDni() ? View.GONE : View.VISIBLE);

        if (!p.tieneDni() || !p.tieneTelefono()) {
            String falta = !p.tieneDni() && !p.tieneTelefono() ? "tu DNI y tu número de teléfono"
                    : !p.tieneDni() ? "tu DNI" : "tu número de teléfono";
            tvAvisoDatosPendientes.setText("Para operar con tu cuenta necesitás completar " + falta);
            tvAvisoDatosPendientes.setVisibility(View.VISIBLE);
        } else {
            tvAvisoDatosPendientes.setVisibility(View.GONE);
        }

        if (rellenarFormulario) {
            etNombreUsuario.setText(p.getNombreUsuario());
            etAlias.setText(p.getAlias());
            etTelefono.setText(p.tieneTelefono() ? p.getTelefono() : "");
            etDni.setText("");
        }
    }

    // ── Edición de datos ─────────────────────────────────────────────────────

    private void guardarCambios() {
        if (guardando) return;
        if (perfil == null) {
            Toast.makeText(this, "Esperá a que cargue el perfil", Toast.LENGTH_SHORT).show();
            return;
        }

        String nuevoUsuario = getText(etNombreUsuario);
        String nuevoAlias = getText(etAlias);
        String nuevoTelefono = getText(etTelefono);
        String dniNuevo = perfil.tieneDni() ? null : getText(etDni);

        boolean hayError = CamposUi.error(etNombreUsuario, Validadores.nombreUsuario(nuevoUsuario));
        hayError |= CamposUi.error(etAlias, Validadores.alias(nuevoAlias));
        hayError |= CamposUi.error(etTelefono, Validadores.telefono(nuevoTelefono));
        hayError |= CamposUi.error(etDni, perfil.tieneDni() ? null : Validadores.dni(dniNuevo));
        if (hayError) {
            return;
        }

        setGuardando(true);

        RetrofitClient.getService(this)
                .actualizarPerfil(new ActualizarPerfilRequest(nuevoUsuario, nuevoTelefono, nuevoAlias, dniNuevo))
                .enqueue(new Callback<PerfilResponse>() {
                    @Override
                    public void onResponse(Call<PerfilResponse> call, Response<PerfilResponse> response) {
                        if (isFinishing()) return;
                        setGuardando(false);
                        if (response.isSuccessful() && response.body() != null) {
                            PerfilResponse p = response.body();
                            sessionManager.actualizarNombreUsuario(p.getNombreUsuario());
                            mostrarPerfil(p, true);
                            Toast.makeText(PerfilActivity.this,
                                    "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(PerfilActivity.this,
                                    ApiErrores.mensaje(response), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<PerfilResponse> call, Throwable t) {
                        if (isFinishing()) return;
                        setGuardando(false);
                        Toast.makeText(PerfilActivity.this,
                                ApiErrores.mensajeFallo(t), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void setGuardando(boolean valor) {
        guardando = valor;
        btnGuardar.setEnabled(!valor);
        btnGuardar.setText(valor ? "Guardando..." : "Guardar cambios");
    }

    // ── Foto de perfil ───────────────────────────────────────────────────────

    private void elegirFoto() {
        if (subiendoFoto) return;
        tvErrorFoto.setVisibility(View.GONE);
        selectorFoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void subirFoto(Uri uri) {
        if (subiendoFoto) return;
        setSubiendoFoto(true);

        // Decodificar y comprimir fuera del hilo principal; la subida la encola Retrofit
        executorImagen.execute(() -> {
            try {
                byte[] jpeg = ImagenPerfil.prepararJpeg(getContentResolver(), uri);
                runOnUiThread(() -> enviarFoto(jpeg));
            } catch (ImagenPerfil.ImagenInvalidaException e) {
                runOnUiThread(() -> errorFoto(e.getMessage()));
            }
        });
    }

    private void enviarFoto(byte[] jpeg) {
        if (isFinishing()) return;
        RetrofitClient.getService(this)
                .subirFotoPerfil(ImagenPerfil.crearParte(jpeg))
                .enqueue(new Callback<PerfilResponse>() {
                    @Override
                    public void onResponse(Call<PerfilResponse> call, Response<PerfilResponse> response) {
                        if (isFinishing()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            setSubiendoFoto(false);
                            PerfilResponse p = response.body();
                            // El resto de la app (menú del Home) lee la foto del usuario guardado
                            sessionManager.actualizarFotoPerfilUrl(p.getFotoPerfilUrl());
                            mostrarPerfil(p, false);
                            Toast.makeText(PerfilActivity.this,
                                    "Foto de perfil actualizada", Toast.LENGTH_SHORT).show();
                        } else {
                            errorFoto(ApiErrores.mensaje(response));
                        }
                    }

                    @Override
                    public void onFailure(Call<PerfilResponse> call, Throwable t) {
                        if (isFinishing()) return;
                        errorFoto(ApiErrores.mensajeFallo(t));
                    }
                });
    }

    private void errorFoto(String mensaje) {
        if (isFinishing()) return;
        setSubiendoFoto(false);
        tvErrorFoto.setText(mensaje);
        tvErrorFoto.setVisibility(View.VISIBLE);
    }

    private void setSubiendoFoto(boolean valor) {
        subiendoFoto = valor;
        btnEditarFoto.setEnabled(!valor);
        btnEditarFoto.setAlpha(valor ? 0.5f : 1f);
        tvSubiendoFoto.setVisibility(valor ? View.VISIBLE : View.GONE);
    }

    private String getText(TextInputEditText field) {
        return field.getText() != null ? field.getText().toString().trim() : "";
    }
}
