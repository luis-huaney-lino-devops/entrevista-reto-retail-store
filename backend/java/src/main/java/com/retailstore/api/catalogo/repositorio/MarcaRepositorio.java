package com.retailstore.api.catalogo.repositorio;

import com.retailstore.api.catalogo.dominio.Marca;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MarcaRepositorio extends JpaRepository<Marca, Long> {

    Optional<Marca> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** Para la tienda: solo lo publicable. */
    List<Marca> findAllByActivaTrueOrderByNombreAsc();

    Page<Marca> findAllByOrderByNombreAsc(Pageable paginacion);

    Page<Marca> findByNombreContainingIgnoreCaseOrderByNombreAsc(String nombre, Pageable paginacion);

    /**
     * RN-011: no se desactiva lo que tiene contenido activo. Se cuenta en la
     * base y no cargando los productos: la cifra es lo único que hace falta.
     */
    @Query("select count(p) from Producto p where p.marca.id = ?1 and p.activo = true")
    long contarProductosActivos(Long idMarca);

    @Query("select p.nombre from Producto p where p.marca.id = ?1 and p.activo = true order by p.nombre")
    List<String> nombresDeProductosActivos(Long idMarca, Pageable limite);
}
