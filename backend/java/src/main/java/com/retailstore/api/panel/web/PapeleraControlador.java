package com.retailstore.api.panel.web;

import com.retailstore.api.panel.servicio.ServicioPapelera;
import com.retailstore.api.panel.servicio.ServicioPapelera.ElementoEliminado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/papelera")
@Tag(name = "Papelera (panel)")
@SecurityRequirement(name = "tokenPanel")
public class PapeleraControlador {

    private final ServicioPapelera servicio;

    public PapeleraControlador(ServicioPapelera servicio) {
        this.servicio = servicio;
    }

    public record RestaurarPeticion(
            @NotBlank(message = "es obligatorio") String tipo,
            @NotNull(message = "es obligatorio") Long id) {
    }

    @GetMapping
    @Operation(summary = "Todo lo eliminado, con quién lo eliminó y cuándo")
    public List<ElementoEliminado> listar() {
        return servicio.listar();
    }

    @PostMapping("/restaurar")
    @Operation(summary = "Recuperar un elemento. Vuelve desactivado, no se publica solo")
    public ElementoEliminado restaurar(@jakarta.validation.Valid @RequestBody RestaurarPeticion peticion) {
        return servicio.restaurar(peticion.tipo(), peticion.id());
    }
}
