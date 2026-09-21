package com.retailstore.api.carrito.web;

import com.retailstore.api.carrito.dto.AgregarItemPeticion;
import com.retailstore.api.carrito.dto.AplicarCuponPeticion;
import com.retailstore.api.carrito.dto.CambiarCantidadPeticion;
import com.retailstore.api.carrito.dto.CarritoRespuesta;
import com.retailstore.api.carrito.servicio.ServicioCarrito;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Carrito de la tienda. Público: comprar no exige cuenta.
 *
 * <p>Toda operación devuelve el carrito completo (RN-033).
 */
@RestController
@RequestMapping("/api/v1/carritos")
@Tag(name = "Carrito")
public class CarritoControlador {

    private final ServicioCarrito servicio;

    public CarritoControlador(ServicioCarrito servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crear un carrito vacío")
    public CarritoRespuesta crear() {
        return servicio.crear();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ver un carrito")
    public CarritoRespuesta porId(@PathVariable UUID id) {
        return servicio.porId(id);
    }

    @PostMapping("/{id}/items")
    @Operation(summary = "Agregar un producto o sumar a la línea existente")
    public CarritoRespuesta agregarItem(@PathVariable UUID id,
                                        @Valid @RequestBody AgregarItemPeticion peticion) {
        return servicio.agregarItem(id, peticion);
    }

    @PatchMapping("/{id}/items/{idItem}")
    @Operation(summary = "Cambiar la cantidad de una línea")
    public CarritoRespuesta cambiarCantidad(@PathVariable UUID id, @PathVariable Long idItem,
                                            @Valid @RequestBody CambiarCantidadPeticion peticion) {
        return servicio.cambiarCantidad(id, idItem, peticion.cantidad());
    }

    @DeleteMapping("/{id}/items/{idItem}")
    @Operation(summary = "Quitar una línea")
    public CarritoRespuesta eliminarItem(@PathVariable UUID id, @PathVariable Long idItem) {
        return servicio.eliminarItem(id, idItem);
    }

    @PostMapping("/{id}/cupon")
    @Operation(summary = "Aplicar un cupón. Un carrito admite como mucho uno")
    public CarritoRespuesta aplicarCupon(@PathVariable UUID id,
                                         @Valid @RequestBody AplicarCuponPeticion peticion) {
        return servicio.aplicarCupon(id, peticion.codigo());
    }

    @DeleteMapping("/{id}/cupon")
    @Operation(summary = "Quitar el cupón")
    public CarritoRespuesta quitarCupon(@PathVariable UUID id) {
        return servicio.quitarCupon(id);
    }
}
