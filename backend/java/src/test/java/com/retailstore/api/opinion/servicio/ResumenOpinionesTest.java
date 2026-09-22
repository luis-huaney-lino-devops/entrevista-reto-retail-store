package com.retailstore.api.opinion.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.retailstore.api.opinion.dto.DesgloseCalificacionRespuesta;
import com.retailstore.api.opinion.repositorio.OpinionRepositorio;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * El desglose por estrellas (RN-093).
 *
 * <p>Lo que se prueba no es la suma -eso lo hace la base- sino las dos formas
 * de equivocarse al armarlo: omitir las notas que nadie puso, y dividir entre
 * cero cuando no hay ninguna opinión.
 */
class ResumenOpinionesTest {

    private final OpinionRepositorio opiniones = mock(OpinionRepositorio.class);
    private final ResumenOpiniones resumen = new ResumenOpiniones(opiniones);

    @Test
    @DisplayName("vienen las cinco barras aunque solo se hayan puesto dos notas_RN093")
    void desgloseDe_completaLasNotasQueNadiePuso_RN093() {
        when(opiniones.desglosePorProducto(7L)).thenReturn(List.of(
                new Object[]{(short) 5, 3L},
                new Object[]{(short) 3, 1L}));

        List<DesgloseCalificacionRespuesta> desglose = resumen.desgloseDe(7L);

        assertThat(desglose).extracting(DesgloseCalificacionRespuesta::estrellas)
                .containsExactly(5, 4, 3, 2, 1);
        assertThat(desglose).extracting(DesgloseCalificacionRespuesta::cantidad)
                .containsExactly(3L, 0L, 1L, 0L, 0L);
    }

    @Test
    @DisplayName("el porcentaje se calcula en el servidor, no en la tienda_RN093")
    void desgloseDe_calculaElPorcentaje_RN093() {
        when(opiniones.desglosePorProducto(7L)).thenReturn(List.of(
                new Object[]{(short) 5, 3L},
                new Object[]{(short) 1, 1L}));

        List<DesgloseCalificacionRespuesta> desglose = resumen.desgloseDe(7L);

        assertThat(desglose.get(0).porcentaje()).isEqualTo(75);
        assertThat(desglose.get(4).porcentaje()).isEqualTo(25);
    }

    @Test
    @DisplayName("un producto sin opiniones da cinco ceros, no una división entre cero_RN093")
    void desgloseDe_sinOpiniones_noDivideEntreCero_RN093() {
        when(opiniones.desglosePorProducto(7L)).thenReturn(List.of());

        List<DesgloseCalificacionRespuesta> desglose = resumen.desgloseDe(7L);

        assertThat(desglose).hasSize(5);
        assertThat(desglose).allMatch(fila -> fila.cantidad() == 0 && fila.porcentaje() == 0);
    }
}
