package com.retailstore.api.cuenta.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.retailstore.api.cliente.dominio.Cliente;
import com.retailstore.api.cliente.repositorio.ClienteRepositorio;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.cuenta.dto.ActualizarPerfilPeticion;
import com.retailstore.api.cuenta.dto.CambiarContrasenaClientePeticion;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Cambio de contraseña del cliente.
 *
 * <p>El caso que gobierna este servicio es RN-065: nadie puede quedarse sin
 * forma de entrar, y por eso a quien entró con Google -y no tiene contraseña-
 * no se le puede pedir «la actual» para establecer la primera.
 */
class ServicioCuentaTest {

    /**
     * Codificador de juguete: prefija en vez de hashear. No se está probando
     * BCrypt -eso ya está probado por quien lo escribió- sino qué camino sigue
     * el servicio, y un BCrypt real metería 200 ms por prueba.
     */
    private static final PasswordEncoder CODIFICADOR = new PasswordEncoder() {
        @Override
        public String encode(CharSequence cruda) {
            return "hash:" + cruda;
        }

        @Override
        public boolean matches(CharSequence cruda, String codificada) {
            return codificada != null && codificada.equals("hash:" + cruda);
        }
    };

    private final ClienteRepositorio clientes = mock(ClienteRepositorio.class);
    private final ServicioAccesoTienda accesos = mock(ServicioAccesoTienda.class);
    private final ServicioCuenta servicio = new ServicioCuenta(clientes, CODIFICADOR, accesos);

    @Test
    @DisplayName("quien entró con Google establece su primera contraseña sin dar una anterior_RN065")
    void cambiarContrasena_sinContrasenaPrevia_noExigeLaActual_RN065() {
        Cliente cliente = dado(Cliente.deProveedorExterno("ana@ejemplo.pe", "Ana"));

        servicio.cambiarContrasena(7L, new CambiarContrasenaClientePeticion(null, "frase larga seguraaa"), "refresco");

        assertThat(cliente.tieneContrasena()).isTrue();
        assertThat(cliente.getHashContrasena()).isEqualTo("hash:frase larga seguraaa");
    }

    @Test
    @DisplayName("quien ya tiene contraseña tiene que teclearla: sin ella, 400 con el campo señalado_RN065")
    void cambiarContrasena_conContrasenaPrevia_exigeLaActual_RN065() {
        dado(conContrasena("hash:la de siempre"));

        assertThatThrownBy(() -> servicio.cambiarContrasena(
                7L, new CambiarContrasenaClientePeticion(null, "frase larga seguraaa"), "refresco"))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("con la contraseña actual equivocada no se cambia nada: 401")
    void cambiarContrasena_conActualIncorrecta_lanzaInvalidCredentials() {
        Cliente cliente = dado(conContrasena("hash:la de siempre"));

        assertThatThrownBy(() -> servicio.cambiarContrasena(
                7L, new CambiarContrasenaClientePeticion("otra cosa", "frase larga seguraaa"), "refresco"))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.INVALID_CREDENTIALS);

        assertThat(cliente.getHashContrasena()).isEqualTo("hash:la de siempre");
    }

    @Test
    @DisplayName("una contraseña nueva de menos de 10 caracteres se rechaza_RN062")
    void cambiarContrasena_conNuevaDebil_lanzaValidationError_RN062() {
        Cliente cliente = dado(conContrasena("hash:la de siempre"));

        assertThatThrownBy(() -> servicio.cambiarContrasena(
                7L, new CambiarContrasenaClientePeticion("la de siempre", "corta"), "refresco"))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.VALIDATION_ERROR);

        assertThat(cliente.getHashContrasena()).isEqualTo("hash:la de siempre");
    }

    @Test
    @DisplayName("cambiarla con éxito cierra las demás sesiones y conserva la actual")
    void cambiarContrasena_correcta_cierraLasDemasSesiones() {
        dado(conContrasena("hash:la de siempre"));

        servicio.cambiarContrasena(
                7L, new CambiarContrasenaClientePeticion("la de siempre", "frase larga seguraaa"), "refresco-actual");

        verify(accesos).revocarOtrasSesiones(7L, "refresco-actual");
    }

    @Test
    @DisplayName("el perfil deja cambiar nombre y teléfono, y el correo sigue siendo el mismo_RN060")
    void actualizar_noTocaElCorreo_RN060() {
        Cliente cliente = dado(new Cliente("ana@ejemplo.pe", "Ana", null));

        var respuesta = servicio.actualizar(7L, new ActualizarPerfilPeticion("  Ana Torres  ", " 999888777 "));

        assertThat(respuesta.nombre()).isEqualTo("Ana Torres");
        assertThat(respuesta.telefono()).isEqualTo("999888777");
        assertThat(cliente.getEmail()).isEqualTo("ana@ejemplo.pe");
    }

    @Test
    @DisplayName("un teléfono en blanco se guarda como nulo, no como cadena vacía")
    void actualizar_conTelefonoVacio_guardaNulo() {
        dado(new Cliente("ana@ejemplo.pe", "Ana", "999"));

        var respuesta = servicio.actualizar(7L, new ActualizarPerfilPeticion("Ana", "   "));

        assertThat(respuesta.telefono()).isNull();
    }

    @Test
    @DisplayName("si la cuenta ya no está activa, el token que quedaba vivo no sirve de nada")
    void yo_conClienteInactivo_lanzaUnauthenticated() {
        when(clientes.buscarActivo(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.yo(7L))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.UNAUTHENTICATED);
    }

    // ----- apoyo -----

    private Cliente dado(Cliente cliente) {
        when(clientes.buscarActivo(7L)).thenReturn(Optional.of(cliente));
        return cliente;
    }

    private static Cliente conContrasena(String hash) {
        Cliente cliente = new Cliente("ana@ejemplo.pe", "Ana", null);
        cliente.cambiarContrasena(hash);
        return cliente;
    }
}
