package com.example.payxmobile.network;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Gson escribe BigDecimal con toString(), que usa notación científica (0.00000001 -> "1E-8").
 * Este adaptador escribe siempre el número plano ("0.00000001") y lee desde el texto del JSON,
 * sin pasar por double.
 */
public class BigDecimalPlano extends TypeAdapter<BigDecimal> {

    @Override
    public void write(JsonWriter out, BigDecimal valor) throws IOException {
        if (valor == null) {
            out.nullValue();
            return;
        }
        BigDecimal limpio = valor.stripTrailingZeros();
        if (limpio.scale() < 0) limpio = limpio.setScale(0);
        out.jsonValue(limpio.toPlainString());
    }

    @Override
    public BigDecimal read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        return new BigDecimal(in.nextString());
    }
}
