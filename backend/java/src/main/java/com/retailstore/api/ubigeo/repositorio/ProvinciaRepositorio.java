package com.retailstore.api.ubigeo.repositorio;

import com.retailstore.api.ubigeo.dominio.Provincia;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProvinciaRepositorio extends JpaRepository<Provincia, Short> {

    List<Provincia> findByDepartamentoIdOrderByNombreAsc(Short idDepartamento);
}
