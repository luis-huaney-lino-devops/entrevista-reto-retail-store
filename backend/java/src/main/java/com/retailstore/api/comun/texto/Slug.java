package com.retailstore.api.comun.texto;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Generación de slugs (RN-008).
 *
 * <p>El slug se deriva del nombre <strong>una sola vez, al crear</strong>, y no
 * cambia cuando el nombre cambia: renombrar un producto no puede romper los
 * enlaces que ya circulan ni el posicionamiento ganado.
 */
public final class Slug {

    private static final int LARGO_MAXIMO = 120;
    private static final int MAXIMO_INTENTOS = 1000;

    private Slug() {
    }

    /**
     * Minúsculas, sin acentos, palabras unidas por guiones.
     *
     * <p>Los acentos se quitan descomponiendo en NFD y eliminando las marcas
     * diacríticas, no con una tabla de reemplazos: la tabla siempre olvida un
     * carácter -la diéresis, la cedilla, el catalán- y produce slugs con
     * caracteres crudos que rompen la URL.
     */
    public static String de(String texto) {
        if (texto == null || texto.isBlank()) {
            return "";
        }
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        // La ñ ya se convirtió en n al quitar las marcas diacríticas: NFD la
        // descompone en n + tilde combinante.
        String guionizado = sinAcentos.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return guionizado.length() > LARGO_MAXIMO
                ? guionizado.substring(0, LARGO_MAXIMO).replaceAll("-+$", "")
                : guionizado;
    }

    /**
     * El primer slug derivado de {@code texto} que no esté ocupado:
     * {@code laptops}, {@code laptops-2}, {@code laptops-3}...
     *
     * @param estaOcupado devuelve true si ese slug ya existe
     */
    public static String unico(String texto, Predicate<String> estaOcupado) {
        String base = de(texto);
        if (base.isEmpty()) {
            throw new IllegalArgumentException("No se puede derivar un slug de: " + texto);
        }
        if (!estaOcupado.test(base)) {
            return base;
        }
        for (int sufijo = 2; sufijo <= MAXIMO_INTENTOS; sufijo++) {
            String candidato = base + "-" + sufijo;
            if (!estaOcupado.test(candidato)) {
                return candidato;
            }
        }
        // Mil nombres iguales no es un caso de uso, es un bucle descontrolado.
        // Mejor fallar que seguir consultando la base para siempre.
        throw new IllegalStateException("No se encontró un slug libre para: " + base);
    }

    /** Un slug escrito a mano es válido si coincide con su propia normalización. */
    public static boolean esValido(String slug) {
        return slug != null && !slug.isBlank() && slug.equals(de(slug));
    }
}
