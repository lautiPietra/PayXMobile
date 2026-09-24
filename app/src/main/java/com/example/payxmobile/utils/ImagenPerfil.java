package com.example.payxmobile.utils;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/**
 * Prepara la foto elegida para POST /api/perfil/foto: la decodifica respetando la rotación EXIF,
 * la reduce a 1280 px de lado como máximo y la recomprime a JPEG. Así siempre cumple el tipo
 * (jpeg/png/webp) y el peso (< 5 MB) que exige el backend, aunque el original sea HEIC o pese 20 MB.
 * El servidor igual la recorta a 400x400.
 */
public final class ImagenPerfil {

    public static final int LADO_MAXIMO = 1280;
    public static final int CALIDAD_JPEG = 85;
    public static final long PESO_MAXIMO = 5L * 1024 * 1024;
    public static final String NOMBRE_PARTE = "archivo";
    public static final MediaType TIPO_JPEG = MediaType.get("image/jpeg");

    private ImagenPerfil() {}

    /** Error de negocio: la imagen no se pudo leer/decodificar. */
    public static class ImagenInvalidaException extends Exception {
        public ImagenInvalidaException(String mensaje) { super(mensaje); }
    }

    /** Hacerlo SIEMPRE fuera del hilo principal. */
    public static byte[] prepararJpeg(ContentResolver resolver, Uri uri) throws ImagenInvalidaException {
        try {
            BitmapFactory.Options limites = new BitmapFactory.Options();
            limites.inJustDecodeBounds = true;
            try (InputStream in = resolver.openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, limites);
            }
            if (limites.outWidth <= 0 || limites.outHeight <= 0) {
                throw new ImagenInvalidaException("No pudimos leer esa imagen. Probá con otra (JPG, PNG o WEBP)");
            }

            BitmapFactory.Options opciones = new BitmapFactory.Options();
            opciones.inSampleSize = calcularInSampleSize(limites.outWidth, limites.outHeight, LADO_MAXIMO);
            Bitmap bitmap;
            try (InputStream in = resolver.openInputStream(uri)) {
                bitmap = BitmapFactory.decodeStream(in, null, opciones);
            }
            if (bitmap == null) {
                throw new ImagenInvalidaException("No pudimos leer esa imagen. Probá con otra (JPG, PNG o WEBP)");
            }

            int grados;
            try (InputStream in = resolver.openInputStream(uri)) {
                grados = in != null ? new ExifInterface(in).getRotationDegrees() : 0;
            } catch (IOException | RuntimeException e) {
                grados = 0; // sin EXIF (PNG/WEBP): se deja como está
            }

            int[] destino = dimensionesDestino(bitmap.getWidth(), bitmap.getHeight(), LADO_MAXIMO);
            Matrix matrix = new Matrix();
            matrix.postScale(destino[0] / (float) bitmap.getWidth(), destino[1] / (float) bitmap.getHeight());
            if (grados != 0) matrix.postRotate(grados);
            Bitmap escalado = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (escalado != bitmap) bitmap.recycle();

            int calidad = CALIDAD_JPEG;
            byte[] jpeg;
            do {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                escalado.compress(Bitmap.CompressFormat.JPEG, calidad, out);
                jpeg = out.toByteArray();
                calidad -= 15;
            } while (jpeg.length >= PESO_MAXIMO && calidad > 20);
            escalado.recycle();
            return jpeg;
        } catch (IOException | SecurityException e) {
            throw new ImagenInvalidaException("No pudimos abrir esa imagen. Probá con otra");
        } catch (OutOfMemoryError e) {
            throw new ImagenInvalidaException("La imagen es demasiado grande para procesarla. Probá con otra");
        }
    }

    public static MultipartBody.Part crearParte(byte[] jpeg) {
        return MultipartBody.Part.createFormData(NOMBRE_PARTE, "perfil.jpg", RequestBody.create(jpeg, TIPO_JPEG));
    }

    /** Mayor potencia de 2 que deja el lado mayor en >= ladoMaximo (el ajuste fino lo hace la Matrix). */
    public static int calcularInSampleSize(int ancho, int alto, int ladoMaximo) {
        int lado = Math.max(ancho, alto);
        int sample = 1;
        while (lado / (sample * 2) >= ladoMaximo) sample *= 2;
        return sample;
    }

    /** Escala proporcional para que el lado mayor sea como mucho ladoMaximo (nunca agranda). */
    public static int[] dimensionesDestino(int ancho, int alto, int ladoMaximo) {
        int lado = Math.max(ancho, alto);
        if (lado <= ladoMaximo) return new int[]{ancho, alto};
        double factor = ladoMaximo / (double) lado;
        return new int[]{
                Math.max(1, (int) Math.round(ancho * factor)),
                Math.max(1, (int) Math.round(alto * factor))
        };
    }
}
