package com.retailstore.api.catalogo.servicio;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.comun.error.ManejadorGlobalErrores.ErrorCampo;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Sort;

/**
 * Ordenamientos admitidos (RN-023).
 *
 * <p>Lista blanca y no un campo libre: aceptar cualquier nombre de propiedad
 * deja que quien llama ordene por columnas sin índice -o inexistentes- y
 * convierte un parámetro de la URL en una forma de tumbar la base.
 *
 * <p><strong>Todos desempatan por id.</strong> Sin desempate, dos productos con
 * el mismo precio pueden salir en distinto orden en cada consulta, y entonces
 * la página 2 repite filas de la 1 o se salta otras.
 */
public enum OrdenProducto {

    RECIENTES("recientes", Sort.by(Sort.Direction.DESC, "creadoEn")),
    PRECIO_ASC("precio_asc", Sort.by(Sort.Direction.ASC, "precio")),
    PRECIO_DESC("precio_desc", Sort.by(Sort.Direction.DESC, "precio")),
    NOMBRE_ASC("nombre_asc", Sort.by(Sort.Direction.ASC, "nombre")),
    MEJOR_CALIFICADOS("calificacion", Sort.by(Sort.Direction.DESC, "calificacionPromedio"));

    private final String parametro;
    private final Sort orden;

    OrdenProducto(String parametro, Sort orden) {
        this.parametro = parametro;
        this.orden = orden;
    }

    public String parametro() {
        return parametro;
    }

    public Sort orden() {
        return orden.and(Sort.by(Sort.Direction.ASC, "id"));
    }

    public static OrdenProducto desde(String parametro) {
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
        return String.join(", ", Arrays.stream(values()).map(OrdenProducto::parametro).toList());
    }
}
