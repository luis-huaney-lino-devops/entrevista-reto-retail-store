package com.retailstore.api.cuenta.dominio;

/** Los dos motivos por los que se manda un enlace de un solo uso. */
public enum TipoTokenCliente {

    /** Confirma que la dirección existe y es de quien dice (RN-063). */
    VERIFICAR_EMAIL,

    /** Permite poner una contraseña nueva sin saber la anterior (RN-066). */
    RECUPERAR_CONTRASENA
}
