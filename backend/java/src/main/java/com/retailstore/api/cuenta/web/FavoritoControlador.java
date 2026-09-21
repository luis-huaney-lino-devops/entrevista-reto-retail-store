package com.retailstore.api.cuenta.web;

import com.retailstore.api.catalogo.dto.ProductoResumenRespuesta;
import com.retailstore.api.cuenta.servicio.ServicioFavorito;
import com.retailstore.api.seguridad.dominio.ClienteAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lista de deseos.
 *
 * <p>{@code PUT} y no {@code POST} para añadir: lo que se pide es un estado
 * -«este producto está en mis favoritos»-, no un evento. Por eso repetirlo no
 * cambia nada y por eso las dos escrituras devuelven {@code 204}: el cliente ya
 * sabe cuál es el estado, lo acaba de fijar él.
 */
@RestController
@RequestMapping("/api/v1/cuenta/favoritos")
@Tag(name = "Favoritos (tienda)")
@SecurityRequirement(name = "tokenTienda")
public class FavoritoControlador {

    private final ServicioFavorito servicio;

    public FavoritoControlador(ServicioFavorito servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Los favoritos del cliente, del más reciente al más antiguo")
    public List<ProductoResumenRespuesta> listar(@AuthenticationPrincipal ClienteAutenticado cliente) {
        return servicio.listar(cliente.id());
    }

    @PutMapping("/{productoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Marcar un producto como favorito. Idempotente")
    public void agregar(@AuthenticationPrincipal ClienteAutenticado cliente, @PathVariable Long productoId) {
        servicio.agregar(cliente.id(), productoId);
    }

    @DeleteMapping("/{productoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Quitarlo de favoritos. Idempotente")
    public void quitar(@AuthenticationPrincipal ClienteAutenticado cliente, @PathVariable Long productoId) {
        servicio.quitar(cliente.id(), productoId);
    }
}
