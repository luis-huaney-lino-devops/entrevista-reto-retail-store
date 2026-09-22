package com.retailstore.api.correo.servicio;

import java.util.concurrent.Executor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Deja listo el envío de correo fuera de la petición.
 *
 * <p>El ejecutor es propio y no el de Spring por defecto —que es ilimitado— a
 * propósito: sin tope, una caída del proveedor acumularía un hilo por cada
 * correo pendiente hasta tumbar el proceso. Con cuatro hilos y una cola
 * acotada, lo que se pierde en un incidente son correos, no la aplicación.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(PropiedadesCorreo.class)
public class ConfiguracionCorreo {

    @Bean(name = "taskExecutor")
    public Executor ejecutorCorreo() {
        ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
        ejecutor.setCorePoolSize(2);
        ejecutor.setMaxPoolSize(4);
        ejecutor.setQueueCapacity(200);
        ejecutor.setThreadNamePrefix("correo-");
        // Si la cola se llena, lo ejecuta el hilo que llama en vez de
        // descartarlo en silencio: prefiero una petición lenta a un correo de
        // recuperación de contraseña perdido sin rastro.
        ejecutor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        ejecutor.setWaitForTasksToCompleteOnShutdown(true);
        ejecutor.setAwaitTerminationSeconds(20);
        ejecutor.initialize();
        return ejecutor;
    }
}
