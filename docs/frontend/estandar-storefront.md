# Estándar de la tienda pública

Next.js (App Router) + TypeScript, renderizado en servidor con revalidación
incremental, e islas de cliente donde hay estado.

Este documento cubre **una** de las dos aplicaciones de frontend. El panel de
administración tiene reglas distintas y su propio documento:
[estandar-admin.md](estandar-admin.md). Lo que comparten —capas, los cuatro
estados, errores por código, nada de `any`, token en memoria— está repetido a
propósito en ambos: son documentos que se leen por separado.

Antes de escribir una pantalla hay que haber leído
[el modelo de dominio](../negocio/modelo-dominio.md) (categoría → subcategoría →
producto, y por qué el ítem de carrito no guarda precio),
[las reglas de negocio](../negocio/reglas-negocio.md) (sobre todo RN-030 a
RN-036, que son las del carrito),
[el catálogo de errores](../contrato/catalogo-errores.md) (cada `code` que hay
que traducir a un mensaje),
[autenticación](../backend/autenticacion.md) (los tres caminos de acceso del
cliente y dónde vive cada token) y
[archivos e imágenes](../backend/archivos-imagenes.md) (las tres variantes que
sirve el CDN).

---

## 1. Qué es esta tienda y qué no es

| Es | No es |
| --- | --- |
| Un catálogo público que se indexa y se comparte | Un panel: aquí el primer pintado y el SEO lo son todo |
| Un consumidor de la API REST | **Un backend.** Ni una regla de negocio vive aquí |
| React, servido desde el servidor | Una SPA: la mayor parte del HTML llega hecho |

La restricción más importante del proyecto está en
[ADR-0001](../adr/0001-ssr-storefront-csr-admin.md): **cero lógica de negocio en
Next**. Nada de Route Handlers haciendo de API, nada de Server Actions que
calculen precios. Precios, descuentos, stock y totales los calcula la API y solo
la API ([ADR-0002](../adr/0002-carrito-servidor-fuente-de-verdad.md)). El reto
pide separación real entre frontend y backend por REST; una tienda que calcula
su propio total no la tiene.

La única excepción admisible —y hay que decir por qué lo es— es un Route Handler
de **revalidación de caché** (§19). No contiene reglas: le dice a Next que una
página caducó. Es infraestructura de caché, no negocio.

---

## 2. Por qué SSR/ISR y no una SPA

| Razón | Detalle |
| --- | --- |
| **SEO del catálogo** | Una tienda que se pinta en cliente le entrega al rastreador un `<div id="root">` vacío. Google ejecuta JavaScript, pero lo hace tarde y con presupuesto limitado; las páginas de producto de una tienda nueva son justo las que no pueden permitirse esa cola. Con SSR el HTML ya lleva nombre, precio, descripción y datos estructurados. |
| **Primer pintado en móvil** | El comprador llega por un enlace en 4G. Con SPA: descargar JS → ejecutar → pedir datos → pintar. Con SSR: el HTML ya trae el contenido y la imagen principal empieza a cargar en el primer viaje. Es la diferencia entre un LCP de ~1 s y uno de 3–4 s, y es exactamente lo que mide el RNF-02 del [plan](../PLAN.md). |
| **Metadatos por producto** | `generateMetadata` produce título, descripción y Open Graph **por producto**. Un enlace compartido en WhatsApp muestra foto, nombre y precio. Una SPA muestra el mismo título genérico para los 200 productos. |
| **URLs compartibles con filtros** | Los filtros viven en `searchParams`, así que `/productos?subcategoria=laptops&sort=price_asc` es un enlace que otra persona abre y ve lo mismo. Con filtros en `useState` no hay nada que compartir. |
| **Caché real** | Con ISR, una página de producto se genera una vez y se sirve desde caché a los siguientes miles de visitantes sin tocar la API ni la base. En un VPS de 2 GB eso no es un lujo. |

### Lo que cuesta, dicho en voz alta

| Coste | Cómo se gestiona |
| --- | --- |
| Hay un proceso Node en producción | Build `standalone`, imagen alpine. Es el único servicio Node del despliegue |
| Hay una frontera servidor/cliente que entender y respetar | §6. Es la fuente principal de errores de este tipo de aplicación |
| Datos cacheados pueden quedar obsoletos | Ventanas de revalidación explícitas por ruta (§5) y revalidación bajo demanda al editar (§19) |
| Riesgo de desajuste de hidratación | §10, con las tres causas concretas que aparecen en esta tienda |
| Next.js es React, pero alguien podría leerlo como «no usó React» | Se dice explícitamente en el README, y el carrito con `useReducer` es la demostración visible de manejo de estado |

---

## 3. Stack

| Pieza | Elección | Por qué |
| --- | --- | --- |
| Framework | Next.js, App Router | Server Components, ISR y `generateMetadata` en una sola pieza |
| Lenguaje | TypeScript, `strict: true` | §4 |
| Estilos | Tailwind CSS | Mismos tokens que el panel, sin CSS-in-JS en tiempo de ejecución (que rompe el pintado en servidor) |
| Estado del carrito | **`useReducer` + Context** | §9 |
| Formularios | React Hook Form + Zod | Checkout y acceso |
| Toasts | Una biblioteca ligera (sonner) | §16 |
| Pruebas | Vitest (reducer) + React Testing Library | §20 |

**No hay TanStack Query aquí, y es deliberado.** En el panel su trabajo es
cachear estado del servidor; aquí ese trabajo lo hace el propio servidor con
ISR. Los únicos datos de cliente son el carrito y la sesión, que no son una
caché sino una máquina de estados. Añadir un gestor de caché para dos cosas que
no se cachean es peso sin beneficio.

> En App Router reciente (Next 15), `params` y `searchParams` llegan a la página
> como promesas y se esperan con `await`. Conviene fijar la versión mayor en
> `package.json` y no mezclar ambas formas dentro del proyecto.

---

## 4. Estructura

```text
apps/storefront/
├── app/
│   ├── layout.tsx              cabecera, pie, providers de cliente
│   ├── page.tsx                portada · ISR
│   ├── productos/
│   │   ├── page.tsx            listado · SSR con searchParams
│   │   └── [slug]/page.tsx     detalle · ISR + generateMetadata + JSON-LD
│   ├── c/
│   │   ├── [categoria]/page.tsx
│   │   └── [categoria]/[subcategoria]/page.tsx
│   ├── carrito/page.tsx        cliente
│   ├── checkout/page.tsx       cliente
│   ├── pedido/[numero]/page.tsx
│   ├── (cuenta)/…              acceso, registro, recuperar, restablecer, mi-cuenta
│   ├── loading.tsx · error.tsx · not-found.tsx
│   ├── sitemap.ts · robots.ts
├── components/
│   ├── ui/                     Button, Input, Skeleton, EmptyState, Drawer, Toast
│   ├── producto/               ProductCard, ProductGrid, Price, Rating, Gallery
│   ├── carrito/                AddToCartButton, QuantityStepper, CartBadge, MiniCart
│   └── layout/                 Header, Footer, CategoryMenu, SearchBox
├── features/
│   ├── cart/                   CartProvider, cartReducer, cartApi, useCart, types
│   ├── account/                AuthProvider, useSession, accountApi
│   └── wishlist/               favoritos en localStorage
└── lib/
    ├── api.server.ts           fetch servidor→servidor (red interna)
    ├── api.client.ts           fetch desde el navegador
    ├── format.ts               PEN, fechas
    ├── imageLoader.ts          variantes del CDN
    └── jsonld.ts
```

