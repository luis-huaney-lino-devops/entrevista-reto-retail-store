# Catálogo de errores

Todos los errores que la API puede devolver. `code` es el contrato estable y
legible por máquina: los clientes se ramifican por él y **nunca** por `title` ni
`detail`, que pueden reescribirse o traducirse en cualquier momento.

Añadir un código no rompe nada. Quitar uno, o cambiar el estado HTTP de uno
existente, sí rompe y exige versionar la API.

**Formato: RFC 9457 Problem Details**, `application/problem+json`. Spring lo
produce de forma nativa con `ProblemDetail`.

**Idioma** ([ADR-0011](../adr/0011-dominio-en-espanol-convencion-nombres.md)):
el sobre va en inglés porque es estructura del protocolo —`type`, `title`,
`status`, `detail`, `instance`, más `code`, `correlationId` y
`errors[].field`/`message`—; los **campos extra con datos del negocio van en
español**, igual que el resto de la API. Lo que lee una persona —`title`,
`detail`, `errors[].message`— va siempre en español.

```json
{
  "type": "https://retailstore.dev/errors/insufficient-stock",
  "title": "Stock insuficiente",
  "status": 409,
  "detail": "Solo quedan 3 unidades de «Mochila Urbana».",
  "instance": "/api/v1/carritos/8f1e.../items",
  "code": "INSUFFICIENT_STOCK",
  "correlationId": "0f9c2b1e-4a7d-4c11-9b2e-8d3f1a6c5e90"
}
```

---

## Validación — `400`

| `code` | Cuándo | `errors[]` |
| --- | --- | --- |
| `VALIDATION_ERROR` | Cualquier fallo estructural: campo obligatorio ausente, tipo incorrecto, formato inválido, fuera de rango, campo de ordenamiento desconocido, `pageSize` sobre el máximo | sí |
| `MALFORMED_REQUEST` | El cuerpo no es JSON analizable, o un parámetro no se puede convertir | no |

`VALIDATION_ERROR` reporta **todos** los fallos de campo a la vez, no el
primero. Es lo que permite a un formulario resaltar cada campo erróneo en una
sola ida y vuelta.

```json
{
  "status": 400,
  "code": "VALIDATION_ERROR",
  "detail": "La petición contiene 2 campos inválidos.",
  "errors": [
    { "field": "precio", "message": "debe ser mayor que cero" },
    { "field": "nombre", "message": "es obligatorio" }
  ]
}
```

`field` usa la ruta JSON tal como la envió el cliente (`subcategoriaId`, no
`subcategoria.id`), para que pueda mapearla a su campo de entrada sin traducir.
El `message` va en español: lo lee una persona.

---

## Autenticación — `401`

| `code` | Cuándo |
| --- | --- |
| `UNAUTHENTICATED` | Sin token, token mal formado, firma inválida, token vencido, o audiencia incorrecta |
| `INVALID_CREDENTIALS` | Correo o contraseña incorrectos, o cuenta desactivada |
| `INVALID_PROVIDER_TOKEN` | El ID token de Google no supera la verificación: firma, emisor, audiencia o vigencia |

`INVALID_CREDENTIALS` es **un solo código para los tres casos** (RN-067):
correo inexistente, contraseña equivocada y cuenta desactivada. Mismo mensaje,
mismo tiempo de respuesta. Distinguirlos convierte el endpoint en un
comprobador de quién tiene cuenta en la tienda.

La única distinción permitida es una cabecera `WWW-Authenticate` con
`error="invalid_token"` para un token vencido: eso le dice a un *cliente* que
refresque sin decirle a un *atacante* nada sobre ninguna cuenta.

---

## Autorización — `403`

| `code` | Cuándo |
| --- | --- |
| `FORBIDDEN` | Autenticado, pero sin permiso para esta operación |
| `WRONG_AUDIENCE` | Token válido pero de la audiencia equivocada: un token de cliente contra el panel, o al revés |

