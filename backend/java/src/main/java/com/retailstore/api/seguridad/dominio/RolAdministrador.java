package com.retailstore.api.seguridad.dominio;

/**
 * Roles del panel.
 *
 * <p>Deliberadamente dos y no un sistema de permisos: un catálogo de permisos
 * finos que nadie configura acaba con todo el mundo teniendo todos los
 * permisos, con la complejidad añadida de fingir que no.
 */
public enum RolAdministrador {

    /** Gestiona el catálogo: productos, categorías, marcas, cupones, archivos. */
    ADMINISTRADOR,

    /** Además, gestiona administradores. */
    SUPERADMINISTRADOR;

    /** Autoridad de Spring Security correspondiente. */
    public String autoridad() {
        return "ROLE_" + name();
    }

    public boolean gestionaAdministradores() {
        return this == SUPERADMINISTRADOR;
    }
}
