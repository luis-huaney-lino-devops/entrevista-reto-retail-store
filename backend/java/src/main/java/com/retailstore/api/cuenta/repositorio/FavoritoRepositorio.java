package com.retailstore.api.cuenta.repositorio;

import com.retailstore.api.cuenta.dominio.Favorito;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FavoritoRepositorio extends JpaRepository<Favorito, Long> {

    /**
     * Ids en el orden en que se marcaron, del más reciente al más antiguo.
     *
     * <p>Solo los ids: los productos se traen después con su grafo completo.
     * Un {@code join fetch} aquí obligaría a un {@code distinct} incompatible
     * con ordenar por una columna del favorito, que no está en el select.
     */
    @Query("""
            select f.producto.id from Favorito f
             where f.cliente.id = :idCliente
             order by f.creadoEn desc, f.id desc""")
    List<Long> idsProductoDe(@Param("idCliente") Long idCliente);

    /**
     * Alta idempotente, resuelta por la base y no por un «mira si existe y si
     * no insértalo»: dos pulsaciones simultáneas del corazón pasan las dos esa
     * comprobación y la segunda revienta contra {@code uq_favorito}. El
     * {@code on conflict} convierte esa carrera en un no-op.
     */
    @Modifying
    @Query(value = """
            insert into favorito (fk_id_cliente, fk_id_producto, creado_en)
            values (:idCliente, :idProducto, :momento)
            on conflict (fk_id_cliente, fk_id_producto) do nothing""", nativeQuery = true)
    int agregarSiNoExiste(@Param("idCliente") Long idCliente,
                          @Param("idProducto") Long idProducto,
                          @Param("momento") Instant momento);

    /** Devuelve cuántas filas quitó; con cero, quitar algo que no estaba también es éxito. */
    @Modifying
    @Query("delete from Favorito f where f.cliente.id = :idCliente and f.producto.id = :idProducto")
    int quitar(@Param("idCliente") Long idCliente, @Param("idProducto") Long idProducto);

    boolean existsByClienteIdAndProductoId(Long idCliente, Long idProducto);
}
