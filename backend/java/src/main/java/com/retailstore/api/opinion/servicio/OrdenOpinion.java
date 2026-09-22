package com.retailstore.api.opinion.servicio;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.comun.error.ManejadorGlobalErrores.ErrorCampo;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;

/**
 * Cómo se pueden ordenar las opiniones de un producto.
 *
 * <p>Lista blanca, igual que en el catálogo (RN-023): aceptar un nombre de
 * propiedad libre deja ordenar por columnas sin índice, o por columnas que no
 * deberían salir de la base.
 *
 * <p>Las tres desempatan por identificador descendente, que con una clave
 * secuencial es «la más nueva primero». Sin desempate, dos opiniones escritas
 * el mismo día pueden salir en distinto orden en cada consulta, y entonces la
 * página 2 repite una fila de la 1 o se salta otra.
 */
public enum OrdenOpinion {

    RECIENTES("recientes", Sort.by(Sort.Direction.DESC, "creadoEn")),
    MEJORES("mejores", Sort.by(Sort.Direction.DESC, "calificacion")),
    PEORES("peores", Sort.by(Sort.Direction.ASC, "calificacion"));

    private final String parametro;
    private final Sort orden;

    OrdenOpinion(String parametro, Sort orden) {
        this.parametro = parametro;
        this.orden = orden;
    }

    public String parametro() {
        return parametro;
    }

    public Sort orden() {
        return orden.and(Sort.by(Sort.Direction.DESC, "id"));
    }

    public static OrdenOpinion desde(String parametro) {
        if (parametro == null || parametro.isBlank()) {
            return RECIENTES;
        }
        String buscado = parametro.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(valor -> valor.parametro.equals(buscado))
                .findFirst()
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR,
                        "La petición contiene 1 campo inválido.")
                        .con("errors", List.of(new ErrorCampo("orden",
                                "debe ser uno de: " + parametrosAdmitidos()))));
    }

    public static String parametrosAdmitidos() {
        return String.join(", ", Arrays.stream(values()).map(OrdenOpinion::parametro).toList());
    }
}
