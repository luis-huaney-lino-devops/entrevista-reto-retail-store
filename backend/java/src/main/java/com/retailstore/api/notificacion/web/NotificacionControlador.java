package com.retailstore.api.notificacion.web;

import com.retailstore.api.comun.paginacion.RespuestaPagina;
import com.retailstore.api.notificacion.dto.NotificacionRespuesta;
import com.retailstore.api.notificacion.servicio.ServicioNotificacion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notificaciones")
@Tag(name = "Notificaciones (panel)")
@SecurityRequirement(name = "tokenPanel")
@Validated
public class NotificacionControlador {

    private final ServicioNotificacion servicio;

    public NotificacionControlador(ServicioNotificacion servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Lo pendiente y cuántas hay. Es lo que pinta la campana")
    public ServicioNotificacion.Bandeja bandeja() {
        return servicio.bandeja();
    }

    @GetMapping("/historial")
    @Operation(summary = "Todas, leídas incluidas")
    public RespuestaPagina<NotificacionRespuesta> historial(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser al menos 1") int pagina,
            @RequestParam(defaultValue = "30")
            @Min(value = 1, message = "debe ser al menos 1")
            @Max(value = 100, message = "no puede superar 100") int tamanoPagina) {
        return servicio.historial(pagina, tamanoPagina);
    }

    @PostMapping("/{id}/leida")
    @Operation(summary = "Marcar una como leída")
    public Map<String, Long> marcarLeida(@PathVariable Long id) {
        return Map.of("pendientes", servicio.marcarLeida(id));
    }

    @PostMapping("/leidas")
    @Operation(summary = "Marcar todas como leídas")
    public Map<String, Long> marcarTodas() {
        return Map.of("pendientes", servicio.marcarTodasLeidas());
    }
}
