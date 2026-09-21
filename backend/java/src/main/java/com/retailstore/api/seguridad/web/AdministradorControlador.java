package com.retailstore.api.seguridad.web;

import com.retailstore.api.comun.paginacion.RespuestaPagina;
import com.retailstore.api.seguridad.dto.ActualizarAdministradorPeticion;
import com.retailstore.api.seguridad.dto.AdministradorRespuesta;
import com.retailstore.api.seguridad.dto.CambiarContrasenaPeticion;
import com.retailstore.api.seguridad.dto.CrearAdministradorPeticion;
import com.retailstore.api.seguridad.dto.RestablecerContrasenaPeticion;
import com.retailstore.api.seguridad.servicio.ServicioAdministrador;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cuentas del panel.
 *
 * <p>Todo lo que gestiona a <em>otros</em> administradores exige
 * SUPERADMINISTRADOR. Lo que un administrador hace sobre su propia cuenta -ver
 * quién es, cambiarse la contraseña- no.
 */
@RestController
@RequestMapping("/api/v1/admin/administradores")
@Tag(name = "Administradores")
@SecurityRequirement(name = "tokenPanel")
@Validated
public class AdministradorControlador {

    private final ServicioAdministrador servicio;

    public AdministradorControlador(ServicioAdministrador servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/yo")
    @Operation(summary = "Quién soy")
    public AdministradorRespuesta yo(Principal principal) {
        return servicio.porUsuario(principal.getName());
    }

    @PostMapping("/yo/contrasena")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cambiar mi propia contraseña")
    public void cambiarMiContrasena(Principal principal, @Valid @RequestBody CambiarContrasenaPeticion peticion) {
        servicio.cambiarContrasenaPropia(principal.getName(), peticion);
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMINISTRADOR')")
    @Operation(summary = "Listado de administradores")
    public RespuestaPagina<AdministradorRespuesta> listar(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "debe ser al menos 1") int pagina,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "debe ser al menos 1")
            @Max(value = 100, message = "no puede superar 100") int tamanoPagina) {
        return servicio.listar(pagina, tamanoPagina);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('SUPERADMINISTRADOR')")
    @Operation(summary = "Crear un administrador")
    public AdministradorRespuesta crear(@Valid @RequestBody CrearAdministradorPeticion peticion) {
        return servicio.crear(peticion);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMINISTRADOR')")
    @Operation(summary = "Actualizar nombre, rol y estado")
    public AdministradorRespuesta actualizar(@PathVariable Long id,
                                             @Valid @RequestBody ActualizarAdministradorPeticion peticion) {
        return servicio.actualizar(id, peticion);
    }

    @PostMapping("/{id}/contrasena")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SUPERADMINISTRADOR')")
    @Operation(summary = "Restablecer la contraseña de otro administrador")
    public void restablecerContrasena(@PathVariable Long id,
                                      @Valid @RequestBody RestablecerContrasenaPeticion peticion) {
        servicio.restablecerContrasena(id, peticion.contrasenaNueva());
    }
}
