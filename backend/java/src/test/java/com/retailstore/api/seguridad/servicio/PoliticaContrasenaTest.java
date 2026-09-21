package com.retailstore.api.seguridad.servicio;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.retailstore.api.comun.error.ExcepcionAplicacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PoliticaContrasenaTest {

    @ParameterizedTest
    @ValueSource(strings = {"corta", "123456789", ""})
    @DisplayName("rechaza contraseñas de menos de 10 caracteres_RN062")
    void rechazaCortas(String contrasena) {
        assertThatThrownBy(() -> PoliticaContrasena.exigir(contrasena, "contrasena"))
                .isInstanceOf(ExcepcionAplicacion.class);
    }

    @Test
    @DisplayName("rechaza null_RN062")
    void rechazaNulo() {
        assertThatThrownBy(() -> PoliticaContrasena.exigir(null, "contrasena"))
                .isInstanceOf(ExcepcionAplicacion.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"password123", "Contrasena123", "QWERTYUIOP"})
    @DisplayName("rechaza las contraseñas más usadas aunque sean largas_RN062")
    void rechazaComunes(String contrasena) {
        assertThatThrownBy(() -> PoliticaContrasena.exigir(contrasena, "contrasena"))
                .isInstanceOf(ExcepcionAplicacion.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"una frase larga y memorable", "correcthorsebatterystaple", "10caracter"})
    @DisplayName("una frase larga se acepta sin exigir símbolos ni dígitos_RN062")
    void aceptaFrasesLargas(String contrasena) {
        assertThatCode(() -> PoliticaContrasena.exigir(contrasena, "contrasena"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("el error señala el campo que el formulario envió_RN062")
    void elErrorSenalaElCampo() {
        assertThatThrownBy(() -> PoliticaContrasena.exigir("x", "contrasenaNueva"))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).datos().get("errors").toString())
                .asString()
                .contains("contrasenaNueva");
    }
}
