# Modelo de dominio

Independiente de la tecnología. Sin frameworks, sin SQL, sin nombres de clase.
Si algo aquí menciona una tabla o una anotación, está en el archivo equivocado:
se mueve a [persistencia-postgres.md](../backend/persistencia-postgres.md).

> **Los atributos se nombran aquí en español, y así se llaman también en la
> API, en las tablas y en el código**
> ([ADR-0011](../adr/0011-dominio-en-espanol-convencion-nombres.md)). No hay
> traducción que mantener: `precioAnterior` es `precioAnterior` en los tres
> sitios.
>
> Dos convenciones de la base, para leer sin sorpresas: las tablas van en
> **singular**, la clave primaria es `id_<tabla>` y las foráneas `fk_id_<tabla>`.

---

## Glosario

| Término | Significado |
| --- | --- |
| **Producto** | Artículo a la venta. Un producto = un precio, un stock, un SKU. No tiene variantes (ADR-0007). |
| **SKU** | *Stock Keeping Unit*. Código interno único del producto, visible para el administrador, no para el comprador. |
| **Marca** | Fabricante o sello del producto. Un producto pertenece a una marca, o a ninguna. |
| **Categoría** | Agrupación de primer nivel. Nunca contiene productos directamente. |
| **Subcategoría** | Agrupación de segundo nivel, dentro de una categoría. **Aquí cuelgan los productos.** |
| **Carrito** | Selección temporal de un comprador. Identificado por UUID, sobrevive sin cuenta. |
| **Orden** | Carrito confirmado. Inmutable en sus importes: guarda una foto de precios y nombres. |
| **Cliente** | Persona con cuenta: se registra, entra con correo o con Google, ve sus pedidos. |
| **Administrador** | Quien gestiona el catálogo. Acceso fijo, sin registro ni recuperación (ADR-0008). |
| **Cupón** | Descuento aplicable a un carrito, por porcentaje o monto fijo. |
| **Archivo** | Imagen subida al sistema. Se convierte a WebP y se guarda en R2 (ADR-0009). |

**Dos identidades distintas.** Un **Cliente** es una persona que compra; un
**Administrador** es quien gestiona la tienda. No comparten tabla, ni flujo de
acceso, ni tipo de token. Mezclarlos es la forma habitual de que un bug de
permisos se convierta en un incidente: un `rol` extra en la tabla de clientes
significa que cualquier fallo en el registro público es una escalada a
administrador. Aquí no puede pasar porque no existe ese camino.

---

## Mapa de entidades

```text
      MARCA ──────┐
                  │  0..1
                  ▼
  CATEGORÍA ──< SUBCATEGORÍA ──< PRODUCTO >── ARCHIVO
   (nivel 1)      (nivel 2)          │  1..n    (imágenes)
                                     │
                    ┌────────────────┼────────────────┐
                    │                                 │
              ÍTEM CARRITO                      ÍTEM ORDEN
                    │                                 │
                 CARRITO >── CUPÓN               ORDEN ──< CLIENTE
                    │                                 │      0..1
                    └────────── se convierte en ──────┘

  CLIENTE ──< IDENTIDAD EXTERNA  (Google)
  CLIENTE ──< TOKEN DE UN SOLO USO  (verificación, recuperación)
  ADMINISTRADOR  (aparte, sin relación con CLIENTE)
```

---

## Catálogo

### Marca

| Atributo | Tipo | Obligatorio | Notas |
| --- | --- | --- | --- |
| `id` | identificador | sistema | |
| `nombre` | texto(1..80) | sí | Único. |
| `slug` | texto(1..100) | sistema | Derivado del nombre, único, estable. Ver «Sobre los slugs». |
| `descripcion` | texto(..500) | no | |
| `logo` | Archivo | no | |
| `activa` | booleano | sí | Una marca inactiva no aparece en la tienda. |
| auditoría | | sistema | `creadoEn`, `creadoPor`, `actualizadoEn`, `actualizadoPor`, `version` |

