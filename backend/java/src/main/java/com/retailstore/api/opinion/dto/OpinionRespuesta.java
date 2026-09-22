package com.retailstore.api.opinion.dto;

import com.retailstore.api.opinion.dominio.Opinion;
import java.time.Instant;
import java.util.Locale;

/**
 * Una opinión tal como la lee la tienda.
 *
 * <p><strong>El autor va abreviado</strong> -«María Q.»- y no con su nombre
 * completo. Publicar el nombre y el apellido de alguien junto a lo que compró
 * es un dato personal que esa persona no ha decidido publicar, y la utilidad de
 * la reseña no cambia en nada. Es lo mismo que hacen las tiendas grandes, y por
 * el mismo motivo.
 *
 * <p>Sin correo, sin identificador de cliente y sin la fecha de la compra: son
 * tres formas de cruzar quién compró qué desde un endpoint público.
 *
 * @param editada calculado, no almacenado: {@code actualizadoEn} distinto de
 *                {@code creadoEn}. La tienda lo enseña porque un texto que
 *                cambió después de publicarse es otra cosa
 */
public record OpinionRespuesta(
        Long id,
        Long productoId,
        int calificacion,
        String titulo,
        String cuerpo,
        boolean compraVerificada,
        String autor,
        Instant creadoEn,
        Instant actualizadoEn,
        boolean editada) {

    public static OpinionRespuesta de(Opinion opinion) {
        return new OpinionRespuesta(
                opinion.getId(),
                opinion.getProducto().getId(),
                opinion.getCalificacion(),
                opinion.getTitulo(),
                opinion.getCuerpo(),
                opinion.isCompraVerificada(),
                autorAbreviado(opinion.getCliente().getNombre()),
                opinion.getCreadoEn(),
                opinion.getActualizadoEn(),
                opinion.fueEditada());
    }

    /**
     * «María Quispe Rojas» -> «María Q.». Un solo nombre se queda como está, y
     * lo vacío -que la base no admite- se anuncia como anónimo en vez de
     * devolver una cadena en blanco que la tienda tendría que interpretar.
     */
    private static String autorAbreviado(String nombre) {
        String limpio = nombre == null ? "" : nombre.trim();
        if (limpio.isEmpty()) {
            return "Cliente";
        }
        String[] partes = limpio.split("\s+");
        if (partes.length == 1) {
            return partes[0];
        }
        return partes[0] + " " + partes[1].substring(0, 1).toUpperCase(Locale.ROOT) + ".";
    }
}
