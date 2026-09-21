package com.retailstore.api.cuenta.dto;

/**
 * Lo que devuelven acceso, refresco y acceso con Google: los tres emiten
 * exactamente la misma sesión, así que los tres devuelven la misma forma.
 *
 * <p><strong>El token de refresco no está aquí a propósito</strong>: viaja en
 * una cookie {@code httpOnly} que el JavaScript no puede leer. Devolverlo en el
 * cuerpo obligaría al frontend a guardarlo en algún sitio, y a cualquier sitio
 * al que llegue el JavaScript también le llega un XSS.
 */
public record SesionClienteRespuesta(String tokenAcceso, long expiraEnSegundos, CuentaRespuesta cliente) {
}
