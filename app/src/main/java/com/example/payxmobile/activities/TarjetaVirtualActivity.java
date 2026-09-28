package com.example.payxmobile.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.payxmobile.R;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.model.TarjetaResponse;
import com.example.payxmobile.network.RetrofitClient;
import com.example.payxmobile.saldos.EstadoSaldos;
import com.example.payxmobile.saldos.SaldosRepository;
import com.example.payxmobile.tarjeta.FormatoTarjeta;
import com.example.payxmobile.tarjeta.RevelarTarjeta;
import com.example.payxmobile.tarjeta.TarjetaViewModel;
import com.example.payxmobile.utils.SesionUtils;
import com.example.payxmobile.utils.SessionManager;

import java.util.Locale;

/**
 * Tarjeta virtual (Tarjeta.jsx + TarjetaVirtual.jsx). Tapada por defecto con los últimos 4 y el
 * vencimiento del PERFIL (sin pedir /api/tarjeta); el número completo y el CVV se piden SOLO al tocar
 * "Ver tarjeta completa". Mejoras sobre la web: FLAG_SECURE (sin capturas ni vista previa en
 * recientes), copiar CVV, portapapeles marcado como sensible y datos olvidados al salir.
 */
public class TarjetaVirtualActivity extends AppCompatActivity {

    private static final long FEEDBACK_COPIADO_MS = 1500L;