`WRONG_AUDIENCE` existe separado de `FORBIDDEN` a propósito: **es una señal de
alarma, no un permiso que falta.** Un token de cliente llegando al panel
significa que alguien está probando, o que una aplicación está mal configurada.
Merece su propio código para poder alertarlo.

Un `403` nunca revela si el recurso existe: la comprobación de permisos va
antes que la de existencia.

---

## No encontrado — `404`

| `code` | Cuándo |
| --- | --- |
| `PRODUCT_NOT_FOUND` | No hay producto con ese id o slug, o no es visible para quien pregunta |
| `CATEGORY_NOT_FOUND` | |
| `SUBCATEGORY_NOT_FOUND` | Al consultar o al asignar un producto |
| `BRAND_NOT_FOUND` | |
| `CART_NOT_FOUND` | El UUID no corresponde a ningún carrito |
| `CART_ITEM_NOT_FOUND` | La línea no está en ese carrito |
| `ORDER_NOT_FOUND` | No hay orden con ese id |
| `COUPON_NOT_FOUND` | El código no existe |
| `FILE_NOT_FOUND` | |
| `ADMIN_NOT_FOUND` | No hay administrador con ese id o usuario |
| `ADDRESS_NOT_FOUND` | No hay dirección con ese id **en la cuenta que pregunta** |
| `DISTRICT_NOT_FOUND` | El `distritoId` de una dirección no existe |
| `RESOURCE_NOT_FOUND` | La ruta no existe |

**La dirección de otro cliente es `404`, no `403`.** Un `403` confirmaría que
esa dirección existe, y con ids correlativos eso es un contador de direcciones
de la tienda. La consulta va acotada por cliente para que no haya forma de
equivocarse.

**Un producto inactivo es `404` en la tienda y `200` en el panel.** No es una
incoherencia: para el comprador ese producto no existe, y decirle «existe pero
está oculto» filtra el catálogo interno. El administrador sí necesita verlo.

---

## Conflicto — `409`

| `code` | Cuándo | Regla |
| --- | --- | --- |
| `DUPLICATE_SKU` | Ya existe un producto con ese SKU | RN-002 |
| `DUPLICATE_SLUG` | Slug manual ya en uso | RN-008 |
| `DUPLICATE_NAME` | Nombre de marca/categoría/subcategoría repetido | RN-010 |
| `DUPLICATE_EMAIL` | *(interno; el registro nunca lo devuelve — ver RN-061)* | RN-060 |
| `DUPLICATE_COUPON_CODE` | Ya existe un cupón con ese código | — |
| `DUPLICATE_USERNAME` | Ya existe un administrador con ese usuario | — |
| `HAS_DEPENDENTS` | Desactivar algo que tiene contenido | RN-011 |
| `INSUFFICIENT_STOCK` | La cantidad pedida supera el stock | RN-030, RN-051 |
| `CONCURRENT_MODIFICATION` | Otro cambió el recurso entre la lectura y la escritura | — |

`INSUFFICIENT_STOCK` dice cuánto hay, porque «no hay suficiente» sin cifra no
es accionable:

```json
{
  "status": 409,
  "code": "INSUFFICIENT_STOCK",
  "detail": "Solo quedan 3 unidades de «Mochila Urbana».",
  "productoId": 42,
  "solicitado": 5,
  "disponible": 3
}
```

`HAS_DEPENDENTS` lista qué lo bloquea en `bloqueantes`, hasta 50 elementos,
con `totalBloqueantes` para el recuento completo. Una lista sin límite en un cuerpo de error es un vector de
denegación de servicio contra tus propios logs.

---

## Reglas de negocio — `422`

Bien formado, autorizado, y rechazado por política.

