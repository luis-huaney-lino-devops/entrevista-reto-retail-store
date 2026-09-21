package com.retailstore.api.chat.web;

import com.retailstore.api.catalogo.dto.CambiarEstadoPeticion;
import com.retailstore.api.chat.dto.AbrirConversacionPeticion;
import com.retailstore.api.chat.dto.ConversacionDetalleRespuesta;
import com.retailstore.api.chat.dto.ConversacionResumenRespuesta;
import com.retailstore.api.chat.dto.EnviarMensajePeticion;
import com.retailstore.api.chat.dto.MensajeRespuesta;
import com.retailstore.api.chat.servicio.ServicioAdjunto;
import com.retailstore.api.chat.servicio.ServicioChat;
import com.retailstore.api.chat.ws.ServicioTicketWs;
import com.retailstore.api.comun.auditoria.UsuarioActual;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.comun.paginacion.RespuestaPagina;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/admin/conversaciones")
@Tag(name = "Chat (panel)")
@SecurityRequirement(name = "tokenPanel")
@Validated
public class ChatAdminControlador {

    private final ServicioChat chat;
    private final ServicioAdjunto adjuntos;
    private final ServicioTicketWs tickets;

    public ChatAdminControlador(ServicioChat chat, ServicioAdjunto adjuntos, ServicioTicketWs tickets) {
        this.chat = chat;
        this.adjuntos = adjuntos;
        this.tickets = tickets;
    }

    @GetMapping
    @Operation(summary = "Bandeja de conversaciones, la más activa primero")
    public RespuestaPagina<ConversacionResumenRespuesta> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser al menos 1") int pagina,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "debe ser al menos 1")
            @Max(value = 100, message = "no puede superar 100") int tamanoPagina) {
        return chat.listar(estado, pagina, tamanoPagina);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Abrir el hilo. Marca como leídos los mensajes del cliente")
    public ConversacionDetalleRespuesta abrir(@PathVariable Long id) {
        return chat.abrirEnPanel(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Empezar una conversación con un cliente")
    public ConversacionDetalleRespuesta crear(@Valid @RequestBody AbrirConversacionPeticion peticion) {
        return chat.abrir(peticion);
    }

    @PostMapping("/{id}/mensajes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Responder. También se puede por WebSocket; el camino interno es el mismo")
    public MensajeRespuesta responder(@PathVariable Long id, @Valid @RequestBody EnviarMensajePeticion peticion) {
        return chat.responder(id, peticion.cuerpo(), peticion.adjuntoIds());
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Cerrar o reabrir la conversación")
    public ConversacionDetalleRespuesta cambiarEstado(@PathVariable Long id,
                                                      @Valid @RequestBody CambiarEstadoPeticion peticion) {
        return chat.cambiarEstado(id, peticion.activo());
    }

    @PostMapping(value = "/adjuntos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Subir un adjunto. Solo imagen o PDF, y el PDF se revisa antes")
    public Map<String, Object> subirAdjunto(@RequestParam("archivo") MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR, "Falta el archivo.");
        }
        try {
            var adjunto = adjuntos.subir(archivo.getBytes(), archivo.getOriginalFilename(), UsuarioActual.nombre());
            return Map.of(
                    "id", adjunto.getId(),
                    "nombre", adjunto.getNombreOriginal(),
                    "tipoMime", adjunto.getTipoMime(),
                    "bytes", adjunto.getBytes(),
                    "url", adjunto.getUrlPublica(),
                    "esImagen", adjunto.esImagen());
        } catch (IOException ex) {
            throw new ExcepcionAplicacion(CodigoError.MALFORMED_REQUEST, "No se pudo leer el archivo subido.");
        }
    }

    /**
     * Ticket para abrir el WebSocket.
     *
     * <p>El navegador no puede poner cabeceras en el apretón de manos de un
     * WebSocket, así que la credencial tiene que ir en la URL. Se emite un
     * ticket de un solo uso y treinta segundos en lugar de mandar allí el
     * token de acceso, que acabaría en los registros del servidor y del proxy.
     */
    @PostMapping("/ticket-ws")
    @Operation(summary = "Ticket de un solo uso para conectar el WebSocket")
    public Map<String, Object> ticket() {
        return Map.of(
                "ticket", tickets.emitir(UsuarioActual.nombre()),
                "expiraEnSegundos", tickets.segundosDeVigencia());
    }
}
