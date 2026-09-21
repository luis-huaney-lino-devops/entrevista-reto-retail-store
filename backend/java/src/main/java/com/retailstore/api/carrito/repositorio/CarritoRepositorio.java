package com.retailstore.api.carrito.repositorio;

import com.retailstore.api.carrito.dominio.Carrito;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarritoRepositorio extends JpaRepository<Carrito, UUID> {

    /**
     * Trae el carrito con sus líneas, sus productos y el cupón en una consulta.
     * Toda operación sobre el carrito devuelve el carrito completo (RN-033), así
     * que siempre hacen falta.
     */
    @EntityGraph(attributePaths = {"items", "items.producto", "items.producto.subcategoria", "cupon"})
    Optional<Carrito> findConDetalleById(UUID id);
}
