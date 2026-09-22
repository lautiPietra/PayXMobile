package com.example.payxmobile.model;

public class GoogleLoginRequest {

    private final String idToken;

    public GoogleLoginRequest(String idToken) {
        this.idToken = idToken;
    }

    public String getIdToken() { return idToken; }
}
