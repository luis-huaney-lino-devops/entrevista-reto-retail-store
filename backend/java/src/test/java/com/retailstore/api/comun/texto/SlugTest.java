package com.retailstore.api.comun.texto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlugTest {

    @ParameterizedTest
    @CsvSource({
            "Audífonos Bluetooth,audifonos-bluetooth",
            "Tecnología,tecnologia",
            "Niños y Niñas,ninos-y-ninas",
            "  Espacios   de sobra  ,espacios-de-sobra",
            "Mochila 25 L,mochila-25-l",
            "¡Oferta! 50% menos,oferta-50-menos",
            "Ç cedilla,c-cedilla",
    })
    @DisplayName("normaliza acentos, mayúsculas y signos_RN008")
    void normaliza(String entrada, String esperado) {
        assertThat(Slug.de(entrada)).isEqualTo(esperado);
    }

    @Test
    @DisplayName("nunca empieza ni termina en guion_RN008")
    void sinGuionesEnLosExtremos() {
        assertThat(Slug.de("--- hola ---")).isEqualTo("hola");
        assertThat(Slug.de("!!!")).isEmpty();
    }

    @Test
    @DisplayName("añade sufijo numérico cuando el slug ya existe_RN008")
    void anadeSufijoSiEstaOcupado() {
        Set<String> ocupados = new HashSet<>(Set.of("laptops", "laptops-2"));

        assertThat(Slug.unico("Laptops", ocupados::contains)).isEqualTo("laptops-3");
    }

    @Test
    @DisplayName("devuelve el slug base cuando está libre_RN008")
    void usaLaBaseSiEstaLibre() {
        assertThat(Slug.unico("Laptops", s -> false)).isEqualTo("laptops");
    }

    @Test
    @DisplayName("un slug escrito a mano solo es válido si ya está normalizado")
    void validaSlugManual() {
        assertThat(Slug.esValido("mochila-urbana")).isTrue();
        assertThat(Slug.esValido("Mochila Urbana")).isFalse();
        assertThat(Slug.esValido("mochila_urbana")).isFalse();
        assertThat(Slug.esValido("")).isFalse();
    }
}