### Categoría (nivel 1)

| Atributo | Tipo | Obligatorio | Notas |
| --- | --- | --- | --- |
| `id` | identificador | sistema | |
| `nombre` | texto(1..80) | sí | Único. |
| `slug` | texto | sistema | Único. |
| `descripcion` | texto(..500) | no | |
| `imagen` | Archivo | no | |
| `orden` | entero | sí | Posición en el menú. Ver «Sobre el orden». |
| `activa` | booleano | sí | |
| auditoría | | sistema | |

**Una categoría no contiene productos.** Solo subcategorías. Es una decisión, no
un descuido: si algunos productos colgaran de la categoría y otros de la
subcategoría, toda consulta de catálogo tendría dos caminos y todo recuento
tendría dos fuentes. Una tienda que necesita «productos sueltos en Tecnología»
crea una subcategoría «General».

### Subcategoría (nivel 2)

| Atributo | Tipo | Obligatorio | Notas |
| --- | --- | --- | --- |
| `id` | identificador | sistema | |
| `categoriaId` | identificador | sí | Su categoría padre. |
| `nombre` | texto(1..80) | sí | Único **dentro de su categoría**, no globalmente. |
| `slug` | texto | sistema | Único globalmente, para que la URL no necesite el padre. |
| `descripcion` | texto(..500) | no | |
| `imagen` | Archivo | no | |
| `orden` | entero | sí | |
| `activa` | booleano | sí | |
| auditoría | | sistema | |

«Accesorios» puede existir bajo Tecnología y bajo Deportes: el nombre es único
por categoría. El slug sí es único globalmente (`accesorios-tecnologia`,
`accesorios-deportes`) para que `/c/accesorios-tecnologia` sea inequívoco.

### Producto

| Atributo | Tipo | Obligatorio | Notas |
| --- | --- | --- | --- |
| `id` | identificador | sistema | |
| `sku` | texto(1..40) | sí | Único. Inmutable tras la creación. |
| `nombre` | texto(1..160) | sí | |
| `slug` | texto | sistema | Único. |
| `descripcionCorta` | texto(..300) | no | Para la tarjeta del listado. |
| `descripcion` | texto(..5000) | no | Para el detalle. |
| `subcategoriaId` | identificador | sí | **Los productos cuelgan de la subcategoría.** |
| `marcaId` | identificador | no | Un producto puede no tener marca. |
| `precio` | decimal(12,2) | sí | Precio vigente, con IGV incluido. Mayor que cero. |
| `precioAnterior` | decimal(12,2) | no | Si existe y es mayor que `precio`, la tienda muestra precio tachado y % de descuento. |
| `stock` | entero | sí | Unidades disponibles. Cero o más. |
| `imagenes` | lista de Archivo | no | Ordenadas; la primera es la principal. |
| `destacado` | booleano | sí | Aparece en la portada. |
| `activo` | booleano | sí | Un producto inactivo no aparece en la tienda ni se puede agregar al carrito. |
| `calificacionPromedio` | decimal(2,1) | sistema | 0.0 a 5.0. Hoy viene del seed; no hay reseñas (fuera de alcance). |
| `calificacionConteo` | entero | sistema | |
| auditoría | | sistema | |

### Archivo (imagen)

| Atributo | Tipo | Obligatorio | Notas |
| --- | --- | --- | --- |
| `id` | identificador | sistema | |
| `clave` | texto | sistema | Ruta dentro del almacén (R2). Opaca para el cliente. |
| `nombreOriginal` | texto | sistema | Lo que el administrador subió, solo para reconocerlo en el panel. |
| `tipoMime` | texto | sistema | Siempre `image/webp` tras el procesamiento. |
| `bytes` | entero | sistema | Tamaño ya optimizado. |
| `ancho` / `alto` | entero | sistema | De la imagen original, tras normalizar. |
| `variantes` | lista | sistema | Tamaños derivados: miniatura, tarjeta, detalle. Ver [archivos-imagenes.md](../backend/archivos-imagenes.md). |
| `subidoEn` / `subidoPor` | | sistema | |

