package com.example.payxmobile.network;

import com.example.payxmobile.model.ActualizarPerfilRequest;
import com.example.payxmobile.model.CambiarPasswordRequest;
import com.example.payxmobile.model.ConceptoRequest;
import com.example.payxmobile.model.CotizacionCripto;
import com.example.payxmobile.model.CotizacionDolar;
import com.example.payxmobile.model.CrearTransferenciaRequest;
import com.example.payxmobile.model.DestinatarioResponse;
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
import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.model.VerificarCodigoRequest;

import java.util.List;

import okhttp3.MultipartBody;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

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

    // multipart/form-data con la parte "archivo". No se setea Content-Type a mano: OkHttp agrega el boundary.
    @Multipart
    @POST("api/perfil/foto")
    Call<PerfilResponse> subirFotoPerfil(@Part MultipartBody.Part archivo);

    @PUT("api/perfil/password")
    Call<MensajeResponse> cambiarPassword(@Body CambiarPasswordRequest request);

    // Precio en pesos de BTC, ETH, SOL, USDT, BNB y XRP (cacheado 60 s en el backend)
    @GET("api/cotizacion/cripto")
    Call<List<CotizacionCripto>> obtenerCotizacionesCripto();

    // ── Transferencias ──

    // "valor" ya va codificado (URLEncoder): "@ana" -> "%40ana", espacios -> "+"
    @GET("api/transferencias/destinatario")
    Call<DestinatarioResponse> resolverDestinatario(@Query(value = "valor", encoded = true) String valorCodificado);

    // SIN idempotencia en el backend: usar SOLO con el cliente sin reintentos (RetrofitClient.getServiceSinReintentos)
    @POST("api/transferencias")
    Call<TransferenciaResponse> crearTransferencia(@Body CrearTransferenciaRequest request);

    @GET("api/transferencias")
    Call<List<TransferenciaResponse>> listarTransferencias();

    @GET("api/transferencias/{id}")
    Call<TransferenciaResponse> obtenerTransferencia(@Path("id") String id);

    @PATCH("api/transferencias/{id}/concepto")
    Call<TransferenciaResponse> actualizarConcepto(@Path("id") String id, @Body ConceptoRequest request);

    @PATCH("api/transferencias/{id}/confirmar")
    Call<TransferenciaResponse> confirmarTransferencia(@Path("id") String id);

    @PATCH("api/transferencias/{id}/cancelar")
    Call<TransferenciaResponse> cancelarTransferencia(@Path("id") String id);

    @GET("api/cotizacion/dolar")
    Call<CotizacionDolar> obtenerCotizacionDolar();

    @GET("api/notificaciones")
    Call<List<NotificacionResponse>> obtenerNotificaciones();

    @GET("api/notificaciones/sin-leer")
    Call<SinLeerResponse> contarSinLeer();

    @PATCH("api/notificaciones/leer")
    Call<Void> marcarTodasLeidas();
}
