package com.retailstore.api.seguridad.dto;

/**
 * Respuesta de un acceso o de un refresco.
 *
 * <p><strong>El token de refresco no está aquí a propósito</strong>: viaja en
 * una cookie {@code httpOnly} que el JavaScript no puede leer. Devolverlo en el
 * cuerpo obligaría al frontend a guardarlo en algún sitio, y cualquier sitio al
 * que llegue el JavaScript también le llega a un XSS.
 *
 * @param tokenAcceso       se guarda en memoria, nunca en localStorage
 * @param expiraEnSegundos  para que el cliente refresque antes de que caduque
 *                          en medio de una acción
 */
public record SesionRespuesta(String tokenAcceso, long expiraEnSegundos, AdministradorRespuesta administrador) {
}
