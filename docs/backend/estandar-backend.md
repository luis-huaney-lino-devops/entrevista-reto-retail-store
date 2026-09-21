# Estándar de backend

Cómo se construye el backend de Retail Store. **Un solo backend: Java 21 /
Spring Boot 3.5** ([ADR-0003](../adr/0003-backend-unico-en-despliegue.md)). Este
documento dice *qué* regla rige y *por qué*;
[java-spring-boot.md](java-spring-boot.md) dice *cómo* se escribe en este stack.

Antes de leerlo conviene tener a mano:

| Documento | Para qué |
| --- | --- |
| [modelo-dominio.md](../negocio/modelo-dominio.md) | Qué existe y qué invariantes se cumplen. Es la base de todo lo demás |
| [`contracts/openapi.yaml`](../../contracts/openapi.yaml) | Cómo se ve la API desde fuera |
| [PLAN.md](../PLAN.md) | Alcance del reto y fases |

Documentos hermanos, que **no** se duplican aquí:

* [autenticacion.md](autenticacion.md) — tokens, registro, Google, recuperación
* [correo.md](correo.md) — envío asíncrono, plantillas, reintentos
* [archivos-imagenes.md](archivos-imagenes.md) — subida, WebP, Cloudflare R2
* [pruebas.md](pruebas.md) — estrategia de pruebas y lo que hoy falta

---

## 1. Capas

```text
   HTTP
     │
     ▼
┌──────────────────┐  Solo HTTP: rutas, enlace de parámetros, validación de forma,
│  Controlador     │  código de estado. Sin reglas. Sin repositorios. Sin try/catch.
│  (paquete .web)  │
└─────────┬────────┘
          ▼
┌──────────────────┐  Peticiones y respuestas. Records inmutables.
│  DTO (.dto)      │  Aquí y solo aquí vive la validación estructural.
└─────────┬────────┘
          ▼
┌──────────────────┐  Orquesta, aplica reglas, abre y cierra la transacción.
│  Servicio        │  No conoce HttpServletRequest, ni ResponseEntity, ni cabeceras.
│  (.service)      │
└─────────┬────────┘
          ▼
┌──────────────────┐  Invariantes que son del objeto, no del caso de uso:
│  Dominio         │  Product.discountPercent(), Coupon.isUsableFor(),
│  (.domain)       │  Cart.addOrIncrement(). Sin Spring, sin repositorios.
└─────────┬────────┘
          ▼
┌──────────────────┐  Acceso a datos. Consultas, proyecciones, Specifications.
│  Repositorio     │  Sin reglas de negocio.
│  (.repository)   │
└─────────┬────────┘
          ▼
┌──────────────────┐  El esquema lo gobierna Flyway, no el ORM (§8).
│  PostgreSQL 17   │
└──────────────────┘
```

Las dependencias apuntan **solo hacia abajo**. Un repositorio nunca llama a un
servicio; un servicio nunca devuelve un `ResponseEntity`; una entidad nunca
lleva una anotación de Bean Validation pensada para un cuerpo de petición.

**La prueba de que las capas están bien puestas:** se debería poder poner un
segundo punto de entrada delante de `CartService` —un consumidor de mensajes,
una tarea programada que caduca carritos, una CLI de mantenimiento— sin tocarlo.
Si eso obligara a cambiar el servicio, hay lógica que se filtró al controlador.

### La capa de dominio no es decorativa

En este proyecto hay tres piezas que ilustran por qué existe:

| Pieza | Qué decide | Por qué no está en el servicio |
| --- | --- | --- |
| `Product.discountPercent()` | Si hay descuento y de cuánto | Depende solo de `price` y `compareAtPrice`. Llevarlo al servicio obligaría a repetirlo en listado, detalle y relacionados |
| `Coupon.isUsableFor(subtotal, now)` | Si el cupón aplica | Es una propiedad del cupón, no del caso de uso. Se prueba sin base de datos y sin Spring |
| `Cart.addOrIncrement(product, qty)` | Una línea por producto | Es la invariante I-8 del dominio. Si viviera en el servicio, un segundo caso de uso podría saltársela |

Contrapartida honesta: repartir lógica entre dominio y servicio obliga a decidir
cada vez dónde va cada cosa, y la frontera no siempre es obvia. El criterio que
se usa aquí: **si la regla se puede responder con los datos del propio objeto,
va al dominio; si necesita consultar otra cosa, va al servicio.** Por eso el
control de stock está en `CartService` (necesita el `Product` que el repositorio
trajo) y la vigencia del cupón está en `Coupon`.

### Estructura de paquetes

Empaquetado **por funcionalidad, luego por capa**. Así es como está hoy:

