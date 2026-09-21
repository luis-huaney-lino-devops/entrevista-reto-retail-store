package com.retailstore.api.comun.error;

import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Catálogo de errores de la API. Es el contrato estable: los clientes se
 * ramifican por el {@code code}, nunca por el {@code title} ni el {@code detail},
 * que se pueden reescribir o traducir en cualquier momento.
 *
 * <p>Los códigos van en inglés y son la única excepción a ADR-0011: no son
 * términos del negocio sino constantes de protocolo, como {@code invalid_grant}
 * en OAuth. Lo que lee una persona -título y detalle- va en español.
 *
 * <p>Añadir un código no rompe nada. Quitar uno, o cambiarle el estado HTTP, sí:
 * exige versionar la API.
 */
public enum CodigoError {

    // --- 400 -----------------------------------------------------------------
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Solicitud inválida"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Petición mal formada"),

    // --- 401 -----------------------------------------------------------------
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "No autenticado"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"),
    INVALID_PROVIDER_TOKEN(HttpStatus.UNAUTHORIZED, "Token del proveedor inválido"),

    // --- 403 -----------------------------------------------------------------
    FORBIDDEN(HttpStatus.FORBIDDEN, "Sin permiso"),
    WRONG_AUDIENCE(HttpStatus.FORBIDDEN, "Token de otra audiencia"),

    // --- 404 -----------------------------------------------------------------
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "Producto no encontrado"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Categoría no encontrada"),
    SUBCATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Subcategoría no encontrada"),
    BRAND_NOT_FOUND(HttpStatus.NOT_FOUND, "Marca no encontrada"),
    CART_NOT_FOUND(HttpStatus.NOT_FOUND, "Carrito no encontrado"),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Línea de carrito no encontrada"),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "Cupón no encontrado"),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Orden no encontrada"),
    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "Cliente no encontrado"),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Conversación no encontrada"),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Archivo no encontrado"),
    ADMIN_NOT_FOUND(HttpStatus.NOT_FOUND, "Administrador no encontrado"),
    ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "Dirección no encontrada"),
    DISTRICT_NOT_FOUND(HttpStatus.NOT_FOUND, "Distrito no encontrado"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso no encontrado"),

    // --- 409 -----------------------------------------------------------------
    DUPLICATE_SKU(HttpStatus.CONFLICT, "SKU duplicado"),
    DUPLICATE_SLUG(HttpStatus.CONFLICT, "Slug duplicado"),
    DUPLICATE_NAME(HttpStatus.CONFLICT, "Nombre duplicado"),
    DUPLICATE_COUPON_CODE(HttpStatus.CONFLICT, "Código de cupón duplicado"),
    DUPLICATE_USERNAME(HttpStatus.CONFLICT, "Usuario duplicado"),
    HAS_DEPENDENTS(HttpStatus.CONFLICT, "Tiene contenido asociado"),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT, "Stock insuficiente"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Modificación concurrente"),

    // --- 422 -----------------------------------------------------------------
    SUBCATEGORY_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "Subcategoría inactiva"),
    CATEGORY_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "Categoría inactiva"),
    SKU_IMMUTABLE(HttpStatus.UNPROCESSABLE_ENTITY, "El SKU no se puede cambiar"),
    INVALID_COMPARE_PRICE(HttpStatus.UNPROCESSABLE_ENTITY, "Precio anterior inválido"),
    PRODUCT_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "Producto inactivo"),
    PRODUCT_REQUIRES_IMAGE(HttpStatus.UNPROCESSABLE_ENTITY, "El producto necesita una imagen"),
    INVALID_PRICE_RANGE(HttpStatus.UNPROCESSABLE_ENTITY, "Rango de precios inválido"),
    COUPON_NOT_APPLICABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Cupón no aplicable"),
    COUPON_MIN_NOT_MET(HttpStatus.UNPROCESSABLE_ENTITY, "No se alcanza el mínimo del cupón"),
    COUPON_EXHAUSTED(HttpStatus.UNPROCESSABLE_ENTITY, "Cupón agotado"),
    INVALID_ORDER_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "Cambio de estado no permitido"),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.UNPROCESSABLE_ENTITY, "Tipo de imagen no admitido"),
    UNSUPPORTED_ATTACHMENT_TYPE(HttpStatus.UNPROCESSABLE_ENTITY, "Tipo de adjunto no admitido"),
    DANGEROUS_ATTACHMENT(HttpStatus.UNPROCESSABLE_ENTITY, "Adjunto potencialmente peligroso"),
    ATTACHMENT_TOO_LARGE(HttpStatus.UNPROCESSABLE_ENTITY, "Adjunto demasiado grande"),
    CONVERSATION_CLOSED(HttpStatus.UNPROCESSABLE_ENTITY, "La conversación está cerrada"),
    IMAGE_TOO_LARGE(HttpStatus.UNPROCESSABLE_ENTITY, "Imagen demasiado grande"),
    IMAGE_TOO_SMALL(HttpStatus.UNPROCESSABLE_ENTITY, "Imagen demasiado pequeña"),
    FILE_IN_USE(HttpStatus.UNPROCESSABLE_ENTITY, "El archivo está en uso"),
    LAST_ADMIN(HttpStatus.UNPROCESSABLE_ENTITY, "Es el último administrador activo"),
    EMAIL_NOT_VERIFIED_BY_PROVIDER(HttpStatus.UNPROCESSABLE_ENTITY, "El proveedor no verificó el correo"),

    // --- 429 -----------------------------------------------------------------
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos"),

    // --- 500 / 503 -----------------------------------------------------------
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible"),
    GOOGLE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Acceso con Google no configurado");

    private final HttpStatus estado;
    private final String titulo;

    CodigoError(HttpStatus estado, String titulo) {
        this.estado = estado;
        this.titulo = titulo;
    }

    public HttpStatus estado() {
        return estado;
    }

    public String titulo() {
        return titulo;
    }

    /** Segmento del URI {@code type}: {@code INSUFFICIENT_STOCK} -> {@code insufficient-stock}. */
    public String segmentoTipo() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