Un archivo es **inmutable**: no se edita, se reemplaza. Cambiar la imagen de un
producto sube una nueva y desreferencia la anterior. Así una URL cacheada nunca
devuelve una imagen distinta de la que se cacheó.

### Sobre los slugs

El `slug` lo genera el sistema a partir del nombre (minúsculas, sin acentos,
guiones) y **no cambia cuando el nombre cambia**. Renombrar «Laptops» a
«Computadoras portátiles» no rompe los enlaces que ya circulan ni el
posicionamiento ganado.

Si el slug derivado ya existe, se añade un sufijo numérico (`laptops-2`). El
administrador puede editarlo manualmente, y entonces es su responsabilidad.

### Sobre el orden

Categorías y subcategorías tienen `orden` explícito porque el criterio de
negocio no es alfabético: «Ofertas» va primero aunque empiece por O. Sin un
campo de orden, la única alternativa es renombrar cosas para que queden en el
sitio deseado, que es exactamente el tipo de apaño que ensucia los datos.

Ante empate de `orden`, se desempata por `nombre` ascendente, para que el
listado sea **total** y la paginación no repita ni salte filas.

---

## Compra

### Carrito

| Atributo | Tipo | Obligatorio | Notas |
| --- | --- | --- | --- |
| `id` | UUID | sistema | Lo conoce el navegador; identifica el carrito sin cuenta. |
| `clienteId` | identificador | no | Se asocia si el comprador inicia sesión. |
| `cuponId` | identificador | no | Como mucho un cupón por carrito. |
| `estado` | `ACTIVO` \| `CONVERTIDO` | sistema | |
| auditoría | | sistema | |

### Ítem de carrito

| Atributo | Tipo | Obligatorio | Notas |
| --- | --- | --- | --- |
| `id` | identificador | sistema | |
| `carritoId` | identificador | sí | |
| `productoId` | identificador | sí | Único dentro del carrito: un producto = una línea. |
| `cantidad` | entero | sí | Al menos 1. |

**El ítem de carrito no guarda precio.** El carrito refleja siempre el precio
vigente del producto. Si el precio sube mientras el carrito está abierto, el
comprador ve el precio nuevo — que es el que se le va a cobrar. Guardar el
precio aquí crearía la expectativa de respetarlo, y eso es una promesa de
negocio que nadie tomó.

### Orden

| Atributo | Tipo | Obligatorio | Notas |
| --- | --- | --- | --- |
| `id` | identificador | sistema | |
| `numero` | texto | sistema | Legible, único, el que ve el cliente. |
| `clienteId` | identificador | no | Nulo si compró sin cuenta. |
| `nombreContacto`, `email`, `telefono` | texto | sí | Se copian: la orden no depende de que la cuenta siga existiendo. |
| `direccion` | texto | sí | |
| `subtotal`, `descuento`, `total` | decimal(12,2) | sistema | Calculados en el servidor. |
| `codigoCupon` | texto | no | Copiado, no referenciado. |
| `estado` | `PENDIENTE` \| `PAGADA` \| `ENVIADA` \| `ENTREGADA` \| `CANCELADA` | sistema | |
| `creadaEn` | instante | sistema | |

### Ítem de orden

| Atributo | Tipo | Notas |
| --- | --- | --- |
| `ordenId` | identificador | |
| `productoId` | identificador | Referencia, solo para trazar |
| `nombreProducto`, `sku` | texto | **Copiados** |
| `precioUnitario` | decimal(12,2) | **Copiado** |
| `cantidad` | entero | |
| `totalLinea` | decimal(12,2) | |

**El ítem de orden sí copia nombre y precio.** Es la contrapartida exacta del
ítem de carrito, y la razón es la misma vista al revés: una orden es un hecho
ocurrido. Si mañana el producto sube de precio o se renombra, la orden del mes
pasado debe seguir diciendo lo que el cliente compró y pagó. Un `JOIN` al
producto para mostrar una orden vieja es un error que se paga en la primera
auditoría contable.