```text
com.retailstore.api
├── RetailStoreApplication.java
├── catalog/
│   ├── domain/        Product, Category
│   ├── dto/           ProductSearchRequest, ProductSummaryResponse, ...
│   ├── mapper/        CatalogMapper
│   ├── repository/    ProductRepository, ProductSpecifications, CategoryRepository
│   ├── service/       ProductService, CategoryService, ProductSort
│   └── web/           ProductController, CategoryController
├── cart/
│   ├── domain/        Cart, CartItem, Coupon, CartStatus, CouponType
│   ├── dto/           AddCartItemRequest, CartResponse, ...
│   ├── mapper/        CartMapper
│   ├── repository/    CartRepository, CouponRepository
│   ├── service/       CartService, CartPricingCalculator, CartPricing, excepciones
│   └── web/           CartController
├── common/
│   ├── error/         GlobalExceptionHandler, ResourceNotFoundException,
│   │                  InvalidRequestException
│   └── pagination/    PageResponse
└── config/            ClockConfig, CorsProperties, OpenApiConfig, WebConfig
```

Los paquetes que faltan, cuando se implementen, siguen la misma forma:
`order/`, `customer/`, `auth/`, `file/`, `mail/`, `admin/`.

Por qué por funcionalidad y no `controller/`, `service/`, `repository/` en la
raíz: todo lo necesario para cambiar el carrito vive en un directorio. Con el
empaquetado por capa, un cambio pequeño toca cuatro carpetas lejanas y revisar
el diff se vuelve una búsqueda del tesoro.

**Un servicio por agregado.** `ProductService`, `CartService`. Cuando uno crece
más de lo comprensible, se divide por **caso de uso**
(`CheckoutService`, `CartExpirationService`), nunca por capa técnica. El modo de
fallo a evitar es el servicio-Dios con todas las operaciones de todas las
entidades; el modo de fallo contrario —un servicio por método— también.

---

## 2. Controladores

Hacen cuatro cosas y ninguna más: recibir, validar la forma, delegar y mapear
el resultado a un estado HTTP.

**Nunca en un controlador:**

* reglas de negocio ni condicionales de política
* acceso a repositorios
* `try`/`catch` — para eso existe el manejador central (§6)
* comprobaciones de rol escritas a mano
* transformaciones más allá de llamar a un mapeador

**Siempre:**

* inyección por constructor, nunca por campo. Un campo `@Autowired` esconde las
  dependencias, permite construir el objeto en un estado incompleto e impide
  declarar el campo `final`. Con constructor, una dependencia nueva es visible
  en el diff y la clase se puede instanciar en una prueba sin levantar Spring
* el DTO validado de forma declarativa en el borde (`@Valid`)
* por norma, **una sola llamada** al servicio por petición: si el controlador
  encadena dos llamadas, la transacción está partida en dos y la coordinación
  quedó en la capa equivocada

`ProductController` y `CartController` cumplen esto hoy: ninguno tiene un `if`.

### Códigos de estado

| Situación | Estado | Ejemplo real |
| --- | --- | --- |
| Lectura correcta | `200` | `GET /producto` |
| Recurso creado | `201` | `POST /carrito` |
| Mutación que devuelve el recurso completo | `200` | `POST /carrito/{id}/items` |
| Forma o rango inválido | `400` | `pageSize=500` |
| Regla de entrada entre campos | `400` | `minPrice > maxPrice` |
| Recurso inexistente o inactivo | `404` | `GET /producto/no-existe` |
| Conflicto con el estado del sistema | `409` | cantidad > stock |
| Fallo inesperado | `500` | cualquier excepción no reconocida |

Sobre la mutación que responde `200` con el carrito entero en lugar de `204`:
es una decisión deliberada de
[ADR-0002](../adr/0002-carrito-servidor-fuente-de-verdad.md). Toda mutación del
carrito devuelve el carrito completo con los totales recalculados, así que el
frontend nunca tiene que recomponer un total sumando respuestas parciales —que
es exactamente donde aparecen las discrepancias de céntimos entre lo que la UI
muestra y lo que el servidor cobra. El costo es una respuesta más grande por
operación; a cambio, **un solo contrato de respuesta y un solo lugar donde vive
la regla de precios**.

---

## 3. DTOs

**Las entidades nunca cruzan el límite HTTP, en ninguna dirección.** Ni como
respuesta, ni como cuerpo de petición, ni anidadas dentro de otro tipo.

Tres razones concretas, no una preferencia estética:

1. Una entidad JPA con relaciones perezosas serializada directamente produce
   `LazyInitializationException` fuera de la transacción, o —peor— dispara las
   consultas que faltaban mientras Jackson recorre el grafo.
2. `Cart` referencia `CartItem`, que referencia `Product`, que referencia
   `Category`. Serializar el grafo expone datos que la respuesta no necesita y
   puede ciclar.
3. Un campo añadido a la entidad aparecería **solo** en la API sin que nadie lo
   decidiera. Exponer algo tiene que ser un acto explícito.