    private RevelarTarjeta revelar;
    private EstadoSaldos estadoSaldos;
    private final SaldosRepository.Observador observadorSaldos = e -> {
        estadoSaldos = e;
        if (e.sesionInvalida) {
            SesionUtils.sesionInvalida(this);
            return;
        }
        render();
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    // Cuenta regresiva del botón tras un 429
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            if (revelar.segundosParaReintentar() > 0) handler.postDelayed(this, 1000);
        }
    };

    private TextView tvNumero, tvTitular, tvVencimiento, tvCvv, tvVerNumero, tvError;
    private ImageView ivOjo;
    private View btnVerNumero, filaCopiar;
    private Button btnCopiarNumero, btnCopiarCvv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Número y CVV: sin capturas de pantalla ni vista previa en "recientes"
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_tarjeta_virtual);

        TarjetaViewModel vm = new ViewModelProvider(this).get(TarjetaViewModel.class);
        if (vm.revelar == null) {
            Context app = getApplicationContext();
            vm.revelar = new RevelarTarjeta(() -> RetrofitClient.getService(app), SystemClock::elapsedRealtime);
        }
        revelar = vm.revelar;
        revelar.setObservador(r -> {
            if (r.isSesionInvalida()) {
                SesionUtils.sesionInvalida(this);
                return;
            }
            render();
            if (r.segundosParaReintentar() > 0) {
                handler.removeCallbacks(tick);
                handler.postDelayed(tick, 1000);
            }
        });

        tvNumero = findViewById(R.id.tvNumeroTarjeta);
        tvTitular = findViewById(R.id.tvTitularTarjeta);
        tvVencimiento = findViewById(R.id.tvVencimiento);
        tvCvv = findViewById(R.id.tvCvv);
        tvVerNumero = findViewById(R.id.tvVerNumero);
        tvError = findViewById(R.id.tvErrorTarjeta);
        ivOjo = findViewById(R.id.ivOjoTarjeta);
        btnVerNumero = findViewById(R.id.btnVerNumero);
        filaCopiar = findViewById(R.id.filaCopiar);
        btnCopiarNumero = findViewById(R.id.btnCopiarNumero);
        btnCopiarCvv = findViewById(R.id.btnCopiarCvv);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnVerNumero.setOnClickListener(v -> {
            if (revelar.isVisible()) revelar.ocultar();
            else revelar.revelar();
        });
        btnCopiarNumero.setOnClickListener(v -> {
            TarjetaResponse t = revelar.getVisible();
            if (t != null) copiar("Número de tarjeta", FormatoTarjeta.paraCopiar(t.getNumero()), btnCopiarNumero, "Copiar número");
        });
        btnCopiarCvv.setOnClickListener(v -> {
            TarjetaResponse t = revelar.getVisible();
            if (t != null) copiar("Código de seguridad", t.getCvv(), btnCopiarCvv, "Copiar CVV");
        });
        render();
    }

    @Override
    protected void onStart() {
        super.onStart();
        SaldosRepository saldos = SaldosRepository.get(this);
        saldos.observar(observadorSaldos);
        saldos.refrescar(); // /api/perfil (sin límite): últimos 4 y vencimiento
        if (revelar.segundosParaReintentar() > 0) handler.post(tick);
    }

    @Override
    protected void onStop() {
        super.onStop();
        SaldosRepository.get(this).dejarDeObservar(observadorSaldos);
        handler.removeCallbacksAndMessages(null);
        // Al salir de la pantalla (no al rotar) se olvidan número y CVV: al volver hay que pedirlos otra vez
        if (!isChangingConfigurations()) revelar.olvidar();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        revelar.setObservador(null);
    }

    /**
     * Al portapapeles marcado como SENSIBLE (Android 13+ no muestra el contenido en la vista previa;
     * versiones anteriores ignoran el extra). No se limpia solo: la app no lo hace en ningún otro lado.
     */
    private void copiar(String etiqueta, String valor, Button boton, String textoBoton) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || valor == null || valor.isEmpty()) return;
        ClipData clip = ClipData.newPlainText(etiqueta, valor);
        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean("android.content.extra.IS_SENSITIVE", true); // = ClipDescription.EXTRA_IS_SENSITIVE (API 33)
        clip.getDescription().setExtras(extras);
        cm.setPrimaryClip(clip);
        boton.setText("Copiado ✓");
        handler.postDelayed(() -> boton.setText(textoBoton), FEEDBACK_COPIADO_MS);
        // Android 13+ ya muestra su propio aviso de "copiado"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(this, etiqueta + " copiado", Toast.LENGTH_SHORT).show();
        }
    }

    private void render() {
        if (isFinishing() || tvNumero == null) return;
        PerfilResponse perfil = estadoSaldos != null ? estadoSaldos.perfil : null;
        TarjetaResponse t = revelar.getVisible();
        RevelarTarjeta.Estado estado = revelar.getEstado();

        if (t != null) {
            tvNumero.setText(FormatoTarjeta.numeroEnGrupos(t.getNumero()));
            tvTitular.setText(t.getTitular()); // el de la TARJETA (el backend lo fija al crearla)
            tvVencimiento.setText(FormatoTarjeta.vencimientoMmAa(t.getVencimiento()));
            tvCvv.setText(t.getCvv());
        } else {
            tvNumero.setText(FormatoTarjeta.enmascarado(perfil != null ? perfil.getTarjetaUltimosCuatro() : null));
            tvTitular.setText(titularPerfil(perfil));
            tvVencimiento.setText(FormatoTarjeta.vencimientoMmAa(perfil != null ? perfil.getTarjetaVencimiento() : null));
            tvCvv.setText(FormatoTarjeta.CVV_OCULTO);
            btnCopiarNumero.setText("Copiar número");
            btnCopiarCvv.setText("Copiar CVV");
        }

        long espera = revelar.segundosParaReintentar();
        boolean cargando = estado == RevelarTarjeta.Estado.CARGANDO;
        tvVerNumero.setText(cargando ? "Cargando..."
                : t != null ? "Ocultar"
                : espera > 0 ? "Reintentá en " + espera + " s"
                : estado == RevelarTarjeta.Estado.ERROR ? "Reintentar"
                : "Ver tarjeta completa");
        ivOjo.setImageResource(t != null ? R.drawable.ic_eye_off : R.drawable.ic_eye);
        boolean habilitado = !cargando && (t != null || espera == 0);
        btnVerNumero.setEnabled(habilitado);
        btnVerNumero.setAlpha(habilitado ? 1f : 0.5f);

        String error = estado == RevelarTarjeta.Estado.ERROR ? revelar.getError() : null;
        tvError.setVisibility(error != null ? View.VISIBLE : View.GONE);
        tvError.setText(error);
        filaCopiar.setVisibility(t != null ? View.VISIBLE : View.GONE);
    }

    /** Tapada: el nombre del perfil (o el de la sesión si el perfil todavía no llegó), en mayúsculas. */
    private String titularPerfil(PerfilResponse perfil) {
        String nombre = perfil != null ? perfil.getNombreCompleto() : null;
        if (nombre == null || nombre.isEmpty()) nombre = new SessionManager(this).getNombreCompleto();
        return nombre != null ? nombre.toUpperCase(new Locale("es", "AR")) : "";
    }
}
