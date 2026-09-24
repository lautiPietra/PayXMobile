package com.example.payxmobile.model;

import java.math.BigDecimal;

public class PerfilResponse {
    private String id;
    private String email;
    private String nombreCompleto;
    private String dni;
    private String telefono;
    private String nombreUsuario;
    private String alias;
    private String cvu;
    private BigDecimal saldoPesos;
    private BigDecimal saldoUsd;
    private BigDecimal saldoBtc;
    private BigDecimal saldoEth;
    private BigDecimal saldoSolana;
    private BigDecimal saldoUsdt;
    private BigDecimal saldoBnb;
    private BigDecimal saldoXrp;
    private String fotoPerfilUrl;
    private String tarjetaUltimosCuatro;
    private String tarjetaVencimiento; // "yyyy-MM-dd"

    public String getId() { return id; }
    public String getEmail() { return email; }
    public String getNombreCompleto() { return nombreCompleto; }
    /** null si la cuenta no tiene DNI cargado (típico de las creadas con Google). */
    public String getDni() { return dni; }
    /** Puede ser null (cuentas creadas con Google). */
    public String getTelefono() { return telefono; }
    public String getNombreUsuario() { return nombreUsuario; }
    public String getAlias() { return alias; }
    public String getCvu() { return cvu; }
    public BigDecimal getSaldoPesos() { return cero(saldoPesos); }
    public BigDecimal getSaldoUsd() { return cero(saldoUsd); }
    public BigDecimal getSaldoBtc() { return cero(saldoBtc); }
    public BigDecimal getSaldoEth() { return cero(saldoEth); }
    public BigDecimal getSaldoSolana() { return cero(saldoSolana); }
    public BigDecimal getSaldoUsdt() { return cero(saldoUsdt); }
    public BigDecimal getSaldoBnb() { return cero(saldoBnb); }
    public BigDecimal getSaldoXrp() { return cero(saldoXrp); }
    public String getFotoPerfilUrl() { return fotoPerfilUrl; }
    public String getTarjetaUltimosCuatro() { return tarjetaUltimosCuatro; }
    public String getTarjetaVencimiento() { return tarjetaVencimiento; }

    /** Criptos de la tarjeta de saldo, en el orden de la web. */
    public static final String[] CRIPTOS = {"BTC", "ETH", "SOL", "USDT", "BNB", "XRP"};

    /**
     * Saldo de una cripto en su propia unidad. OJO: SOL sale del campo "saldoSolana".
     * Símbolo desconocido -> IllegalArgumentException (mejor fallar que mostrar otro saldo).
     */
    public BigDecimal getSaldoCripto(String simbolo) {
        switch (simbolo) {
            case "BTC": return getSaldoBtc();
            case "ETH": return getSaldoEth();
            case "SOL": return getSaldoSolana();
            case "USDT": return getSaldoUsdt();
            case "BNB": return getSaldoBnb();
            case "XRP": return getSaldoXrp();
            default: throw new IllegalArgumentException("Cripto desconocida: " + simbolo);
        }
    }

    public boolean tieneDni() { return dni != null && !dni.trim().isEmpty(); }
    public boolean tieneTelefono() { return telefono != null && !telefono.trim().isEmpty(); }

    /** "2030-05-31" -> "05/30" (formato de tarjeta). null si no hay fecha o no tiene ese formato. */
    public String getTarjetaVencimientoMmAa() {
        if (tarjetaVencimiento == null || !tarjetaVencimiento.matches("\\d{4}-\\d{2}-\\d{2}")) return null;
        return tarjetaVencimiento.substring(5, 7) + "/" + tarjetaVencimiento.substring(2, 4);
    }

    private static BigDecimal cero(BigDecimal valor) {
        return valor != null ? valor : BigDecimal.ZERO;
    }
}
