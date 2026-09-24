package com.example.payxmobile;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.example.payxmobile.utils.Validadores;

import org.junit.Test;

/** A7, B1, B3, B4, F5, F7, H4: mismas reglas que los @Valid del backend. */
public class ValidadoresTest {

    @Test
    public void email() {
        assertNull(Validadores.email("ana@mail.com"));
        assertNull(Validadores.email("  Ana@Mail.COM ")); // A2: se valida recortado
        assertNotNull(Validadores.email(""));
        assertNotNull(Validadores.email("   "));
        assertNotNull(Validadores.email(null));
        assertNotNull(Validadores.email("x"));
        assertNotNull(Validadores.email("ana@mail"));
        assertNotNull(Validadores.email("ana mail@x.com"));
    }

    @Test
    public void passwordLogin() {
        assertNull(Validadores.passwordObligatoria("a"));
        assertNull(Validadores.passwordObligatoria("   ")); // no se recorta: el backend la compara tal cual
        assertNotNull(Validadores.passwordObligatoria(""));
        assertNotNull(Validadores.passwordObligatoria(null));
    }

    @Test
    public void passwordNuevaMinimo8() {
        assertNull(Validadores.passwordNueva("12345678"));
        assertNotNull(Validadores.passwordNueva("1234567"));
        assertNotNull(Validadores.passwordNueva(""));
    }

    @Test
    public void confirmacion() {
        assertNull(Validadores.confirmacion("12345678", "12345678"));
        assertNotNull(Validadores.confirmacion("12345678", "12345679"));
        assertNotNull(Validadores.confirmacion("12345678", ""));
    }

    @Test
    public void nombreCompleto3a100() {
        assertNull(Validadores.nombreCompleto("Ana"));
        assertNotNull(Validadores.nombreCompleto("An"));
        assertNotNull(Validadores.nombreCompleto(repetir('a', 101)));
        assertNull(Validadores.nombreCompleto(repetir('a', 100)));
    }

    @Test
    public void nombreUsuario3a20() {
        assertNull(Validadores.nombreUsuario("ana"));
        assertNull(Validadores.nombreUsuario(repetir('a', 20)));
        assertNotNull(Validadores.nombreUsuario("an"));
        assertNotNull(Validadores.nombreUsuario(repetir('a', 21)));
        assertNotNull(Validadores.nombreUsuario(""));
    }

    @Test
    public void telefonoRegexDelBackend() {
        assertNull(Validadores.telefono("11 5555-1234"));
        assertNull(Validadores.telefono("+54 (11) 5555-1234"));
        assertNull(Validadores.telefono("12345678"));
        assertNotNull(Validadores.telefono("1234567"));
        assertNotNull(Validadores.telefono(repetir('1', 21)));
        assertNotNull(Validadores.telefono("11-5555-abcd"));
        assertNotNull(Validadores.telefono(""));
        assertNotNull(Validadores.telefono(null));
    }

    @Test
    public void dni7a10Digitos() {
        assertNull(Validadores.dni("1234567"));
        assertNull(Validadores.dni("1234567890"));
        assertNotNull(Validadores.dni("123456"));
        assertNotNull(Validadores.dni("12345678901"));
        assertNotNull(Validadores.dni("12.345.678"));
        assertNotNull(Validadores.dni(""));
    }

    @Test
    public void aliasRegexDelBackend() {
        assertNull(Validadores.alias("ana.payx"));
        assertNull(Validadores.alias("a_b-c.d"));
        assertNotNull(Validadores.alias("abcde"));
        assertNotNull(Validadores.alias(repetir('a', 31)));
        assertNotNull(Validadores.alias("ana payx"));
        assertNotNull(Validadores.alias("aña.payx"));
        assertNotNull(Validadores.alias(""));
    }

    @Test
    public void codigoDe6Digitos() {
        assertNull(Validadores.codigo("012345"));
        assertNotNull(Validadores.codigo("12345"));
        assertNotNull(Validadores.codigo("1234567"));
        assertNotNull(Validadores.codigo("12a456"));
        assertNotNull(Validadores.codigo(null));
    }

    private static String repetir(char c, int n) {
        return new String(new char[n]).replace('\0', c);
    }
}
