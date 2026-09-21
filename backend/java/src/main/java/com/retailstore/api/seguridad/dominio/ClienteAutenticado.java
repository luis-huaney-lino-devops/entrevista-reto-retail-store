package com.retailstore.api.seguridad.dominio;

import java.security.Principal;

/**
 * El cliente que viene en el token, tal y como lo ven los controladores de la
 * tienda ({@code @AuthenticationPrincipal}).
 *
 * <p>Implementa {@link Principal} por una razón práctica: la auditoría escribe
 * {@code Authentication.getName()} en {@code creado_por} y {@code eliminado_por},
 * columnas de 60 caracteres. Sin {@code getName()}, Spring guardaría el
 * {@code toString()} del record entero y la inserción fallaría por longitud
 * -y solo en producción, cuando alguien con un nombre largo guarde una
 * dirección-.
 *
 * <p>El nombre registrado es {@code cliente:&lt;id&gt;} y no el correo: cabe
 * siempre, no cambia si el cliente se renombra y no siembra datos personales
 * en las columnas de auditoría de media base de datos.
 */
public record ClienteAutenticado(Long id, String email, String nombre) implements Principal {

    /** Autoridad única de la tienda. No hay roles de cliente: o eres el dueño de la cuenta o no. */
    public static final String AUTORIDAD = "ROLE_CLIENTE";

    public static final String PREFIJO_AUDITORIA = "cliente:";

    @Override
    public String getName() {
        return PREFIJO_AUDITORIA + id;
    }
}
