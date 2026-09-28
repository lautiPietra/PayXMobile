package com.example.payxmobile.estadisticas.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Gráfico de dona dibujado a mano (sin librería, como EstadisticaDonut.jsx): cada porción es un arco
 * proporcional a monto/total. Se usa la proporción real y no el porcentaje redondeado del backend
 * para que las porciones cierren exacto (los porcentajes redondeados pueden no sumar 100).
 */
public class DonutView extends View {

    public static final class Porcion {
        final int color;
        final float fraccion;

        public Porcion(int color, float fraccion) {
            this.color = color;
            this.fraccion = fraccion;
        }
    }

    private final Paint pista = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arco = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final List<Porcion> porciones = new ArrayList<>();
    private final float grosor;

    public DonutView(Context context, AttributeSet attrs) {
        super(context, attrs);
        grosor = 22 * getResources().getDisplayMetrics().density;
        pista.setStyle(Paint.Style.STROKE);
        pista.setStrokeWidth(grosor);
        pista.setColor(0xFF2A2A2A);
        arco.setStyle(Paint.Style.STROKE);
        arco.setStrokeWidth(grosor);
        arco.setStrokeCap(Paint.Cap.BUTT);
    }

    /** Porciones con fracción > 0 (las de gasto 0 no se dibujan). Vacío = solo la pista. */
    public void setPorciones(List<Porcion> nuevas) {
        porciones.clear();
        if (nuevas != null) porciones.addAll(nuevas);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float lado = Math.min(getWidth(), getHeight());
        float m = grosor / 2f;
        float x = (getWidth() - lado) / 2f, y = (getHeight() - lado) / 2f;
        rect.set(x + m, y + m, x + lado - m, y + lado - m);
        canvas.drawArc(rect, 0, 360, false, pista);
        float inicio = -90f;
        for (Porcion p : porciones) {
            float barrido = 360f * p.fraccion;
            if (barrido <= 0f) continue;
            arco.setColor(p.color);
            canvas.drawArc(rect, inicio, barrido, false, arco);
            inicio += barrido;
        }
    }
}