| Propósito | Tipo (ejemplo real) |
| --- | --- |
| Parámetros de consulta agrupados | `ProductSearchRequest` |
| Cuerpo de creación | `AddCartItemRequest` |
| Cuerpo de actualización parcial | `UpdateCartItemRequest` |
| Respuesta de listado | `ProductSummaryResponse` |
| Respuesta de detalle | `ProductDetailResponse` |
| Referencia anidada | `CategoryRef` |
| Respuesta paginada | `PageResponse<T>` |

**Resumen y detalle son tipos separados.** `ProductSummaryResponse` no lleva
`description`; `ProductDetailResponse` sí. Una descripción larga multiplicada
por 48 productos en una página es carga inútil en cada listado del catálogo, y
el catálogo es la petición más frecuente de la tienda.

**Agregar y actualizar son tipos separados aunque hoy casi coincidan.**
`AddCartItemRequest` lleva `productId` y `quantity`; `UpdateCartItemRequest`
solo `quantity`, porque el producto de una línea no se cambia: se quita la línea
y se agrega otra. Un tipo compartido con un `productId` que a veces se ignora es
un contrato que miente.

Los DTO son `record`: inmutables, sin comportamiento más allá de la validación y
de los valores por omisión. `ProductSearchRequest` usa el constructor compacto
para fijar `page = 1` y `pageSize = 12` **antes** de que Bean Validation revise
el objeto, de modo que la ausencia del parámetro y el parámetro válido recorren
exactamente el mismo camino.

**Nunca en una respuesta:** hashes de contraseña, tokens, `createdBy`, banderas
internas, ni nada que el cliente no use. Incluir un campo es una decisión que se
toma y se refleja en [`contracts/openapi.yaml`](../../contracts/openapi.yaml).

### Mapeadores

El mapeo entidad → DTO es manual, en clases `final` con constructor privado y
métodos estáticos (`CatalogMapper`, `CartMapper`). Sin MapStruct y sin
ModelMapper.

El razonamiento: son dos agregados y una veintena de campos. MapStruct aporta su
valor real —detectar en compilación un campo añadido y olvidado— cuando hay
decenas de tipos; aquí añadiría un procesador de anotaciones, una fase de
generación y código que no se lee en el repositorio a cambio de ahorrar treinta
líneas explícitas. **La contrapartida es real y hay que decirla:** si mañana se
añade un campo a `Product` y se olvida en `CatalogMapper`, nada falla en
compilación. Lo cubre una prueba del mapeador ([pruebas.md](pruebas.md) §5) y,
si el número de tipos crece de verdad, MapStruct vuelve a estar sobre la mesa.

---

## 4. Validación: dos clases, separadas

```text
Estructural   "¿tiene la forma correcta?"    declarativa, en el DTO       → 400
De entrada    "¿los campos son coherentes?"  en el servicio, sin tocar BD → 400
De negocio    "¿está permitido, dado el estado del sistema?"  servicio    → 404 / 409
```

| Estructural (DTO, Bean Validation) | De entrada (servicio) | De negocio (servicio + BD) |
| --- | --- | --- |
| `quantity` entre 1 y 99 | `minPrice <= maxPrice` | el producto existe y está activo |
| `pageSize` entre 1 y 48 | `sort` pertenece a la lista blanca | la cantidad cabe en el stock |
| `search` máximo 100 caracteres | | el cupón está vigente y llega al mínimo |
| `code` de cupón no vacío | | el ítem pertenece a este carrito |
| `minPrice >= 0` | | |

Por qué separarlas: las reglas estructurales son estables, baratas y se
comprueban sin tocar la base de datos; las de negocio cambian con la política y
casi siempre necesitan una consulta. Mezclarlas termina con un cambio de
política obligando a editar un DTO, y con un framework de validación cargando
lógica que no sabe expresar.

La validación estructural reporta **todos** los fallos a la vez —el usuario
corrige el formulario una vez, no cinco—; la de negocio reporta el **primero**,
porque la segunda comprobación suele depender de que la primera pasara.

### El caso que tienta a difuminar la frontera

`minPrice <= maxPrice` podría escribirse como una restricción a nivel de clase
en `ProductSearchRequest` y quedaría como validación estructural. Aquí está en
`ProductService.validatePriceRange()` y lanza `InvalidRequestException`. Las dos
opciones son defendibles; se eligió el servicio porque la prueba
`searchRejectsInvertedPriceRangeBeforeHittingTheDatabase` puede entonces
verificar además que **no se llamó al repositorio**, que es la mitad
interesante: la regla no solo rechaza, rechaza *antes* de gastar una consulta.

Lo que **no** se difumina: el stock. `quantity <= stock` no es estructural bajo
ninguna lectura —depende de una fila de la base— y vive en `CartService`.

