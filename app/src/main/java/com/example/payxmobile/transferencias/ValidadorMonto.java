package com.example.payxmobile.transferencias;

import java.math.BigDecimal;

/**
 * Reglas de un monto antes de mandarlo, iguales a las del backend: pesos y dólares con hasta 2 decimales
 * (sus saldos se guardan con 2: un monto con más creaba plata de la nada y ahora se rechaza con 400) y
 * cripto con hasta 8. Los ceros de más no cuentan ("10.500" son 10,50) y solo vale un número plano: sin
 * signo, espacios intermedios ni notación científica ("1e-7"). Clase pura.
 *
 * Cuántos decimales van en cada operación: 2 en transferencias en PESOS/USD, compra y venta de dólares,
 * plazo fijo, cajas de ahorro (depósito, retiro y meta) y la COMPRA de cripto (el monto son pesos); 8 en
 * transferencias de cripto y la VENTA de cripto (el monto es cripto). Ver {@link Moneda#decimales}.
 */
public final class ValidadorMonto {

    public static final String MSG_MONTO_INVALIDO = "Ingresá un monto válido.";

    private ValidadorMonto() {}

    public static String msgDecimales(int maxDecimales) {
        return "El monto puede tener hasta " + maxDecimales + " decimales.";
    }

    /** null si el texto es un monto válido con hasta maxDecimales decimales; si no, el mensaje para el formulario. */
    public static String validar(String texto, int maxDecimales) {
        return validar(MontoInput.parsear(texto), maxDecimales);
    }

    /** null si el monto es mayor a cero, con hasta maxDecimales decimales significativos y hasta 13 enteros. */
    public static String validar(BigDecimal monto, int maxDecimales) {
        if (monto == null || monto.signum() <= 0) return MSG_MONTO_INVALIDO;
        if (!decimalesPermitidos(monto, maxDecimales)) return msgDecimales(maxDecimales);
        BigDecimal limpio = monto.stripTrailingZeros();
        if (limpio.precision() - limpio.scale() > Moneda.MAX_ENTEROS) return MSG_MONTO_INVALIDO;
        return null;
    }

    /** Decimales que importan, sin los ceros de más: 10.500 tiene 1; 0.005, 3. */
    public static boolean decimalesPermitidos(BigDecimal monto, int maxDecimales) {
        return monto.stripTrailingZeros().scale() <= maxDecimales;
    }
}
