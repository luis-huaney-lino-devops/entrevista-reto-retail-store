package com.retailstore.api.opinion.web;

import com.retailstore.api.opinion.dto.ActualizarOpinionPeticion;
import com.retailstore.api.opinion.dto.CrearOpinionPeticion;
import com.retailstore.api.opinion.dto.OpinionRespuesta;
import com.retailstore.api.opinion.servicio.ServicioOpinion;
import com.retailstore.api.seguridad.dominio.ClienteAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
 * Lo que un cliente hace con su propia opinión.
 *
 * <p>Ninguna ruta lleva el identificador del autor: sale del token y nunca de
 * la petición. Una ruta del estilo {@code /clientes/{id}/opiniones} obligaría a
 * comprobar en cada método que ese id es el de la sesión, y el día que alguien
 * olvide la comprobación cualquiera reescribe la opinión de cualquiera.
 */
@RestController
@RequestMapping("/api/v1/cuenta/opiniones")
@Tag(name = "Opiniones (tienda)")
@SecurityRequirement(name = "tokenTienda")
public class OpinionCuentaControlador {

    private final ServicioOpinion servicio;

    public OpinionCuentaControlador(ServicioOpinion servicio) {
        this.servicio = servicio;
    }

    /**
     * Sirve para que la ficha sepa si ofrecer «escribe la tuya» o «edita la
     * tuya». Sin ella, el formulario se enviaría a ciegas y el {@code 409} de
     * RN-090 sería la forma de enterarse.
     */
    @GetMapping("/producto/{productoId}")
    @Operation(summary = "Mi opinión sobre un producto. 404 si todavía no escribí ninguna")
    public OpinionRespuesta mia(@AuthenticationPrincipal ClienteAutenticado cliente,
                                @PathVariable Long productoId) {
        return servicio.miOpinionDe(cliente.id(), productoId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Escribir una opinión. Una por producto (RN-090)")
    public OpinionRespuesta crear(@AuthenticationPrincipal ClienteAutenticado cliente,
                                  @Valid @RequestBody CrearOpinionPeticion peticion) {
        return servicio.crear(cliente.id(), peticion);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Editar la opinión propia. La de otra persona es 404, no 403")
    public OpinionRespuesta actualizar(@AuthenticationPrincipal ClienteAutenticado cliente,
                                       @PathVariable Long id,
                                       @Valid @RequestBody ActualizarOpinionPeticion peticion) {
        return servicio.actualizar(cliente.id(), id, peticion);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retirar la opinión propia. Es lógica (RN-094) y libera el sitio para otra")
    public void eliminar(@AuthenticationPrincipal ClienteAutenticado cliente, @PathVariable Long id) {
        servicio.eliminar(cliente.id(), id);
    }
}
