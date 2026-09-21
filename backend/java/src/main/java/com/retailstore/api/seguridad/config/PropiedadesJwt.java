package com.retailstore.api.seguridad.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parámetros de los tokens. Todo configurable por entorno; ver
 * {@code backend/java/README.md}.
 *
 * @param secreto              clave HMAC. Mínimo 32 bytes: HS256 con una clave
 *                             más corta que su propio digest es un token que se
 *                             puede forzar.
 * @param emisor               claim {@code iss}
 * @param vigenciaAcceso       duración del token de acceso. Corto a propósito:
 *                             no se puede revocar, así que caduca solo.
 * @param vigenciaRefrescoPanel duración de la cookie de refresco del panel
 * @param vigenciaRefrescoTienda duración de la cookie de refresco de la tienda.
 *                             Mucho más larga que la del panel -30 días frente
 *                             a 12 horas- porque obligar a un comprador a
 *                             volver a entrar cada mañana le cuesta la compra,
 *                             y lo que su sesión puede hacer es incomparable
 *                             con lo que puede hacer la de un administrador.
 * @param cookieSegura         marca {@code Secure} en la cookie. Falso solo en
 *                             desarrollo sobre http; en producción, siempre true.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record PropiedadesJwt(
        String secreto,
        String emisor,
        Duration vigenciaAcceso,
        Duration vigenciaRefrescoPanel,
        Duration vigenciaRefrescoTienda,
        boolean cookieSegura) {

    /** Valor de desarrollo. Si llega a producción, la aplicación se niega a arrancar. */
    public static final String SECRETO_DESARROLLO = "desarrollo-local-no-usar-en-produccion-32b";

    public PropiedadesJwt {
        emisor = emisor == null ? "retail-store" : emisor;
        vigenciaAcceso = vigenciaAcceso == null ? Duration.ofMinutes(15) : vigenciaAcceso;
        vigenciaRefrescoPanel = vigenciaRefrescoPanel == null ? Duration.ofHours(12) : vigenciaRefrescoPanel;
        vigenciaRefrescoTienda = vigenciaRefrescoTienda == null ? Duration.ofDays(30) : vigenciaRefrescoTienda;
    }
}