---

## 5. Reglas de negocio: dónde vive cada una

**El catálogo de reglas es [`docs/negocio/reglas-negocio.md`](../negocio/reglas-negocio.md)
y es la única autoridad sobre la numeración `RN-###`.** Este documento no define
reglas.

**Qué está implementado y dónde** está en
[`docs/estado-y-brecha.md`](../estado-y-brecha.md) §3. Tenerlo en un solo sitio
es deliberado: cuando la misma tabla vivía aquí y allí, las dos se
desincronizaron en el primer cambio.

### Dónde va cada tipo de regla

Esto sí es de este documento, porque es una convención de implementación y no
un estado:

| Tipo de regla | Dónde vive | Ejemplo |
| --- | --- | --- |
| Depende solo de la entidad | **En la entidad** | `Producto.cambiarPrecios()` rechaza un precio anterior menor |
| Necesita consultar otras filas | En el servicio | `ServicioProducto` comprueba que el SKU no exista |
| Es una invariante estructural | Restricción de la base | `uq_producto_sku`, `ck_producto_precio` |
| Es de forma, no de negocio | Anotación de validación en el DTO | `@Size(max = 160)` sobre el nombre |

La prueba de que la primera fila se respeta: `ProductoTest` y `CuponTest` no
necesitan ni base de datos ni contexto de Spring. Si para probar una regla hace
falta levantar la aplicación, esa regla está en el sitio equivocado.

### Nombre de las pruebas

Se nombran por la regla que cubren, con el número al final:

```
publicarSinImagenLanzaProductRequiresImage_RN009
elDescuentoNoSuperaElSubtotal_RN044
```

Así un fallo dice qué regla de negocio se rompió, no solo qué método.

## 6. Manejo de errores

Un único manejador: `GlobalExceptionHandler`, anotado `@RestControllerAdvice`.
Los controladores no capturan nunca.

### El contrato de error

Toda respuesta de error sale como `application/problem+json` (RFC 7807) con la
misma forma:

```json
{
  "type": "https://retailstore.dev/errors/insufficient-stock",
  "title": "Stock insuficiente",
  "status": 409,
  "detail": "Solo quedan 3 unidades de 'Mochila Urbana 25 L'.",
  "availableStock": 3
}
```

| `type` | Estado | Cuándo | Extras |
| --- | --- | --- | --- |
| `.../validation` | `400` | Bean Validation, fallo de conversión, regla de entrada | `errors: { campo: [mensajes] }` |
| `.../not-found` | `404` | Producto, carrito o ítem inexistente o inactivo | — |
| `.../insufficient-stock` | `409` | La cantidad pedida supera el stock | `availableStock` |
| `.../invalid-coupon` | `400` | Cupón inexistente, caducado, sin usos o bajo el mínimo | — |
| `.../internal` | `500` | Cualquier excepción no reconocida | — |

**El `type` es el discriminador legible por máquina**, no el `title` ni el
`detail`. El frontend decide qué hacer mirando el `type`; los textos son prosa
en español pensada para una persona y pueden reescribirse sin romper a nadie.
Es la misma idea que en el proyecto anterior se resolvía con una propiedad
`code`, resuelta aquí con el campo que RFC 7807 ya define para eso.

`availableStock` es una **extensión** del problema, no un adorno: sin ella el
frontend no puede ofrecer «ajustar a 3 unidades» y solo le queda mostrar un
error. Cada extensión existe porque un cliente concreto la necesita.

### Requisitos del manejador

1. **Las excepciones no reconocidas se convierten en un `500` genérico.** Ni
   traza, ni SQL, ni nombres de clase llegan al cliente. `server.error` está
   configurado con `include-stacktrace: never` e `include-message: never` como
   segunda barrera, por si algo se escapa del advice.
2. **Se registra una sola vez, en el punto de manejo.** Registrar el mismo fallo
   en cada nivel del camino hacia arriba no da más información: da tres entradas
   que hay que correlacionar a mano durante un incidente.
3. **Nivel de log:** `500` es `ERROR`. Un `4xx` es `DEBUG` como mucho. Un usuario
   que pide `pageSize=500` no es un evento operativo, y tratarlo como tal enseña
   al equipo a ignorar el log.
4. **Un fallo de conversión no expone el mensaje del framework.** `minPrice=abc`
   produce `"Formato inválido."`, no el texto de `NumberFormatException` con el
   nombre del tipo de destino.
5. **Las violaciones de restricción se resuelven por nombre de restricción**
   (`uq_item_carrito`), nunca analizando el texto del driver: ese
   texto cambia entre versiones de PostgreSQL.

### 400 frente a 409, decidido a conciencia

Es la elección que más se hace por costumbre y la que más confunde al cliente.
El criterio aquí:

