package com.example.payxmobile.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.payxmobile.R;

public class NuevaCajaAhorroActivity extends AppCompatActivity {

    private final int[] idsColores = {
            R.id.colorSwatch0, R.id.colorSwatch1, R.id.colorSwatch2, R.id.colorSwatch3,
            R.id.colorSwatch4, R.id.colorSwatch5, R.id.colorSwatch6
    };

    private final int[] coloresCaja = {
            R.color.caja_color_1, R.color.caja_color_2, R.color.caja_color_3, R.color.caja_color_4,
            R.color.caja_color_5, R.color.caja_color_6, R.color.caja_color_7
    };

    private final int[] idsIconos = {
            R.id.iconoOpcion0, R.id.iconoOpcion1, R.id.iconoOpcion2, R.id.iconoOpcion3,
            R.id.iconoOpcion4, R.id.iconoOpcion5, R.id.iconoOpcion6, R.id.iconoOpcion7,
            R.id.iconoOpcion8, R.id.iconoOpcion9, R.id.iconoOpcion10, R.id.iconoOpcion11
    };

    private final int[] dibujosIcono = {
            R.drawable.ic_piggy_bank, R.drawable.ic_target, R.drawable.ic_shield, R.drawable.ic_plane,
            R.drawable.ic_briefcase, R.drawable.ic_book, R.drawable.ic_heart, R.drawable.ic_gift,
            R.drawable.ic_bag, R.drawable.ic_home, R.drawable.ic_wallet, R.drawable.ic_trending_up
    };

    private FrameLayout[] swatches;
    private FrameLayout[] opcionesIcono;
    private int indiceColor = 0;
    private int indiceIcono = 0;

    private View previewCaja;
    private ImageView ivPreviewIcono;
    private TextView tvPreviewNombre;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nueva_caja_ahorro);

        previewCaja = findViewById(R.id.previewCaja);
        ivPreviewIcono = findViewById(R.id.ivPreviewIcono);
        tvPreviewNombre = findViewById(R.id.tvPreviewNombre);

        swatches = new FrameLayout[idsColores.length];
        for (int i = 0; i < idsColores.length; i++) {
            FrameLayout swatch = findViewById(idsColores[i]);
            swatches[i] = swatch;
            int indice = i;
            swatch.setOnClickListener(v -> seleccionarColor(indice));
        }

        opcionesIcono = new FrameLayout[idsIconos.length];
        for (int i = 0; i < idsIconos.length; i++) {
            FrameLayout opcion = findViewById(idsIconos[i]);
            opcionesIcono[i] = opcion;
            int indice = i;
            opcion.setOnClickListener(v -> seleccionarIcono(indice));
        }

        EditText etNombre = findViewById(R.id.etNombreCaja);
        etNombre.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                tvPreviewNombre.setText(s.toString().trim().isEmpty() ? "Nombre de la caja" : s.toString());
            }
        });

        seleccionarColor(0);
        seleccionarIcono(0);

        findViewById(R.id.btnVolver).setOnClickListener(v -> finish());
        findViewById(R.id.btnCerrar).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancelar).setOnClickListener(v -> finish());
        findViewById(R.id.btnCrearCaja).setOnClickListener(v ->
                Toast.makeText(this, "Esta función va a estar disponible próximamente.", Toast.LENGTH_SHORT).show());
    }

    private void seleccionarColor(int indice) {
        if (swatches[indiceColor].getChildCount() > 1) {
            swatches[indiceColor].removeViews(1, swatches[indiceColor].getChildCount() - 1);
        }
        indiceColor = indice;

        View anillo = new View(this);
        anillo.setBackgroundResource(R.drawable.bg_ring_selector);
        int px = (int) (38 * getResources().getDisplayMetrics().density);
        anillo.setLayoutParams(new FrameLayout.LayoutParams(px, px, android.view.Gravity.CENTER));
        swatches[indice].addView(anillo);

        ImageView check = new ImageView(this);
        check.setImageResource(R.drawable.ic_check);
        int pxCheck = (int) (16 * getResources().getDisplayMetrics().density);
        check.setLayoutParams(new FrameLayout.LayoutParams(pxCheck, pxCheck, android.view.Gravity.CENTER));
        swatches[indice].addView(check);

        previewCaja.setBackgroundResource(R.drawable.bg_preview_caja);
        previewCaja.setBackgroundTintList(getColorStateList(coloresCaja[indice]));
    }

    private void seleccionarIcono(int indice) {
        opcionesIcono[indiceIcono].setSelected(false);
        tintarIcono(opcionesIcono[indiceIcono], R.color.text_secondary);

        indiceIcono = indice;
        opcionesIcono[indice].setSelected(true);
        tintarIcono(opcionesIcono[indice], R.color.payx_orange);

        ivPreviewIcono.setImageResource(dibujosIcono[indice]);
    }

    private void tintarIcono(FrameLayout contenedor, int colorRes) {
        ((ImageView) contenedor.getChildAt(0)).setImageTintList(getColorStateList(colorRes));
    }
}
