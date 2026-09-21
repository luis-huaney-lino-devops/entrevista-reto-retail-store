package com.retailstore.api.seguridad.servicio;

import com.retailstore.api.comun.error.CodigoError;
import com.retailstore.api.comun.error.ExcepcionAplicacion;
import com.retailstore.api.comun.paginacion.RespuestaPagina;
import com.retailstore.api.seguridad.dominio.Administrador;
import com.retailstore.api.seguridad.dominio.RolAdministrador;
import com.retailstore.api.seguridad.dto.ActualizarAdministradorPeticion;
import com.retailstore.api.seguridad.dto.AdministradorRespuesta;
import com.retailstore.api.seguridad.dto.CambiarContrasenaPeticion;
import com.retailstore.api.seguridad.dto.CrearAdministradorPeticion;
import com.retailstore.api.seguridad.repositorio.AdministradorRepositorio;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de las cuentas del panel.
 *
 * <p>No hay registro público ni recuperación por correo (ADR-0008): si un
 * administrador pierde la contraseña, otro se la restablece desde aquí. Es más
 * incómodo y es deliberado -el flujo de «he olvidado mi contraseña» es la vía
 * más usada para tomar cuentas privilegiadas, y para una cuenta que edita
 * precios no compensa.
 */
@Service
@Transactional
public class ServicioAdministrador {

    private final AdministradorRepositorio administradores;
    private final PasswordEncoder codificador;
    private final ServicioAccesoPanel acceso;

    public ServicioAdministrador(AdministradorRepositorio administradores,
                                 PasswordEncoder codificador,
                                 ServicioAccesoPanel acceso) {
        this.administradores = administradores;
        this.codificador = codificador;
        this.acceso = acceso;
    }

    @Transactional(readOnly = true)
    public RespuestaPagina<AdministradorRespuesta> listar(int pagina, int tamano) {
        return RespuestaPagina.de(
                administradores.findAllByOrderByUsuarioAsc(PageRequest.of(pagina - 1, tamano)),
                AdministradorRespuesta::de);
    }

    @Transactional(readOnly = true)
    public AdministradorRespuesta porUsuario(String usuario) {
        return AdministradorRespuesta.de(buscarPorUsuario(usuario));
    }

    public AdministradorRespuesta crear(CrearAdministradorPeticion peticion) {
        String usuario = peticion.usuario().trim().toLowerCase(Locale.ROOT);
        PoliticaContrasena.exigir(peticion.contrasena(), "contrasena");

        if (administradores.existsByUsuario(usuario)) {
            throw new ExcepcionAplicacion(CodigoError.DUPLICATE_USERNAME,
                    "Ya existe un administrador con el usuario '" + usuario + "'.");
        }

        Administrador administrador = new Administrador(
                usuario,
                codificador.encode(peticion.contrasena()),
                peticion.nombre().trim(),
                rolDe(peticion.rol()));
        return AdministradorRespuesta.de(administradores.save(administrador));
    }

    public AdministradorRespuesta actualizar(Long id, ActualizarAdministradorPeticion peticion) {
        Administrador administrador = buscar(id);
        administrador.renombrar(peticion.nombre().trim());
        administrador.cambiarRol(rolDe(peticion.rol()));

        if (peticion.activo()) {
            administrador.activar();
        } else {
            desactivar(administrador);
        }
        administradores.flush();
        return AdministradorRespuesta.de(administrador);
    }

    /** Restablecimiento por otro administrador: no pide la contraseña actual. */
    public void restablecerContrasena(Long id, String nueva) {
        PoliticaContrasena.exigir(nueva, "contrasenaNueva");
        Administrador administrador = buscar(id);
        administrador.cambiarContrasena(codificador.encode(nueva));
        // Restablecer una contraseña sin cortar las sesiones abiertas deja
        // dentro a quien la robó, que es justo de quien se está recuperando
        // la cuenta.
        acceso.revocarSesionesDe(administrador.getId());
    }

    /** Cambio de la propia contraseña: exige la actual. */
    public void cambiarContrasenaPropia(String usuario, CambiarContrasenaPeticion peticion) {
        Administrador administrador = buscarPorUsuario(usuario);
        if (!codificador.matches(peticion.contrasenaActual(), administrador.getHashContrasena())) {
            throw new ExcepcionAplicacion(CodigoError.INVALID_CREDENTIALS, "La contraseña actual no es correcta.");
        }
        PoliticaContrasena.exigir(peticion.contrasenaNueva(), "contrasenaNueva");
        administrador.cambiarContrasena(codificador.encode(peticion.contrasenaNueva()));
        acceso.revocarSesionesDe(administrador.getId());
    }

    // ------------------------------------------------------------------ apoyo

    private void desactivar(Administrador administrador) {
        if (administrador.isActivo() && administradores.countByActivoTrue() <= 1) {
            // Sin esta comprobación, desactivar al último administrador deja la
            // tienda sin nadie que pueda entrar al panel, y la única salida es
            // tocar la base de datos a mano.
            throw new ExcepcionAplicacion(CodigoError.LAST_ADMIN,
                    "No se puede desactivar al último administrador activo.");
        }
        administrador.desactivar();
        acceso.revocarSesionesDe(administrador.getId());
    }

    /** find(id) se salta el @SQLRestriction: lo eliminado se descarta aquí. */
    private Administrador buscar(Long id) {
        return administradores.findById(id)
                .filter(administrador -> !administrador.estaEliminado())
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.ADMIN_NOT_FOUND,
                        "No existe el administrador " + id + "."));
    }

    private Administrador buscarPorUsuario(String usuario) {
        return administradores.findByUsuario(usuario)
                .orElseThrow(() -> new ExcepcionAplicacion(CodigoError.ADMIN_NOT_FOUND,
                        "No existe el administrador '" + usuario + "'."));
    }

    private static RolAdministrador rolDe(String rol) {
        return rol == null || rol.isBlank()
                ? RolAdministrador.ADMINISTRADOR
                : RolAdministrador.valueOf(rol);
    }
}
