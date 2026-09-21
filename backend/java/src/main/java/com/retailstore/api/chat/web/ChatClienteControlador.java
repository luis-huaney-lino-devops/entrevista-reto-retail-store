package com.retailstore.api.chat.web;

import com.retailstore.api.chat.dto.ConversacionDetalleRespuesta;
import com.retailstore.api.chat.dto.EnviarMensajePeticion;
import com.retailstore.api.chat.dto.MensajeRespuesta;
import com.retailstore.api.chat.servicio.ServicioAdjunto;
import com.retailstore.api.chat.servicio.ServicioChat;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * El lado del cliente del chat.
 *
 * <p>Público y sin sesión: se identifica con el token del hilo, igual que el
 * carrito se identifica con su UUID. Quien tiene el enlace puede escribir en
 * esa conversación y en ninguna otra.
 *
 * <p>Lo consumirá la tienda cuando exista. Que esté ya permite probar el chat
 * de extremo a extremo y deja el contrato fijado.
 */
@RestController
@RequestMapping("/api/v1/conversaciones")
@Tag(name = "Chat (tienda)")
public class ChatClienteControlador {

    private final ServicioChat chat;
    private final ServicioAdjunto adjuntos;

    public ChatClienteControlador(ServicioChat chat, ServicioAdjunto adjuntos) {
        this.chat = chat;
        this.adjuntos = adjuntos;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Ver la conversación con su token de acceso")
    public ConversacionDetalleRespuesta ver(@PathVariable String token) {
        return chat.porToken(token);
    }

    @PostMapping("/{token}/mensajes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Escribir como cliente")
    public MensajeRespuesta escribir(@PathVariable String token,
                                     @Valid @RequestBody EnviarMensajePeticion peticion) {
        return chat.escribirComoCliente(token, peticion.cuerpo(), peticion.adjuntoIds());
    }

    @PostMapping(value = "/{token}/adjuntos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Adjuntar imagen o PDF. Se valida el contenido, no la extensión")
    public Map<String, Object> subirAdjunto(@PathVariable String token,
                                            @RequestParam("archivo") MultipartFile archivo) {
        // Se comprueba primero que el token exista: si no, subir sería un
        // canal abierto para llenar el almacén desde fuera.
        var conversacion = chat.porToken(token);
        if (archivo == null || archivo.isEmpty()) {
            throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR, "Falta el archivo.");
        }
        try {
            var adjunto = adjuntos.subir(
                    archivo.getBytes(),
                    archivo.getOriginalFilename(),
                    conversacion.conversacion().clienteNombre());
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
}
