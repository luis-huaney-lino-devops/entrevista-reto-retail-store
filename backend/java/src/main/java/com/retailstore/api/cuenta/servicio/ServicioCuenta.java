package com.retailstore.api.cuenta.servicio;

import com.retailstore.api.cliente.dominio.Cliente;
import com.retailstore.api.cliente.repositorio.ClienteRepositorio;
import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.comun.error.ManejadorGlobalErrores.ErrorCampo;
import com.retailstore.api.cuenta.dto.ActualizarPerfilPeticion;
import com.retailstore.api.cuenta.dto.CambiarContrasenaClientePeticion;
import com.retailstore.api.cuenta.dto.CuentaRespuesta;
import com.retailstore.api.seguridad.servicio.PoliticaContrasena;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El perfil del cliente: verlo, editarlo y cambiar la contraseña.
 *
 * <p>Todo va contra el id que trae el token, nunca contra uno de la ruta: no
 * existe «ver el perfil de otro», así que tampoco existe la forma de pedirlo.
 */
@Service
@Transactional
public class ServicioCuenta {

    private final ClienteRepositorio clientes;
    private final PasswordEncoder codificador;
    private final ServicioAccesoTienda accesos;

    public ServicioCuenta(ClienteRepositorio clientes, PasswordEncoder codificador, ServicioAccesoTienda accesos) {
        this.clientes = clientes;
        this.codificador = codificador;
        this.accesos = accesos;
    }

    @Transactional(readOnly = true)
    public CuentaRespuesta yo(Long idCliente) {
        return CuentaRespuesta.de(exigir(idCliente));
    }

    public CuentaRespuesta actualizar(Long idCliente, ActualizarPerfilPeticion peticion) {
        Cliente cliente = exigir(idCliente);
        cliente.actualizarPerfil(peticion.nombre().trim(), normalizar(peticion.telefono()));
        // Entidad ya gestionada: flush() del repositorio, nunca saveAndFlush().
        // saveAndFlush() sobre algo gestionado hace merge, y merge trabaja sobre
        // una copia: lo que se devolviera vendría de la copia, no de esta.
        clientes.flush();
        return CuentaRespuesta.de(cliente);
    }

    /**
     * Cambia la contraseña, o establece la primera.
     *
     * <p>{@code contrasenaActual} es obligatoria <strong>salvo</strong> que la
     * cuenta no tenga contraseña todavía, que es el caso de quien entró con
     * Google (RN-065). Pedírsela a quien nunca tuvo una sería un formulario
     * imposible de rellenar; exigirla a quien sí la tiene es lo que impide que
     * quien pase junto a una sesión abierta se apropie de la cuenta.
     *
     * <p>Después caen las <em>demás</em> sesiones: si alguien cambia su
     * contraseña es porque sospecha que hay otro dentro, y dejar vivas las
     * sesiones existentes vacía de sentido el cambio.
     */
    public void cambiarContrasena(Long idCliente, CambiarContrasenaClientePeticion peticion, String refrescoActual) {
        Cliente cliente = exigir(idCliente);

        if (cliente.tieneContrasena()) {
            String actual = peticion.contrasenaActual();
            if (actual == null || actual.isBlank()) {
                throw new ExcepcionAplicacion(CodigoError.VALIDATION_ERROR, "La petición contiene 1 campo inválido.")
                        .con("errors", List.of(new ErrorCampo("contrasenaActual", "es obligatoria")));
            }
            if (!codificador.matches(actual, cliente.getHashContrasena())) {
                throw new ExcepcionAplicacion(CodigoError.INVALID_CREDENTIALS, "La contraseña actual no es correcta.");
            }
        }

        PoliticaContrasena.exigir(peticion.contrasenaNueva(), "contrasenaNueva");
        cliente.cambiarContrasena(codificador.encode(peticion.contrasenaNueva()));
        clientes.flush();
        accesos.revocarOtrasSesiones(idCliente, refrescoActual);
    }

    // ------------------------------------------------------------------ apoyo

    /**
     * El token puede vivir hasta 15 minutos más que la cuenta: caduca solo y no
     * se puede revocar. Por eso aquí se vuelve a mirar si el cliente sigue
     * existiendo y activo, y no basta con que la firma del token fuera buena.
     */
    private Cliente exigir(Long idCliente) {
        return clientes.buscarActivo(idCliente)
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.UNAUTHENTICATED,
                        "Tu sesión ya no es válida. Vuelve a entrar."));
    }

    private static String normalizar(String telefono) {
        return telefono == null || telefono.isBlank() ? null : telefono.trim();
    }
}