**Idioma.** Las URL y todo el texto visible en español; los identificadores de
código en inglés. Las carpetas bajo `app/` son segmentos de URL, así que van en
español (`productos`, `carrito`); las de `components/` y `features/` son código.
No es incoherencia: son dos cosas distintas que casualmente son carpetas.

**Sin `any`.** Los tipos se generan desde
[`contracts/openapi.yaml`](../../contracts/openapi.yaml) con
`openapi-typescript` hacia `packages/api-types`, compartidos con el panel.

---

## 5. Estrategia de render por ruta

| Ruta | Estrategia | `revalidate` | Por qué |
| --- | --- | --- | --- |
| `/` | ISR | 60 s | Destacados y promociones. Cambia poco y la ve todo el mundo: es la página que más se beneficia de la caché |
| `/productos` | **SSR dinámico** | — | Lee `searchParams`. Una página que depende de la consulta no se puede precalcular |
| `/productos/[slug]` | ISR + `generateStaticParams` | 300 s | El detalle es la página que se comparte y se indexa. Se pregeneran los destacados; el resto se genera en la primera visita y queda cacheado |
| `/c/[categoria]` | ISR | 600 s | Hub de la categoría: sus subcategorías y algunos destacados. Cambia con menos frecuencia que un producto |
| `/c/[categoria]/[subcategoria]` | ISR | 600 s | Página de aterrizaje del listado: primera página, orden por defecto, sin filtros |
| `/carrito` | Cliente | — | Es estado de cliente puro. Renderizarlo en servidor no aporta nada e impediría cachear la ruta |
| `/checkout` | Cliente | — | Formulario, validación y una mutación |
| `/pedido/[numero]` | SSR, `noindex` | — | Datos de una compra concreta: nunca cacheable, nunca indexable |
| `/favoritos` | Cliente | — | Viven en `localStorage` |
| `/acceso`, `/registro`, `/recuperar`, `/restablecer`, `/mi-cuenta/*` | Cliente, `noindex` | — | §14 |

### Las URL de categoría, con dos niveles

El dominio tiene **categoría → subcategoría** y los productos cuelgan de la
subcategoría ([ADR-0006](../adr/0006-categorias-dos-niveles.md)). Las URL lo
reflejan:

```text
  /c/tecnologia                      categoría   → lista sus subcategorías
  /c/tecnologia/laptops              subcategoría → lista sus productos
  /productos/mochila-urbana-negra    producto
```

El slug de subcategoría es único globalmente (`accesorios-tecnologia`,
`accesorios-deportes`), así que técnicamente serviría una URL plana
`/c/accesorios-tecnologia`. **Se elige la anidada como canónica** porque la
jerarquía en la ruta produce migas de pan naturales, hace obvio el
`BreadcrumbList` de datos estructurados y evita que una misma página exista en
dos direcciones. Si hiciera falta soportar la forma plana (enlaces antiguos),
sería una redirección `301` a la anidada, nunca una segunda página viva.

> Esto actualiza la §2.3 del [plan](../PLAN.md), que aún describe
> `/categorias/[slug]` con un solo nivel, escrita antes de que el modelo de
> dominio fijara los dos niveles.

### Por qué el listado filtrable está separado de la página de subcategoría

Una página que lee `searchParams` es dinámica **siempre**: no hay ISR posible.
Si la página de subcategoría soportara `?sort=&page=&minPrice=`, perdería la
caché justo en la ruta que más interesa cachear, que es la que indexa Google.

La separación es:

```text
  /c/tecnologia/laptops         ISR · primera página · orden por defecto · indexable
        │
        └─ «Filtrar y ordenar» ─▶ /productos?subcategoria=laptops&sort=price_asc
                                   SSR · todos los filtros · noindex, follow
```

Y para que esas dos páginas no compitan como contenido duplicado, el listado
filtrado lleva `robots: noindex, follow` y un `canonical` que apunta a la página
de subcategoría cuando el único filtro activo es la subcategoría. `follow`
importa: los enlaces a los productos siguen valiendo para el rastreo aunque la
página filtrada no se indexe.

---

## 6. La frontera servidor / cliente

Es la decisión de diseño más característica de esta aplicación y la que más
errores produce cuando no está escrita.

**Por omisión todo es Server Component.** `'use client'` es una excepción que se
justifica, y se pone **lo más abajo posible** del árbol.

```text
  app/productos/[slug]/page.tsx              ← SERVIDOR
  ├── <Breadcrumbs/>                         ← servidor (datos, sin interacción)
  ├── <ProductGallery/>                      ← CLIENTE (cambia de imagen al pulsar)
  ├── <Price/>                               ← servidor (solo formatea)
  ├── <QuantityStepper/>  ─┐                 ← CLIENTE
  ├── <AddToCartButton/>  ─┘ islas pequeñas  ← CLIENTE (despacha al carrito)
  ├── <RelatedProducts/>                     ← servidor (misma subcategoría, RN-025)
  └── <ProductJsonLd/>                       ← servidor (script, cero JS al cliente)
```

Regla de decisión, en este orden:

| ¿Necesita…? | Entonces |
| --- | --- |
| `useState`, `useReducer`, `useEffect`, Context | Cliente |
| Un manejador de eventos (`onClick`, `onChange`) | Cliente |
| APIs del navegador (`localStorage`, `window`) | Cliente |
| Solo datos y formato | **Servidor** |
| Datos secretos o la URL interna de la API | **Servidor, obligatoriamente** |

**El coste de subir `'use client'` demasiado arriba.** Marcar el layout como
cliente convierte todo su subárbol en cliente: el HTML deja de venir hecho, el
paquete de JavaScript crece y se pierde la razón por la que se eligió Next. El
error típico es poner `'use client'` en la página de detalle entera porque el
botón de añadir necesita estado. Lo correcto es que el botón sea la isla.

**Lo que nunca cruza la frontera.** Un Server Component puede pasarle a una isla
cliente solo datos serializables: números, cadenas, objetos planos. No funciones,
no clases, no `Date` con métodos esperados al otro lado. Y jamás un token ni la
URL interna de la API: lo que se pasa como prop acaba en el HTML, visible para
cualquiera que mire el código fuente.

---

## 7. Dos URL de API, y no es un descuido

```text
  ┌── Server Components ────────┐
  │  fetch(API_URL_INTERNAL)    │ ──▶ http://api:8080/api/v1   (red Docker, no sale a internet)
  └─────────────────────────────┘
  ┌── Islas cliente ────────────┐
  │  fetch(NEXT_PUBLIC_API_URL) │ ──▶ https://api.tudominio.com/api/v1
  └─────────────────────────────┘
```

El mismo dato se pide por dos caminos distintos según quién pregunte. El fetch
de servidor no da la vuelta por internet: va por la red interna del compose, sin
TLS ni DNS público, y es más rápido y más barato.

Dos archivos separados, `lib/api.server.ts` y `lib/api.client.ts`, y el primero
marcado con el paquete `server-only`. Así, si alguien lo importa desde un
componente cliente, **falla la compilación** en lugar de filtrar la URL interna
al HTML. Un comentario pidiendo que no se haga no impide que se haga.

Las peticiones del carrito y de la cuenta salen **del navegador**, directas a la
API: son mutaciones con estado de usuario y no tiene sentido enrutarlas por el
servidor de Next, que además obligaría a reenviar cookies a mano. Eso exige CORS
en la API para el origen de la tienda y `credentials: 'include'` en el lado del
cliente para que viaje la cookie de refresco.

---

## 8. Carga, vacío, error, éxito

Los cuatro estados siguen siendo obligatorios; lo que cambia respecto a una SPA
es **quién los pinta**.

