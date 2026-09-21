package com.retailstore.api.cuenta.servicio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Las claves públicas de Google, cacheadas.
 *
 * <p>Se piden una vez y se reutilizan durante {@code vigenciaClaves}. Pedirlas
 * en cada acceso metería una llamada de red -y la disponibilidad de Google- en
 * el camino crítico de cada inicio de sesión.
 *
 * <p><strong>Un {@code kid} desconocido fuerza una recarga</strong>, porque es
 * exactamente lo que ocurre cuando Google rota sus claves: el token viene
 * firmado con una que aún no tenemos. Pero esa recarga está limitada por
 * {@link #ESPERA_MINIMA}: sin ese freno, mandar tokens con {@code kid}
 * aleatorios convierte nuestro endpoint de acceso en un generador de tráfico
 * contra Google, y a nosotros en el origen bloqueado.
 */
@Component
public class ClavesGoogleJwks implements ClavesGoogle {

    private static final Logger log = LoggerFactory.getLogger(ClavesGoogleJwks.class);

    /** Ni una recarga más seguida que esto, aunque el kid siga sin aparecer. */
    private static final Duration ESPERA_MINIMA = Duration.ofMinutes(1);
    private static final Duration TIEMPO_ESPERA_RED = Duration.ofSeconds(5);

    private final PropiedadesGoogle propiedades;
    private final ObjectMapper json;
    private final Clock reloj;
    private final HttpClient http;

    private volatile Map<String, PublicKey> claves = Map.of();
    private volatile Instant obtenidoEn = Instant.EPOCH;

    public ClavesGoogleJwks(PropiedadesGoogle propiedades, ObjectMapper json, Clock reloj) {
        this.propiedades = propiedades;
        this.json = json;
        this.reloj = reloj;
        this.http = HttpClient.newBuilder().connectTimeout(TIEMPO_ESPERA_RED).build();
    }

    @Override
    public Optional<PublicKey> porIdentificador(String identificador) {
        if (identificador == null) {
            return Optional.empty();
        }
        Map<String, PublicKey> actuales = claves;
        if (actuales.containsKey(identificador) && !caducadas()) {
            return Optional.of(actuales.get(identificador));
        }
        return Optional.ofNullable(recargar().get(identificador));
    }

    private boolean caducadas() {
        return obtenidoEn.plus(propiedades.vigenciaClaves()).isBefore(reloj.instant());
    }

    /**
     * Sincronizado: diez accesos simultáneos tras una rotación harían diez
     * peticiones idénticas a Google. El primero que entra recarga y los demás
     * encuentran el juego nuevo al comprobar de nuevo dentro del bloque.
     */
    private synchronized Map<String, PublicKey> recargar() {
        if (obtenidoEn.plus(ESPERA_MINIMA).isAfter(reloj.instant())) {
            return claves;
        }
        try {
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(propiedades.urlJwks()))
                    .timeout(TIEMPO_ESPERA_RED)
                    .GET()
                    .build();
            HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                log.warn("El JWKS de Google respondió {}", respuesta.statusCode());
                return claves;
            }
            Map<String, PublicKey> nuevas = leer(respuesta.body());
            if (!nuevas.isEmpty()) {
                claves = nuevas;
            }
            // La marca se actualiza aunque el cuerpo viniera vacío: es el freno
            // de reintentos, no la fecha de las claves.
            obtenidoEn = reloj.instant();
            return claves;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return claves;
        } catch (Exception ex) {
            // Google caído no es un error del cliente: se conserva lo último
            // que funcionaba y el token se rechazará por firma desconocida.
            log.warn("No se pudo obtener el JWKS de Google: {}", ex.toString());
            obtenidoEn = reloj.instant();
            return claves;
        }
    }

    private Map<String, PublicKey> leer(String cuerpo) throws Exception {
        Map<String, PublicKey> resultado = new LinkedHashMap<>();
        JsonNode raiz = json.readTree(cuerpo);
        for (JsonNode jwk : raiz.path("keys")) {
            // Solo RSA con firma: Google publica RS256, y aceptar cualquier
            // "kty" que aparezca sería construir claves que no sabemos usar.
            if (!"RSA".equals(jwk.path("kty").asText()) || jwk.path("kid").isMissingNode()) {
                continue;
            }
            BigInteger modulo = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("n").asText()));
            BigInteger exponente = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.path("e").asText()));
            PublicKey clave = KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(modulo, exponente));
            resultado.put(jwk.path("kid").asText(), clave);
        }
        return resultado;
    }
}
