package com.retailstore.api.cuenta.servicio;

import com.retailstore.api.catalogo.dominio.Producto;
import com.retailstore.api.catalogo.dto.ProductoResumenRespuesta;
import com.retailstore.api.catalogo.repositorio.ProductoRepositorio;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.cuenta.repositorio.FavoritoRepositorio;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La lista de deseos.
 *
 * <p><strong>Las dos escrituras son idempotentes</strong>, y eso es contrato y
 * no cortesía: el corazón de una tarjeta se pulsa dos veces por un doble clic,
 * por un reintento del móvil al perder cobertura o por dos pestañas abiertas.
 * Un {@code 409} en cualquiera de esos casos sería un error que el usuario no
 * cometió.
 *
 * <p>El alta la resuelve la base con {@code on conflict do nothing} y no un
 * «mira si existe y si no insértalo»: dos peticiones simultáneas pasan las dos
 * esa comprobación y la segunda revienta contra {@code uq_favorito}.
 */
@Service
@Transactional
public class ServicioFavorito {

    private final FavoritoRepositorio favoritos;
    private final ProductoRepositorio productos;
    private final Clock reloj;

    public ServicioFavorito(FavoritoRepositorio favoritos, ProductoRepositorio productos, Clock reloj) {
        this.favoritos = favoritos;
        this.productos = productos;
        this.reloj = reloj;
    }

    /**
     * Los favoritos, del más reciente al más antiguo y con la misma forma que
     * cualquier otra rejilla de la tienda.
     *
     * <p>Se filtra por producto visible. Un producto despublicado es 404 en la
     * tienda, así que devolverlo aquí sería pintar una tarjeta que lleva a
     * ningún sitio. La fila del favorito se conserva: si vuelve a publicarse,
     * reaparece.
     */
    @Transactional(readOnly = true)
    public List<ProductoResumenRespuesta> listar(Long idCliente) {
        List<Long> ids = favoritos.idsProductoDe(idCliente);
        if (ids.isEmpty()) {
            return List.of();
        }

        // Dos consultas y no un join: traer el producto con su grafo completo
        // -marca, subcategoría, imágenes- obligaría a un distinct incompatible
        // con ordenar por la fecha del favorito, que no está en el select.
        Map<Long, Producto> porId = productos.findByIdInAndActivoTrue(ids).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));

        return ids.stream()
                .map(porId::get)
                .filter(Objects::nonNull)
                .map(ProductoResumenRespuesta::de)
                .toList();
    }

    /** Repetirlo no duplica ni falla. */
    public void agregar(Long idCliente, Long idProducto) {
        // El producto se comprueba antes de insertar para que un id inventado
        // sea un 404 legible y no una violación de clave foránea traducida a
        // un 500. Visible, además: no se puede desear lo que no se puede ver.
        productos.findByIdAndActivoTrue(idProducto)
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.PRODUCT_NOT_FOUND,
                        "No encontramos ese producto."));

        favoritos.agregarSiNoExiste(idCliente, idProducto, reloj.instant());
    }

    /** Quitar algo que no estaba también es éxito: el estado final es el pedido. */
    public void quitar(Long idCliente, Long idProducto) {
        favoritos.quitar(idCliente, idProducto);
    }
}
