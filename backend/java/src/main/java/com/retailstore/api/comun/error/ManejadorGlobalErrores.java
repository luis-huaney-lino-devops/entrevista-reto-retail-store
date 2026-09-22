package com.retailstore.api.comun.error;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Punto único de traducción excepción -&gt; HTTP. Ningún controlador lleva
 * try/catch.
 *
 * <p>Toda respuesta de error sale como {@code application/problem+json}
 * (RFC 9457) con la misma forma: {@code type}, {@code title}, {@code status},
 * {@code detail}, {@code code} y {@code correlationId}.
 *
 * <p><strong>Orden de los manejadores.</strong> Spring elige el manejador más
 * específico, no el primero declarado, pero eso solo funciona si el tipo
 * específico está declarado. {@link AccessDeniedException} tiene manejador
 * propio justamente por eso: sin él, el catch-all de {@link Exception} la
 * convertiría en un 500 y ningún 403 llegaría nunca al cliente.
 */
@RestControllerAdvice
public class ManejadorGlobalErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);
    private static final String BASE_TIPO = "https://retailstore.dev/errors/";

    /**
     * Violación de restricción -&gt; código, <strong>por nombre de restricción</strong>.
     * Nunca analizando el texto del driver: ese texto cambia entre versiones de
     * PostgreSQL y dejaría de coincidir en una actualización menor.
     */
    private static final Map<String, CodigoError> POR_RESTRICCION = Map.ofEntries(
            Map.entry("uq_producto_sku", CodigoError.DUPLICATE_SKU),
            Map.entry("uq_producto_slug", CodigoError.DUPLICATE_SLUG),
            Map.entry("uq_marca_slug", CodigoError.DUPLICATE_SLUG),
            Map.entry("uq_categoria_slug", CodigoError.DUPLICATE_SLUG),
            Map.entry("uq_subcategoria_slug", CodigoError.DUPLICATE_SLUG),
            Map.entry("uq_marca_nombre", CodigoError.DUPLICATE_NAME),
            Map.entry("uq_categoria_nombre", CodigoError.DUPLICATE_NAME),
            Map.entry("uq_subcategoria_nombre", CodigoError.DUPLICATE_NAME),
            Map.entry("uq_cupon_codigo", CodigoError.DUPLICATE_COUPON_CODE),
            Map.entry("uq_administrador_usuario", CodigoError.DUPLICATE_USERNAME),
            Map.entry("uq_item_carrito", CodigoError.CONCURRENT_MODIFICATION),
            Map.entry("uq_opinion_cliente_producto", CodigoError.DUPLICATE_REVIEW),
            Map.entry("fk_producto_subcategoria", CodigoError.HAS_DEPENDENTS),
            Map.entry("fk_subcategoria_categoria", CodigoError.HAS_DEPENDENTS),
            Map.entry("fk_producto_marca", CodigoError.HAS_DEPENDENTS),
            Map.entry("fk_producto_imagen_archivo", CodigoError.FILE_IN_USE),
            Map.entry("fk_marca_archivo", CodigoError.FILE_IN_USE),
            Map.entry("fk_categoria_archivo", CodigoError.FILE_IN_USE),
            Map.entry("fk_subcategoria_archivo", CodigoError.FILE_IN_USE));

    // ------------------------------------------------------------------ dominio

    /**
     * Devuelve {@code ResponseEntity} y no el {@code ProblemDetail} a secas por
     * un solo caso: el {@code 429} tiene que llevar {@code Retry-After}. Sin esa
     * cabecera el cliente reintenta de inmediato y empeora justo lo que el
     * límite intenta contener, y es lo que exige RN-068.
     */
    @ExceptionHandler(ExcepcionAplicacion.class)
    public ResponseEntity<ProblemDetail> manejarAplicacion(ExcepcionAplicacion ex) {
        ProblemDetail problema = problema(ex.codigo(), ex.getMessage());
        ex.datos().forEach(problema::setProperty);

        HttpHeaders cabeceras = new HttpHeaders();
        if (ex.datos().get("reintentarEn") instanceof Number segundos) {
            cabeceras.set(HttpHeaders.RETRY_AFTER, String.valueOf(segundos.longValue()));
        }
        return new ResponseEntity<>(problema, cabeceras, ex.codigo().estado());
    }

    // ------------------------------------------------------------- seguridad

    /**
     * Un 403 nunca revela si el recurso existe: la comprobación de permisos va
     * antes que la de existencia, así que aquí no hay nada que contar.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail manejarAccesoDenegado(AccessDeniedException ex) {
        return problema(CodigoError.FORBIDDEN, "No tienes permiso para esta operación.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail manejarNoAutenticado(AuthenticationException ex) {
        return problema(CodigoError.UNAUTHENTICATED, "Necesitas iniciar sesión.");
    }

    // ------------------------------------------------------------- persistencia

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail manejarIntegridad(DataIntegrityViolationException ex) {
        String restriccion = nombreRestriccion(ex);
        CodigoError codigo = POR_RESTRICCION.get(restriccion);
        if (codigo == null) {
            // Una restricción sin traducción es un fallo del servicio, no del
            // usuario: la base no debería ser quien detecte esto.
            log.error("Restricción sin traducir: {}", restriccion, ex);
            return problema(CodigoError.INTERNAL_ERROR, detalleInterno());
        }
        ProblemDetail problema = problema(codigo, "La operación choca con un registro existente.");
        problema.setProperty("restriccion", restriccion);
        return problema;
    }

    /**
     * Otro modificó el recurso entre la lectura y la escritura. No es un error
     * del cliente: es una carrera, y la respuesta correcta es que vuelva a
     * cargar y reintente.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail manejarConcurrencia(OptimisticLockingFailureException ex) {
        return problema(CodigoError.CONCURRENT_MODIFICATION,
                "Otro usuario modificó este registro. Recarga y vuelve a intentarlo.");
    }

    /** Validación sobre parámetros sueltos (@Validated en la clase). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail manejarViolacionParametros(ConstraintViolationException ex) {
        List<ErrorCampo> errores = ex.getConstraintViolations().stream()
                .map(v -> new ErrorCampo(ultimoSegmento(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return problemaValidacion(errores);
    }

    // --------------------------------------------------------------- catch-all

    @ExceptionHandler(Exception.class)
    public ProblemDetail manejarInesperado(Exception ex) {
        log.error("Error no controlado", ex);
        return problema(CodigoError.INTERNAL_ERROR, detalleInterno());
    }

    // ------------------------------------------- excepciones propias de Spring

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
        List<ErrorCampo> errores = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(fallo -> errores.add(desdeFieldError(fallo)));
        ex.getBindingResult().getGlobalErrors().forEach(fallo ->
                errores.add(new ErrorCampo(fallo.getObjectName(), fallo.getDefaultMessage())));
        return handleExceptionInternal(ex, problemaValidacion(errores), cabeceras,
                CodigoError.VALIDATION_ERROR.estado(), peticion);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
        List<ErrorCampo> errores = new ArrayList<>();
        for (ParameterValidationResult resultado : ex.getParameterValidationResults()) {
            if (resultado instanceof ParameterErrors erroresParametro) {
                erroresParametro.getFieldErrors().forEach(fallo -> errores.add(desdeFieldError(fallo)));
            } else {
                String parametro = resultado.getMethodParameter().getParameterName();
                resultado.getResolvableErrors().forEach(fallo ->
                        errores.add(new ErrorCampo(parametro, fallo.getDefaultMessage())));
            }
        }
        return handleExceptionInternal(ex, problemaValidacion(errores), cabeceras,
                CodigoError.VALIDATION_ERROR.estado(), peticion);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
        // El mensaje original cita la clase Java y la posición del parser: eso
        // describe nuestra implementación, no el error del cliente (RN-080).
        return handleExceptionInternal(ex,
                problema(CodigoError.MALFORMED_REQUEST, "El cuerpo de la petición no es JSON válido."),
                cabeceras, CodigoError.MALFORMED_REQUEST.estado(), peticion);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            org.springframework.beans.TypeMismatchException ex,
            HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
        return handleExceptionInternal(ex,
                problema(CodigoError.MALFORMED_REQUEST,
                        "El parámetro '" + ex.getPropertyName() + "' no tiene un valor válido."),
                cabeceras, CodigoError.MALFORMED_REQUEST.estado(), peticion);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
        return handleExceptionInternal(ex,
                problema(CodigoError.RESOURCE_NOT_FOUND, "La ruta solicitada no existe."),
                cabeceras, CodigoError.RESOURCE_NOT_FOUND.estado(), peticion);
    }

    /**
     * El límite del contenedor, no el de negocio: aquí el archivo ni siquiera
     * llegó entero. {@code ProcesadorImagen} aplica el límite de RN-071 sobre
     * los archivos que sí llegan.
     */
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
        ProblemDetail problema = problema(CodigoError.IMAGE_TOO_LARGE,
                "El archivo supera el tamaño máximo permitido.");
        problema.setProperty("dimension", "bytes");
        return handleExceptionInternal(ex, problema, cabeceras, CodigoError.IMAGE_TOO_LARGE.estado(), peticion);
    }

    /**
     * Red de seguridad: toda respuesta de error sale con {@code code} y
     * {@code correlationId}, también las que construye Spring por su cuenta
     * -método no permitido, tipo de contenido no admitido, parámetro ausente-.
     *
     * <p>Sin esto habría dos formas de error en la misma API: la nuestra y la
     * del framework. Un cliente que se ramifica por {@code code} fallaría justo
     * en los casos que no probamos.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object cuerpo, HttpHeaders cabeceras, HttpStatusCode estado, WebRequest peticion) {
        ResponseEntity<Object> respuesta = super.handleExceptionInternal(ex, cuerpo, cabeceras, estado, peticion);
        if (respuesta != null && respuesta.getBody() instanceof ProblemDetail problema) {
            completar(problema, estado);
        }
        return respuesta;
    }

    /** Añade code y correlationId si no los tiene ya. */
    private static void completar(ProblemDetail problema, HttpStatusCode estado) {
        Map<String, Object> propiedades = problema.getProperties();
        if (propiedades != null && propiedades.containsKey("code")) {
            return;
        }
        CodigoError codigo = porEstado(estado);
        problema.setProperty("code", codigo.name());
        problema.setProperty("correlationId", Correlacion.actual());
        if (problema.getType() == null || "about:blank".equals(problema.getType().toString())) {
            problema.setType(URI.create(BASE_TIPO + codigo.segmentoTipo()));
        }
    }

    /**
     * Código genérico para un estado HTTP. Solo lo usan las excepciones del
     * propio framework, que no tienen un código de negocio que las describa.
     */
    private static CodigoError porEstado(HttpStatusCode estado) {
        return switch (estado.value()) {
            case 400 -> CodigoError.MALFORMED_REQUEST;
            case 401 -> CodigoError.UNAUTHENTICATED;
            case 403 -> CodigoError.FORBIDDEN;
            case 404 -> CodigoError.RESOURCE_NOT_FOUND;
            case 429 -> CodigoError.TOO_MANY_REQUESTS;
            case 503 -> CodigoError.SERVICE_UNAVAILABLE;
            default -> estado.is4xxClientError() ? CodigoError.VALIDATION_ERROR : CodigoError.INTERNAL_ERROR;
        };
    }

    // ------------------------------------------------------------------ apoyo

    private static ProblemDetail problemaValidacion(List<ErrorCampo> errores) {
        String detalle = errores.size() == 1
                ? "La petición contiene 1 campo inválido."
                : "La petición contiene " + errores.size() + " campos inválidos.";
        ProblemDetail problema = problema(CodigoError.VALIDATION_ERROR, detalle);
        problema.setProperty("errors", errores);
        return problema;
    }

    private static ProblemDetail problema(CodigoError codigo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(codigo.estado(), detalle);
        problema.setType(URI.create(BASE_TIPO + codigo.segmentoTipo()));
        problema.setTitle(codigo.titulo());
        problema.setProperty("code", codigo.name());
        problema.setProperty("correlationId", Correlacion.actual());
        return problema;
    }

    private static String detalleInterno() {
        return "Ocurrió un error inesperado. Cita el identificador de correlación al reportarlo.";
    }

    private static ErrorCampo desdeFieldError(FieldError fallo) {
        // Un fallo de conversión ("abc" en un número) trae un mensaje técnico
        // generado por el binder: se reemplaza por uno que se pueda leer.
        String mensaje = fallo.isBindingFailure() ? "tiene un formato inválido" : fallo.getDefaultMessage();
        return new ErrorCampo(fallo.getField(), mensaje);
    }

    private static String ultimoSegmento(String ruta) {
        int punto = ruta.lastIndexOf('.');
        return punto < 0 ? ruta : ruta.substring(punto + 1);
    }

    /** Nombre de la restricción violada, en minúsculas, o cadena vacía si no se pudo obtener. */
    private static String nombreRestriccion(DataIntegrityViolationException ex) {
        Throwable causa = ex;
        while (causa != null) {
            if (causa instanceof org.hibernate.exception.ConstraintViolationException hibernate) {
                String nombre = hibernate.getConstraintName();
                return nombre == null ? "" : nombre.toLowerCase(Locale.ROOT);
            }
            causa = causa.getCause();
        }
        return "";
    }

    /** Un fallo por campo. La forma es contrato: {@code field} + {@code message}. */
    public record ErrorCampo(String field, String message) {
    }
}
