package com.retailstore.api.seguridad.dto;

import com.retailstore.api.seguridad.dominio.Administrador;
import java.time.Instant;

/** Lo que el panel sabe de un administrador. Nunca incluye el hash. */
public record AdministradorRespuesta(
        Long id,
        String usuario,
        String nombre,
        String rol,
        boolean activo,
        Instant ultimoAccesoEn,
        Instant creadoEn) {

    public static AdministradorRespuesta de(Administrador administrador) {
        return new AdministradorRespuesta(
                administrador.getId(),
                administrador.getUsuario(),
                administrador.getNombre(),
                administrador.getRol().name(),
                administrador.isActivo(),
                administrador.getUltimoAccesoEn(),
                administrador.getCreadoEn());
    }
}