### Cupón

| Atributo | Tipo | Notas |
| --- | --- | --- |
| `codigo` | texto | Único, mayúsculas |
| `tipo` | `PORCENTAJE` \| `MONTO_FIJO` | |
| `valor` | decimal | Porcentaje (1..100) o monto |
| `subtotalMinimo` | decimal | Compra mínima para que aplique |
| `iniciaEn`, `terminaEn` | instante | Vigencia |
| `usosMaximos`, `usosActuales` | entero | |
| `activo` | booleano | |

---

## Identidad

### Cliente

| Atributo | Tipo | Obligatorio | Notas |
| --- | --- | --- | --- |
| `id` | identificador | sistema | |
| `email` | texto(..160) | sí | Único, en minúsculas. Es la identidad. |
| `emailVerificado` | booleano | sistema | Falso hasta confirmar. |
| `hashContrasena` | texto | no | **Nulo si se registró con Google.** |
| `nombre` | texto(1..120) | sí | |
| `telefono` | texto(..20) | no | |
| `activo` | booleano | sistema | Un cliente desactivado no puede entrar. |
| `ultimoAccesoEn` | instante | sistema | |
| auditoría | | sistema | |

`hashContrasena` nulo es un estado legítimo y hay que tratarlo: quien entró con
Google no tiene contraseña, y pedirle «la actual» para cambiarla no tiene
sentido. El flujo correcto para ese caso es establecer contraseña por correo,
no cambiarla. Ver [autenticacion.md](../backend/autenticacion.md).

### Identidad externa

| Atributo | Tipo | Notas |
| --- | --- | --- |
| `clienteId` | identificador | |
| `proveedor` | `GOOGLE` | Hoy solo uno; la tabla admite más sin migrar |
| `sujetoExterno` | texto | El `sub` de Google. **Único por proveedor.** Es lo que identifica, no el correo |
| `emailEnProveedor` | texto | Informativo; puede diferir del `email` del cliente |
| `vinculadoEn` | instante | |

Se guarda el `sub` y no el correo porque **el correo de una cuenta de Google
puede cambiar**, y porque dos proveedores distintos pueden reportar el mismo
correo sin que sea la misma persona verificada.

### Token de un solo uso

Sirve a tres flujos: verificar correo, recuperar contraseña, establecer
contraseña por primera vez.

| Atributo | Tipo | Notas |
| --- | --- | --- |
| `clienteId` | identificador | |
| `proposito` | `VERIFICAR_EMAIL` \| `RECUPERAR_CONTRASENA` \| `ESTABLECER_CONTRASENA` | |
| `hashToken` | texto | **Se guarda el hash, nunca el token.** Ver abajo |
| `expiraEn` | instante | |
| `usadoEn` | instante | Nulo mientras no se use |

**Se guarda el hash del token, no el token.** El token viaja en el enlace del
correo y solo existe en claro en ese correo. Si alguien lee la base de datos, no
puede fabricar un enlace de recuperación válido. Es el mismo razonamiento que
aplica a las contraseñas, y se olvida con frecuencia porque «un token es
temporal».

### Administrador

| Atributo | Tipo | Notas |
| --- | --- | --- |
| `usuario` | texto | Único |
| `hashContrasena` | texto | BCrypt |
| `nombre` | texto | |
| `activo` | booleano | |

Sin registro público, sin Google, sin recuperación por correo (ADR-0008). Si un
administrador pierde la contraseña, otro se la restablece desde el panel, o se
cambia por configuración. Es deliberado: el flujo de recuperación por correo es
la superficie de ataque más usada para tomar cuentas privilegiadas, y aquí no
compensa.

---

## Ciclo de vida del producto

```text
      crear (borrador)
            │
            ▼
     ┌──────────────┐   activar    ┌──────────────┐
     │   INACTIVO   │─────────────▶│    ACTIVO    │
     │ no visible   │◀─────────────│  en la tienda│
     └──────────────┘  desactivar  └──────────────┘
```

