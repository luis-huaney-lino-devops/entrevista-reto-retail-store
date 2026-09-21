package com.retailstore.api.seguridad.repositorio;

import com.retailstore.api.seguridad.dominio.Administrador;
import com.retailstore.api.seguridad.dominio.RolAdministrador;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdministradorRepositorio extends JpaRepository<Administrador, Long> {

    Optional<Administrador> findByUsuario(String usuario);

    boolean existsByUsuario(String usuario);

    Page<Administrador> findAllByOrderByUsuarioAsc(Pageable paginacion);

    /** Para no dejar la tienda sin nadie que pueda entrar al panel. */
    long countByActivoTrue();

    long countByRolAndActivoTrue(RolAdministrador rol);
}
