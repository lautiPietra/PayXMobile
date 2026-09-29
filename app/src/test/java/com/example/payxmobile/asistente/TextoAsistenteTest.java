package com.example.payxmobile.asistente;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TextoAsistenteTest {

    @Test
    public void sacaLosAsteriscosYMarcaLasNegritas() {
        TextoAsistente t = TextoAsistente.de("- **Pesos:** $ 10 y **USD:** US$ 2");
        assertEquals("- Pesos: $ 10 y USD: US$ 2", t.texto);
        assertEquals(2, t.negritas.size());
        assertArrayEquals(new int[]{2, 8}, t.negritas.get(0));
        assertEquals("USD:", t.texto.substring(t.negritas.get(1)[0], t.negritas.get(1)[1]));
    }

    @Test
    public void sinParejaOVacio_quedaTalCual() {
        assertEquals("5 ** 2", TextoAsistente.de("5 ** 2").texto);
        assertTrue(TextoAsistente.de("5 ** 2").negritas.isEmpty());
        assertEquals("a  b", TextoAsistente.de("a **** b").texto);
        assertTrue(TextoAsistente.de("a **** b").negritas.isEmpty());
        assertEquals("", TextoAsistente.de(null).texto);
        assertEquals("sin formato", TextoAsistente.de("sin formato").texto);
    }
}
