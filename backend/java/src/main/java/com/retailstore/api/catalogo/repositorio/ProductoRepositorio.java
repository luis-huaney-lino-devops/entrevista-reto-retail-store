package com.retailstore.api.catalogo.repositorio;

import com.retailstore.api.catalogo.dominio.Producto;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoRepositorio extends JpaRepository<Producto, Long>, JpaSpecificationExecutor<Producto> {

    boolean existsBySku(String sku);

    boolean existsBySlug(String slug);

    /**
     * El grafo trae subcategoría, categoría, marca e imágenes en una consulta.
     * Sin él, pintar una ficha de producto son cinco viajes a la base.
     */
    @EntityGraph(attributePaths = {"subcategoria", "subcategoria.categoria", "marca", "imagenes", "imagenes.archivo"})
    Optional<Producto> findBySlug(String slug);

    @EntityGraph(attributePaths = {"subcategoria", "subcategoria.categoria", "marca", "imagenes", "imagenes.archivo"})
    Optional<Producto> findConDetalleById(Long id);

    @EntityGraph(attributePaths = {"subcategoria", "subcategoria.categoria", "marca", "imagenes", "imagenes.archivo"})
    Optional<Producto> findBySlugAndActivoTrue(String slug);

    /**
     * Relacionados (RN-025): hasta 4 de la misma subcategoría, excluyendo el
     * actual. El desempate por id hace el orden total.
     */
    @Query("""
            select p from Producto p
             where p.subcategoria.id = :idSubcategoria
               and p.id <> :idProducto
               and p.activo = true
             order by p.calificacionPromedio desc, p.id asc""")
    List<Producto> relacionados(@Param("idSubcategoria") Long idSubcategoria,
                                @Param("idProducto") Long idProducto,
                                org.springframework.data.domain.Pageable limite);

    /**
     * Suma una visita sin cargar el producto ni tocar su version.
     *
     * <p>Un UPDATE directo y no {@code producto.setVistas(+1)}: cargar la
     * entidad haría que el bloqueo optimista considerara la visita una
     * modificación, y dos personas mirando la misma ficha a la vez se
     * producirían un conflicto entre ellas.
     *
     * <p>Con mucho tráfico esto es contención sobre la fila de los productos
     * populares. La salida entonces es acumular en memoria y volcar cada
     * cierto tiempo, no quitar la métrica.
     */
    @Modifying
    @Query("update Producto p set p.vistas = p.vistas + 1 where p.id = :id")
    void sumarVista(@Param("id") Long id);

    long countByActivoTrue();

    long countByActivoFalse();

    long countByActivoTrueAndStock(int stock);

    @Query("select coalesce(sum(p.vistas), 0) from Producto p")
    long totalVistas();

    @Query("select coalesce(sum(p.precio * p.stock), 0) from Producto p where p.activo = true")
    java.math.BigDecimal valorInventario();

    List<Producto> findTop8ByActivoTrueOrderByVistasDesc();

    List<Producto> findTop8ByActivoTrueAndStockLessThanOrderByStockAsc(int limite);

    long countBySubcategoriaId(Long idSubcategoria);

    long countByMarcaId(Long idMarca);
}
