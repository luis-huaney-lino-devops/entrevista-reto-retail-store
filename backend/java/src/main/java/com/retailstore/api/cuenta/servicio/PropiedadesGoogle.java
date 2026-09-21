package com.retailstore.api.cuenta.servicio;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del acceso con Google.
 *
 * @param clientId   el identificador de la aplicación en la consola de Google.
 *                   <strong>Vacío por omisión</strong>: el repositorio no puede
 *                   traer credenciales, así que sin variable de entorno el
 *                   endpoint responde {@code 503} diciendo exactamente eso, en
 *                   lugar de fallar de una forma que parezca un error del
 *                   usuario.
 * @param urlJwks    de dónde salen las claves públicas con las que se comprueba
 *                   la firma. Es configurable para poder apuntarla a un doble
 *                   en una prueba de integración, no para cambiar de proveedor.
 * @param vigenciaClaves cuánto se reutiliza el juego de claves antes de volver
 *                   a pedirlo. Google las rota cada pocos días; una hora es
 *                   corto frente a eso y evita una llamada de red por acceso.
 */
@ConfigurationProperties(prefix = "app.google")
public record PropiedadesGoogle(String clientId, String urlJwks, Duration vigenciaClaves) {

    public PropiedadesGoogle {
        urlJwks = urlJwks == null || urlJwks.isBlank()
                ? "https://www.googleapis.com/oauth2/v3/certs"
                : urlJwks;
        vigenciaClaves = vigenciaClaves == null ? Duration.ofHours(1) : vigenciaClaves;
    }

    public boolean estaConfigurado() {
        return clientId != null && !clientId.isBlank();
    }
}
