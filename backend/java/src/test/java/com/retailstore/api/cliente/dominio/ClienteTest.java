package com.retailstore.api.cliente.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClienteTest {

    @Test
    @DisplayName("el correo es la identidad y se guarda en minúsculas_RN060")
    void normalizaElCorreo_RN060() {
        Cliente cliente = new Cliente("  Ana@Ejemplo.PE  ", "Ana Torres", "999888777");

        assertThat(cliente.getEmail()).isEqualTo("ana@ejemplo.pe");
    }

    @Test
    @DisplayName("Ana@x.com y ana@x.com son la misma identidad, no dos cuentas_RN060")
    void dosGrafiasDelMismoCorreoSeNormalizanIgual_RN060() {
        assertThat(Cliente.normalizar("Ana@x.com")).isEqualTo(Cliente.normalizar("ana@X.COM"));
    }

    @Test
    @DisplayName("un cliente registrado con correo nace sin verificar: comprar no lo exige_RN063")
    void registradoConCorreoNaceSinVerificar_RN063() {
        Cliente cliente = new Cliente("ana@ejemplo.pe", "Ana", null);

        assertThat(cliente.isEmailVerificado()).isFalse();
        assertThat(cliente.isActivo()).isTrue();
    }

    @Test
    @DisplayName("quien llega por Google nace verificado y sin contraseña")
    void deProveedorExternoNaceVerificadoYSinContrasena() {
        Cliente cliente = Cliente.deProveedorExterno("Ana@Ejemplo.PE", "Ana Torres");

        assertThat(cliente.getEmail()).isEqualTo("ana@ejemplo.pe");
        assertThat(cliente.isEmailVerificado()).isTrue();
        assertThat(cliente.tieneContrasena()).isFalse();
    }

    @Test
    @DisplayName("establecer una contraseña es lo que convierte una cuenta de Google en una con dos llaves_RN065")
    void establecerContrasenaAnadeUnaFormaDeEntrar_RN065() {
        Cliente cliente = Cliente.deProveedorExterno("ana@ejemplo.pe", "Ana");

        cliente.cambiarContrasena("{bcrypt}loquesea");

        assertThat(cliente.tieneContrasena()).isTrue();
    }

    @Test
    @DisplayName("el perfil cambia nombre y teléfono; el correo no se toca_RN060")
    void actualizarPerfilNoTocaElCorreo_RN060() {
        Cliente cliente = new Cliente("ana@ejemplo.pe", "Ana", "999");

        cliente.actualizarPerfil("Ana Torres", null);

        assertThat(cliente.getNombre()).isEqualTo("Ana Torres");
        assertThat(cliente.getTelefono()).isNull();
        assertThat(cliente.getEmail()).isEqualTo("ana@ejemplo.pe");
    }

    @Test
    @DisplayName("registrar el acceso deja la marca temporal del último")
    void registrarAccesoGuardaElInstante() {
        Cliente cliente = new Cliente("ana@ejemplo.pe", "Ana", null);
        Instant momento = Instant.parse("2026-06-15T12:00:00Z");

        cliente.registrarAcceso(momento);

        assertThat(cliente.getUltimoAccesoEn()).isEqualTo(momento);
    }
}
