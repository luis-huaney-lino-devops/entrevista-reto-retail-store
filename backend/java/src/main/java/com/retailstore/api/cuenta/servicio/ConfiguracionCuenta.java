package com.retailstore.api.cuenta.servicio;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registra las propiedades de la identidad de la tienda.
 *
 * <p>Solo hay que declarar {@link PropiedadesGoogle}: el resto de la
 * configuración de sesión -secreto, vigencias, cookie- vive en
 * {@code PropiedadesJwt}, que es una sola para las dos audiencias porque la
 * clave de firma sí es común.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesGoogle.class)
public class ConfiguracionCuenta {
}
