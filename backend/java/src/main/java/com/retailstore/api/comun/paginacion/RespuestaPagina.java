package com.retailstore.api.comun.paginacion;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Sobre de paginación del contrato.
 *
 * <p>La página es 1-based hacia afuera y 0-based hacia dentro: {@code ?pagina=1}
 * es la primera. Exponer el índice de Spring obligaría a cada cliente -y a cada
 * enlace compartido- a restar uno.
 */
public record RespuestaPagina<T>(List<T> items, int pagina, int tamanoPagina, long totalItems, int totalPaginas) {

    public static <E, T> RespuestaPagina<T> de(Page<E> pagina, Function<E, T> mapeador) {
        return new RespuestaPagina<>(
                pagina.getContent().stream().map(mapeador).toList(),
                pagina.getNumber() + 1,
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }
}
