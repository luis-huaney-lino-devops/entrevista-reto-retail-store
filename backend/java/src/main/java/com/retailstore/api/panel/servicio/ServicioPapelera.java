package com.retailstore.api.panel.servicio;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La papelera: qué se eliminó, quién lo eliminó y cómo recuperarlo.
 *
 * <p>Sin esta pantalla, la eliminación lógica es una promesa que nadie puede
 * comprobar: el dato está en la tabla, pero no hay forma de verlo ni de
 * deshacerlo, y el efecto práctico es el mismo que un borrado de verdad.
 *
 * <p><strong>Por qué SQL nativo.</strong> Las entidades llevan
 * {@code @SQLRestriction("eliminado_en is null")}, que filtra lo eliminado en
 * toda consulta de JPA —que es justo lo que se quiere en el otro 99% del
 * código—. Esta pantalla necesita exactamente lo contrario, así que baja un
 * nivel. La alternativa sería quitar el filtro automático y acordarse de poner
 * la condición en cada consulta del sistema; el día que alguien se olvide, un
 * producto eliminado reaparece en la tienda.
 */
@Service
@Transactional(readOnly = true)
public class ServicioPapelera {

    /**
     * Qué se puede recuperar, y de dónde.
     *
     * <p>El nombre de tabla y de columna no viene de fuera: se elige de este
     * mapa a partir del tipo que pide el cliente. Concatenar un nombre de tabla
     * recibido por parámetro sería una inyección de SQL con otro nombre.
     */
    private static final Map<String, Recurso> RECURSOS = new LinkedHashMap<>(Map.of(
            "producto", new Recurso("producto", "id_producto", "nombre", "sku", "Producto", "/productos/"),
            "marca", new Recurso("marca", "id_marca", "nombre", "slug", "Marca", "/marcas"),
            "categoria", new Recurso("categoria", "id_categoria", "nombre", "slug", "Categoría", "/categorias"),
            "subcategoria", new Recurso("subcategoria", "id_subcategoria", "nombre", "slug", "Subcategoría", "/subcategorias"),
            "cupon", new Recurso("cupon", "id_cupon", "codigo", "codigo", "Cupón", "/cupones")));

    private record Recurso(String tabla, String columnaId, String columnaNombre, String columnaDetalle,
                           String etiqueta, String ruta) {
    }

    public record ElementoEliminado(String tipo, String etiquetaTipo, Long id, String nombre, String detalle,
                                    Instant eliminadoEn, String eliminadoPor) {
    }

    @PersistenceContext
    private EntityManager em;

    public List<ElementoEliminado> listar() {
        List<ElementoEliminado> elementos = new java.util.ArrayList<>();

        for (Map.Entry<String, Recurso> entrada : RECURSOS.entrySet()) {
            Recurso recurso = entrada.getValue();
            String sql = """
                    select %s, %s, %s, eliminado_en, eliminado_por
                      from %s
                     where eliminado_en is not null
                     order by eliminado_en desc
                     limit 100
                    """.formatted(recurso.columnaId(), recurso.columnaNombre(),
                    recurso.columnaDetalle(), recurso.tabla());

            @SuppressWarnings("unchecked")
            List<Object[]> filas = em.createNativeQuery(sql).getResultList();
            for (Object[] fila : filas) {
                elementos.add(new ElementoEliminado(
                        entrada.getKey(),
                        recurso.etiqueta(),
                        ((Number) fila[0]).longValue(),
                        (String) fila[1],
                        (String) fila[2],
                        aInstante(fila[3]),
                        (String) fila[4]));
            }
        }

        // Lo último que se borró es lo que más probablemente se borró por error.
        elementos.sort((a, b) -> b.eliminadoEn().compareTo(a.eliminadoEn()));
        return elementos;
    }

    /**
     * Devuelve un elemento a la vida.
     *
     * <p>Vuelve <strong>desactivado</strong>: lo que se recupera no se publica
     * solo. Quien lo restaura decide cuándo vuelve a verse en la tienda, y
     * mientras tanto puede revisar que siga teniendo sentido.
     */
    @Transactional
    public ElementoEliminado restaurar(String tipo, Long id) {
        Recurso recurso = RECURSOS.get(tipo == null ? "" : tipo.toLowerCase(java.util.Locale.ROOT));
        if (recurso == null) {
            throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR,
                    "'" + tipo + "' no es un tipo que se pueda restaurar.");
        }

        String columnaActivo = "producto".equals(recurso.tabla()) || "cupon".equals(recurso.tabla())
                ? "activo" : "activa";

        int filas = em.createNativeQuery("""
                        update %s
                           set eliminado_en = null, eliminado_por = null, %s = false
                         where %s = :id and eliminado_en is not null
                        """.formatted(recurso.tabla(), columnaActivo, recurso.columnaId()))
                .setParameter("id", id)
                .executeUpdate();

        if (filas == 0) {
            throw new ExcepcionAplicacion(CodigoError.RESOURCE_NOT_FOUND,
                    "No hay nada eliminado con ese identificador.");
        }

        // El contexto de persistencia no se entera de un update nativo: sin
        // limpiarlo, una lectura posterior en la misma transacción devolvería
        // la versión en caché, todavía marcada como eliminada.
        em.clear();

        return new ElementoEliminado(tipo, recurso.etiqueta(), id, null, null, null, null);
    }

    private static Instant aInstante(Object valor) {
        if (valor instanceof Instant instante) {
            return instante;
        }
        if (valor instanceof Timestamp marca) {
            return marca.toInstant();
        }
        if (valor instanceof java.time.OffsetDateTime fecha) {
            return fecha.toInstant();
        }
        throw new IllegalStateException("Tipo de fecha inesperado: " + valor.getClass());
    }
}
