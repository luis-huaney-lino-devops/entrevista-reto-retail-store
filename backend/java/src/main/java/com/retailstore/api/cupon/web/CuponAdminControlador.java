package com.retailstore.api.cupon.web;

import com.retailstore.api.comun.paginacion.RespuestaPagina;
import com.retailstore.api.cupon.dto.CuponPeticion;
import com.retailstore.api.cupon.dto.CuponRespuesta;
import com.retailstore.api.cupon.servicio.ServicioCupon;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/cupones")
@Tag(name = "Cupones (panel)")
@SecurityRequirement(name = "tokenPanel")
@Validated
public class CuponAdminControlador {

    private final ServicioCupon servicio;

    public CuponAdminControlador(ServicioCupon servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Listado de cupones")
    public RespuestaPagina<CuponRespuesta> listar(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser al menos 1") int pagina,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "debe ser al menos 1")
            @Max(value = 100, message = "no puede superar 100") int tamanoPagina) {
        return servicio.listar(pagina, tamanoPagina);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un cupón")
    public CuponRespuesta porId(@PathVariable Long id) {
        return servicio.porId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un cupón")
    public CuponRespuesta crear(@Valid @RequestBody CuponPeticion peticion) {
        return servicio.crear(peticion);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar un cupón. El código no cambia")
    public CuponRespuesta actualizar(@PathVariable Long id, @Valid @RequestBody CuponPeticion peticion) {
        return servicio.actualizar(id, peticion);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminación lógica. El código no se libera: aparece en órdenes pasadas")
    public void eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
    }
}
