package com.example.payxmobile.network;

import com.example.payxmobile.model.ActualizarPerfilRequest;
import com.example.payxmobile.model.CambiarPasswordRequest;
import com.example.payxmobile.model.GoogleLoginRequest;
import com.example.payxmobile.model.LoginRequest;
import com.example.payxmobile.model.LoginResponse;
import com.example.payxmobile.model.MensajeResponse;
import com.example.payxmobile.model.NotificacionResponse;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.model.ReenviarCodigoRequest;
import com.example.payxmobile.model.RegistroRequest;
import com.example.payxmobile.model.RegistroResponse;
import com.example.payxmobile.model.ResetPasswordRequest;
import com.example.payxmobile.model.SinLeerResponse;
import com.example.payxmobile.model.VerificarCodigoRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;

public interface ApiService {

    @POST("api/auth/register")
    Call<RegistroResponse> registrar(@Body RegistroRequest request);

    @POST("api/auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("api/auth/google")
    Call<LoginResponse> loginConGoogle(@Body GoogleLoginRequest request);

    @POST("api/auth/verify-email")
    Call<MensajeResponse> verificarEmail(@Body VerificarCodigoRequest request);

    @POST("api/auth/resend-code")
    Call<MensajeResponse> reenviarCodigo(@Body ReenviarCodigoRequest request);

    @POST("api/auth/forgot-password")
    Call<MensajeResponse> solicitarReset(@Body ReenviarCodigoRequest request);

    @POST("api/auth/reset-password")
    Call<MensajeResponse> resetearPassword(@Body ResetPasswordRequest request);

    @GET("api/perfil")
    Call<PerfilResponse> obtenerPerfil();

    @PUT("api/perfil")
    Call<PerfilResponse> actualizarPerfil(@Body ActualizarPerfilRequest request);

    @PUT("api/perfil/password")
    Call<MensajeResponse> cambiarPassword(@Body CambiarPasswordRequest request);

    @GET("api/notificaciones")
    Call<List<NotificacionResponse>> obtenerNotificaciones();

    @GET("api/notificaciones/sin-leer")
    Call<SinLeerResponse> contarSinLeer();

    @PATCH("api/notificaciones/leer")
    Call<Void> marcarTodasLeidas();
}
