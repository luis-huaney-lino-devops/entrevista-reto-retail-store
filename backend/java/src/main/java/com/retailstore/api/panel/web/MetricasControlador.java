package com.retailstore.api.panel.web;

import com.retailstore.api.panel.dto.MetricasRespuesta;
import com.retailstore.api.panel.servicio.ServicioMetricas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/metricas")
@Tag(name = "Tablero (panel)")
@SecurityRequirement(name = "tokenPanel")
public class MetricasControlador {

    private final ServicioMetricas servicio;

    public MetricasControlador(ServicioMetricas servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Todas las cifras del tablero en una sola petición")
    public MetricasRespuesta metricas() {
        return servicio.calcular();
    }
}
