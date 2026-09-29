package com.example.payxmobile.tarjeta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.example.payxmobile.model.LoginRequest;
import com.example.payxmobile.model.LoginResponse;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.model.TarjetaResponse;
import com.example.payxmobile.network.ApiService;
import com.example.payxmobile.network.RetrofitClient;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Locale;
import java.util.function.BooleanSupplier;

import retrofit2.Response;

/**
 * V8 contra el backend LOCAL real, con la MISMA clase que usa la pantalla. Solo corre con PAYX_E2E=1
 * (cuenta A: PAYX_E2E_EMAIL_A y PAYX_E2E_PASSWORD; opcional PAYX_E2E_URL). 2 GET /api/tarjeta (límite 15/min).
 * NO imprime número, CVV, titular ni token: solo "•••• 1234" y si cada dato coincide.
 */
public class E2ETarjetaRealTest {

    private static final String URL = env("PAYX_E2E_URL", "http://localhost:8080/");

    private static ApiService api;

    @BeforeClass
    public static void login() throws Exception {
        assumeTrue("Definí PAYX_E2E=1 para correr contra el backend real", System.getenv("PAYX_E2E") != null);
        String email = requerida("PAYX_E2E_EMAIL_A");
        String password = requerida("PAYX_E2E_PASSWORD");
        ApiService sinToken = RetrofitClient.crear(URL, () -> null, () -> {}, false);
        Response<LoginResponse> r = sinToken.login(new LoginRequest(email, password)).execute();
        assertTrue("login -> " + r.code(), r.isSuccessful());
        String jwt = r.body().getToken();
        api = RetrofitClient.crear(URL, () -> jwt, () -> {}, false, true);
    }

    private static String env(String nombre, String porDefecto) {
        String v = System.getenv(nombre);
        return v != null && !v.isEmpty() ? v : porDefecto;
    }

    private static String requerida(String nombre) {
        String v = System.getenv(nombre);
        assumeTrue("Falta la variable de entorno " + nombre, v != null && !v.isEmpty());
        return v;
    }

    private static void esperar(String que, BooleanSupplier c) throws InterruptedException {
        long fin = System.currentTimeMillis() + 15000;
        while (!c.getAsBoolean()) {
            if (System.currentTimeMillis() > fin) throw new AssertionError("Timeout: " + que);
            Thread.sleep(20);
        }
    }

    private static boolean luhnValido(String numero) {
        int suma = 0;
        boolean duplicar = false;
        for (int i = numero.length() - 1; i >= 0; i--) {
            int d = numero.charAt(i) - '0';
            if (duplicar) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            suma += d;
            duplicar = !duplicar;
        }
        return suma % 10 == 0;
    }

    @Test
    public void v8_coincideConElPerfilYEsEstable() throws Exception {
        PerfilResponse perfil = api.obtenerPerfil().execute().body();
        assertNotNull(perfil);

        RevelarTarjeta r = new RevelarTarjeta(() -> api, System::currentTimeMillis);
        r.revelar();
        esperar("GET /api/tarjeta", () -> r.getEstado() != RevelarTarjeta.Estado.CARGANDO);
        assertEquals("error: " + r.getError(), RevelarTarjeta.Estado.VISIBLE, r.getEstado());
        TarjetaResponse t = r.getVisible();

        String numero = t.getNumero();
        assertEquals("últimos 4 == los de GET /api/perfil", perfil.getTarjetaUltimosCuatro(), numero.substring(12));
        assertEquals("vencimiento == el de GET /api/perfil", perfil.getTarjetaVencimiento(), t.getVencimiento());
        assertTrue("16 dígitos", numero.matches("\\d{16}"));
        assertTrue("prefijo propio del backend", numero.startsWith("9004"));
        assertTrue("dígito verificador válido", luhnValido(numero));
        assertTrue("CVV de 3 dígitos", t.getCvv().matches("\\d{3}"));
        assertEquals(19, FormatoTarjeta.numeroEnGrupos(numero).length());

        boolean titularIgualPerfil = t.getTitular().equals(perfil.getNombreCompleto().toUpperCase(Locale.ROOT));
        System.out.println("Tarjeta " + FormatoTarjeta.enmascarado(perfil.getTarjetaUltimosCuatro())
                + " | vence " + FormatoTarjeta.vencimientoMmAa(t.getVencimiento())
                + " | titular == nombre del perfil en mayúsculas: " + titularIgualPerfil);

        // Se crea UNA vez: un segundo pedido (salir y volver a entrar) trae exactamente lo mismo
        r.olvidar();
        r.revelar();
        esperar("segundo GET", () -> r.getEstado() != RevelarTarjeta.Estado.CARGANDO);
        TarjetaResponse otra = r.getVisible();
        assertEquals(numero, otra.getNumero());
        assertEquals(t.getCvv(), otra.getCvv());
        assertEquals(t.getTitular(), otra.getTitular());
        assertEquals(t.getVencimiento(), otra.getVencimiento());
    }
}
