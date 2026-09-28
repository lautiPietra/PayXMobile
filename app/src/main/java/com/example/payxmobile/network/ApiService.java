package com.example.payxmobile.network;

import com.example.payxmobile.model.ActualizarPerfilRequest;
import com.example.payxmobile.model.CajaAhorroRequest;
import com.example.payxmobile.model.CajaAhorroResponse;
import com.example.payxmobile.model.CambiarPasswordRequest;
import com.example.payxmobile.model.ConceptoRequest;
import com.example.payxmobile.model.CotizacionCripto;
import com.example.payxmobile.model.CotizacionDolar;
import com.example.payxmobile.model.CrearCambioDolaresRequest;
import com.example.payxmobile.model.CrearOperacionCriptoRequest;
import com.example.payxmobile.model.CrearPlazoFijoRequest;
import com.example.payxmobile.model.CrearTransferenciaRequest;
import com.example.payxmobile.model.DestinatarioResponse;
import com.example.payxmobile.model.EstadisticaGastosResponse;
import com.example.payxmobile.model.FacturaResponse;
import com.example.payxmobile.model.GoogleLoginRequest;
import com.example.payxmobile.model.LimiteCajasResponse;
import com.example.payxmobile.model.LoginRequest;
import com.example.payxmobile.model.LoginResponse;
import com.example.payxmobile.model.MensajeResponse;
import com.example.payxmobile.model.MontoCajaAhorroRequest;
import com.example.payxmobile.model.NotificacionResponse;
import com.example.payxmobile.model.OperacionCambioResponse;
import com.example.payxmobile.model.OperacionCriptoResponse;
import com.example.payxmobile.model.PerfilResponse;
import com.example.payxmobile.model.PlazoFijoResponse;
import com.example.payxmobile.model.ReenviarCodigoRequest;
import com.example.payxmobile.model.RegistroRequest;
import com.example.payxmobile.model.RegistroResponse;
import com.example.payxmobile.model.ResetPasswordRequest;
import com.example.payxmobile.model.ServicioConFacturaResponse;
import com.example.payxmobile.model.SinLeerResponse;
import com.example.payxmobile.model.TarjetaResponse;
import com.example.payxmobile.model.TasasPlazoFijoResponse;
import com.example.payxmobile.model.TransferenciaResponse;
import com.example.payxmobile.model.VerificarCodigoRequest;

import java.util.List;

