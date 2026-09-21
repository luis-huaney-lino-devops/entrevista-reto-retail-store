package com.retailstore.api.cuenta.dominio;

/**
 * Proveedores de identidad admitidos.
 *
 * <p>Hoy uno solo. La tabla es multi-proveedor desde el principio porque
 * añadir el segundo entonces es una fila más en este enum y una constante en
 * la restricción {@code ck_identidad_externa_prov}, no un rediseño.
 */
public enum ProveedorIdentidad {
    GOOGLE
}