| Estado | En páginas de servidor | En islas cliente |
| --- | --- | --- |
| Carga | `loading.tsx` del segmento + `<Suspense>` con esqueleto | Botón en estado «enviando», fila atenuada |
| Vacío | Comprobación explícita antes de pintar la rejilla | Carrito vacío con enlace al catálogo |
| Error | `error.tsx` del segmento, con botón de reintentar | Toast con mensaje por código + reversión (§9) |
| No existe | `notFound()` → `not-found.tsx` | — |
| Éxito | El marcado | — |

Cuatro detalles propios del App Router:

* **`error.tsx` tiene que ser un componente cliente.** Recibe `reset()` y lo
  invoca al pulsar «Reintentar». Es la única forma de recuperarse sin recargar.
* **Un producto inexistente es `notFound()`, no un error.** Da `404` de verdad,
  que es lo que el rastreador necesita ver. Devolver `200` con un mensaje de
  «no encontrado» deja al buscador indexando páginas vacías.
* **El esqueleto imita la rejilla**, con el mismo número de tarjetas y la misma
  proporción de imagen. Un spinner centrado produce un salto de maquetación
  cuando llegan los datos, y ese salto lo penaliza la métrica de CLS.
* **Vacío con filtros y vacío sin filtros son mensajes distintos.** «No hay
  productos entre S/ 50 y S/ 80 en Laptops» con un botón de limpiar filtros;
  nunca el mismo texto genérico que cuando la subcategoría está realmente vacía.

### Los errores se traducen por `code`, y los datos del error se usan

La API responde `application/problem+json` con un campo **`code`** estable, y
[el catálogo de errores](../contrato/catalogo-errores.md) es el contrato. Se
ramifica por `code`, nunca por `title` ni `detail`, y jamás se enseña el
`detail` crudo.

Lo que distingue un mensaje útil de uno inútil en esta tienda es **usar los
campos extra que el error trae**:

| `code` | Datos que trae | Mensaje |
| --- | --- | --- |
| `INSUFFICIENT_STOCK` | `disponible`, `solicitado` | «Solo quedan 3 unidades. Ajustamos tu carrito a 3.» |
| `COUPON_MIN_NOT_MET` | `subtotalActual`, `subtotalMinimo` | «Te faltan S/ 18.10 para usar este cupón.» |
| `COUPON_NOT_APPLICABLE` | — | «Ese cupón no está vigente.» |
| `COUPON_EXHAUSTED` | — | «Ese cupón ya se agotó.» |
| `COUPON_NOT_FOUND` | — | «No encontramos ese código.» |
| `PRODUCT_INACTIVE` | `productoNombre` | «"Mochila Urbana" ya no está disponible.» |
| `CART_NOT_FOUND` | — | Sin mensaje: se crea un carrito nuevo (§9) |
| `TOO_MANY_REQUESTS` | cabecera `Retry-After` | «Demasiados intentos. Vuelve a intentarlo en 12 minutos.» |

«Stock insuficiente» a secas no le dice al comprador qué hacer; «solo quedan 3»
sí. La diferencia son dos campos que el backend ya está enviando.

Un código desconocido cae a un mensaje genérico acompañado del `correlationId`
que viaja en el cuerpo y en la cabecera `X-Correlation-Id` (RN-081): es lo que
permite que un usuario reporte «me salió el código 0f9c2b1e» y alguien encuentre
la línea de log exacta.

---

## 9. El carrito: `useReducer` + Context

Es el corazón de la tienda y el punto que se va a preguntar en la entrevista.
Merece precisión.

### Por qué el servidor es la fuente de verdad

El ítem de carrito **no guarda precio**: el carrito refleja siempre el precio
vigente del producto (RN-032), y `subtotal`, `discount` y `total` los calcula la
API ([ADR-0002](../adr/0002-carrito-servidor-fuente-de-verdad.md)). El cliente
nunca envía precios ni totales, porque cualquiera puede editar lo que su
navegador envía.

De ahí sale la propiedad que hace todo lo demás fácil: **toda mutación del
carrito devuelve el carrito completo con totales recalculados** (RN-033). La
reconciliación es entonces un reemplazo, no una fusión. No hay que mezclar
líneas ni decidir qué campo gana: llega el carrito autoritativo y sustituye al
optimista.

Dos reglas más que el reducer debe respetar al pie de la letra, porque son
justamente donde una implementación ingenua se desvía del servidor:

* **Un producto, una línea** (RN-031). Agregar algo que ya está **suma** a la
  línea existente; no crea una segunda.
* **El stock se valida sobre el total resultante** (RN-030), no sobre lo que se
  añade: con 3 en el carrito y 3 de stock, añadir 1 más se rechaza. Si el
  optimista comprobara solo la cantidad añadida, mostraría 4 y el servidor
  devolvería 3: un parpadeo evitable.

### Por qué `useReducer` y no Redux ni Zustand

| Argumento | Detalle |
| --- | --- |
| Es **una** máquina de estados, no un almacén global | El carrito tiene transiciones con nombre: optimista → confirmado → revertido. Un reducer es literalmente esa tabla de transiciones, en un archivo que se lee de arriba abajo |
| Es una función pura | Se prueba con Vitest sin React, sin render, sin mocks. Los casos difíciles —revertir un `409`, ignorar una respuesta obsoleta— se prueban en milisegundos |
| El reto pide `useState`/`useReducer` | Y aquí no es una concesión: es la herramienta adecuada para el tamaño del problema |
| Redux añade store, middleware y devtools para un solo dominio | Coste de configuración y de explicación que no compra nada aquí |
| Zustand es ligero, pero su API invita a `set()` repartidos por los componentes | Se pierde la tabla única de transiciones, que es justo lo que hace auditable el rollback |

La contrapartida real: **Context repinta a todos sus consumidores en cada
cambio**. Con un carrito de pocas líneas es irrelevante, pero la mitigación es
barata y se aplica desde el principio: **dos contextos**, uno para el estado y
otro para `dispatch`. El botón «Agregar al carrito» de cada tarjeta consume solo
el de `dispatch`, que nunca cambia de identidad, así que una rejilla de 24
tarjetas no se repinta cuando cambia una cantidad.

Si algún día el carrito creciera hasta que eso dejara de bastar, la respuesta
sería `useSyncExternalStore`, no Redux. Pero eso se decide con una medición, no
por si acaso.

### Forma del estado

```ts
type CartState = {
  status: 'idle' | 'hydrating' | 'ready' | 'error';
  cart: Cart | null;        // lo que se pinta: puede ser optimista
  server: Cart | null;      // último carrito autoritativo, para revertir
  pending: number;          // mutaciones en vuelo
  seq: number;              // número de la última mutación despachada
  lastError: string | null; // código, no mensaje
};
```

Dos copias, `cart` y `server`, porque revertir necesita un punto al que volver.
`server` solo cambia cuando responde la API; `cart` cambia también con cada
acción optimista.

### Las acciones

