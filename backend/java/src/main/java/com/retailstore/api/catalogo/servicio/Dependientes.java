package com.retailstore.api.catalogo.servicio;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import java.util.List;

/**
 * RN-011: no se desactiva lo que tiene contenido activo.
 *
 * <p>Primero se vacía o se reasigna. Evita que un clic deje productos colgando
 * de algo invisible: seguirían existiendo, nadie podría llegar a ellos y nadie
 * se enteraría hasta que cayeran las ventas.
 */
final class Dependientes {

    /**
     * Tope de la lista que viaja en el error. Una lista sin límite en un cuerpo
     * de error es un vector de denegación de servicio contra tus propios logs.
     */
    static final int MAXIMO_LISTADO = 50;

    private Dependientes() {
    }

    static void exigirNinguno(long cuantos, List<String> primeros, String que, String queBloquea) {
        if (cuantos == 0) {
            return;
        }
        throw new ExcepcionAplicacion(CodigoError.HAS_DEPENDENTS,
                "No se puede desactivar " + que + ": tiene " + cuantos + " " + queBloquea
                        + " activos. Desactívalos o reasígnalos primero.")
                .con("totalBloqueantes", cuantos)
                .con("bloqueantes", primeros);
    }
}
