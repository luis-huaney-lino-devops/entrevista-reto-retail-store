package com.retailstore.api.cuenta.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.retailstore.api.cliente.dominio.Cliente;
import com.retailstore.api.cliente.repositorio.ClienteRepositorio;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.cuenta.dominio.IdentidadExterna;
import com.retailstore.api.cuenta.dominio.ProveedorIdentidad;
import com.retailstore.api.cuenta.repositorio.IdentidadExternaRepositorio;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Vinculación de cuentas con Google.
 *
 * <p>Es el punto más delicado de todo el bloque de identidad: aquí es donde una
 * comprobación olvidada deja que cualquiera se apropie de una cuenta ajena
 * creando una cuenta de Google que declare su correo.
 */
class ServicioAccesoGoogleTest {

    private static final Instant AHORA = Instant.parse("2026-06-15T12:00:00Z");

    private final VerificadorTokenGoogle verificador = mock(VerificadorTokenGoogle.class);
    private final IdentidadExternaRepositorio identidades = mock(IdentidadExternaRepositorio.class);
    private final ClienteRepositorio clientes = mock(ClienteRepositorio.class);
    private final ServicioAccesoTienda accesos = mock(ServicioAccesoTienda.class);

    private final ServicioAccesoGoogle servicio = new ServicioAccesoGoogle(
            verificador, identidades, clientes, accesos, Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    @DisplayName("si Google no verificó el correo no se vincula nada: EMAIL_NOT_VERIFIED_BY_PROVIDER_RN064")
    void acceder_conCorreoNoVerificadoPorGoogle_lanzaEmailNotVerified_RN064() {
        dado(new IdentidadGoogle("sujeto-atacante", "victima@ejemplo.pe", false, "Impostor", null));
        when(identidades.findByProveedorAndSujeto(ProveedorIdentidad.GOOGLE, "sujeto-atacante"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.acceder("credencial"))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.EMAIL_NOT_VERIFIED_BY_PROVIDER);

        // Lo importante no es el código: es que no se tocó ninguna cuenta.
        verify(clientes, never()).save(any());
        verify(identidades, never()).save(any());
    }

    @Test
    @DisplayName("con el correo verificado y una cuenta existente se VINCULA, no se crea otra_RN064")
    void acceder_conCuentaExistente_vincula_RN064() {
        dado(new IdentidadGoogle("sujeto-nuevo", "ana@ejemplo.pe", true, "Ana Torres", "https://foto"));
        when(identidades.findByProveedorAndSujeto(ProveedorIdentidad.GOOGLE, "sujeto-nuevo"))
                .thenReturn(Optional.empty());

        Cliente existente = conId(new Cliente("ana@ejemplo.pe", "Ana", "999"), 7L);
        existente.cambiarContrasena("{bcrypt}algo");
        when(clientes.findByEmail("ana@ejemplo.pe")).thenReturn(Optional.of(existente));
        when(identidades.existsByClienteIdAndProveedor(7L, ProveedorIdentidad.GOOGLE)).thenReturn(false);

        servicio.acceder("credencial");

        ArgumentCaptor<IdentidadExterna> guardada = ArgumentCaptor.forClass(IdentidadExterna.class);
        verify(identidades).save(guardada.capture());
        assertThat(guardada.getValue().getCliente()).isSameAs(existente);
        assertThat(guardada.getValue().getSujeto()).isEqualTo("sujeto-nuevo");
        verify(clientes, never()).save(any());
        // Google acaba de demostrar que el correo es suyo.
        assertThat(existente.isEmailVerificado()).isTrue();
        // Y la contraseña sigue ahí: vincular añade una forma de entrar, no la sustituye (RN-065).
        assertThat(existente.tieneContrasena()).isTrue();
        verify(accesos).emitirPara(existente);
    }

    @Test
    @DisplayName("con un correo que nadie tiene se crea el cliente, verificado y sin contraseña")
    void acceder_conCorreoDesconocido_creaLaCuenta() {
        dado(new IdentidadGoogle("sujeto-nuevo", "nueva@ejemplo.pe", true, "Persona Nueva", null));
        when(identidades.findByProveedorAndSujeto(ProveedorIdentidad.GOOGLE, "sujeto-nuevo"))
                .thenReturn(Optional.empty());
        when(clientes.findByEmail("nueva@ejemplo.pe")).thenReturn(Optional.empty());
        when(clientes.save(any(Cliente.class))).thenAnswer(llamada -> conId(llamada.getArgument(0), 99L));

        servicio.acceder("credencial");

        ArgumentCaptor<Cliente> creado = ArgumentCaptor.forClass(Cliente.class);
        verify(clientes).save(creado.capture());
        assertThat(creado.getValue().getEmail()).isEqualTo("nueva@ejemplo.pe");
        assertThat(creado.getValue().isEmailVerificado()).isTrue();
        assertThat(creado.getValue().tieneContrasena()).isFalse();
        verify(identidades).save(any(IdentidadExterna.class));
    }

    @Test
    @DisplayName("si la identidad ya existe es un acceso normal: ni crea ni vincula")
    void acceder_conIdentidadConocida_esAccesoNormal() {
        dado(new IdentidadGoogle("sujeto-conocido", "ana@ejemplo.pe", true, "Ana Torres", null));
        Cliente cliente = conId(new Cliente("ana@ejemplo.pe", "Ana", null), 7L);
        IdentidadExterna identidad = new IdentidadExterna(cliente, ProveedorIdentidad.GOOGLE,
                "sujeto-conocido", "ana@ejemplo.pe", "Ana", null, AHORA.minusSeconds(86400));
        when(identidades.findByProveedorAndSujeto(ProveedorIdentidad.GOOGLE, "sujeto-conocido"))
                .thenReturn(Optional.of(identidad));

        servicio.acceder("credencial");

        verify(clientes, never()).save(any());
        verify(identidades, never()).save(any());
        verify(accesos).emitirPara(cliente);
        assertThat(identidad.getUltimoAccesoEn()).isEqualTo(AHORA);
    }

    @Test
    @DisplayName("un cliente bloqueado desde el panel no entra por Google: INVALID_CREDENTIALS_RN067")
    void acceder_conClienteDesactivado_lanzaInvalidCredentials_RN067() {
        dado(new IdentidadGoogle("sujeto-conocido", "ana@ejemplo.pe", true, "Ana", null));
        Cliente cliente = conId(new Cliente("ana@ejemplo.pe", "Ana", null), 7L);
        cliente.desactivar();
        when(identidades.findByProveedorAndSujeto(ProveedorIdentidad.GOOGLE, "sujeto-conocido"))
                .thenReturn(Optional.of(new IdentidadExterna(cliente, ProveedorIdentidad.GOOGLE,
                        "sujeto-conocido", "ana@ejemplo.pe", "Ana", null, AHORA)));

        assertThatThrownBy(() -> servicio.acceder("credencial"))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.INVALID_CREDENTIALS);

        verify(accesos, never()).emitirPara(any());
    }

