package com.retailstore.api.seguridad.web;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.seguridad.dto.AccesoPeticion;
import com.retailstore.api.seguridad.dto.AdministradorRespuesta;
import com.retailstore.api.seguridad.dto.SesionRespuesta;
import com.retailstore.api.seguridad.servicio.ServicioAccesoPanel;
import com.retailstore.api.seguridad.servicio.ServicioAccesoPanel.SesionEmitida;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Acceso al panel")
public class AccesoPanelControlador {

    /**
     * El {@code path} de la cookie la limita a las rutas del panel: así no se
     * envía en ninguna petición de la tienda, que es donde no pinta nada.
     */
    static final String COOKIE_REFRESCO = "refresco_panel";
    static final String RUTA_COOKIE = "/api/v1/admin";

    private final ServicioAccesoPanel servicio;

    public AccesoPanelControlador(ServicioAccesoPanel servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/acceso")
    @Operation(summary = "Entrar al panel con usuario y contraseña")
    public ResponseEntity<SesionRespuesta> acceder(@Valid @RequestBody AccesoPeticion peticion,
                                                   HttpServletRequest http) {
        SesionEmitida sesion = servicio.acceder(peticion.usuario(), peticion.contrasena(), ipDe(http));
        return conCookie(sesion);
    }

    @PostMapping("/refrescar")
    @Operation(summary = "Renovar el token de acceso con la cookie de refresco")
    public ResponseEntity<SesionRespuesta> refrescar(
            @CookieValue(name = COOKIE_REFRESCO, required = false) String refresco) {
        if (refresco == null || refresco.isBlank()) {
            throw new ExcepcionAplicacion(CodigoError.UNAUTHENTICATED, "No hay sesión que renovar.");
        }
        return conCookie(servicio.refrescar(refresco));
    }

    @PostMapping("/salir")
    @Operation(summary = "Cerrar la sesión y revocar el refresco")
    public ResponseEntity<Void> salir(@CookieValue(name = COOKIE_REFRESCO, required = false) String refresco) {
        servicio.cerrarSesion(refresco);
        // Se borra la cookie pase lo que pase: si el token ya no era válido, el
        // navegador no tiene por qué seguir guardándolo.
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieVacia().toString())
                .build();
    }

    private ResponseEntity<SesionRespuesta> conCookie(SesionEmitida sesion) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_REFRESCO, sesion.tokenRefresco())
                .httpOnly(true)
                .secure(servicio.cookieSegura())
                // Lax y no None: el panel y la API se sirven bajo el mismo
                // sitio, así que la cookie llega donde tiene que llegar y un
                // POST desde otro dominio no la arrastra. Eso es lo que hace
                // innecesario el token CSRF.
                .sameSite("Lax")
                .path(RUTA_COOKIE)
                .maxAge(servicio.vigenciaRefresco())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new SesionRespuesta(
                        sesion.tokenAcceso(),
                        sesion.expiraEnSegundos(),
                        AdministradorRespuesta.de(sesion.administrador())));
    }

    private ResponseCookie cookieVacia() {
        return ResponseCookie.from(COOKIE_REFRESCO, "")
                .httpOnly(true)
                .secure(servicio.cookieSegura())
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