| `code` | Cuándo | Regla |
| --- | --- | --- |
| `SUBCATEGORY_INACTIVE` | Asignar o publicar un producto en una subcategoría inactiva | RN-001 |
| `CATEGORY_INACTIVE` | Activar una subcategoría dentro de una categoría inactiva | RN-012 |
| `SKU_IMMUTABLE` | La actualización intenta cambiar el SKU | RN-002 |
| `INVALID_COMPARE_PRICE` | `precioAnterior` no es mayor que `precio` | RN-004 |
| `PRODUCT_INACTIVE` | Agregar al carrito o comprar un producto inactivo | RN-006, RN-051 |
| `PRODUCT_REQUIRES_IMAGE` | Activar un producto sin imágenes | RN-009 |
| `INVALID_PRICE_RANGE` | `precioMin > precioMax` | RN-022 |
| `COUPON_NOT_APPLICABLE` | Cupón inactivo o fuera de vigencia | RN-040 |
| `COUPON_MIN_NOT_MET` | El subtotal no alcanza el mínimo | RN-041 |
| `COUPON_EXHAUSTED` | Se agotaron los usos | RN-042 |
| `INVALID_ORDER_TRANSITION` | Cambio de estado no permitido | RN-054 |
| `EMAIL_NOT_VERIFIED_BY_PROVIDER` | Google no confirma el correo al vincular | RN-064 |
| `LAST_LOGIN_METHOD` | Quitaría la única forma de entrar | RN-065 |
| `EMAIL_NOT_VERIFIED` | Operación que exige correo verificado | RN-063 |
| `UNSUPPORTED_IMAGE_TYPE` | El contenido no es una imagen admitida | RN-070 |
| `IMAGE_TOO_LARGE` / `IMAGE_TOO_SMALL` | Fuera de los límites de tamaño o dimensión | RN-071 |
| `FILE_IN_USE` | Borrar un archivo referenciado por un producto, marca o categoría | RN-075 |
| `LAST_ADMIN` | Desactivar al último administrador activo | — |

> `FILE_NOT_READY` no existe: el procesamiento de imagen es síncrono, así que
> un archivo registrado ya está listo. Si algún día se encola, vuelve.

### Campos extra de cada `422`

Los nombres son parte del contrato: sin fijarlos, cada implementación elige
distinto y el cliente no puede tratarlos de forma uniforme. El patrón es
siempre `valorEnviado` más el límite violado.

| `code` | Campos extra |
| --- | --- |
| `COUPON_MIN_NOT_MET` | `subtotalActual`, `subtotalMinimo` |
| `INVALID_COMPARE_PRICE` | `precio`, `precioAnterior` |
| `INVALID_PRICE_RANGE` | `precioMinimo`, `precioMaximo` |
| `IMAGE_TOO_LARGE` | `valorEnviado`, `maximoPermitido`, `dimension` (`bytes` \| `ancho` \| `alto` \| `megapixeles`) |
| `IMAGE_TOO_SMALL` | `valorEnviado`, `minimoPermitido` |
| `INSUFFICIENT_STOCK` | `productoId`, `solicitado`, `disponible` |
| `HAS_DEPENDENTS` | `totalBloqueantes`, `bloqueantes` (hasta 50 nombres) |
| `SUBCATEGORY_INACTIVE` | `subcategoriaId`, `subcategoriaNombre` |
| `CATEGORY_INACTIVE` | `categoriaId`, `categoriaNombre` |
| `PRODUCT_INACTIVE` | `productoId`, `productoNombre` |
| `PRODUCT_REQUIRES_IMAGE` | `productoId` |
| `DUPLICATE_SKU` | `sku` |
| `TOO_MANY_REQUESTS` | `reintentarEn` (segundos) |
| `INVALID_ORDER_TRANSITION` | `estadoActual`, `estadoSolicitado`, `transicionesPermitidas` |

`maximoPermitido` es siempre una magnitud comparable con `valorEnviado`, nunca
un parámetro de la regla, para que el cliente pueda escribir
`valorEnviado > maximoPermitido` sin saber qué regla se violó.

