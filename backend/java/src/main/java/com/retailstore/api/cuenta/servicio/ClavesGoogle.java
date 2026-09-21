package com.retailstore.api.cuenta.servicio;

import java.security.PublicKey;
import java.util.Optional;

/**
 * De dónde salen las claves públicas con las que se verifica la firma de Google.
 *
 * <p>Es una interfaz y no un método dentro del verificador para que la prueba
 * unitaria pueda firmar un token con su propio par de claves. Verificar firmas
 * es justo lo que hay que probar, y no se puede probar contra un servicio que
 * rota sus claves cuando quiere.
 */
public interface ClavesGoogle {

    /**
     * @param identificador el {@code kid} de la cabecera del token
     * @return la clave, o vacío si el proveedor no publica ninguna con ese id
     */
    Optional<PublicKey> porIdentificador(String identificador);
}
