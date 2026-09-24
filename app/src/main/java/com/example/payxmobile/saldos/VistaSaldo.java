package com.example.payxmobile.saldos;

import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.utils.MontoFormatter;

import java.math.BigDecimal;

/**
 * Qué muestra la tarjeta de saldo en una pestaña, calculado sin Android (se testea en la JVM).
 * Pestañas: {@link #PESOS} ($, saldoPesos), {@link #DOLARES} (US$, saldoUsd) y {@link #CRIPTO}
 * (saldo de la cripto elegida en su unidad + "≈ $" en pesos).
 */
public final class VistaSaldo {

    public static final int PESOS = 0;
    public static final int DOLARES = 1;
    public static final int CRIPTO = 2;

    public enum Tipo { CARGANDO, ERROR, SALDO }

    public final Tipo tipo;
    /** "$" o "US$"; null en cripto (el símbolo va como sufijo del monto). */
    public final String prefijo;
    /** Monto listo para mostrar ("1.234,50", "0,5 BTC" o "••••••"); null si no hay saldo. */
    public final String monto;
    /** Valor exacto (para animar el número); null si está oculto o no hay saldo. */
    public final BigDecimal valor;
    /** "≈ $ 1.234,56", o "≈ $ —" sin cotización; null si no es cripto o el saldo está oculto. */
    public final String equivalente;
    /** La cotización de esta cripto es el último precio conocido (proveedor caído). */
    public final boolean cotizacionDesactualizada;
    /** Hay saldo, pero el último refresco falló. */
    public final boolean desactualizado;
    /** Mensaje de la primera carga fallida (tipo ERROR). */
    public final String error;

    private VistaSaldo(Tipo tipo, String prefijo, String monto, BigDecimal valor, String equivalente,
                       boolean cotizacionDesactualizada, boolean desactualizado, String error) {
        this.tipo = tipo;
        this.prefijo = prefijo;
        this.monto = monto;
        this.valor = valor;
        this.equivalente = equivalente;
        this.cotizacionDesactualizada = cotizacionDesactualizada;
        this.desactualizado = desactualizado;
        this.error = error;
    }

    public static VistaSaldo de(EstadoSaldos estado, int pestania, String cripto, boolean visible) {
        String prefijo = pestania == PESOS ? "$" : pestania == DOLARES ? "US$" : null;
        if (estado == null || estado.esPrimeraCarga()) {
            return new VistaSaldo(Tipo.CARGANDO, prefijo, null, null, null, false, false, null);
        }
        if (!estado.tieneSaldos()) {
            return new VistaSaldo(Tipo.ERROR, prefijo, null, null, null, false, false, estado.errorPrimeraCarga);
        }

        PerfilResponse p = estado.perfil;
        BigDecimal saldo = pestania == PESOS ? p.getSaldoPesos()
                : pestania == DOLARES ? p.getSaldoUsd()
                : p.getSaldoCripto(cripto);

        if (!visible) {
            return new VistaSaldo(Tipo.SALDO, prefijo, MontoFormatter.OCULTO, null, null,
                    false, estado.desactualizado, null);
        }

        String monto = formatear(pestania, cripto, saldo);
        String equivalente = null;
        boolean cotizacionDesactualizada = false;
        if (pestania == CRIPTO) {
            equivalente = "≈ $ " + MontoFormatter.equivalentePesos(saldo, estado.precio(cripto));
            cotizacionDesactualizada = estado.cotizacionDesactualizada(cripto);
        }
        return new VistaSaldo(Tipo.SALDO, prefijo, monto, saldo, equivalente,
                cotizacionDesactualizada, estado.desactualizado, null);
    }

    /** Mismo formato que el monto final, para los cuadros intermedios de la animación. */
    public static String formatear(int pestania, String cripto, BigDecimal valor) {
        return pestania == CRIPTO
                ? MontoFormatter.cripto(valor) + " " + cripto
                : MontoFormatter.fiat(valor);
    }
}
