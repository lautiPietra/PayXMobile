package com.example.payxmobile.model;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Body de POST /api/cajas-ahorro (crear) y PUT /api/cajas-ahorro/{id} (editar): nombre, color e
 * icono (de las whitelists) y meta opcional.
 *
 * montoObjetivo SIEMPRE viaja, aunque sea null: al editar, null = "sin meta" explícito (reemplaza la
 * anterior por ninguna). El Gson de la app omite los null, así que este body tiene su propio adapter.
 */
@JsonAdapter(CajaAhorroRequest.Adapter.class)
public class CajaAhorroRequest {
    private final String nombre;
    private final String color;
    private final String icono;
    private final BigDecimal montoObjetivo;

    public CajaAhorroRequest(String nombre, String color, String icono, BigDecimal montoObjetivo) {
        this.nombre = nombre;
        this.color = color;
        this.icono = icono;
        this.montoObjetivo = montoObjetivo;
    }

    public String getNombre() { return nombre; }
    public String getColor() { return color; }
    public String getIcono() { return icono; }
    public BigDecimal getMontoObjetivo() { return montoObjetivo; }

    static final class Adapter extends TypeAdapter<CajaAhorroRequest> {
        @Override
        public void write(JsonWriter out, CajaAhorroRequest r) throws IOException {
            out.beginObject();
            out.name("nombre").value(r.nombre);
            out.name("color").value(r.color);
            out.name("icono").value(r.icono);
            boolean antes = out.getSerializeNulls();
            out.setSerializeNulls(true);
            out.name("montoObjetivo");
            if (r.montoObjetivo == null) out.nullValue();
            else out.jsonValue(r.montoObjetivo.toPlainString()); // sin notación científica
            out.setSerializeNulls(antes);
            out.endObject();
        }

        @Override
        public CajaAhorroRequest read(JsonReader in) {
            throw new UnsupportedOperationException("Solo se envía");
        }
    }
}