* **`400`** — la petición está mal formada o es incoherente **por sí misma**.
  Repetirla igual siempre fallará. `pageSize=500`, `minPrice > maxPrice`.
* **`409`** — la petición es válida, pero choca con el estado actual del
  sistema. **La misma petición podría funcionar en otro momento.** Pedir 5
  unidades de un producto con 3 en stock: si llega mercadería, funciona.

Con ese criterio, el cupón es un caso de frontera interesante: hoy devuelve
`400` en todos sus casos, y para «el código no existe» se defiende solo. Pero
«el subtotal no llega al mínimo» sí cambiaría al agregar productos, así que por
el criterio estricto sería `409`. Queda anotado como deuda consciente (D-4), no
como descuido.

**No se usa `422`.** El proyecto anterior lo reservaba para violaciones de regla
de negocio; aquí la distinción `400`/`409` cubre todos los casos existentes, y
un tercer código sin una regla clara de cuándo usarlo solo produce respuestas
inconsistentes.

---

## 7. Transacciones

* **El método de servicio es el límite transaccional.** Ni el controlador, ni el
  repositorio.
* Las clases de servicio se anotan con el modo que corresponde a su naturaleza:
  `ProductService` y `CategoryService` son `@Transactional(readOnly = true)` a
  nivel de clase; `CartService` es `@Transactional` y marca como `readOnly` los
  métodos que solo leen. Así un método nuevo hereda el modo correcto en lugar de
  olvidarlo.
* `readOnly = true` no es cosmético: permite a Hibernate saltarse el *dirty
  checking* del contexto de persistencia y a PostgreSQL saber que la transacción
  no va a escribir.
* Las transacciones de escritura son lo más cortas posible. **Nunca** se
  mantiene una abierta durante una llamada HTTP saliente, una subida a R2, un
  envío SMTP, ni nada que espere a una persona o a un tercero.
* Cualquier fallo revierte todo. Las escrituras parciales no existen.
* **Nunca repartir trabajo entre hilos dentro de una transacción.** Una conexión
  de base de datos pertenece a un hilo; un `parallelStream()` dentro de un
  método transaccional produce fallos intermitentes que solo aparecen bajo
  carga.
* `open-in-view` está **desactivado**. Es la configuración correcta y merece su
  párrafo.

### Por qué `open-in-view: false`

Con `open-in-view` activo (el valor por omisión de Spring Boot, y por eso hay
que desactivarlo explícitamente), la sesión de Hibernate permanece abierta
durante el renderizado de la respuesta. Eso hace que una relación perezosa
accedida por el serializador se cargue en silencio, fuera de cualquier
transacción y con una consulta por objeto: la fuente clásica de N+1 en la que
nadie escribió una sola consulta de más.

Con `false`, ese mismo acceso lanza `LazyInitializationException` —ruidoso,
inmediato, imposible de ignorar— y obliga a decidir en el repositorio qué se
carga. Por eso `ProductRepository` y `CartRepository` declaran `@EntityGraph`
explícitos: la carga anticipada es una decisión por consulta, no un accidente
del renderizado.

Contrapartida: hay que pensar cada `@EntityGraph`. Es trabajo, y es trabajo que
se hace una vez en lugar de diagnosticar una degradación en producción.

### Efectos externos y transacciones

| Efecto | Dónde va | Por qué |
| --- | --- | --- |
| Escrituras en base de datos | Dentro | Revierten con el fallo |
| Auditoría (cuando exista) | **Dentro** | Es parte del hecho registrado; si el hecho no ocurrió, no debe haber entrada |
| Envío de correo | **Fuera**, tras el commit | Un correo no se puede deshacer. Ver [correo.md](correo.md) §2 |
| Subida a R2 | **Antes** del commit, con limpieza posterior de huérfanos | Ver [archivos-imagenes.md](archivos-imagenes.md) §2 |

Auditoría dentro y correo fuera parece contradictorio y no lo es: cada uno va
donde su riesgo es menor. La auditoría debe revertir con el hecho; el correo no
puede revertirse, así que solo debe salir cuando el hecho es definitivo.

---

## 8. Persistencia

### El ORM nunca es dueño del esquema

`ddl-auto: validate`. Nunca `update`, nunca `create-drop` fuera de una prueba
desechable.

```text
   database/migrations/*.sql          ← la única fuente del esquema
             │
             ├── Flyway las aplica al arrancar
             │
   Hibernate ┴── ddl-auto: validate   ← solo comprueba que el mapeo coincide
```

Por qué importa tanto como para ser una decisión declarada:

* `update` **nunca borra ni modifica** una columna existente. Renombrar un campo
  en Java con `update` deja la columna vieja llena de datos y crea una nueva
  vacía, sin avisar.
* `update` no expresa migraciones de **datos**. Partir un nombre completo en
  nombre y apellido es SQL, no una anotación.
