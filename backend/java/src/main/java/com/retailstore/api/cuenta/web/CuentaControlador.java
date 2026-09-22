package com.retailstore.api.cuenta.web;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.cuenta.dto.AccesoClientePeticion;
import com.retailstore.api.cuenta.dto.AccesoGooglePeticion;
import com.retailstore.api.cuenta.dto.ActualizarPerfilPeticion;
import com.retailstore.api.cuenta.dto.CambiarContrasenaClientePeticion;
import com.retailstore.api.cuenta.dto.CuentaRespuesta;
import com.retailstore.api.cuenta.dto.RecuperarPeticion;
import com.retailstore.api.cuenta.dto.RegistroPeticion;
import com.retailstore.api.cuenta.dto.RestablecerPeticion;
import com.retailstore.api.cuenta.dto.VerificarPeticion;
import com.retailstore.api.cuenta.dto.SesionClienteRespuesta;
import com.retailstore.api.cuenta.servicio.ServicioAccesoGoogle;
import com.retailstore.api.cuenta.servicio.ServicioAccesoTienda;
import com.retailstore.api.cuenta.servicio.ServicioAccesoTienda.SesionEmitida;
import com.retailstore.api.cuenta.servicio.ServicioCuenta;
import com.retailstore.api.cuenta.servicio.ServicioRecuperacion;
import com.retailstore.api.seguridad.dominio.ClienteAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La cuenta del comprador: entrar, salir y su perfil.
 *
 * <p>La cookie de refresco de la tienda es otra que la del panel y con otra
 * ruta. Que {@code refresco_tienda} viva en {@code /api/v1/cuenta} significa
 * que el navegador no la manda ni al panel ni al catálogo: no pinta nada ahí, y
 * una credencial que viaja donde no hace falta es una credencial más expuesta.
 */
@RestController
@RequestMapping("/api/v1/cuenta")
@Tag(name = "Cuenta (tienda)")
@SecurityRequirement(name = "tokenTienda")
public class CuentaControlador {

    static final String COOKIE_REFRESCO = "refresco_tienda";
    static final String RUTA_COOKIE = "/api/v1/cuenta";

    /**
     * El mismo texto exista o no el correo (RN-061). Si aquí hubiera dos
     * mensajes, el endpoint sería un comprobador de quién tiene cuenta.
     */
    private static final String MENSAJE_REGISTRO =
            "Si el correo es válido, te hemos enviado un enlace para confirmarlo.";

    /**
     * Mismo razonamiento que arriba, aplicado a la recuperación (RN-066): con
     * dos mensajes distintos, probar direcciones diría cuáles tienen cuenta.
     */
    private static final String MENSAJE_RECUPERACION =
            "Si el correo tiene una cuenta, te hemos enviado un enlace para cambiar la contraseña.";

    private final ServicioAccesoTienda accesos;
    private final ServicioAccesoGoogle google;
    private final ServicioCuenta cuentas;
    private final ServicioRecuperacion recuperacion;

    public CuentaControlador(ServicioAccesoTienda accesos, ServicioAccesoGoogle google,
                             ServicioCuenta cuentas, ServicioRecuperacion recuperacion) {
        this.accesos = accesos;
        this.google = google;
        this.cuentas = cuentas;
        this.recuperacion = recuperacion;
    }

    // ---------------------------------------------------------------- sesión

    @PostMapping("/registro")
    @Operation(summary = "Crear una cuenta. Responde 202 exista o no el correo (RN-061)")
    public ResponseEntity<Map<String, String>> registro(@Valid @RequestBody RegistroPeticion peticion,
                                                        HttpServletRequest http) {
        accesos.registrar(peticion, ipDe(http));
        // 202 y no 201: lo que queda hecho es la solicitud, no necesariamente
        // una cuenta nueva -y decir cuál de las dos fue es justo lo que RN-061
        // prohíbe-.
        return ResponseEntity.accepted().body(Map.of("mensaje", MENSAJE_REGISTRO));
    }

    @PostMapping("/recuperar")
    @Operation(summary = "Pedir el enlace para restablecer la contraseña",
            description = "Responde 202 con el mismo cuerpo exista o no el correo (RN-066). "
                    + "Contestar «esa dirección no está registrada» convertiría el endpoint "
                    + "en un comprobador de quién tiene cuenta en la tienda.")
    public ResponseEntity<Map<String, String>> recuperar(@Valid @RequestBody RecuperarPeticion peticion,
                                                         HttpServletRequest http) {
        recuperacion.solicitar(peticion.email(), ipDe(http));
        return ResponseEntity.accepted().body(Map.of("mensaje", MENSAJE_RECUPERACION));
    }

    @PostMapping("/restablecer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Poner una contraseña nueva con el token del correo",
            description = "El token dura 30 minutos y sirve una sola vez. Al terminar se "
                    + "cierran todas las sesiones abiertas: quien restablece suele hacerlo "
                    + "porque sospecha que hay otro dentro.")
    public void restablecer(@Valid @RequestBody RestablecerPeticion peticion) {
        recuperacion.restablecer(peticion.token(), peticion.contrasenaNueva());
    }

