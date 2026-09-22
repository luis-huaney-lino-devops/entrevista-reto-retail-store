package com.retailstore.api.opinion.servicio;

import com.retailstore.api.opinion.dominio.Opinion;
import com.retailstore.api.opinion.dto.DesgloseCalificacionRespuesta;
import com.retailstore.api.opinion.repositorio.OpinionRepositorio;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El desglose por estrellas de un producto.
 *
 * <p>Vive aparte de {@link ServicioOpinion} porque lo usa el catálogo -la ficha
 * de producto lo devuelve junto al promedio- y no quien escribe opiniones. Así
 * el catálogo depende de una consulta de lectura y no del servicio que muta.
 *
 * <p>No se guarda en ninguna columna (RN-093): son cinco filas agregadas de una
 * tabla indexada por producto, y se piden una sola vez por ficha. Guardarlas
 * añadiría cinco columnas más que mantener en cada escritura para ahorrar una
 * consulta que ya es barata.
 */
@Service
@Transactional(readOnly = true)
public class ResumenOpiniones {

    private final OpinionRepositorio opiniones;

    public ResumenOpiniones(OpinionRepositorio opiniones) {
        this.opiniones = opiniones;
    }

    /**
     * De 5 a 1, siempre las cinco.
     *
     * <p>La consulta solo devuelve las notas que alguien puso; las que faltan
     * se completan con cero aquí. Omitirlas obligaría a la tienda a
     * reconstruirlas para pintar las barras, y una barra ausente se lee como un
     * dato que falta, no como un cero.
     */
    public List<DesgloseCalificacionRespuesta> desgloseDe(Long idProducto) {
        Map<Integer, Long> porNota = new HashMap<>();
        long total = 0;
        for (Object[] fila : opiniones.desglosePorProducto(idProducto)) {
            int estrellas = ((Number) fila[0]).intValue();
            long cuantas = ((Number) fila[1]).longValue();
            porNota.put(estrellas, cuantas);
            total += cuantas;
        }

        List<DesgloseCalificacionRespuesta> desglose = new ArrayList<>();
        for (int estrellas = Opinion.MAXIMO; estrellas >= Opinion.MINIMO; estrellas--) {
            long cuantas = porNota.getOrDefault(estrellas, 0L);
            // Sin opiniones el porcentaje es 0 y no una división por cero. Y se
            // redondea al entero sin repartir el resto: inventar un punto para
            // que la suma dé 100 sería mentir sobre una de las barras.
            int porcentaje = total == 0 ? 0 : (int) Math.round(cuantas * 100.0 / total);
            desglose.add(new DesgloseCalificacionRespuesta(estrellas, cuantas, porcentaje));
        }
        return desglose;
    }
}
