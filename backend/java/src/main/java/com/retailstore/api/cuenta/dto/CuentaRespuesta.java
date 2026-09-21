package com.retailstore.api.cuenta.dto;

import com.retailstore.api.cliente.dominio.Cliente;

/**
 * La cuenta vista por su dueño.
 *
 * <p>{@code tieneContrasena} existe para que la tienda sepa qué formulario
 * pintar: a quien entró con Google hay que ofrecerle «establecer contraseña»,
 * no «cambiarla», porque no tiene una actual que teclear (RN-065).
 *
 * <p>Sin {@code activo}: un cliente desactivado no llega a ver esto -no puede
 * entrar-, y decirle que su cuenta está bloqueada es una decisión de producto
 * que hoy no está tomada.
 */
public record CuentaRespuesta(
        Long id,
        String email,
        boolean emailVerificado,
        String nombre,
        String telefono,
        boolean tieneContrasena) {

    public static CuentaRespuesta de(Cliente cliente) {
        return new CuentaRespuesta(
                cliente.getId(),
                cliente.getEmail(),
                cliente.isEmailVerificado(),
                cliente.getNombre(),
                cliente.getTelefono(),
                cliente.tieneContrasena());
    }
}
