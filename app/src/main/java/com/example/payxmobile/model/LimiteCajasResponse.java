package com.example.payxmobile.model;

/** GET /api/cajas-ahorro/limite: cuántas cajas puede tener cada usuario (lo configura el admin). */
public class LimiteCajasResponse {
    private Integer maxPorUsuario;

    public LimiteCajasResponse() {}

    public LimiteCajasResponse(Integer maxPorUsuario) {
        this.maxPorUsuario = maxPorUsuario;
    }

    public Integer getMaxPorUsuario() { return maxPorUsuario; }
}
