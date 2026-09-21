package com.retailstore.api.catalogo.repositorio;

import com.retailstore.api.catalogo.dominio.Categoria;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoriaRepositorio extends JpaRepository<Categoria, Long> {

    Optional<Categoria> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /**
     * Orden explícito y luego nombre: el desempate hace que el listado sea
     * total, y sin orden total una consulta paginada puede repetir o saltarse
     * filas entre páginas.
     */
    List<Categoria> findAllByActivaTrueOrderByOrdenAscNombreAsc();

    Page<Categoria> findAllByOrderByOrdenAscNombreAsc(Pageable paginacion);

    @Query("select count(s) from Subcategoria s where s.categoria.id = ?1 and s.activa = true")
    long contarSubcategoriasActivas(Long idCategoria);

    @Query("select s.nombre from Subcategoria s where s.categoria.id = ?1 and s.activa = true order by s.nombre")
    List<String> nombresDeSubcategoriasActivas(Long idCategoria, Pageable limite);
}
