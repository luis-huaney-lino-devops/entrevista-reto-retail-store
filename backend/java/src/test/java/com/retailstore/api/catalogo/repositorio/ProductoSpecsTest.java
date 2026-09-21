package com.retailstore.api.catalogo.repositorio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductoSpecsTest {

    @Test
    @DisplayName("el comodín % del usuario se escapa_RN026")
    void escapaPorcentaje() {
        assertThat(ProductoSpecs.escapar("50%")).isEqualTo("50\\%");
    }

    @Test
    @DisplayName("el comodín _ del usuario se escapa_RN026")
    void escapaGuionBajo() {
        assertThat(ProductoSpecs.escapar("a_b")).isEqualTo("a\\_b");
    }

    @Test
    @DisplayName("la propia barra de escape se escapa primero_RN026")
    void escapaLaBarra() {
        // Si se escapara al final, la barra añadida por % volvería a escaparse
        // y el patrón buscaría una barra literal que nadie escribió.
        assertThat(ProductoSpecs.escapar("a\\b")).isEqualTo("a\\\\b");
    }

    @Test
    @DisplayName("un texto normal no cambia_RN026")
    void textoNormalIntacto() {
        assertThat(ProductoSpecs.escapar("mochila urbana")).isEqualTo("mochila urbana");
    }
}
