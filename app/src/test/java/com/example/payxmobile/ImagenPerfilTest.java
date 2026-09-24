package com.example.payxmobile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.example.payxmobile.utils.ImagenPerfil;

import org.junit.Test;

/** G1 / G4: cálculo del redimensionado a 1280 px de lado como máximo. */
public class ImagenPerfilTest {

    @Test
    public void noAgrandaImagenesChicas() {
        assertArrayEquals(new int[]{800, 600}, ImagenPerfil.dimensionesDestino(800, 600, 1280));
        assertArrayEquals(new int[]{1280, 720}, ImagenPerfil.dimensionesDestino(1280, 720, 1280));
    }

    @Test
    public void reduceFotoDeCamaraManteniendoProporcion() {
        // 12 MP típica de celular (4000x3000) -> 1280x960
        assertArrayEquals(new int[]{1280, 960}, ImagenPerfil.dimensionesDestino(4000, 3000, 1280));
        // Vertical
        assertArrayEquals(new int[]{960, 1280}, ImagenPerfil.dimensionesDestino(3000, 4000, 1280));
        // Panorámica extrema: nunca 0 px
        assertArrayEquals(new int[]{1280, 1}, ImagenPerfil.dimensionesDestino(20000, 10, 1280));
    }

    @Test
    public void inSampleSizeNoBajaDelLadoMaximo() {
        assertEquals(1, ImagenPerfil.calcularInSampleSize(1000, 800, 1280));
        assertEquals(1, ImagenPerfil.calcularInSampleSize(2000, 1500, 1280));
        assertEquals(2, ImagenPerfil.calcularInSampleSize(4000, 3000, 1280)); // 2000 >= 1280
        assertEquals(4, ImagenPerfil.calcularInSampleSize(6000, 8000, 1280)); // 2000 >= 1280, 1000 no
        for (int lado : new int[]{1281, 2560, 4000, 9000, 12000}) {
            int s = ImagenPerfil.calcularInSampleSize(lado, lado / 2, 1280);
            assertEquals(true, lado / s >= 1280);
        }
    }

    @Test
    public void constantesDelContrato() {
        assertEquals("archivo", ImagenPerfil.NOMBRE_PARTE);
        assertEquals("image/jpeg", ImagenPerfil.TIPO_JPEG.toString());
        assertEquals(5L * 1024 * 1024, ImagenPerfil.PESO_MAXIMO);
    }
}
