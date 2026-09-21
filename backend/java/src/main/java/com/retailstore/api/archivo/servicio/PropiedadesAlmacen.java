package com.retailstore.api.archivo.servicio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del almacén de objetos.
 *
 * @param tipo            {@code local} o {@code r2}
 * @param directorioLocal raíz en disco cuando el tipo es {@code local}
 * @param urlPublicaBase  prefijo de las URL públicas. Con R2 es el dominio del
 *                        bucket o el de Cloudflare delante de él
 */
@ConfigurationProperties(prefix = "app.almacen")
public record PropiedadesAlmacen(String tipo, String directorioLocal, String urlPublicaBase, R2 r2) {

    public static final String TIPO_LOCAL = "local";
    public static final String TIPO_R2 = "r2";

    public PropiedadesAlmacen {
        tipo = tipo == null || tipo.isBlank() ? TIPO_LOCAL : tipo.trim().toLowerCase(java.util.Locale.ROOT);
        directorioLocal = directorioLocal == null ? "./datos/archivos" : directorioLocal;
        urlPublicaBase = urlPublicaBase == null ? "" : urlPublicaBase.replaceAll("/+$", "");
        r2 = r2 == null ? new R2(null, null, null, null, "auto") : r2;
    }

    public boolean esLocal() {
        return TIPO_LOCAL.equals(tipo);
    }

    /**
     * @param endpoint      {@code https://<cuenta>.r2.cloudflarestorage.com}
     * @param region        R2 ignora la región, pero el SDK de AWS exige una.
     *                      {@code auto} es la que documenta Cloudflare
     */
    public record R2(String endpoint, String bucket, String claveAcceso, String claveSecreta, String region) {
        public R2 {
            region = region == null || region.isBlank() ? "auto" : region;
        }
    }
}
