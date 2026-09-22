package com.retailstore.api.correo.servicio;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Envío de correo por la API HTTP de Resend.
 *
 * <p><strong>Nunca dentro de la petición.</strong> Todos los métodos van con
 * {@code @Async}: quien los llama no espera. Un servidor de correo lento
 * convertiría cada registro en ocho segundos de espera, y uno caído en un 500
 * al usuario <em>aunque su cuenta se hubiera creado bien</em>. La respuesta
 * «te hemos enviado un correo» es una promesa de intentarlo, no un acuse de
 * entrega; ningún sistema puede garantizar lo segundo al responder.
 *
 * <p><strong>Nunca propaga la excepción.</strong> Que el correo falle no puede
 * deshacer lo que ya ocurrió: la cuenta está creada y el token emitido. Se
 * registra y se sigue.
 *
 * <p>Se usa la API HTTP y no SMTP a propósito: un POST con
 * {@link java.net.http.HttpClient} no añade dependencias, no mantiene
 * conexiones abiertas y falla rápido y de forma legible.
 */
@Service
public class ServicioCorreo {

    private static final Logger log = LoggerFactory.getLogger(ServicioCorreo.class);
    private static final URI ENVIOS = URI.create("https://api.resend.com/emails");

    private final PropiedadesCorreo propiedades;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public ServicioCorreo(PropiedadesCorreo propiedades, ObjectMapper json) {
        this.propiedades = propiedades;
        this.json = json;
        if (!propiedades.activo()) {
            log.warn("app.correo.api-key vacía: los correos se registrarán en el log, no se enviarán.");
        }
    }

    /**
     * Da la bienvenida a quien acaba de registrarse.
     *
     * <p>Se llama <strong>después</strong> del commit: antes, un rollback
     * dejaría a alguien con un correo de bienvenida a una cuenta que no existe.
     */
    @Async
    public void bienvenida(String destinatario, String nombre) {
        enviar(destinatario,
                "Bienvenido a Retail Store",
                PlantillasCorreo.bienvenida(nombre, propiedades.urlTienda()));
    }

    /**
     * El enlace para poner una contraseña nueva.
     *
     * <p>El token va en la URL porque tiene que viajar en un enlace pulsable.
     * Lo que protege la cuenta es que dure 30 minutos, sirva una sola vez y en
     * la base solo esté su hash (RN-066).
     */
    @Async
    public void recuperacion(String destinatario, String nombre, String token) {
        String enlace = propiedades.urlTienda() + "/restablecer?token=" + token;
        enviar(destinatario,
                "Restablece tu contraseña",
                PlantillasCorreo.recuperacion(nombre, enlace));
    }

    /**
     * El enlace que confirma que la dirección existe y es suya (RN-063).
     *
     * <p>Dura 24 h y no media hora como el de recuperación: este no cambia
     * ninguna credencial, y obligar a repetir el proceso por tardar una tarde
     * en abrir el correo es fricción sin ganancia.
     */
    @Async
    public void verificacion(String destinatario, String nombre, String token) {
        String enlace = propiedades.urlTienda() + "/verificar?token=" + token;
        enviar(destinatario,
                "Confirma tu correo",
                PlantillasCorreo.verificacion(nombre, enlace));
    }

    /**
     * Aviso al dueño legítimo de un correo sobre el que alguien intentó
     * registrarse (RN-061).
     *
     * <p>Es la otra mitad de responder siempre lo mismo al registrarse: la
     * respuesta no revela si el correo existe, y quien sí lo tiene se entera
     * por aquí en lugar de quedarse sin saber por qué «su» alta no llegó.
     */
    @Async
    public void intentoDeRegistroDuplicado(String destinatario, String nombre) {
        enviar(destinatario,
                "Alguien intentó registrarse con tu correo",
                PlantillasCorreo.registroDuplicado(nombre, propiedades.urlTienda()));
    }

    private void enviar(String destinatario, String asunto, String html) {
        if (!propiedades.activo()) {
            log.info("[correo desactivado] para={} asunto={}", destinatario, asunto);
            return;
        }
        try {
            String cuerpo = json.writeValueAsString(Map.of(
                    "from", propiedades.remitente(),
                    "to", new String[] { destinatario },
                    "subject", asunto,
                    "html", html));

            HttpRequest peticion = HttpRequest.newBuilder(ENVIOS)
                    .header("Authorization", "Bearer " + propiedades.apiKey())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                    .build();

            HttpResponse<String> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() >= 300) {
                // El cuerpo de Resend dice el motivo -dominio sin verificar,
                // clave revocada-, y sin registrarlo el fallo es invisible.
                log.error("Resend rechazó el envío a {}: {} {}",
                        destinatario, respuesta.statusCode(), respuesta.body());
            } else {
                log.info("Correo enviado a {} — {}", destinatario, asunto);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Envío de correo interrumpido", ex);
        } catch (Exception ex) {
            // A propósito no se relanza: ver el Javadoc de la clase.
            log.error("No se pudo enviar el correo a {}", destinatario, ex);
        }
    }
}