| Acción | Payload | Efecto sobre el estado | Quién la despacha |
| --- | --- | --- | --- |
| `HYDRATE` | `Cart` | Reemplaza `cart` y `server`, `status='ready'` | El montaje del provider, tras `GET /carts/{id}` |
| `ADD_ITEM` | `{ product, quantity, seq }` | Inserta la línea o **suma** a la existente; recalcula líneas y subtotal; `pending++` | `AddToCartButton` |
| `SET_QTY` | `{ itemId, quantity, seq }` | Fija la cantidad (valor absoluto, no delta); recalcula; `pending++` | `QuantityStepper` |
| `REMOVE_ITEM` | `{ itemId, seq }` | Quita la línea; recalcula; `pending++` | Carrito y mini-carrito |
| `CLEAR` | `{ seq }` | Vacía las líneas; conserva el `id` del carrito | «Vaciar carrito» |
| `APPLY_COUPON` | `{ code, seq }` | Marca `couponPending`. **No calcula descuento** | Formulario de cupón |
| `REMOVE_COUPON` | `{ seq }` | Marca `couponPending` | Botón de quitar |
| `SYNC_SUCCESS` | `{ seq, cart }` | Si `seq` es la última: reemplaza `cart` y `server` con el del servidor. Si no, se ignora. `pending--` | La respuesta de la API |
| `SYNC_ERROR` | `{ seq, code }` | Revierte `cart = server`, guarda `lastError`, `pending--` | Un fallo de la API |

Son las nueve de la §9 del [plan](../PLAN.md), sin inventar ninguna: lo que se
añade es el `seq`, y tiene un motivo concreto que se explica abajo.

### El flujo optimista, paso a paso

```text
  El usuario pulsa «+»
        │
        ├─▶ dispatch(SET_QTY, { itemId, quantity: 3, seq: 7 })
        │      └─ el reducer recalcula línea y subtotal  ← se ve YA, sin red
        │
        └─▶ PATCH /carts/{id}/items/{itemId}  { quantity: 3 }
                 │
                 ├── 200 ──▶ dispatch(SYNC_SUCCESS, { seq: 7, cart })
                 │             └─ el carrito del servidor sustituye al optimista:
                 │                cupones, precios cambiados y redondeos incluidos
                 │
                 └── 409 ──▶ dispatch(SYNC_ERROR, { seq: 7, code: 'INSUFFICIENT_STOCK' })
                               └─ vuelve a `server` + toast «Solo quedan 2 unidades»
```

**Qué recalcula el reducer y qué no.** Recalcula `lineTotal` y `subtotal`:
cantidad por precio unitario es aritmética, no una regla de negocio. **No
calcula el descuento del cupón.** Los cupones tienen tipo, mínimo de compra,
vigencia y usos máximos; reimplementar eso en el cliente es duplicar reglas de
negocio, que es exactamente lo que prohíbe el primer párrafo de este documento.
Mientras hay un cambio en vuelo, la línea de descuento y el total se marcan como
provisionales (atenuados, sin animación de cambio de cifra) y se confirman con
`SYNC_SUCCESS`. Es honesto y cuesta una clase de CSS.

### Respuestas fuera de orden: para qué está `seq`

Un usuario impaciente pulsa «+» tres veces. Salen tres `PATCH` y **nada
garantiza que las respuestas lleguen en orden**. Si la respuesta de «2» llega
después de la de «3», sin protección el carrito retrocede a 2 solo, delante del
usuario, y parece un fantasma.

```text
   despacha  PATCH(2) seq=5 ─────────────┐
   despacha  PATCH(3) seq=6 ──┐          │
                              ▼          ▼
                          resp seq=6  resp seq=5
                          se aplica    se IGNORA (5 < 6)
```

`SYNC_SUCCESS` solo se aplica si su `seq` coincide con la última mutación
despachada. Las respuestas rezagadas se descartan. Son cuatro líneas en el
reducer y eliminan un bug que es muy difícil de diagnosticar después.

**Además se agrupan los clics.** El `+`/`−` aplica el cambio optimista al
instante pero **retrasa 400 ms el `PATCH`**, enviando solo la cantidad final.
Tres clics rápidos son una petición, no tres. Es seguro porque `PATCH` fija un
valor absoluto: la última gana y el resultado es el mismo.

**Lo que no se agrupa ni se reintenta: `POST /carts/{id}/items`.** Ese endpoint
**suma** cantidad si el producto ya está en el carrito (RN-031). Reintentarlo
tras un *timeout* de red añade el producto dos veces, y el usuario ve 2 donde
pidió 1. Regla: los `GET` se reintentan, las mutaciones del carrito no. Un fallo
de red en una mutación revierte y ofrece reintentar **al usuario**, que es quien
puede decidir.

### Bajar de 1 elimina la línea

Pulsar «−» con cantidad 1 **quita la línea**, no la deja en cero: una línea de
cero artículos no significa nada para el comprador. La acción despachada es
`REMOVE_ITEM`, con la línea desapareciendo al instante y un toast con
«Deshacer».

En el transporte hay hoy una ambigüedad que conviene conocer: RN-034 dice que
enviar `quantity: 0` en el `PATCH` elimina la línea, mientras que el
[contrato](../../contracts/openapi.yaml) declara `minimum: 1` para ese campo —lo
que haría que un `0` diera `400`. La tienda **usa `DELETE` sobre la línea**, que
es inequívoco en ambas lecturas y no depende de cómo se resuelva (§22).

### El identificador del carrito

```text
  primera visita ─▶ no hay nada en localStorage ─▶ NO se crea carrito
  primer «Agregar» ─▶ POST /carts ─▶ cartId ─▶ localStorage
  visitas siguientes ─▶ GET /carts/{cartId} ─▶ HYDRATE
```

**El carrito se crea con el primer añadido, no al cargar la página.** Crearlo al
entrar genera una fila por cada visitante y por cada rastreador que pase, y la
inmensa mayoría no comprará nada.

Si el `GET` de hidratación responde `404 CART_NOT_FOUND` —carrito purgado a los
90 días de inactividad (RN-035), o de otro entorno—, se limpia `localStorage` en
silencio y se empieza de cero. Es un caso normal, no un error que merezca un
toast: el usuario no hizo nada malo.

El `cartId` es un UUID: no identifica a una persona ni da acceso a nada más que
a ese carrito.

**Al iniciar sesión, el carrito se rehidrata.** El servidor asocia el carrito
anónimo al cliente y, si este ya tenía uno de una sesión anterior, **los
fusiona**: cantidades que suman, acotadas por el stock (RN-036). Eso significa
que el carrito que hay en memoria tras el acceso puede no ser el que el servidor
tiene, así que el flujo de acceso termina siempre con un `GET /carts/{id}` y un
`HYDRATE`. Dar por bueno el estado local después de entrar es la forma más
directa de mostrar un carrito que no existe.

---

## 10. Hidratación: el badge del carrito y otras tres trampas

El desajuste de hidratación ocurre cuando el HTML que generó el servidor no
coincide con lo que React pinta en el primer render del cliente. React avisa en
consola y, en el peor caso, descarta el árbol y lo vuelve a pintar: parpadeo y
trabajo tirado.

### El badge del carrito

El número de artículos depende del `cartId` de `localStorage`. **El servidor no
tiene `localStorage`**: al renderizar no puede saber si hay 0 o 3 artículos.
Escribir `<span>{count}</span>` produce `0` en el servidor y `3` en el cliente:
desajuste garantizado, en el componente que aparece en **todas** las páginas.

```text
  SERVIDOR                          CLIENTE (tras montar)
  ┌──────────────┐                  ┌──────────────┐
  │   🛒         │   ── hidrata ──▶ │   🛒 ③       │
  └──────────────┘                  └──────────────┘
   sin número,                        aparece el número,
   hueco reservado                    anunciado con aria-live
```

La solución: **el badge no se renderiza en servidor con datos**. El icono sí
—está siempre— pero el contador solo aparece cuando el provider ha hidratado.
Dos formas correctas, en orden de preferencia:

1. Un `mounted` en el provider (`useEffect` que lo pone a `true`); el contador
   se pinta solo si `mounted && count > 0`. El primer render del cliente es
   idéntico al del servidor, así que la hidratación cuadra, y el número aparece
   en el siguiente pintado.
