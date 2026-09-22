package com.retailstore.api.opinion.web;

import com.retailstore.api.comun.paginacion.RespuestaPagina;
import com.retailstore.api.opinion.dto.BusquedaOpinionPeticion;
import com.retailstore.api.opinion.dto.OpinionRespuesta;
import com.retailstore.api.opinion.servicio.ServicioOpinion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Las opiniones de un producto, sin autenticación.
 *
 * <p>Cuelga del producto -{@code /productos/{slug}/opiniones}- y no de un
 * recurso propio: una opinión suelta no significa nada, y la única pregunta que
 * hace la tienda es «qué se dice de esto». El slug, además, es lo que ya tiene
 * la ficha en la mano.
 *
 * <p>Escribir está en {@link OpinionCuentaControlador}, bajo {@code /cuenta}:
 * leer es público y escribir exige sesión (RN-091), y tenerlo en dos
 * controladores hace que eso se vea en la ruta en vez de en un {@code if}.
 */
@RestController
@RequestMapping("/api/v1/productos")
@Tag(name = "Opiniones (tienda)")
public class OpinionControlador {

    private final ServicioOpinion servicio;

    public OpinionControlador(ServicioOpinion servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/{slug}/opiniones")
    @Operation(summary = "Opiniones de un producto, paginadas. Orden: recientes, mejores o peores")
    public RespuestaPagina<OpinionRespuesta> deProducto(
            @PathVariable String slug,
            @Valid @ParameterObject BusquedaOpinionPeticion peticion) {
        return servicio.listarDeProducto(slug, peticion);
    }
}