import okhttp3.MultipartBody;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
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

    // { compra, venta, fechaActualizacion, desactualizada }. Cacheada 60 s en el backend, límite 30/min.
    // COMPRA de dólares usa "venta" (lo que paga el usuario); VENTA usa "compra" (lo que recibe).
    @GET("api/cotizacion/dolar")
    Call<CotizacionDolar> obtenerCotizacionDolar();

    // ── Dólares ──

    // SIN idempotencia en el backend: usar SOLO con el cliente sin reintentos (RetrofitClient.getServiceSinReintentos)
    @POST("api/cambio-dolares")
    Call<OperacionCambioResponse> crearCambioDolares(@Body CrearCambioDolaresRequest request);

    @GET("api/cambio-dolares")
    Call<List<OperacionCambioResponse>> listarCambiosDolares();

    // ── Cripto ──

    // SIN idempotencia en el backend: usar SOLO con el cliente sin reintentos (RetrofitClient.getServiceSinReintentos)
    @POST("api/cripto")
    Call<OperacionCriptoResponse> crearOperacionCripto(@Body CrearOperacionCriptoRequest request);

    @GET("api/cripto")
    Call<List<OperacionCriptoResponse>> listarOperacionesCripto();

    // ── Plazos fijos ──

    // Plazos con su TNA, monto mínimo y máximo de activos: configurables desde el admin, pedirlos SIEMPRE
    @GET("api/plazos-fijos/tasas")
    Call<TasasPlazoFijoResponse> obtenerTasasPlazoFijo();

    // SIN idempotencia en el backend: usar SOLO con el cliente sin reintentos (RetrofitClient.getServiceSinReintentos)
    @POST("api/plazos-fijos")
    Call<PlazoFijoResponse> crearPlazoFijo(@Body CrearPlazoFijoRequest request);

    // Activos y vencidos, más recientes primero (por fechaCreacion)
    @GET("api/plazos-fijos")
    Call<List<PlazoFijoResponse>> listarPlazosFijos();

    // ── Estadísticas de gastos ──

    // "dias" SIEMPRE explícito: sin el parámetro el backend usa 30. 0 (o negativo) = todo el tiempo.
    // Límite 30/min (hace varias consultas por dentro): pedirlo solo al abrir o cambiar de período.
    @GET("api/estadisticas/gastos")
    Call<EstadisticaGastosResponse> obtenerEstadisticaGastos(@Query("dias") int dias);

    // ── Pago de servicios ──

    // Los 6 servicios del catálogo con la factura del período actual (el backend la crea si no existía)
    @GET("api/facturas")
    Call<List<ServicioConFacturaResponse>> listarServicios();

    // TODAS las facturas (todos los períodos, pendientes y pagadas). Sin orden garantizado: ordenar en el cliente
    @GET("api/facturas/historial")
    Call<List<FacturaResponse>> historialFacturas();

    // SIN idempotencia en el backend (límite 15/min): usar SOLO con RetrofitClient.getServiceSinReintentos
    @POST("api/facturas/{id}/pagar")
    Call<FacturaResponse> pagarFactura(@Path("id") String facturaId);

    // ── Tarjeta virtual ──

    // Número COMPLETO y CVV: pedirlo SOLO cuando el usuario lo pide (límite 15/min). Los últimos 4 y el
    // vencimiento ya vienen en /api/perfil. Se crea sola la primera vez.
    @GET("api/tarjeta")
    Call<TarjetaResponse> obtenerTarjeta();

    // ── Cajas de ahorro ──

    // Cuántas cajas puede tener cada usuario: configurable desde el admin, pedirlo SIEMPRE antes de crear
    @GET("api/cajas-ahorro/limite")
    Call<LimiteCajasResponse> obtenerLimiteCajas();

    // Ordenadas por fecha de creación (la más vieja primero)
    @GET("api/cajas-ahorro")
    Call<List<CajaAhorroResponse>> listarCajasAhorro();

    // Los POST y el DELETE no tienen idempotencia: usar SOLO con RetrofitClient.getServiceSinReintentos
    @POST("api/cajas-ahorro")
    Call<CajaAhorroResponse> crearCajaAhorro(@Body CajaAhorroRequest request);

    @PUT("api/cajas-ahorro/{id}")
    Call<CajaAhorroResponse> editarCajaAhorro(@Path("id") String id, @Body CajaAhorroRequest request);

    // Mueve del saldo PRINCIPAL en pesos a la caja
    @POST("api/cajas-ahorro/{id}/depositar")
    Call<CajaAhorroResponse> depositarEnCaja(@Path("id") String id, @Body MontoCajaAhorroRequest request);

    // Mueve de la caja al saldo PRINCIPAL en pesos
    @POST("api/cajas-ahorro/{id}/retirar")
    Call<CajaAhorroResponse> retirarDeCaja(@Path("id") String id, @Body MontoCajaAhorroRequest request);

    // 204 sin body. Si la caja tenía saldo, el backend lo devuelve a la cuenta principal
    @DELETE("api/cajas-ahorro/{id}")
    Call<Void> eliminarCajaAhorro(@Path("id") String id);

    // ── Notificaciones (sin rate limit). A propósito NO está POST /registro-login: el backend ya
    // crea la notificación de inicio de sesión dentro de /api/auth/login y /api/auth/google. ──

    @GET("api/notificaciones")
    Call<List<NotificacionResponse>> obtenerNotificaciones();

    @GET("api/notificaciones/sin-leer")
    Call<SinLeerResponse> contarSinLeer();

    @PATCH("api/notificaciones/leer")
    Call<Void> marcarTodasLeidas();
}
