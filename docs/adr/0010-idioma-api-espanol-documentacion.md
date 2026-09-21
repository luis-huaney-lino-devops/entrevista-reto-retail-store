# ADR-0010: La API habla inglés; la documentación, español

> **Reemplazado por [ADR-0011](0011-dominio-en-espanol-convencion-nombres.md).**
> El invariante sobre `errors[].field` sigue vigente; el idioma elegido cambió
> a español en todas las capas.

## Contexto
El proyecto se documenta en español. El código existente —entidades, DTOs,
rutas y campos JSON— está en inglés: `compareAtPrice`, `categoryId`,
`imageUrl`, `/api/v1/products`.

Al escribir la especificación nueva aparecieron campos en español
(`precioAnterior`, `subcategoriaId`) y rutas mezcladas (`/products` junto a
`/auth/registro`, `/admin/archivos`). Eso produce un problema concreto y no
estético: **el `field` de un error de validación tiene que coincidir
exactamente con el nombre del campo que el formulario envió**. Si la API
responde `field: "precioAnterior"` y el formulario tiene `compareAtPrice`, el
resaltado por campo no funciona y el usuario ve un error genérico sin saber
qué corregir.

## Decisión

**Superficie de la API en inglés. Documentación y mensajes para personas en
español.**

| Qué | Idioma | Ejemplo |
| --- | --- | --- |
| Rutas | inglés | `/api/v1/products`, `/api/v1/auth/register` |
| Campos JSON | inglés | `compareAtPrice`, `subcategoryId`, `shortDescription` |
| Valores de enum | inglés, mayúsculas | `ACTIVE`, `PENDING`, `PERCENT` |
| `code` de error | inglés, mayúsculas | `INSUFFICIENT_STOCK` |
| `errors[].field` | inglés, **idéntico al campo enviado** | `compareAtPrice` |
| Identificadores de código | inglés | `ProductService`, `findBySlug` |
| Nombres de tabla y columna | inglés | `products.compare_at_price` |
| `title` y `detail` de un error | **español** | «Stock insuficiente» |
| Textos de la interfaz | **español** | «Agregar al carrito» |
| Documentación (`docs/`) | **español** | este archivo |
| Comentarios de código | **español** | |
| Nombres de prueba | español, con `RN-###` | `crear_conSkuDuplicado_lanzaDuplicateSku_RN002` |

## Consecuencias

- **No hay que renombrar nada del código existente.** El contrato actual ya es
  correcto en este aspecto; lo que había que corregir eran los documentos
  nuevos.
- El modelo de dominio (`docs/negocio/modelo-dominio.md`) describe los
  conceptos en español —`precioAnterior`, `subcategoría`— porque es un
  documento de negocio, no un contrato. Lleva una tabla de correspondencia con
  los nombres reales de la API para que nadie tenga que adivinarla.
- Las rutas de autenticación pasan a inglés: `/auth/register`, `/auth/login`,
  `/auth/forgot-password`, `/auth/reset-password`, `/auth/verify-email`. La
  mezcla anterior era el peor de los dos mundos.
- **Ventaja secundaria:** el inglés en la API deja la puerta abierta a que la
  consuma alguien que no habla español, sin rehacer el contrato. En una tienda
  es un escenario plausible.
- **Coste:** quien lee el código en español y la API en inglés tiene que hacer
  la traducción mental. La tabla de correspondencia del modelo de dominio
  existe exactamente para eso.
