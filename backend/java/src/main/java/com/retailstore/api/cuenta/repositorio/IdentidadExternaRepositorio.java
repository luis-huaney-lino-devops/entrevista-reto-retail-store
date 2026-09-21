package com.retailstore.api.cuenta.repositorio;

import com.retailstore.api.cuenta.dominio.IdentidadExterna;
import com.retailstore.api.cuenta.dominio.ProveedorIdentidad;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentidadExternaRepositorio extends JpaRepository<IdentidadExterna, Long> {

    /**
     * La consulta del acceso con Google: por proveedor y sujeto, nunca por
     * correo. El cliente viene en el mismo viaje porque lo siguiente que se
     * hace con la identidad es emitirle un token.
     */
    @EntityGraph(attributePaths = "cliente")
    Optional<IdentidadExterna> findByProveedorAndSujeto(ProveedorIdentidad proveedor, String sujeto);

    boolean existsByClienteIdAndProveedor(Long idCliente, ProveedorIdentidad proveedor);
}