* Con `update`, el esquema de producción es el resultado acumulado de qué
  versiones del código pasaron por esa base y en qué orden. Es irreproducible.
* Con `validate`, un mapeo que ya no corresponde al esquema **impide el
  arranque** en lugar de fallar en la primera consulta que toque esa columna.

Las migraciones viven en
[`database/migrations/`](../../database/migrations/V001__catalogo.sql) y
el `pom.xml` las copia al classpath como `db/migration` al empaquetar. Una
migración aplicada **nunca se edita**: Flyway guarda su checksum y una edición
posterior rompe el arranque. Se corrige con una migración nueva.

### Consultas

* **Nunca cargar una tabla entera.** Ningún `findAll()` sin paginar sobre
  `producto`.
* Las colecciones se devuelven paginadas y acotadas (RN-007), salvo dos
  excepciones declaradas: `GET /categoria` (decenas de filas, sin crecimiento
  esperado) y `GET /producto/{slug}/related` (tope de 4 en la propia consulta).
* Las relaciones son **perezosas por omisión**. `Producto.subcategoria`,
  `ItemCarrito.producto` y `ItemCarrito.carrito` son todas `FetchType.LAZY`.
* La carga anticipada es una decisión **por consulta**, con `@EntityGraph`,
  nunca un `FetchType.EAGER` en el mapeo. `EAGER` es una decisión global tomada
  una vez y pagada en todas las consultas, incluidas las que no necesitan la
  relación.
* **Las consultas N+1 son un defecto, no una optimización pendiente.** El
  listado de catálogo trae la categoría en la misma consulta; el carrito trae
  ítems, productos y categorías en un solo `@EntityGraph`.
* Los filtros dinámicos se componen con **Specifications**, no concatenando
  cadenas. El usuario nunca aporta un nombre de columna: el orden pasa por la
  lista blanca de `ProductSort` y los predicados son objetos tipados.
* Toda ordenación termina con un desempate por clave primaria (RN-006). Sin él,
  dos productos con el mismo precio pueden aparecer en dos páginas o en ninguna,
  porque `ORDER BY price OFFSET 24 LIMIT 12` no garantiza un orden total.

### Dinero y tiempo

| Tipo | Regla | Por qué |
| --- | --- | --- |
| Importes | `BigDecimal` en Java, `numeric(12,2)` en PostgreSQL | `double` no representa `0.10` exactamente. En dinero eso es un céntimo que aparece o desaparece al sumar |
| Comparación de importes | `compareTo`, nunca `equals` | `new BigDecimal("10.0").equals(new BigDecimal("10.00"))` es `false`: `equals` compara también la escala |
| Redondeo | Explícito, `RoundingMode.HALF_UP`, en la operación | Un `divide` sin escala ni modo lanza `ArithmeticException` cuando el resultado es periódico |
| Instantes | `Instant` en Java, `timestamptz` en PostgreSQL, UTC en el driver | Un `timestamp` sin zona es ambiguo en cuanto hay un servidor en otra región |
| Presentación local | `America/Lima`, aplicada en el frontend | El backend no convierte a hora local: transporta instantes |

`hibernate.jdbc.time_zone: UTC` está fijado en la configuración. Sin eso, el
driver usa la zona de la JVM y un contenedor con `TZ` distinta escribe instantes
desplazados sin que nada falle visiblemente.

---

## 9. Seguridad

El diseño completo de accesos está en [autenticacion.md](autenticacion.md). Lo
que este estándar fija y no se repite allí:

* **Denegar por omisión.** La cadena de seguridad termina en una regla que
  rechaza. Lo que no esté explícitamente permitido es inalcanzable.
* La autorización se aplica **en el servidor, en todos los endpoints**. Ocultar
  un botón en el admin no es una medida de seguridad: es una medida de
  usabilidad sobre un endpoint que sigue abierto.
* Los secretos vienen del entorno. **Ninguno se versiona, jamás.** Un secreto
  con valor por omisión o ausente **impide el arranque**: una advertencia no
  sirve, porque nadie lee las advertencias.
* CORS nombra los orígenes explícitamente (`app.cors.allowed-origins`). Nunca
  `*` con credenciales — los navegadores lo rechazan igual, y configurarlo así
  delata que la política nunca se pensó.
* El parámetro `sort` y cualquier otro que pudiera llegar a una consulta pasan
  por una lista blanca. Ver `ProductSortTest.rejectsUnknownParam`, que usa
  `"price; drop table"` como caso.
* Los comodines `%` y `_` del término de búsqueda se escapan (RN-009). No es una
  inyección SQL —Specifications parametriza— pero un `%` suelto convierte
  `LIKE '%%%'` en un escaneo completo de la tabla: es una denegación de servicio
  barata de provocar.

