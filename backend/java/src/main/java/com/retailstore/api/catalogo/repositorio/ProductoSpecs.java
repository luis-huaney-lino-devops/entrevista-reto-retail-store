package com.retailstore.api.catalogo.repositorio;

import com.retailstore.api.catalogo.dominio.Producto;
import jakarta.persistence.criteria.JoinType;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Filtros combinables del listado de productos (RN-022). */
public final class ProductoSpecs {

    /**
     * Carácter de escape del LIKE. Se declara explícitamente en la consulta
     * porque el de por omisión depende del motor.
     */
    private static final char ESCAPE = '\\';

    private ProductoSpecs() {
    }

    public static Specification<Producto> estaActivo() {
        return (raiz, consulta, cb) -> cb.isTrue(raiz.get("activo"));
    }

    public static Specification<Producto> estaInactivo() {
        return (raiz, consulta, cb) -> cb.isFalse(raiz.get("activo"));
    }

    public static Specification<Producto> esDestacado() {
        return (raiz, consulta, cb) -> cb.isTrue(raiz.get("destacado"));
    }

    public static Specification<Producto> conStock() {
        return (raiz, consulta, cb) -> cb.greaterThan(raiz.get("stock"), 0);
    }

    /** Búsqueda de la tienda: nombre y descripción corta (RN-021). */
    public static Specification<Producto> coincideTexto(String termino) {
        String patron = "%" + escapar(termino.trim().toLowerCase(Locale.ROOT)) + "%";
        return (raiz, consulta, cb) -> cb.or(
                cb.like(cb.lower(raiz.get("nombre")), patron, ESCAPE),
                cb.like(cb.lower(cb.coalesce(raiz.get("descripcionCorta"), "")), patron, ESCAPE));
    }

    /** Búsqueda del panel: añade el SKU, que es lo que el administrador tiene a mano. */
    public static Specification<Producto> coincideTextoOSku(String termino) {
        String patron = "%" + escapar(termino.trim().toLowerCase(Locale.ROOT)) + "%";
        return (raiz, consulta, cb) -> cb.or(
                cb.like(cb.lower(raiz.get("nombre")), patron, ESCAPE),
                cb.like(cb.lower(raiz.get("sku")), patron, ESCAPE));
    }

    public static Specification<Producto> enSubcategoria(Long idSubcategoria) {
        return (raiz, consulta, cb) -> cb.equal(raiz.get("subcategoria").get("id"), idSubcategoria);
    }

    public static Specification<Producto> enSubcategoriaPorSlug(String slug) {
        return (raiz, consulta, cb) -> cb.equal(raiz.join("subcategoria", JoinType.INNER).get("slug"), slug);
    }

    public static Specification<Producto> enCategoriaPorSlug(String slug) {
        return (raiz, consulta, cb) -> cb.equal(
                raiz.join("subcategoria", JoinType.INNER).join("categoria", JoinType.INNER).get("slug"), slug);
    }

    public static Specification<Producto> deMarca(Long idMarca) {
        return (raiz, consulta, cb) -> cb.equal(raiz.get("marca").get("id"), idMarca);
    }

    public static Specification<Producto> deMarcaPorSlug(String slug) {
        return (raiz, consulta, cb) -> cb.equal(raiz.join("marca", JoinType.INNER).get("slug"), slug);
    }

    public static Specification<Producto> precioDesde(BigDecimal minimo) {
        return (raiz, consulta, cb) -> cb.greaterThanOrEqualTo(raiz.get("precio"), minimo);
    }

    public static Specification<Producto> precioHasta(BigDecimal maximo) {
        return (raiz, consulta, cb) -> cb.lessThanOrEqualTo(raiz.get("precio"), maximo);
    }

    /** Solo lo que la tienda puede mostrar: producto, subcategoría y categoría activos. */
    public static Specification<Producto> esVisibleEnTienda() {
        return (raiz, consulta, cb) -> {
            var subcategoria = raiz.join("subcategoria", JoinType.INNER);
            var categoria = subcategoria.join("categoria", JoinType.INNER);
            return cb.and(
                    cb.isTrue(raiz.get("activo")),
                    cb.isTrue(subcategoria.get("activa")),
                    cb.isTrue(categoria.get("activa")));
        };
    }

    /**
     * Los comodines que escribe el comprador son texto, no patrón (RN-026).
     *
     * <p>Sin escapar, buscar {@code %} devuelve el catálogo entero y buscar
     * {@code _} devuelve cualquier cosa con al menos un carácter. No es solo un
     * resultado raro: es un recorrido completo de tabla que cualquiera puede
     * provocar desde la barra de búsqueda.
     */
    static String escapar(String termino) {
        return termino
                .replace(String.valueOf(ESCAPE), ESCAPE + "" + ESCAPE)
                .replace("%", ESCAPE + "%")
                .replace("_", ESCAPE + "_");
    }
}
