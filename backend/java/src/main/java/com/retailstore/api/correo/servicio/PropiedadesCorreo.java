package com.retailstore.api.correo.servicio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del correo saliente.
 *
 * @param apiKey    clave de Resend. <strong>Vacía desactiva el envío</strong>: la
 *                  aplicación arranca igual y cada correo se registra en el log
 *                  en lugar de salir. Es lo que permite levantar el proyecto sin
 *                  credenciales, y lo que evita que unas pruebas manden correo
 *                  de verdad a direcciones reales.
 * @param remitente de dónde salen. El dominio tiene que estar verificado en
 *                  Resend o la API responde 403.
 * @param urlTienda base para los enlaces del correo. Sin ella, el enlace de
 *                  recuperación apuntaría a localhost en producción.
 */
@ConfigurationProperties(prefix = "app.correo")
public record PropiedadesCorreo(String apiKey, String remitente, String urlTienda) {

    public PropiedadesCorreo {
        apiKey = apiKey == null ? "" : apiKey.trim();
        remitente = remitente == null || remitente.isBlank() ? "no-reply@example.com" : remitente.trim();
        urlTienda = urlTienda == null ? "" : urlTienda.replaceAll("/+$", "");
    }

    /** Si no hay clave, no se envía nada: se registra y sigue. */
    public boolean activo() {
        return !apiKey.isEmpty();
    }
}
