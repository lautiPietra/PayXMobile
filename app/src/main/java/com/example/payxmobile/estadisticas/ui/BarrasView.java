package com.example.payxmobile.estadisticas.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Barras "día a día" (como EstadisticaBarras.jsx): una barra por punto de la serie, escaladas al día
 * de mayor gasto, que se destaca. Un día sin gasto deja una marca mínima para que se vea el hueco.
 */
public class BarrasView extends View {

    private final Paint barra = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vacia = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final List<Float> valores = new ArrayList<>();
    private final float radio;
    private int colorBarra = 0xFFE85500;
    private int colorPico = 0xFFFF8A3D;

    public BarrasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        radio = 3 * getResources().getDisplayMetrics().density;
        vacia.setColor(0xFF2A2A2A);
    }

    public void setColores(int barra, int pico) {
        colorBarra = barra;
        colorPico = pico;
        invalidate();
    }

    public void setMontos(List<BigDecimal> montos) {
        valores.clear();
        if (montos != null) for (BigDecimal m : montos) valores.add(m != null ? m.floatValue() : 0f);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = valores.size();
        if (n == 0) return;
        float max = 0f;
        int iPico = -1;
        for (int i = 0; i < n; i++) {
            if (valores.get(i) > max) {
                max = valores.get(i);
                iPico = i;
            }
        }
        float ancho = getWidth() / (float) n;
        float hueco = Math.min(ancho * 0.3f, 6 * getResources().getDisplayMetrics().density);
        float alto = getHeight();
        float minimo = 2 * getResources().getDisplayMetrics().density;
        for (int i = 0; i < n; i++) {
            float v = valores.get(i);
            float h = max > 0 && v > 0 ? Math.max(minimo, alto * (v / max)) : minimo;
            rect.set(i * ancho + hueco / 2f, alto - h, (i + 1) * ancho - hueco / 2f, alto);
            if (v > 0) {
                barra.setColor(i == iPico ? colorPico : colorBarra);
                canvas.drawRoundRect(rect, radio, radio, barra);
            } else {
                canvas.drawRoundRect(rect, radio, radio, vacia);
            }
        }
    }
}
