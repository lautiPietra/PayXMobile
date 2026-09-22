package com.example.payxmobile.model;

public class CambiarPasswordRequest {
    private String passwordActual;
    private String nuevaPassword;

    public CambiarPasswordRequest(String passwordActual, String nuevaPassword) {
        this.passwordActual = passwordActual;
        this.nuevaPassword = nuevaPassword;
    }
}