2. `useSyncExternalStore` con una instantánea de servidor explícita (`0`),
   que es la misma idea con la API que React ofrece para esto.

Lo que **no** es solución: `suppressHydrationWarning`. No arregla el desajuste,
solo silencia el aviso; el problema sigue ahí y se ha perdido la señal que lo
delataba.

**La alternativa que se descartó, y por qué.** Si el `cartId` viviera en una
cookie, el servidor podría pintar el badge con el número correcto. Pero leer
cookies en un componente de servidor **marca la ruta como dinámica**, y eso
mataría el ISR de la portada y del detalle de producto: se cambiaría la caché de
todo el catálogo por un número en un icono. No compensa. El hueco del badge se
reserva con `min-width` para que su aparición no mueva la cabecera.

### Las otras tres trampas, que aparecen todas en esta tienda

| Trampa | Por qué falla | Qué se hace |
| --- | --- | --- |
| `Intl.NumberFormat` para el precio | El ICU de Node y el del navegador pueden diferir en el espacio que separa `S/` del número. Un carácter distinto ya es desajuste | Un único helper en `lib/format.ts`, imagen de Node con ICU completo, y el mismo `locale` fijado explícitamente (`es-PE`), nunca el del sistema |
| `new Date()` o «hace 3 minutos» durante el render | El servidor y el cliente renderizan en instantes distintos | Nada de tiempo relativo en servidor. Se pinta la fecha absoluta y, si se quiere el relativo, se calcula tras montar |
| `Math.random()` o `crypto.randomUUID()` para claves | Dan valores distintos en cada lado | Las claves salen del `id` del dato, siempre |

---

## 11. Filtros y búsqueda: en la URL, no en `useState`

```text
  /productos?q=mochila&subcategoria=accesorios-tecnologia&marca=xyz
            &minPrice=50&maxPrice=200&inStock=true&sort=price_asc&page=2
```

| Parámetro | Notas |
| --- | --- |
| `q` | Búsqueda por nombre y descripción corta, insensible a acentos y mayúsculas (RN-021), con retardo de 350 ms antes de escribirse en la URL |
| `subcategoria` | Slug. **No hay filtro por categoría a secas**: los productos cuelgan de la subcategoría. Filtrar por categoría sería un `IN` sobre sus subcategorías, y eso se pide con la página `/c/[categoria]` |
| `marca`, `minPrice`, `maxPrice`, `inStock` | Combinables entre sí (RN-022). Si `minPrice > maxPrice` el servidor responde `422 INVALID_PRICE_RANGE`: el control de rango lo impide antes, y el error se trata igual por si llega desde una URL pegada a mano |
| `sort` | Enum cerrado (`newest`, `price_asc`, `price_desc`, `name_asc`, `rating_desc`). Un valor fuera de la lista es `400`, no un orden por defecto silencioso (RN-023) |
| `page` | Empieza en 1 y siempre vuelve a 1 al cambiar cualquier otro parámetro. El `pageSize` tiene tope y superarlo se rechaza, no se recorta (RN-024) |

**Por qué la URL y no el estado local:**

* La vista es compartible: un enlace que otra persona abre y ve lo mismo.
* Es indexable por el rastreador si se decide que lo sea (y `noindex` si no).
* El botón atrás funciona como el usuario espera.
* Recargar no pierde la búsqueda.
* El servidor recibe los filtros en la petición inicial y devuelve el HTML ya
  filtrado: sin ese diseño habría que pintar el listado vacío y rellenarlo
  después, que es el comportamiento de SPA que se quería evitar.

Escribir los parámetros se hace desde una isla cliente con `useRouter` y
`useSearchParams`, con dos matices:

* `router.replace` para el campo de búsqueda mientras se escribe —no ensuciar el
  historial— y `router.push` para un cambio deliberado de filtro o de página, que
  sí debe poder deshacerse con «atrás».
* `useTransition` alrededor de la navegación: mientras el servidor re-renderiza,
  `isPending` atenúa la rejilla en vez de dejarla congelada. Es lo que evita que
  cambiar un filtro parezca que no hizo nada.
* `scroll: false` al cambiar un filtro. Saltar al principio de la página cada
  vez que se mueve el precio máximo es desorientador.

SEO de los listados: `canonical` sin los parámetros volátiles, `noindex, follow`
en cualquier combinación con filtros, y solo indexables `/productos` sin
parámetros y las páginas de subcategoría. Sin esa regla, un catálogo de 200
productos genera decenas de miles de URL equivalentes y el buscador reparte su
presupuesto de rastreo entre basura.

---

## 12. Imágenes

El CDN ya sirve exactamente lo que hace falta: WebP en tres anchos
([ADR-0009](../adr/0009-imagenes-r2-webp.md) y
[archivos e imágenes](../backend/archivos-imagenes.md) §3).

| Variante | Ancho | Dónde |
| --- | --- | --- |
| `miniatura` | 160 px | Mini-carrito, línea de carrito, resumen del checkout |
| `tarjeta` | 480 px | Rejilla de productos, relacionados |
| `detalle` | 1200 px | Galería del producto, imagen de Open Graph |

### `next/image` con un loader propio

Pasar estas imágenes por el optimizador de Next sería pagar dos veces: el
servidor Node redimensionaría en el VPS algo que el pipeline ya redimensionó y
subió al CDN. Con un **loader personalizado**, `next/image` conserva lo que
aporta —`sizes`, carga diferida, reserva de espacio, `priority`— y los bytes
viajan directos del CDN al navegador, **sin tocar el proceso Node**:

```ts
// lib/imageLoader.ts   — recibe cualquier URL de variante y devuelve la que toca
const pick = (w: number) => (w <= 160 ? 'miniatura' : w <= 480 ? 'tarjeta' : 'detalle');

export default function cdnLoader({ src, width }: { src: string; width: number }) {
  return src.replace(/\/[^/]+\.webp$/, `/${pick(width)}.webp`);
}
```

La contrapartida honesta: se renuncia a que Next genere AVIF y anchos
arbitrarios. No se echa de menos, porque el pipeline ya decidió los tres anchos
que la tienda usa y el formato es WebP por diseño. Y se gana lo que de verdad
importa en un VPS de 2 GB: el servidor de Next no procesa ni una imagen.

### `sizes`, que es donde se equivoca todo el mundo

`sizes` le dice al navegador **qué espacio ocupará la imagen**, para que elija la
variante antes de conocer el CSS. Mal puesto, descarga la de 1200 px para pintar
una tarjeta de 240:

| Contexto | `sizes` |
| --- | --- |
| Tarjeta en rejilla (2/3/4 columnas) | `(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 240px` |
| Imagen principal del detalle | `(max-width: 1024px) 100vw, 600px` |
| Miniatura del mini-carrito | `80px` |

### `priority` solo en la primera

`priority` inyecta un `preload`. Precargar cinco imágenes las pone a competir
por el mismo ancho de banda y **retrasa** justo la que se quería acelerar. Se
usa en una sola por página:

* Portada: la primera tarjeta de la rejilla destacada (o el banner, si lo hay).
* Detalle: la imagen principal de la galería.
* En ninguna otra. El resto se carga en diferido, que es el comportamiento por
  defecto.

### Encaje y `alt`

Las imágenes **no se recortan** en el servidor. El encaje lo hace el CSS con un
contenedor de proporción fija y `object-fit: contain`, no `cover`: `cover`
decapita productos altos, y eso hace que una tienda parezca descuidada. Fijar la
proporción del contenedor es además lo que evita el salto de maquetación.

