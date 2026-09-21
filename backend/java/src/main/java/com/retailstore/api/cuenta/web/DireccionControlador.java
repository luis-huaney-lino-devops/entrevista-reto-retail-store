package com.retailstore.api.cuenta.web;

import com.retailstore.api.cuenta.dto.DireccionPeticion;
import com.retailstore.api.cuenta.dto.DireccionRespuesta;
import com.retailstore.api.cuenta.servicio.ServicioDireccion;
import com.retailstore.api.seguridad.dominio.ClienteAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Direcciones de entrega del cliente que trae el token.
 *
 * <p>Ninguna ruta lleva el id del cliente: el dueño sale del token y nunca de
 * la petición. Una ruta del estilo {@code /clientes/{id}/direcciones} obligaría
 * a comprobar en cada método que ese id es el de la sesión, y el día que
 * alguien olvide la comprobación, cualquiera lee las direcciones de cualquiera.
 */
@RestController
@RequestMapping("/api/v1/cuenta/direcciones")
@Tag(name = "Direcciones (tienda)")
@SecurityRequirement(name = "tokenTienda")
public class DireccionControlador {

    private final ServicioDireccion servicio;

    public DireccionControlador(ServicioDireccion servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Las direcciones del cliente, la predeterminada primero")
    public List<DireccionRespuesta> listar(@AuthenticationPrincipal ClienteAutenticado cliente) {
        return servicio.listar(cliente.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Añadir una dirección. La primera queda predeterminada siempre")
    public DireccionRespuesta crear(@AuthenticationPrincipal ClienteAutenticado cliente,
                                    @Valid @RequestBody DireccionPeticion peticion) {
        return servicio.crear(cliente.id(), peticion);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar una dirección")
    public DireccionRespuesta actualizar(@AuthenticationPrincipal ClienteAutenticado cliente,
                                         @PathVariable Long id,
                                         @Valid @RequestBody DireccionPeticion peticion) {
        return servicio.actualizar(cliente.id(), id, peticion);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar una dirección. Es lógica (RN-086): las órdenes antiguas siguen explicándose")
    public void eliminar(@AuthenticationPrincipal ClienteAutenticado cliente, @PathVariable Long id) {
        servicio.eliminar(cliente.id(), id);
    }

    @PostMapping("/{id}/predeterminada")
    @Operation(summary = "Marcarla como predeterminada. Desmarca la anterior")
    public DireccionRespuesta predeterminada(@AuthenticationPrincipal ClienteAutenticado cliente,
                                             @PathVariable Long id) {
        return servicio.marcarPredeterminada(cliente.id(), id);
    }
}
