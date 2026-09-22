package com.retailstore.api.chat.web;

import com.retailstore.api.chat.dto.AbrirConsultaPeticion;
import com.retailstore.api.chat.dto.ConversacionDetalleRespuesta;
import com.retailstore.api.chat.dto.ConversacionMiaRespuesta;
import com.retailstore.api.chat.dto.EnviarMensajePeticion;
import com.retailstore.api.chat.dto.MensajeRespuesta;
import com.retailstore.api.chat.servicio.ServicioChat;
import com.retailstore.api.seguridad.dominio.ClienteAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * El chat desde la tienda, para el cliente con sesión.
 *
 * <p>Ya existía {@code /api/v1/conversaciones/{token}}, que identifica por el
 * token del hilo y sirve a quien compró <strong>sin cuenta</strong>. Este
 * controlador es el otro caso: quien tiene sesión no debería depender de haber
 * guardado un enlace, y además necesita algo que el token no puede dar —la
 * lista de <em>sus</em> conversaciones y cuántas respuestas lleva sin leer—.
 *
 * <p>Cuelga de {@code /api/v1/cuenta} a propósito: ahí es donde el filtro de la
 * tienda lee la cabecera {@code Authorization} y donde las reglas de
 * autorización exigen sesión.
 */
@RestController
@RequestMapping("/api/v1/cuenta/conversaciones")
@SecurityRequirement(name = "bearer-jwt")
@Tag(name = "Chat (tienda)", description = "Consultas del cliente a la tienda")
public class ChatTiendaControlador {

    private final ServicioChat chat;

    public ChatTiendaControlador(ServicioChat chat) {
        this.chat = chat;
    }

    @GetMapping
    @Operation(summary = "Mis conversaciones, de la más reciente a la más antigua")
    public List<ConversacionMiaRespuesta> mias(@AuthenticationPrincipal ClienteAutenticado cliente) {
        return chat.mias(cliente.id());
    }

    /**
     * Lo que pinta la campana. Va aparte del listado porque la cabecera lo pide
     * en cada página y no necesita los hilos, solo el número.
     */
    @GetMapping("/no-leidos")
    @Operation(summary = "Cuántas respuestas sin leer tengo")
    public Map<String, Long> noLeidos(@AuthenticationPrincipal ClienteAutenticado cliente) {
        return Map.of("noLeidos", chat.noLeidosDelCliente(cliente.id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Abrir una consulta, opcionalmente sobre una orden mía",
            description = "Si ya hay una conversación sobre esa orden, devuelve esa en vez de crear otra: "
                    + "dos hilos sobre la misma compra acaban con el cliente contando su problema dos veces.")
    public ConversacionDetalleRespuesta abrir(@Valid @RequestBody AbrirConsultaPeticion peticion,
                                              @AuthenticationPrincipal ClienteAutenticado cliente) {
        return chat.abrirDesdeLaTienda(cliente.id(), peticion.ordenId(),
                peticion.asunto(), peticion.mensaje());
    }

    /** Abrir un hilo lo marca como leído: se está mirando. */
    @GetMapping("/{token}")
    @Operation(summary = "Ver una conversación mía y marcarla como leída")
    public ConversacionDetalleRespuesta ver(@PathVariable String token,
                                            @AuthenticationPrincipal ClienteAutenticado cliente) {
        return chat.abrirComoCliente(token, cliente.id());
    }

    @PostMapping("/{token}/mensajes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Escribir en una conversación mía")
    public MensajeRespuesta escribir(@PathVariable String token,
                                     @Valid @RequestBody EnviarMensajePeticion peticion,
                                     @AuthenticationPrincipal ClienteAutenticado cliente) {
        return chat.escribirComoClienteAutenticado(token, cliente.id(),
                peticion.cuerpo(), peticion.adjuntoIds());
    }
}
