# ADR-0011: El dominio habla español; tablas en singular con `id_<tabla>`

**Estado:** aceptado · **Reemplaza a:** [ADR-0010](0010-idioma-api-espanol-documentacion.md)

## Contexto

ADR-0010 eligió inglés para la superficie de la API. Su argumento era concreto
y sigue siendo válido: **`errors[].field` tiene que coincidir exactamente con
el nombre del campo que el formulario envió**, o el resaltado por campo no
funciona.

Lo que ese ADR asumió, sin decirlo, es que los formularios estarían en inglés.
No es así: el panel y la tienda se escriben en español, los lee gente que
trabaja en español, y el modelo de dominio ya está redactado en español. Con
ADR-0010 vigente, cada campo pasaba por una traducción —`precioAnterior` en el
documento, `compareAtPrice` en el JSON, `compare_at_price` en la tabla— y esa
traducción hay que sostenerla a mano en el mapeador, en el contrato y en la
cabeza de quien lee.

El requisito explícito es que **modelos y base de datos estén en español**, con
una convención fija para las claves.

## Decisión

**Todo el dominio en español, en las cuatro capas.** El invariante de ADR-0010
se conserva: el campo JSON, el campo del formulario y el `field` del error
siguen siendo la misma cadena — solo que ahora esa cadena es `precioAnterior`.

| Qué | Convención | Ejemplo |
| --- | --- | --- |
| Tabla | español, **singular**, `snake_case` | `producto`, `item_carrito`, `archivo_variante` |
| **Clave primaria** | **`id_<tabla>`** | `producto.id_producto` |
| **Clave foránea** | **`fk_id_<tabla_referenciada>`** | `producto.fk_id_subcategoria` |
| Columnas | español, `snake_case` | `precio_anterior`, `calificacion_promedio` |
| Restricciones | `pk_`, `uq_`, `fk_`, `ck_`, `ix_` + tabla | `uq_producto_sku`, `ck_producto_precio` |
| Entidad JPA | español | `Producto`, `ItemCarrito` |
| Repositorio / servicio / controlador | español, sufijo español | `ProductoRepositorio`, `ProductoServicio`, `ProductoControlador` |
| DTO de entrada / salida | sufijo `Peticion` / `Respuesta` | `CrearProductoPeticion`, `ProductoDetalleRespuesta` |
| Rutas | español | `/api/v1/admin/productos` |
| Campos JSON | español, `camelCase` | `precioAnterior`, `subcategoriaId` |
| Valores de enum | español, mayúsculas | `PORCENTAJE`, `MONTO_FIJO`, `ACTIVO` |
| `code` de error | **inglés**, mayúsculas | `INSUFFICIENT_STOCK` |

### Por qué las tablas van en singular

Porque es lo que hace que la regla `id_<tabla>` sea literal y no tenga
excepciones. Con tablas en plural habría que escribir `productos.id_producto`,
y entonces la convención ya no se deriva del nombre de la tabla: hay que
recordar singularizarlo, y alguien escribirá `id_productos` el primer día.

### Por qué `fk_id_<tabla>` y no `id_<tabla>` a secas

Porque el prefijo dice, en la propia columna, que ese valor **no es de esta
fila**. En un `SELECT` con tres `JOIN`, `fk_id_categoria` se distingue de
`id_categoria` sin mirar el `FROM`. El coste es tres caracteres.

Consecuencia que hay que aceptar: una `JOIN` nunca es `USING (id_categoria)`,
siempre es `ON s.fk_id_categoria = c.id_categoria`. Es más verboso y es
explícito sobre qué lado manda.

### Por qué el `code` de error se queda en inglés

Es la única excepción, y es deliberada. `code` no es un término del negocio: es
una constante de protocolo, del mismo tipo que `invalid_grant` en OAuth o que
el nombre de un estado HTTP. Ya está publicado en
[catalogo-errores.md](../contrato/catalogo-errores.md), referenciado desde los
dos frontends y desde las reglas de negocio. Traducirlo no aporta nada a quien
lo lee —`INSUFFICIENT_STOCK` se entiende igual— y obliga a tocar cada
referencia cruzada del proyecto.

Lo que sí va en español es todo lo que lee una persona: `title`, `detail`, y
los `errors[].message`.

## Consecuencias

- **Hay que renombrar el código existente**: catálogo y carrito pasan de
  `Product`/`Cart` a `Producto`/`Carrito`. Es la mayor parte del coste de este
  ADR y se paga una sola vez, ahora, mientras el proyecto cabe en una tarde.
- **Las migraciones `V001`–`V003` se reescriben en lugar de añadirse.** Va
  contra la regla de que una migración aplicada no se toca, y es correcto
  aquí: no hay ningún entorno desplegado, solo una base de desarrollo
  desechable. La regla vuelve a regir desde `V001` de este esquema. Recrear la
  base local: `docker compose -f deploy/docker-compose.dev.yml down -v`.
- **La tabla «Nombres en la API» de [modelo-dominio.md](../negocio/modelo-dominio.md)
  desaparece.** Ya no hay dos nombres que correlacionar; ese documento nombra
  directamente lo que existe.
- **El contrato `openapi.yaml` se reescribe.** Ningún cliente lo consume
  todavía.
- El invariante de ADR-0010 —`field` idéntico al campo enviado— **sigue
  vigente** y es lo único de ese ADR que sobrevive intacto.