**Los nombres de campo coinciden exactamente con los del cuerpo de la
petición** ([ADR-0011](../adr/0011-dominio-en-espanol-convencion-nombres.md)).
Si `errors[].field` no coincide con el campo que el formulario envió, el
resaltado por campo no funciona y el usuario ve un error sin saber qué
corregir. Eso es lo único que ADR-0010 dejó fijado y que sigue vigente; lo que
cambió es que esa cadena común ahora es española.

Los importes van como número JSON con dos decimales, igual que en el cuerpo
principal: un `subtotalActual` que salga como cadena es un defecto de
serialización, no un detalle cosmético.

`CONCURRENT_MODIFICATION` por violación de restricción añade `restriccion` con
el nombre de la restricción de base de datos: es para el log, no para el
usuario.

**El orden de las claves no es contrato.** Un objeto JSON es una colección
desordenada (RFC 8259). Lo que sí es contrato es el **conjunto exacto**: un
campo de más también es un defecto, porque un cliente que aprende a leerlo
termina dependiendo de él.

---

## Límite de peticiones — `429`

| `code` | Cuándo | Regla |
| --- | --- | --- |
| `TOO_MANY_REQUESTS` | Se superó el límite de intentos | RN-068 |

Siempre con cabecera `Retry-After` en segundos. Sin ella, el cliente no sabe
cuándo reintentar y lo hace de inmediato, empeorando el problema.

---

## Servidor — `500`, `503`

| `code` | Cuándo |
| --- | --- |
| `INTERNAL_ERROR` | Cualquier fallo no manejado |
| `SERVICE_UNAVAILABLE` | Una dependencia no responde: base de datos, R2, SMTP |
| `GOOGLE_NOT_CONFIGURED` | Falta `GOOGLE_CLIENT_ID` en el entorno |

`GOOGLE_NOT_CONFIGURED` es `503` y no `500` porque el servicio no está roto:
falta configurarlo, y funcionará en cuanto se haga. El `detail` nombra la
variable que falta, para que nadie depure durante una hora algo que es una
línea de entorno.

El cuerpo de `INTERNAL_ERROR` es fijo y no lleva más que el identificador de
correlación (RN-080):

```json
{
  "status": 500,
  "code": "INTERNAL_ERROR",
  "title": "Error interno del servidor",
  "detail": "Ocurrió un error inesperado. Cita el identificador de correlación al reportarlo.",
  "correlationId": "0f9c2b1e-4a7d-4c11-9b2e-8d3f1a6c5e90"
}
```

La excepción y su traza se registran contra ese mismo identificador. De eso
trata RN-081: el usuario reporta una cadena opaca y esa cadena lleva
directamente a la línea de log.

Nada más puede devolver `500`. Un `500` que una regla debió atrapar es un
defecto de la capa de servicio, no un resultado aceptado.

**Un fallo de correo o de R2 nunca produce `500` al usuario** si la operación
principal tuvo éxito: esos efectos van después del commit y sus fallos se
registran, no se propagan (RN-083).

---

## Requisitos de implementación

1. **Un solo manejador**: `@RestControllerAdvice`. Ningún `try`/`catch` en los
   controladores.
2. **Mapear en el borde.** Las excepciones de dominio llevan un `code` y los
   datos que el mensaje necesita, y no saben nada de HTTP.
3. **Por omisión, `500`.** Una excepción no reconocida se convierte en
   `INTERNAL_ERROR`. Nunca dejar que una excepción del framework se serialice
   sola.
4. **Traducir violaciones de restricción por nombre de restricción**
   (`uq_products_sku` → `DUPLICATE_SKU`), nunca analizando el texto del
   mensaje del driver: ese texto cambia entre versiones.
5. **Cuidado con `AccessDeniedException`.** Un
   `@ExceptionHandler(Exception.class)` la convierte en `500` en cuanto entre
   Spring Security. Hay que manejarla explícitamente antes del catch-all, o el
   `403` nunca llegará.