    @PostMapping("/verificacion/reenviar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(summary = "Reenviar el correo de verificación. Requiere sesión")
    public void reenviarVerificacion(@AuthenticationPrincipal ClienteAutenticado cliente,
                                     HttpServletRequest http) {
        recuperacion.reenviarVerificacion(cliente.id(), ipDe(http));
    }

    @PostMapping("/verificacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Confirmar el correo con el token del enlace")
    public void verificar(@Valid @RequestBody VerificarPeticion peticion) {
        recuperacion.verificar(peticion.token());
    }

    @PostMapping("/acceso")
    @Operation(summary = "Entrar con correo y contraseña")
    public ResponseEntity<SesionClienteRespuesta> acceso(@Valid @RequestBody AccesoClientePeticion peticion,
                                                         HttpServletRequest http) {
        return conCookie(accesos.acceder(peticion.email(), peticion.contrasena(), ipDe(http)));
    }

    @PostMapping("/google")
    @Operation(summary = "Entrar con el ID token de Google. Vincula si el correo ya tiene cuenta")
    public ResponseEntity<SesionClienteRespuesta> accesoConGoogle(@Valid @RequestBody AccesoGooglePeticion peticion) {
        return conCookie(google.acceder(peticion.credencial()));
    }

    @PostMapping("/refrescar")
    @Operation(summary = "Renovar el token de acceso con la cookie de refresco")
    public ResponseEntity<SesionClienteRespuesta> refrescar(
            @CookieValue(name = COOKIE_REFRESCO, required = false) String refresco) {
        if (refresco == null || refresco.isBlank()) {
            throw new ExcepcionAplicacion(CodigoError.UNAUTHENTICATED, "No hay sesión que renovar.");
        }
        return conCookie(accesos.refrescar(refresco));
    }

    @PostMapping("/salir")
    @Operation(summary = "Cerrar la sesión y revocar el refresco")
    public ResponseEntity<Void> salir(@CookieValue(name = COOKIE_REFRESCO, required = false) String refresco) {
        accesos.cerrarSesion(refresco);
        // La cookie se borra pase lo que pase: si el token ya no era válido, el
        // navegador no tiene por qué seguir guardándolo.
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieVacia().toString())
                .build();
    }

    // ---------------------------------------------------------------- perfil

    @GetMapping("/yo")
    @Operation(summary = "El perfil de quien trae el token")
    public CuentaRespuesta yo(@AuthenticationPrincipal ClienteAutenticado cliente) {
        return cuentas.yo(cliente.id());
    }

    @PutMapping("/yo")
    @Operation(summary = "Cambiar nombre y teléfono. El correo no se edita: es la identidad (RN-060)")
    public CuentaRespuesta actualizarPerfil(@AuthenticationPrincipal ClienteAutenticado cliente,
                                            @Valid @RequestBody ActualizarPerfilPeticion peticion) {
        return cuentas.actualizar(cliente.id(), peticion);
    }

    @PostMapping("/yo/contrasena")
    @Operation(summary = "Cambiar o establecer la contraseña. La actual solo se pide si ya había una (RN-065)")
    public ResponseEntity<Void> cambiarContrasena(
            @AuthenticationPrincipal ClienteAutenticado cliente,
            @Valid @RequestBody CambiarContrasenaClientePeticion peticion,
            @CookieValue(name = COOKIE_REFRESCO, required = false) String refresco) {
        // El refresco llega para poder conservar esta sesión al cerrar las
        // demás. Sin él caerían todas, incluida la de quien está cambiando la
        // contraseña.
        cuentas.cambiarContrasena(cliente.id(), peticion, refresco);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ apoyo

    private ResponseEntity<SesionClienteRespuesta> conCookie(SesionEmitida sesion) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_REFRESCO, sesion.tokenRefresco())
                .httpOnly(true)
                .secure(accesos.cookieSegura())
                // Lax y no None: la tienda y la API se sirven bajo el mismo
                // sitio, así que la cookie llega donde tiene que llegar y un
                // POST desde otro dominio no la arrastra. Eso es lo que hace
                // innecesario el token CSRF.
                .sameSite("Lax")
                .path(RUTA_COOKIE)
                .maxAge(accesos.vigenciaRefresco())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new SesionClienteRespuesta(
                        sesion.tokenAcceso(),
                        sesion.expiraEnSegundos(),
                        CuentaRespuesta.de(sesion.cliente())));
    }

    private ResponseCookie cookieVacia() {
        return ResponseCookie.from(COOKIE_REFRESCO, "")
                .httpOnly(true)
                .secure(accesos.cookieSegura())
                .sameSite("Lax")
                .path(RUTA_COOKIE)
                .maxAge(0)
                .build();
    }

    /**
     * La IP real detrás de Caddy. Se toma el primer valor de
     * {@code X-Forwarded-For}: los siguientes los pudo añadir el propio cliente.
     */
    private static String ipDe(HttpServletRequest http) {
        String reenviada = http.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return http.getRemoteAddr() == null ? "desconocida" : http.getRemoteAddr();
    }
}
