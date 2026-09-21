package com.retailstore.api.cupon.repositorio;

import com.retailstore.api.cupon.dominio.Cupon;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuponRepositorio extends JpaRepository<Cupon, Long> {

    Optional<Cupon> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    Page<Cupon> findAllByOrderByIdDesc(Pageable paginacion);
}