No hay borrado físico de productos: un producto referenciado por órdenes no
puede desaparecer sin romper el histórico. `DELETE` desactiva.

## Ciclo de vida de la orden

```text
PENDIENTE ──▶ PAGADA ──▶ ENVIADA ──▶ ENTREGADA
    │            │
    └────────────┴──▶ CANCELADA
```

Una orden `ENTREGADA` es terminal. Cancelar solo es posible antes de enviar, y
devuelve el stock.

---

## Invariantes

Se cumplen en todo momento observable. Un estado que viola uno es un defecto.

| # | Invariante | Dónde se aplica |
| --- | --- | --- |
| I-1 | `sku` único entre todos los productos | Restricción única |
| I-2 | Todo `slug` es único en su tipo (producto, categoría, subcategoría, marca) | Restricción única |
| I-3 | Todo producto pertenece a una subcategoría existente | Clave foránea |
| I-4 | Toda subcategoría pertenece a una categoría existente | Clave foránea |
| I-5 | `nombre` de subcategoría único **dentro** de su categoría | Restricción única compuesta |
| I-6 | `precio > 0` y `stock >= 0` | Restricción CHECK |
| I-7 | `precioAnterior`, si existe, es mayor que `precio` | Servicio (un «descuento» que sube el precio es un error de captura) |
| I-8 | Un producto aparece como máximo una vez por carrito | Restricción única compuesta |
| I-9 | `cantidad >= 1` en todo ítem de carrito | Restricción CHECK |
| I-10 | `email` de cliente único, en minúsculas | Restricción única |
| I-11 | `sujetoExterno` único por proveedor | Restricción única compuesta |
| I-12 | Un cliente tiene contraseña **o** al menos una identidad externa | Servicio |
| I-13 | Los importes de una orden nunca cambian tras crearse | Servicio: sin endpoint que los modifique |
| I-14 | `usosActuales <= usosMaximos` en todo cupón | Servicio, dentro de la transacción de compra |
| I-15 | Todo archivo referenciado existe en el almacén | Servicio |

---

## Dónde se aplica cada regla

```text
               ┌─────────────────────────────────────────────┐
  Petición ───▶│ 1. Validación estructural (forma, rango)    │──▶ 400
               ├─────────────────────────────────────────────┤
               │ 2. Reglas de negocio (unicidad, política)   │──▶ 409 / 422
               ├─────────────────────────────────────────────┤
               │ 3. Restricciones de la base (última línea)  │──▶ 500 si se llega
               └─────────────────────────────────────────────┘
```

Llegar a la capa 3 con un error que el servicio debió atrapar es un fallo del
servicio. La restricción es una red de seguridad, no una estrategia de
validación. La única excepción deliberada son las carreras de unicidad (dos
registros simultáneos con el mismo correo, dos productos con el mismo SKU),
donde la restricción es el **único** árbitro correcto: ahí se captura la
violación y se traduce a un `409` en regla.

---

## Fuera de alcance

Declarado aquí para que no se infiera de su ausencia: reseñas de clientes,
lista de deseos en servidor (va en el navegador), envíos y courier, pagos
reales, multi-moneda, multi-idioma, inventario por almacén, devoluciones.


---

## Sobre la auditoría

Donde este documento dice «auditoría» en una entidad, las tablas llevan cinco
columnas: `creado_en`, `creado_por`, `actualizado_en`, `actualizado_por` y
`version`.

`version` no es informativa: es bloqueo optimista. Dos administradores que
abren el mismo producto y guardan uno tras otro no se pisan en silencio; el
segundo recibe `CONCURRENT_MODIFICATION` y recarga.

Las entidades **inmutables** llevan solo las dos primeras. Un archivo no se
edita, se reemplaza, así que `subidoEn` y `subidoPor` de la tabla de arriba son
exactamente `creado_en` y `creado_por`.