**Estado real hoy:** el proyecto **no tiene** `spring-boot-starter-security` en
el `pom.xml`. Toda la API es pública, no hay autenticación, no hay autorización
y no hay límite de intentos. Es coherente con el alcance actual —catálogo y
carrito anónimo— pero **todo** lo descrito en
[autenticacion.md](autenticacion.md) está por implementar. Este estándar dice
adónde se va; no describe lo que ya está hecho.

Un detalle no obvio que conviene anotar antes de añadir seguridad: el carrito se
identifica por un UUID que el navegador conserva, sin sesión ni cuenta. Eso
significa que **quien conozca el UUID de un carrito puede verlo y modificarlo**.
Para un carrito anónimo de una tienda de demostración el riesgo es aceptable —el
UUID es impredecible y el contenido no es sensible—, pero en cuanto el carrito
se asocie a un cliente con sesión habrá que comprobar la pertenencia, no basta
con conocer el identificador.

---

## 10. Configuración, registro y observabilidad

### Configuración

Nada específico de un entorno se escribe en el código. Todo valor viene de
`application.yml` con sustitución por variable de entorno y un valor por omisión
apto **solo para desarrollo local**:

| Variable | Omisión (dev) | Para qué |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/retailstore_java` | Conexión |
| `DB_USER` / `DB_PASSWORD` | `retail` / `retail` | Credenciales |
| `PORT` | `8080` | Puerto |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Orígenes permitidos |

Un valor por omisión de desarrollo que sirve en producción es un incidente
esperando fecha. La regla: **si el valor por omisión sería aceptable en
producción, no es un secreto; si no lo sería, la aplicación no debe arrancar sin
él.** Hoy `DB_PASSWORD` incumple esa regla (D-5).

Todo valor de negocio ajustable es configuración, no un literal en el código.
Los que hoy están fijos y deberían migrar a configuración tipada:

| Valor | Dónde está hoy | Regla |
| --- | --- | --- |
| Tamaño de página por omisión / máximo | Constantes en `ProductSearchRequest` | RN-007 |
| Cantidad máxima por línea | Anotación `@Max(99)` | RN-021 |
| Número de relacionados | Nombre del método del repositorio | RN-010 |

La configuración se enlaza a un **record tipado y validado** (`CorsProperties`
es el ejemplo actual), de modo que un valor mal formado impida el arranque en
lugar de aparecer como un fallo extraño tres días después.

### Registro

* Estructurado: pares clave-valor, no prosa interpolada, para poder consultarlo.
* **Nunca** se registran: contraseñas, tokens, la cabecera `Authorization`,
  cuerpos de petición completos, ni datos personales más allá de lo que una
  investigación necesite.
* Niveles: `ERROR` fallo inesperado · `WARN` degradado pero manejado · `INFO`
  cambio de estado significativo · `DEBUG` detalle de desarrollo · `TRACE`
  apagado fuera del trabajo local.
* Nada de `System.out.println` en código de aplicación. Ninguna configuración de
  nivel de log lo apaga, no lleva marca de tiempo ni contexto, y en un
  contenedor se mezcla con la salida de la JVM.

**El log no es la auditoría.** El log se rota en días y sirve para diagnosticar;
una bitácora de auditoría se conserva años y es un registro de negocio. Hoy no
existe bitácora de auditoría: cuando el panel de administración permita editar
precios, hará falta.

### Observabilidad

Hoy: `spring-boot-starter-actuator` con **únicamente** `health` expuesto, en
`/health`. Es lo mínimo correcto —el `healthcheck` de
[`docker-compose.prod.yml`](../../deploy/docker-compose.prod.yml) consulta
exactamente esa URL para decidir si el contenedor está listo antes de que Caddy
le envíe tráfico— y deliberadamente no hay más superficie abierta.

Lo que falta y está justificado añadir cuando haya tráfico real: separar
*liveness* de *readiness*. La distinción importa y se suele pasar por alto:
*liveness* responde «¿hay que reiniciarme?» y *readiness* responde «¿debo
recibir tráfico?». Una caída de PostgreSQL deja al servicio **no disponible**,
pero reiniciar el proceso no arreglaría nada; si la sonda de vida fallara por
eso, el orquestador entraría en un ciclo de reinicios que solo añade ruido al
incidente.

Los endpoints de métricas, si se exponen, van autenticados y nunca públicos.

---

## 11. Documentación de la API

Dos artefactos, y hay que saber cuál manda:

| Artefacto | Quién lo escribe | Papel |
| --- | --- | --- |
| [`contracts/openapi.yaml`](../../contracts/openapi.yaml) | A mano | **El contrato.** Es lo que el storefront y el admin consumen para generar tipos |
| `/v3/api-docs` + `/swagger` | springdoc, desde el código | El reflejo de lo implementado. Sirve para explorar y probar |

Cuando divergen, **manda el contrato** y el código es el que está mal. El orden
correcto es: se cambia el contrato, se discute, y luego se implementa. Si se
implementa primero, el contrato deja de ser un acuerdo y pasa a ser un acta de
lo que alguien ya hizo.

Cada endpoint documenta sus **respuestas de error**. Es la mitad que suele
omitirse y la mitad que los clientes de verdad necesitan: un frontend que no
sabe que `POST /carrito/{id}/items` puede responder `409` no tiene cómo escribir
el mensaje que el usuario merece ver.

En producción, `/swagger` va detrás de autenticación.

---

## 12. Definición de terminado

Un cambio de backend está completo cuando:

* [ ] Cita su `RN-###` o `CU-###` en el mensaje del commit
* [ ] Ninguna entidad cruza el límite HTTP; hay DTO de entrada y de salida
* [ ] La validación estructural es declarativa y está en el DTO
* [ ] Las reglas de negocio están en el servicio o en el dominio, no en el controlador
* [ ] El límite transaccional está en el servicio, y no hay reparto de hilos dentro
* [ ] Los efectos externos (correo, R2) están fuera de la transacción de escritura
* [ ] Los errores pasan por `GlobalExceptionHandler`; nada interno se filtra al cliente
* [ ] El `type` del problema es nuevo o reutiliza uno existente **a propósito**
* [ ] El código de estado se eligió con el criterio de §6 (`400` vs `409`), no por costumbre
* [ ] Toda colección va paginada y acotada, o tiene un tope declarado en la consulta
* [ ] No hay N+1: las consultas se revisaron con el log de SQL activado, no se supusieron
* [ ] Toda ordenación tiene desempate por clave primaria
* [ ] Los importes son `BigDecimal` y se comparan con `compareTo`
* [ ] El esquema cambia por **migración Flyway nueva**, nunca editando una aplicada
* [ ] Pruebas: camino feliz, cada violación de regla y los valores límite ([pruebas.md](pruebas.md))
* [ ] [`contracts/openapi.yaml`](../../contracts/openapi.yaml) actualizado, con las respuestas de error
* [ ] Ningún secreto versionado; ninguna dependencia añadida sin justificación escrita