El `alt` viene de la API —es obligatorio al subir la imagen— y se usa tal cual.
No se sustituye por el nombre del producto «porque queda mejor»: ese texto lo
escribió alguien mirando la foto. Una imagen decorativa (un banner sin
información) lleva `alt=""`, que es distinto de no llevar `alt`.

---

## 13. SEO y datos estructurados

### `generateMetadata`

En `/productos/[slug]`, por producto: `title` (nombre + marca), `description`
(descripción corta, recortada), `alternates.canonical` con la URL absoluta y
`openGraph` con `type: 'product'`, el nombre, el precio y la variante `detalle`
como imagen (1200 px de ancho es justo lo que piden las previsualizaciones).

Si el producto no existe, `generateMetadata` también debe resolver a `notFound()`
en lugar de inventar metadatos de una página que va a dar `404`.

### JSON-LD

Dos bloques, ambos en componentes de servidor, así que **no añaden ni un byte de
JavaScript al cliente**:

* `Product` — nombre, imagen, descripción, `brand` (la marca del dominio), `sku`
  y `offers` con `price`, `priceCurrency: 'PEN'` y `availability` derivada del
  stock real.
* `BreadcrumbList` — Inicio › Categoría › Subcategoría › Producto. Es la
  recompensa directa de haber elegido URL anidadas (§5): las migas salen de la
  ruta.

**Sin `aggregateRating`.** La calificación viene del seed y no hay sistema de
reseñas: declarar una valoración agregada en datos estructurados sería afirmar
ante el buscador algo que no ocurrió, y las penalizaciones por datos
estructurados falsos son reales. Las estrellas pueden pintarse en la interfaz
como dato de demostración; en el JSON-LD, no.

### `sitemap.ts` y `robots.ts`

Se generan desde la API: categorías, subcategorías y productos activos.
`robots.ts` excluye `/carrito`, `/checkout`, `/pedido/*`, `/mi-cuenta/*` y
`/favoritos`, que no son contenido y en algún caso son datos de una persona.

---

## 14. El acceso del cliente

Solo los **clientes** tienen cuenta
([ADR-0008](../adr/0008-cuentas-solo-clientes.md)). El administrador entra por
otra aplicación, con otro token y otra audiencia
([autenticación](../backend/autenticacion.md)). Aquí no hay ni rastro del panel:
un token de cliente presentado al panel devuelve `403 WRONG_AUDIENCE`, y al
revés igual.

Y **comprar no exige cuenta** (RN-063): el carrito es anónimo y el checkout
funciona sin sesión. Tener cuenta añade historial y datos guardados; no es un
peaje para comprar.

| Ruta | Pantalla | Notas |
| --- | --- | --- |
| `/acceso` | Correo + contraseña, y «Continuar con Google» | Mensaje de error único |
| `/registro` | Correo, contraseña, nombre | Responde `202` siempre (ver abajo) |
| `/verificar-email` | Recibe `?token=` del correo y lo canjea | Éxito, ya usado o expirado: tres mensajes distintos |
| `/recuperar` | Pide el correo | Responde `202` siempre |
| `/restablecer` | Recibe `?token=`, pide contraseña nueva | Al terminar, todas las sesiones quedan revocadas: hay que entrar de nuevo, y se dice |
| `/establecer-contrasena` | Para quien entró con Google y aún no tiene contraseña | §14.3 |
| `/mi-cuenta` | Datos y pedidos | Exige correo verificado |
| `/mi-cuenta/seguridad` | Cambiar o establecer contraseña, vincular o desvincular Google | §14.3 |

Todas son componentes cliente y todas llevan `noindex`.

### 14.1 El servidor de Next no sabe quién eres, y está bien

La cookie de refresco la emite `api.tudominio.com` y es de ese host. **El
servidor de Next no puede leerla**, ni aunque quisiera. De ahí una conclusión
que conviene tener escrita, porque ahorra discusiones: *toda* la interfaz que
depende de la sesión es cliente. No hay páginas «privadas renderizadas en
servidor» en esta tienda, y no hace falta que las haya: el catálogo es público y
la cuenta no necesita SEO.

### 14.2 Token en memoria y rehidratación al recargar

El token de acceso vive **en memoria**, nunca en `localStorage`. Recargar la
página lo pierde, y por eso hay que rehidratar:

```text
  carga de la página
        │  sesión: "desconocida"  → la cabecera pinta un hueco neutro,
        │                            ni «Entrar» ni «Mi cuenta»
        ▼
  POST /auth/refrescar    (cookie httpOnly, credentials: 'include')
        ├─ 200 → token en memoria + datos del cliente → "autenticado"
        └─ 401 → "anónimo"    ← esto NO es un error: es el visitante normal
```

Cuatro reglas que salen de ahí:

1. **El servidor renderiza siempre el estado neutro.** Si pintara «Entrar», todo
   cliente con sesión vería ese texto un instante antes de que lo sustituyera
   «Mi cuenta» —y además sería un desajuste de hidratación, el mismo problema del
   badge (§10).
2. **El `401` del arranque no dispara nada.** Ni toast, ni redirección, ni
   registro de error. La inmensa mayoría de quienes visitan una tienda no tienen
   sesión; tratar eso como fallo llena la consola de ruido y, si además está
   enganchado al interceptor genérico de `401`, manda a `/acceso` a todo el
   mundo.
3. **Un solo refresco en vuelo**, con las peticiones que recibieron `401`
   encoladas detrás. El refresco **rota**: si se lanzan tres a la vez, dos
   presentan un token ya usado, el backend lo interpreta como robo y revoca la
   familia entera. Expulsar al cliente por una condición de carrera propia es el
   peor resultado posible.
4. **El carrito no depende de la sesión.** Se puede comprar sin cuenta; el
   `cartId` es anónimo. Iniciar sesión no debe vaciar ni reiniciar el carrito.

### 14.3 Los tres caminos de entrada, y sus consecuencias en la interfaz

**Correo y contraseña.** Mínimo 10 caracteres, **sin** exigir mayúsculas,
dígitos ni símbolos. La interfaz no debe mostrar requisitos que el servidor no
aplica: una lista de «debe contener un símbolo» es sencillamente falsa aquí, y
empuja a la gente hacia contraseñas peores. Un medidor de fuerza sí, como pista.
`autocomplete="email"`, `"new-password"` y `"current-password"` según el caso,
para que los gestores de contraseñas funcionen.

**Google.** El botón es una **navegación completa** a `GET /auth/google`
(`<a>` o `window.location.assign`), **nunca** un `fetch`. Un `fetch` no puede
seguir la redirección a la pantalla de consentimiento de Google, y ese es el
error que se comete siempre. El canje del código ocurre en el backend porque
exige el `client_secret`; la tienda solo ve el final del viaje: vuelve con la
cookie de refresco puesta y llama a `/auth/refrescar` para obtener su token en
memoria.

**Recuperación.** Un formulario con el correo, y la respuesta es siempre la
misma: «Si el correo está registrado, te hemos enviado un enlace». Idéntica
exista o no la cuenta.

> Esto último es más importante de lo que parece: el backend se esfuerza en no
> revelar si un correo existe —respuesta idéntica, tiempo constante, hash
> señuelo (RN-061, RN-066)— y **basta con que la interfaz diga «ese correo no
> está registrado» para tirar todo ese trabajo**. El registro responde `202`
> exista o no el correo; la pantalla de registro muestra «Revisa tu correo» en
> los dos casos. Y el acceso tiene **un solo código de error**,
> `401 INVALID_CREDENTIALS`, para correo inexistente, contraseña equivocada y
> cuenta desactivada (RN-067): un solo mensaje, «Correo o contraseña
> incorrectos». Mapear ese código a tres textos distintos reabre exactamente el
> agujero que el backend cerró.

