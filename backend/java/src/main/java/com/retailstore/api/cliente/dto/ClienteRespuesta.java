package com.retailstore.api.cliente.dto;

import com.retailstore.api.cliente.dominio.Cliente;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Un cliente en el listado del panel.
 *
 * <p>Nunca incluye el hash de la contraseña. {@code tieneContrasena} sí, porque
 * distingue a quien se registró con correo de quien entró con Google, y eso
 * cambia qué se le puede ofrecer.
 */
public record ClienteRespuesta(
        Long id,
        String email,
        boolean emailVerificado,
        String nombre,
        String telefono,
        boolean activo,
        boolean tieneContrasena,
        long ordenes,
        BigDecimal totalComprado,
        Instant ultimoAccesoEn,
        Instant creadoEn) {

    public static ClienteRespuesta de(Cliente cliente, long ordenes, BigDecimal totalComprado) {
        return new ClienteRespuesta(
                cliente.getId(),
                cliente.getEmail(),
                cliente.isEmailVerificado(),
                cliente.getNombre(),
                cliente.getTelefono(),
                cliente.isActivo(),
                cliente.tieneContrasena(),
                ordenes,
                totalComprado,
                cliente.getUltimoAccesoEn(),
                cliente.getCreadoEn());
    }
}
