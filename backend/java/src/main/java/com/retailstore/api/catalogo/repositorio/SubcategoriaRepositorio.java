package com.retailstore.api.catalogo.repositorio;

import com.retailstore.api.catalogo.dominio.Subcategoria;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SubcategoriaRepositorio extends JpaRepository<Subcategoria, Long> {

    @EntityGraph(attributePaths = "categoria")
    Optional<Subcategoria> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByCategoriaIdAndNombreIgnoreCase(Long idCategoria, String nombre);

    /** Igual, pero ignorando la fila que se está editando. */
    boolean existsByCategoriaIdAndNombreIgnoreCaseAndIdNot(Long idCategoria, String nombre, Long id);

    @EntityGraph(attributePaths = "categoria")
    List<Subcategoria> findAllByCategoriaIdOrderByOrdenAscNombreAsc(Long idCategoria);

    /** Menú de la tienda: solo subcategorías activas de categorías activas. */
    @EntityGraph(attributePaths = "categoria")
    @Query("""
            select s from Subcategoria s
             where s.activa = true and s.categoria.activa = true
             order by s.categoria.orden asc, s.categoria.nombre asc, s.orden asc, s.nombre asc""")
    List<Subcategoria> menuVisible();

    @EntityGraph(attributePaths = "categoria")
    Page<Subcategoria> findAllByOrderByCategoriaNombreAscOrdenAscNombreAsc(Pageable paginacion);

    @Query("select count(p) from Producto p where p.subcategoria.id = ?1 and p.activo = true")
    long contarProductosActivos(Long idSubcategoria);

    @Query("select p.nombre from Producto p where p.subcategoria.id = ?1 and p.activo = true order by p.nombre")
    List<String> nombresDeProductosActivos(Long idSubcategoria, Pageable limite);
}