Un detalle de la vinculación que la interfaz tiene que saber explicar: si el
correo ya existe y Google **no** lo da por verificado, la cuenta no se vincula
(`422 EMAIL_NOT_VERIFIED_BY_PROVIDER`, RN-064). El mensaje correcto no es «error
al entrar con Google», sino «Entra con tu contraseña y vincula Google desde tu
perfil», que es la acción que resuelve el caso.

**Cuando no hay contraseña.** Quien entró con Google tiene `hashContrasena`
nulo. Pedirle «tu contraseña actual» para cambiarla no tiene sentido: la
pantalla de seguridad distingue los dos casos y ofrece **establecer** (envía un
enlace al correo verificado) en lugar de **cambiar**. Mostrar un formulario que
el usuario no puede completar es un callejón sin salida.

Y **desvincular Google solo se ofrece si hay contraseña**: quitar la única forma
de entrar deja la cuenta inaccesible (RN-065 → `422 LAST_LOGIN_METHOD`). Si no
la hay, el botón explica qué hacer antes en lugar de fallar al pulsarlo.

**Lo que exige correo verificado.** Comprar, no. Ver el historial de pedidos y
cambiar la contraseña, sí (RN-063 → `422 EMAIL_NOT_VERIFIED`). Esa pantalla no
muestra un error genérico: muestra qué falta y un botón para reenviar el correo
de verificación.

### 14.4 Límite de intentos

Acceso, registro y recuperación tienen límites (RN-068); superarlos da
`429 TOO_MANY_REQUESTS` con cabecera `Retry-After`. La interfaz la lee y la
traduce a algo accionable —«Demasiados intentos. Vuelve a intentarlo en 12
minutos»— y deshabilita el botón con una cuenta atrás. Un `429` mostrado como
«Algo salió mal» hace que el usuario reintente sin parar, que es justo lo que el
límite quiere evitar.

---

## 15. Checkout y confirmación

`/checkout` es cliente: React Hook Form + Zod, validación por campo, datos de
contacto y dirección. Si hay sesión, los campos llegan rellenos con los datos
del cliente; si no, se compra como invitado, que es el caso por defecto.

Al enviar: `POST /orders` con el `cartId` y los datos de contacto. **Nunca con
precios ni totales**: el servidor revalida stock, precios y estado de cada
producto (RN-051), descuenta stock, guarda la foto de precios y devuelve el
número de pedido.

```text
  éxito                ─▶ CLEAR + borrar cartId de localStorage
                          ─▶ router.replace('/pedido/{numero}')
  409 INSUFFICIENT_STOCK ─▶ «El stock cambió mientras comprabas» + volver al carrito
                            con la línea marcada y la cantidad disponible
  422 PRODUCT_INACTIVE   ─▶ «"Mochila Urbana" ya no está disponible» + quitar la línea
  422 COUPON_*           ─▶ el cupón dejó de aplicar: se quita y se recalcula
  otro                   ─▶ mensaje por código; el formulario NO se limpia
```

Que el checkout pueda fallar por cualquiera de esos tres motivos no es un
defecto: es la consecuencia directa de que el servidor revalide todo al
confirmar en lugar de confiar en lo que el carrito llevaba. Lo importante es que
**ninguno devuelve al usuario a un formulario vacío**.

`replace` y no `push`: si el usuario pulsa «atrás» desde la confirmación, no
debe volver a un checkout cuyo carrito ya se convirtió.

**Doble envío.** El botón se deshabilita en el primer clic y no se rehabilita
hasta que hay respuesta. No es suficiente ante un fallo de red —la petición
podría haber llegado— y por eso un `POST /orders` no se reintenta
automáticamente. La solución robusta es una clave de idempotencia en la
petición; hoy el contrato no la tiene y queda anotada en §22.

`/pedido/[numero]` se renderiza en servidor, con `noindex` y sin caché. Que esa
página sea segura depende de una regla del backend, no del frontend: el número
tiene formato `ORD-AAAAMM-XXXXXX` con sufijo **aleatorio, no secuencial**
(RN-057). Con un número correlativo, esa URL sería un catálogo de pedidos
ajenos abierto a cualquiera que hiciera una compra y contara hacia atrás.

Por eso mismo la tienda no enlaza nunca ese número desde ningún sitio público ni
lo pone en parámetros de analítica: es un identificador que actúa como
credencial débil, y tratarlo con menos cuidado que eso anula la regla.

---

## 16. Accesibilidad

Las siete cosas que esta tienda concreta rompe si nadie las mira:

* **El precio tachado no se anuncia solo.** `<del>S/ 159.90</del> <ins>S/
  129.90</ins>` con texto para lector de pantalla: «antes S/ 159.90, ahora S/
  129.90». Dos números seguidos sin relación no significan nada oído en voz
  alta.
* **El badge del carrito es una región viva** (`aria-live="polite"`). Quien no ve
  la pantalla tiene que enterarse de que el carrito pasó a tres artículos.
* **«Agregar al carrito» confirma en texto.** El toast va con `role="status"`, y
  el botón anuncia su estado mientras envía. Un cambio de color no es una
  confirmación.
* **El mini-carrito es un diálogo de verdad**: atrapa el foco, se cierra con
  `Escape` y devuelve el foco al botón que lo abrió.
* **El selector de cantidad** es un `<input type="number">` con `<label>`, y los
  botones `+`/`−` llevan `aria-label` («Aumentar cantidad»). Un `<div>` con
  `onClick` no existe para el teclado.
* **La rejilla de productos es una lista** (`<ul>`/`<li>`): así se anuncia
  «lista de 12 elementos» y se puede navegar por ella. Una sucesión de `<div>`
  no dice nada.
* **Enlace de salto al contenido**, foco visible siempre, contraste AA —incluido
  el gris de «precio anterior», que es donde se suele incumplir.

Y una regla transversal: ningún estado se comunica **solo** con color. «Sin
stock» se dice con palabras, no con un punto rojo.

---

## 17. Moneda y fechas

Todo importe pasa por un único helper:

```ts
// lib/format.ts
export const money = (v: number) =>
  new Intl.NumberFormat('es-PE', { style: 'currency', currency: 'PEN' }).format(v);
```

Los precios ya incluyen IGV; el resumen del carrito desglosa qué parte es IGV
con lo que devuelve la API, sin calcularlo aquí. El `locale` se fija explícito:
si se deja el del sistema, el servidor formatea con el del contenedor y el
cliente con el del navegador, y eso es tanto una incoherencia visual como un
desajuste de hidratación (§10).

---

## 18. Rendimiento

El objetivo es Lighthouse ≥ 90 en Rendimiento y SEO en portada y detalle
(RNF-02 del [plan](../PLAN.md)). Lo que de verdad mueve esa aguja aquí:

* **El LCP es la imagen principal.** `priority` en ella y en ninguna más, la
  proporción del contenedor fijada, y el HTML llegando ya renderizado. Todo lo
  demás es secundario.
* **Islas pequeñas.** Cada `'use client'` es JavaScript que se descarga y
  ejecuta. El botón de añadir, el selector de cantidad y el badge son
  diminutos; que lo sigan siendo.
* **El mini-carrito se carga en diferido** (`next/dynamic`): su marcado solo
  hace falta cuando alguien lo abre.
* **Fuentes con `next/font`**, auto-hospedadas y con `font-display: swap`. Una
  fuente de un tercero añade una conexión más en la ruta crítica y provoca
  reflujo de texto.
* **Nada de bibliotecas pesadas en el cliente.** Un carrusel con animaciones de
  30 KB en la portada cuesta más de lo que aporta.
