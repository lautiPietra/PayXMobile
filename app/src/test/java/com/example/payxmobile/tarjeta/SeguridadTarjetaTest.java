package com.example.payxmobile.tarjeta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Chequeos sobre el CÓDIGO (no en ejecución): V2 quién puede pedir /api/tarjeta y V7 que nada
 * imprima número, CVV ni token. Corre con el directorio del módulo (app/) como directorio de trabajo.
 */
public class SeguridadTarjetaTest {

    private static List<Path> fuentes(String raiz) throws IOException {
        try (Stream<Path> s = Files.walk(Paths.get(raiz))) {
            return s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
    }

    private static String leer(Path p) throws IOException {
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    @Test
    public void v2_soloRevelarTarjetaPideElNumeroCompleto() throws IOException {
        List<String> usan = new ArrayList<>();
        for (Path p : fuentes("src/main/java")) {
            if (leer(p).contains("obtenerTarjeta(")) usan.add(p.getFileName().toString());
        }
        usan.sort(null);
        // ApiService lo declara; RevelarTarjeta es el único que lo llama (y solo en revelar())
        assertEquals(java.util.Arrays.asList("ApiService.java", "RevelarTarjeta.java"), usan);
    }

    @Test
    public void v7_laAppNoLogueaNada() throws IOException {
        Pattern log = Pattern.compile("\\bLog\\.[vdiwe]\\(|System\\.(out|err)\\.|printStackTrace\\(");
        List<String> encontrados = new ArrayList<>();
        for (Path p : fuentes("src/main/java")) {
            String[] lineas = leer(p).split("\n");
            for (int i = 0; i < lineas.length; i++) {
                if (log.matcher(lineas[i]).find()) encontrados.add(p.getFileName() + ":" + (i + 1));
            }
        }
        assertTrue("logs en el código de la app: " + encontrados, encontrados.isEmpty());
        // El logging HTTP (solo en debug) es BASIC: sin headers ni bodies, y con Authorization redactado
        String retrofit = leer(Paths.get("src/main/java/com/example/payxmobile/network/RetrofitClient.java"));
        assertTrue(retrofit.contains("Level.BASIC"));
        assertTrue(retrofit.contains("redactHeader(\"Authorization\")"));
    }

    @Test
    public void v7_losTestsNoImprimenNumeroCvvNiToken() throws IOException {
        Pattern sensible = Pattern.compile("println\\(.*(getNumero\\(\\)|getCvv\\(\\)|getToken\\(\\)|token\\b)");
        List<String> encontrados = new ArrayList<>();
        for (Path p : fuentes("src/test/java")) {
            String[] lineas = leer(p).split("\n");
            for (int i = 0; i < lineas.length; i++) {
                if (sensible.matcher(lineas[i]).find()) encontrados.add(p.getFileName() + ":" + (i + 1));
            }
        }
        assertTrue("prints con datos sensibles: " + encontrados, encontrados.isEmpty());
    }
}
