package com.retailstore.api.seguridad.servicio;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.comun.error.ManejadorGlobalErrores.ErrorCampo;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Política de contraseñas (RN-062): <strong>mínimo 10 caracteres y nada más</strong>.
 *
 * <p>Sin exigir mayúsculas, dígitos ni símbolos. Contradice la costumbre y
 * sigue la recomendación vigente (NIST SP 800-63B): las reglas de composición
 * empujan a la gente a {@code Password1!}, que un atacante prueba de los
 * primeros, mientras que una frase larga es fuerte y se recuerda.
 *
 * <p>Lo que sí se hace es rechazar las contraseñas más usadas. Es la
 * comprobación que de verdad elimina las que se rompen en segundos.
 */
public final class PoliticaContrasena {

    public static final int LARGO_MINIMO = 10;
    public static final int LARGO_MAXIMO = 200;

    /**
     * Muestra de las contraseñas más filtradas. En producción esto sería un
     * fichero con las 10 000 más comunes cargado en memoria; la forma de
     * comprobarlo no cambia, solo el tamaño del conjunto.
     */
    private static final Set<String> BLOQUEADAS = Set.of(
            "contrasena1", "contrasena123", "password12", "password123", "password1234",
            "qwerty12345", "1234567890", "12345678901", "administrador", "admin12345",
            "adminadmin", "letmein1234", "iloveyou12", "bienvenido1", "peru123456",
            "abcdefghij", "0987654321", "qwertyuiop", "asdfghjkl1", "contraseña1");

    private PoliticaContrasena() {
    }

    /** @throws ExcepcionAplicacion {@code VALIDATION_ERROR} con el campo indicado */
    public static void exigir(String contrasena, String campo) {
        String valor = contrasena == null ? "" : contrasena;
        String mensaje = motivoDeRechazo(valor);
        if (mensaje != null) {
            throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR, "La petición contiene 1 campo inválido.")
                    .con("errors", List.of(new ErrorCampo(campo, mensaje)));
        }
    }

    private static String motivoDeRechazo(String contrasena) {
        if (contrasena.length() < LARGO_MINIMO) {
            return "debe tener al menos " + LARGO_MINIMO + " caracteres";
        }
        if (contrasena.length() > LARGO_MAXIMO) {
            // BCrypt trunca en 72 bytes; el tope evita además que alguien suba
            // un megabyte y obligue a hashearlo.
            return "no puede superar " + LARGO_MAXIMO + " caracteres";
        }
        if (BLOQUEADAS.contains(contrasena.toLowerCase(Locale.ROOT))) {
            return "es una contraseña demasiado común";
        }
        return null;
    }
}
