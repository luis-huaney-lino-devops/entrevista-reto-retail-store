package com.retailstore.api.archivo.servicio;

import java.net.URI;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Elige el almacén según {@code app.almacen.tipo}.
 *
 * <p>La aplicación se niega a arrancar si se pide R2 sin credenciales: es
 * mejor no levantar que aceptar subidas que van a fallar una por una cuando
 * alguien ya esté usando el panel.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesAlmacen.class)
public class ConfiguracionAlmacen {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionAlmacen.class);

    @Bean
    public AlmacenObjetos almacenObjetos(PropiedadesAlmacen propiedades) {
        if (propiedades.esLocal()) {
            log.info("Almacén de archivos: disco local en {}", propiedades.directorioLocal());
            return new AlmacenLocal(Path.of(propiedades.directorioLocal()), propiedades.urlPublicaBase());
        }

        PropiedadesAlmacen.R2 r2 = propiedades.r2();
        exigir(r2.endpoint(), "R2_ENDPOINT");
        exigir(r2.bucket(), "R2_BUCKET");
        exigir(r2.claveAcceso(), "R2_ACCESS_KEY_ID");
        exigir(r2.claveSecreta(), "R2_SECRET_ACCESS_KEY");

        S3Client cliente = S3Client.builder()
                .endpointOverride(URI.create(r2.endpoint()))
                .region(Region.of(r2.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(r2.claveAcceso(), r2.claveSecreta())))
                // R2 no admite direccionamiento por subdominio de bucket: sin
                // esto el SDK construye https://bucket.cuenta.r2... y falla la
                // resolución DNS.
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();

        log.info("Almacén de archivos: R2, bucket {}", r2.bucket());
        return new AlmacenR2(cliente, r2.bucket(), propiedades.urlPublicaBase());
    }

    private static void exigir(String valor, String variable) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(
                    "app.almacen.tipo=r2 exige " + variable + ". Usa ALMACEN_TIPO=local para desarrollo.");
        }
    }
}
