package com.retailstore.api.comun.error;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Excepción de dominio. Lleva un {@link CodigoError} y los datos que el mensaje
 * necesita, y <strong>no sabe nada de HTTP</strong>: el estado y la forma de la
 * respuesta los decide {@link ManejadorGlobalErrores} en el borde.
 *
 * <p>Los datos extra se añaden encadenando {@link #con(String, Object)} y
 * terminan como campos de primer nivel del {@code problem+json}. Sus nombres
 * son contrato: están fijados en {@code docs/contrato/catalogo-errores.md}.
 */
public class ExcepcionAplicacion extends RuntimeException {

    private final transient CodigoError codigo;
    private final transient Map<String, Object> datos = new LinkedHashMap<>();

    public ExcepcionAplicacion(CodigoError codigo, String detalle) {
        super(detalle);
        this.codigo = codigo;
    }

    public ExcepcionAplicacion con(String clave, Object valor) {
        datos.put(clave, valor);
        return this;
    }

    public CodigoError codigo() {
        return codigo;
    }

    public Map<String, Object> datos() {
        return Collections.unmodifiableMap(datos);
    }
}
