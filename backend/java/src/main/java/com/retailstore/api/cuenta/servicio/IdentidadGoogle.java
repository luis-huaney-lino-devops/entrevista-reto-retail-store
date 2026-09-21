package com.retailstore.api.cuenta.servicio;

/**
 * Lo que queda de un ID token de Google después de verificarlo.
 *
 * @param sujeto el {@code sub}. Es lo que identifica la cuenta para siempre;
 *               el correo puede cambiar y el nombre también
 */
public record IdentidadGoogle(String sujeto, String email, boolean emailVerificado,
                              String nombre, String urlFoto) {
}