    @Test
    @DisplayName("una cuenta que ya tiene otra identidad de Google no acepta una segunda")
    void acceder_conSegundaIdentidadDeGoogle_noVincula() {
        dado(new IdentidadGoogle("sujeto-nuevo", "ana@ejemplo.pe", true, "Ana", null));
        when(identidades.findByProveedorAndSujeto(ProveedorIdentidad.GOOGLE, "sujeto-nuevo"))
                .thenReturn(Optional.empty());
        Cliente existente = conId(new Cliente("ana@ejemplo.pe", "Ana", null), 7L);
        when(clientes.findByEmail("ana@ejemplo.pe")).thenReturn(Optional.of(existente));
        when(identidades.existsByClienteIdAndProveedor(anyLong(), any())).thenReturn(true);

        assertThatThrownBy(() -> servicio.acceder("credencial"))
                .isInstanceOf(ExcepcionAplicacion.class)
                .extracting(e -> ((ExcepcionAplicacion) e).codigo())
                .isEqualTo(CodigoError.INVALID_CREDENTIALS);

        verify(identidades, never()).save(any());
    }

    // ----- apoyo -----

    private void dado(IdentidadGoogle identidad) {
        when(verificador.verificar("credencial")).thenReturn(identidad);
    }

    /** El id lo pone la base al insertar, y aquí no hay base. */
    private static Cliente conId(Cliente cliente, Long id) {
        try {
            var campo = Cliente.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(cliente, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
        return cliente;
    }
}