* **Las relacionadas se piden en el servidor**, dentro de la misma página
  cacheada: no son una petición extra desde el navegador.

---

## 19. Entorno y despliegue

```text
API_URL_INTERNAL=http://api:8080/api/v1          solo servidor, red Docker
NEXT_PUBLIC_API_URL=https://api.tudominio.com/api/v1   va al navegador
NEXT_PUBLIC_CDN_URL=https://cdn.tudominio.com
APP_PUBLIC_URL=https://tienda.tudominio.com      base de enlaces y canonical
```

**Todo lo que empiece por `NEXT_PUBLIC_` se envía al navegador en texto plano.**
Ningún secreto lleva ese prefijo, nunca. `API_URL_INTERNAL` no lo lleva, y por
eso solo puede leerse desde `lib/api.server.ts`, que está marcado `server-only`.

Build con `output: 'standalone'`, servido tras Caddy en `tienda.tudominio.com`
(ver [plan](../PLAN.md) §10).

### Revalidación bajo demanda

Cuando el panel edita un producto, su página ISR seguiría mostrando el precio
viejo hasta que caducara la ventana. Un Route Handler
`POST /api/revalidate` protegido por un secreto compartido, que el backend llama
tras guardar, ejecuta `revalidatePath('/productos/[slug]')` y la portada.

Esto **no** contradice el «cero lógica de negocio en Next» de
[ADR-0001](../adr/0001-ssr-storefront-csr-admin.md): ese handler no decide nada
ni lee la base de datos, solo invalida una caché. Si algún día empezara a
transformar datos o a aplicar reglas, sí sería una violación.

---

## 20. Pruebas

**El reducer del carrito es lo primero y lo más importante.** Es una función
pura: se prueba sin React, sin render y sin mocks, y cubre la lógica que más
duele si se rompe.

```ts
it('ignora una respuesta de sincronización obsoleta', () => {
  let s = reducer(base, { type: 'SET_QTY', itemId: 1, quantity: 2, seq: 5 });
  s = reducer(s,       { type: 'SET_QTY', itemId: 1, quantity: 3, seq: 6 });
  s = reducer(s,       { type: 'SYNC_SUCCESS', seq: 6, cart: cartWith(3) });
  s = reducer(s,       { type: 'SYNC_SUCCESS', seq: 5, cart: cartWith(2) });

  expect(s.cart!.items[0].quantity).toBe(3);   // la tardía no retrocede el carrito
});
```

| Caso | Por qué |
| --- | --- |
| `ADD_ITEM` de un producto ya presente **suma**, no duplica la línea | Un producto es una sola línea por carrito (invariante I-8) |
| `SET_QTY` recalcula `lineTotal` y `subtotal` al instante | Es el «tiempo real» que pide el reto |
| `SYNC_SUCCESS` reemplaza por completo con el carrito del servidor | La reconciliación no fusiona |
| `SYNC_ERROR` revierte exactamente al estado anterior | Es el rollback, el caso que nadie prueba y siempre falla |
| Una respuesta con `seq` viejo se ignora | §9 |
| `APPLY_COUPON` no inventa un descuento local | Es la frontera con las reglas de negocio |

Con React Testing Library: el formulario de checkout (validación por campo), el
botón de añadir (estado de envío y toast) y que el badge **no** aparezca en el
primer render. Del lado del servidor, comprobar que `generateMetadata` produce
canónica y Open Graph correctos para un producto conocido.

---

## 21. Definición de terminado

* [ ] La ruta usa la estrategia de render de §5 y no lee `searchParams` una
      página que debía ser ISR
* [ ] `'use client'` está en las hojas, no en los layouts
* [ ] Los cuatro estados manejados, con `loading.tsx`, `error.tsx` y
      `notFound()` donde corresponde
* [ ] Tipado contra el contrato, sin `any` sin justificar
* [ ] Filtros y búsqueda en la URL; la página vuelve a 1 al cambiar un filtro
* [ ] `next/image` con el loader del CDN, `sizes` correcto y `priority` en una
      sola imagen por página
* [ ] `alt` real en toda imagen informativa; `alt=""` en las decorativas
* [ ] El carrito: optimista, reconciliado con la respuesta del servidor, con
      reversión probada y sin recalcular descuentos en el cliente
* [ ] Los errores se ramifican por `code` y usan los campos extra del `422`
      (`disponible`, `subtotalMinimo`…) para escribir el mensaje; nunca se
      muestra un `detail` crudo
* [ ] Ningún componente que dependa de `localStorage` o de la sesión se
      renderiza con datos en el servidor
* [ ] El token de acceso está en memoria; la rehidratación es un solo refresco
      y un `401` anónimo no es un error
* [ ] La interfaz no revela si un correo existe
* [ ] Metadatos y JSON-LD por producto, sin `aggregateRating` inventado
* [ ] Navegable por teclado, con foco visible y diálogos que devuelven el foco
* [ ] Ningún secreto en una variable `NEXT_PUBLIC_*`

---

## 22. Lo que el contrato todavía no cubre

Lo que esta tienda necesita y **no está** en
[`contracts/openapi.yaml`](../../contracts/openapi.yaml), o que está de forma
distinta a como lo describen los documentos de negocio. Se lista aquí para que
la divergencia sea visible y no se resuelva improvisando en el frontend:

| Falta o divergencia | Nota |
| --- | --- |
| Categorías de **dos niveles** | El contrato tiene `Category` y `CategoryRef` planos; [ADR-0006](../adr/0006-categorias-dos-niveles.md) exige categoría → subcategoría, y de ahí salen las URL de §5 y las migas del JSON-LD |
| Imágenes con **tres variantes** y `alt` | El contrato devuelve un único `imageUrl`; el loader de §12 y el `alt` de §16 dependen de las tres URL y del texto alternativo |
| `sku` y `marca` en el producto | Necesarios para el JSON-LD (`brand`, `sku`) |
| Endpoints de cuenta (`/auth/registro`, `/auth/acceso`, `/auth/google`, `/auth/recuperar`, `/auth/restablecer`, refresco, salir) | Descritos en [autenticación](../backend/autenticacion.md), ausentes del contrato |
| `POST /orders` y `GET /orders/{numero}` | En el [plan](../PLAN.md) §7.1, no en el contrato. Sin ellos no hay checkout ni confirmación |
| Clave de idempotencia en `POST /orders` | §15: sin ella, un reintento puede duplicar un pedido |
| **Forma del `Problem`** | El contrato no incluye `code` ni `correlationId`, y modela `errors` como un mapa; [el catálogo de errores](../contrato/catalogo-errores.md) define `code`, `correlationId` y `errors[]` como array de `{ field, message }`, más los campos extra de cada `422` que §8 usa para escribir mensajes útiles |
| **`quantity: 0` en el `PATCH`** | RN-034 dice que elimina la línea; el contrato declara `minimum: 1`. La tienda usa `DELETE` para no depender de cómo se resuelva (§9) |
| **`pageSize` máximo** | RN-024 dice máximo 60 por defecto 20; el contrato dice máximo 48 por defecto 12 |
| **Relacionados** | El contrato dice «misma categoría»; RN-025 dice misma **subcategoría**, completando con la categoría si no llegan a cuatro |
| Un prefijo base de imagen por archivo | El loader de §12 sustituye el segmento final de la URL. Si la API devolviera `baseUrl` + variantes, dejaría de ser una manipulación de cadena |
| Idioma de rutas y campos | El contrato usa inglés (`/products`, `compareAtPrice`); los documentos de backend y de errores, español (`/auth/registro`, `precioAnterior`). Conviene unificar antes de generar los tipos |
