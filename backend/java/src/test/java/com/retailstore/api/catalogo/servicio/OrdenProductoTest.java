package com.retailstore.api.catalogo.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class OrdenProductoTest {

    @Test
    @DisplayName("sin parámetro ordena por recientes_RN023")
    void porOmisionRecientes() {
        assertThat(OrdenProducto.desde(null)).isEqualTo(OrdenProducto.RECIENTES);
        assertThat(OrdenProducto.desde("  ")).isEqualTo(OrdenProducto.RECIENTES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"precio_asc", "PRECIO_ASC", "  precio_asc  "})
    @DisplayName("el parámetro no distingue mayúsculas ni espacios_RN023")
    void toleraMayusculasYEspacios(String parametro) {
        assertThat(OrdenProducto.desde(parametro)).isEqualTo(OrdenProducto.PRECIO_ASC);
    }

    @Test
    @DisplayName("un campo fuera de la lista blanca lanza VALIDATION_ERROR_RN023")
    void rechazaCampoDesconocido() {
        assertThatThrownBy(() -> OrdenProducto.desde("creado_por; drop table producto"))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.VALIDATION_ERROR);
    }

    @ParameterizedTest
    @EnumSource(OrdenProducto.class)
    @DisplayName("todo orden desempata por id, para que la paginación sea estable_RN023")
    void todosDesempatanPorId(OrdenProducto orden) {
        var propiedades = orden.orden().stream().map(o -> o.getProperty()).toList();

        assertThat(propiedades).hasSize(2);
        assertThat(propiedades.get(1)).isEqualTo("id");
    }
}
