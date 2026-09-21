package com.retailstore.api.ubigeo.repositorio;

import com.retailstore.api.ubigeo.dominio.Departamento;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartamentoRepositorio extends JpaRepository<Departamento, Short> {

    List<Departamento> findAllByOrderByNombreAsc();
}