---

## 13. Deuda consciente

Un estándar que solo describe el ideal no sirve para revisar código. Esto es lo
que hoy **no** cumple lo anterior, sabido y anotado:

| # | Qué | Impacto | Salida |
| --- | --- | --- | --- |
| D-1 | No hay pruebas de integración: ni Testcontainers, ni Flyway verificado, ni una sola Specification ejecutada contra PostgreSQL | Alto. Las Specifications, el escape de `LIKE` y `ddl-auto: validate` solo se demuestran contra una base real | [pruebas.md](pruebas.md) §4 |
| D-2 | No hay seguridad: el `pom.xml` no incluye Spring Security y toda la API es pública | Alto en cuanto exista `/admin/**` | [autenticacion.md](autenticacion.md) |
| D-3 | `@ExceptionHandler(Exception.class)` capturará `AccessDeniedException` y devolverá `500` en lugar de `403` cuando se añada seguridad | Medio, latente | Manejador específico **antes** de añadir Spring Security |
| D-4 | `InvalidCouponException` devuelve `400` incluso cuando la causa es el subtotal mínimo, que sí cambiaría al agregar productos | Bajo, pero incoherente con el criterio de §6 | Separar «cupón inexistente» (`400`) de «cupón no aplicable ahora» (`409`) |
| D-5 | `DB_PASSWORD` tiene valor por omisión (`retail`), contra la regla de §10 | Medio | Sin valor por omisión fuera de desarrollo |
| D-6 | `Product.@PrePersist` usa `Instant.now()` directamente en vez del `Clock` inyectable de `ClockConfig` | Bajo | Auditoría de JPA con `AuditingEntityListener` y un `DateTimeProvider` basado en el `Clock` |
| D-7 | [modelo-dominio.md](../negocio/modelo-dominio.md) enlaza a un `persistencia-postgres.md` que no existe, y cita ADR-0007, ADR-0008 y ADR-0009, que tampoco | Bajo | Escribir esos documentos o corregir las referencias |
| D-8 | ~~El esquema no tenía subcategorías, marcas, SKU ni archivos~~ | **Resuelto** | El esquema `V001`–`V004` los incluye |
| D-9 | La invariante I-6 del dominio exige `precio > 0`; la migración `V001` comprueba `price >= 0` | Bajo, pero un producto a precio cero pasaría | Endurecer el `CHECK` en una migración nueva |
| D-10 | El carrito no comprueba pertenencia: quien conozca el UUID lo controla (§9) | Bajo hoy, alto cuando haya cuentas | Asociar `cliente_id` y exigirlo cuando el carrito lo tenga |
