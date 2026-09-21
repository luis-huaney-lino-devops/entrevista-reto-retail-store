# Estándar del panel de administración

React 18 + Vite + TypeScript, renderizado en cliente, detrás de un login fijo.

Este documento cubre **una** de las dos aplicaciones de frontend. La tienda
pública tiene reglas distintas y su propio documento:
[estandar-storefront.md](estandar-storefront.md). Lo que comparten —capas, los
cuatro estados, errores por código, nada de `any`, token en memoria— está
repetido a propósito en ambos: son documentos que se leen por separado.

Antes de escribir una pantalla hay que haber leído
[el modelo de dominio](../negocio/modelo-dominio.md) (qué es una subcategoría y
por qué los productos cuelgan de ella),
[las reglas de negocio](../negocio/reglas-negocio.md) (las RN que cada
formulario adelanta),
[el catálogo de errores](../contrato/catalogo-errores.md) (cada `code` que hay
que traducir a un mensaje),
[autenticación](../backend/autenticacion.md) (cómo entra el administrador y
dónde vive cada token) y
[archivos e imágenes](../backend/archivos-imagenes.md) (por qué una subida
responde `202` y no `201`).

---

## 1. Qué es este panel y qué no es

| Es | No es |
| --- | --- |
| La herramienta interna de quien gestiona el catálogo | Una aplicación pública |
| Tablas, formularios y subida de archivos | Un sitio que deba indexarse o compartirse |
| Un consumidor más de la API REST | Un backend: no tiene lógica de negocio propia |
| Una mejora del reto (Fase 6 del [plan](../PLAN.md)) | Un requisito mínimo: es lo primero que se recorta |

El panel no decide nada. Cada regla —que `precioAnterior` sea mayor que
`precio`, que un SKU sea único, que no se pueda desactivar una categoría con
productos— se aplica en el servidor. Lo que el panel hace con esas reglas es
**adelantar el error al usuario** para que no descubra a los treinta segundos
que el formulario estaba mal. Si alguna regla vive *solo* aquí, es un defecto.

---

## 2. Por qué CSR y no SSR

La tienda usa Next.js con renderizado en servidor. El panel, no. No es
incoherencia: son problemas distintos.

| Razón | Detalle |
| --- | --- |
| **Va detrás de login** | Ningún contenido del panel es público. No hay nada que indexar, nada que compartir por enlace a un desconocido, ningún metadato Open Graph que generar. El principal argumento a favor de SSR vale aquí exactamente cero. |
| **Es interacción, no lectura** | Una sesión de trabajo típica es: abrir la tabla de productos, filtrar, abrir un formulario, subir cuatro imágenes, guardar, volver. El coste está en las transiciones *dentro* de la aplicación, no en el primer pintado. El SSR optimiza justo lo contrario. |
| **Se despliega como estáticos** | `vite build` produce HTML, JS y CSS. Caddy los sirve con `file_server`. **Cero procesos Node en el VPS**, que con 2 GB de RAM y cuatro contenedores no es un detalle cosmético (ver [plan](../PLAN.md), §10 y §12). |
| **Menos piezas que explicar** | Sin frontera servidor/cliente, sin caché de datos, sin revalidación. Un panel con SSR obligaría a resolver la sesión en el servidor de Next, que es exactamente lo que [autenticación](../backend/autenticacion.md) evita poniendo el token de acceso en memoria del navegador. |

### Lo que se pierde, dicho en voz alta

| Coste | Por qué se acepta |
| --- | --- |
| Pantalla vacía durante el arranque | El arranque incluye una llamada de refresco de sesión (§7). Se cubre con un esqueleto del layout, no con un spinner a pantalla completa |
| No funciona sin JavaScript | Un panel de gestión sin JavaScript no es un objetivo realista |
| El paquete inicial pesa (~180 KB comprimidos entre React, Router, Query, RHF y Zod) | Usuarios recurrentes, caché caliente, red de oficina. El mismo peso en la tienda sí sería inaceptable |
| El primer dato tarda dos saltos: cargar JS → pedir datos | Se mitiga con carga diferida por ruta y `keepPreviousData` al paginar |

### Consecuencia de despliegue que no se puede olvidar

Una SPA con enrutado en cliente necesita que el servidor web devuelva
`index.html` para **cualquier** ruta desconocida. Sin eso, recargar en
`/productos/42/editar` da un `404` del servidor y parece que la aplicación está
rota.

```text
  Caddy (admin.tudominio.com)
    ├─ /assets/*        → archivos con hash · Cache-Control: immutable, 1 año
    └─ cualquier otra   → index.html        · Cache-Control: no-cache
```

`index.html` **nunca** se cachea: es el archivo que apunta a los `assets` con
hash nuevo. Cachearlo es desplegar una versión que nadie recibe.

---

## 3. Stack

| Pieza | Elección | Por qué esta y no otra |
| --- | --- | --- |
| Biblioteca | React 18 | Requisito del reto |
| Lenguaje | TypeScript, `strict: true` | §5 |
| Construcción | Vite | Arranque en frío inmediato, salida estática |
| Enrutado | React Router | Rutas anidadas y `useSearchParams`, que es donde viven los filtros (§10) |
| Estado del servidor | TanStack Query | §8 |
| HTTP | Axios, una única instancia | §6 |
| Formularios | React Hook Form + Zod | §11 |
| Estilos | Tailwind CSS | Los mismos tokens de diseño que la tienda |
| Pruebas | Vitest + React Testing Library | §18 |

**TanStack Query es la única dependencia de estado y se gana el sitio.** Caché,
deduplicación, reintento, invalidación tras mutar y los estados de carga y error
se reimplementan a mano en cada `useEffect` —mal, y distinto cada vez— o se
resuelven una sola vez. Además es lo que hace que la regla de «carga / vacío /
error / éxito» (§9) sea lo bastante barata como para cumplirse de verdad y no
solo en la pantalla que alguien revisó.

**Idioma del código.** Las URL y todo el texto visible van en español; los
identificadores de código, en inglés (`ProductForm`, `useProducts`,
`productService`). Los campos que llegan de la API se usan **tal como los
nombra la API**, sin traducirlos en la frontera: un diccionario de nombres es
una capa más que mantener y romper.

Con una advertencia que hoy es real: el
[contrato](../../contracts/openapi.yaml) usa `camelCase` inglés
(`compareAtPrice`) mientras que
[el catálogo de errores](../contrato/catalogo-errores.md) nombra los campos en
español (`precioAnterior`, `subcategoriaId`). De eso depende que el mapeo de
`errors[].field` a los campos del formulario funcione (§6), así que hay que
unificarlo antes de generar los tipos. Ver §20.

---

## 4. Estructura

```text
apps/admin/src/
├── app/                  providers (QueryClient, Router, AuthProvider), router
├── components/ui/        Button, Input, Select, Checkbox, Modal, DataTable,
│                         Pagination, Skeleton, EmptyState, ErrorState,
│                         ConfirmDialog, Dropzone, Toast
├── features/
│   ├── auth/             LoginPage, AuthProvider, useSession, authService
│   ├── products/         components/ pages/ hooks/ services/ schemas/
│   ├── brands/
│   ├── categories/       categorías y subcategorías: un árbol, una pantalla
│   ├── coupons/
│   ├── orders/
│   ├── files/            subida, sondeo de PROCESANDO, previsualización
│   └── dashboard/
├── layouts/              AdminLayout (barra lateral, cabecera, migas)
├── lib/                  httpClient, tokenStore, problem, format, useDebounce
└── main.tsx
```

Primero la funcionalidad, no el tipo de archivo. Todo lo necesario para cambiar
las pantallas de productos vive bajo `features/products/`: sus componentes, sus
hooks, su servicio y sus esquemas. Solo lo que es transversal de verdad —un
`Button` que usan seis funcionalidades— sube a `components/ui/`.

`features/categories/` cubre los **dos niveles**. No son dos funcionalidades:
una subcategoría no existe sin su categoría, se editan en el mismo árbol y
comparten servicio. Separarlas obligaría a que cada una importara a la otra.

---

## 5. Capas

```text
  componente  →  hook  →  servicio  →  cliente HTTP  →  red
   (pinta)       (caché)   (URL y forma)  (transporte)
```

Un componente **nunca** llama a `axios` ni construye una URL. Llama a un hook;
el hook llama a un servicio; el servicio es el único dueño de la forma del
endpoint. Cuando el contrato pase a `/api/v2`, cambia un directorio y no
cuarenta componentes.

```ts
// features/products/services/productService.ts
export const productService = {
  list: (query: ProductQuery) =>
    http.get<Page<ProductRow>>('/admin/products', { params: query }).then(r => r.data),

  create: (body: ProductInput) =>
    http.post<Product>('/admin/products', body).then(r => r.data),
};
```

```ts
// features/products/hooks/useProducts.ts
export function useProducts(query: ProductQuery) {
  return useQuery({
    queryKey: ['products', 'list', query],
    queryFn: () => productService.list(query),
    placeholderData: keepPreviousData,   // sin parpadeo a vacío al paginar
  });
}
```

`keepPreviousData` es lo que hace que la paginación se sienta instantánea: la
página anterior sigue en pantalla, atenuada, mientras llega la siguiente, en vez
de que la tabla colapse a un esqueleto en cada clic.

**Sin `any`.** Los tipos de respuesta se generan desde
[`contracts/openapi.yaml`](../../contracts/openapi.yaml) con
`openapi-typescript` hacia `packages/api-types`, y ambas aplicaciones los
importan. Un tipo escrito a mano es una copia del contrato que se desactualiza
en silencio. Si hiciera falta `any` para desbloquear algo, va con un comentario
que diga por qué y qué lo sustituirá.

---

## 6. El cliente HTTP y los errores

Una sola instancia de Axios. Un solo sitio para URL base, tiempo de espera,
credenciales, cabeceras y forma del error.

```ts
export const http = axios.create({
  baseURL: import.meta.env.VITE_API_URL,   // p. ej. http://localhost:8080/api/v1
  timeout: 15_000,
  withCredentials: true,                   // imprescindible: la cookie de refresco
});
```

`withCredentials: true` no es opcional. El refresco vive en una cookie
`httpOnly` emitida por `api.tudominio.com` y el panel corre en
`admin.tudominio.com`: sin esa opción —y sin `Access-Control-Allow-Credentials`
con origen explícito en el servidor— la cookie no viaja y el refresco silencioso
(§7) no funciona nunca.

> Detalle de despliegue que conviene tener presente: `admin.tudominio.com` y
> `api.tudominio.com` son **el mismo sitio** a efectos de `SameSite`, así que la
> cookie `Lax` que define [autenticación](../backend/autenticacion.md) se envía
> sin problema (y en local, `localhost:5173` → `localhost:8080`, también). Si el
> panel viviera algún día en otro dominio registrable, `Lax` dejaría de
> enviarse en las peticiones `fetch` y habría que pasar a `SameSite=None;
> Secure`. Es una restricción de arquitectura, no un detalle de estilo.

Los interceptores manejan **solo asuntos de transporte**: adjuntar el token,
conservar el `X-Correlation-Id` de la respuesta (lo genera el servidor, RN-081),
disparar el refresco ante un `401` y normalizar la forma del error. Una decisión
de negocio escondida en un interceptor —«una categoría con productos no se
desactiva»— es lógica invisible para la funcionalidad a la que pertenece, y
nadie la encuentra hasta que falla.

### El error del backend

La API responde `application/problem+json` (RFC 9457) con `type`, `title`,
`status`, `detail`, **`code`** y `correlationId`, más `errors[]` en los fallos
de validación. **El contrato es
[el catálogo de errores](../contrato/catalogo-errores.md)**, y el campo por el
que se ramifica es `code`, nunca `title` ni `detail`: esos se reescriben o se
traducen en cualquier momento, y el `if` dejaría de entrar sin que falle ningún
test.

```ts
const messages: Record<string, string> = {
  DUPLICATE_SKU:          'Ya existe un producto con ese SKU.',
  DUPLICATE_SLUG:         'Ese slug ya está en uso. Edítalo.',
  DUPLICATE_NAME:         'Ya existe un registro con ese nombre.',
  SKU_IMMUTABLE:          'El SKU no se puede cambiar después de crear el producto.',
  INVALID_COMPARE_PRICE:  'El precio anterior debe ser mayor que el precio.',
  PRODUCT_REQUIRES_IMAGE: 'Un producto necesita al menos una imagen para activarse.',
  SUBCATEGORY_INACTIVE:   'Esa subcategoría está inactiva. Actívala o elige otra.',
  FILE_NOT_READY:         'Una imagen aún se está procesando. Espera unos segundos.',
  INVALID_CREDENTIALS:    'Usuario o contraseña incorrectos.',
  WRONG_AUDIENCE:         'Esa sesión no es válida para el panel. Vuelve a entrar.',
  TOO_MANY_REQUESTS:      'Demasiados intentos. Inténtalo de nuevo más tarde.',
};

export const messageFor = (e: ApiError) =>
  messages[e.code] ?? 'Algo salió mal. Inténtalo de nuevo.';
```

Cinco reglas sobre esto:

* **Nunca se muestra el `detail` crudo del backend.** Está escrito para quien
  depura, no para quien gestiona el catálogo.
* **`VALIDATION_ERROR` es un caso aparte**: sus `errors[]` son pares
  `{ field, message }` y se mapean a los campos del formulario por `field`, con
  `setError` de React Hook Form, de modo que todos los campos incorrectos quedan
  marcados en una sola ida y vuelta. Un toast genérico obliga al usuario a
  adivinar cuál falló.
* **Los `422` traen los datos para construir un mensaje útil**, y hay que
  usarlos en lugar de escribir una frase vaga. `INVALID_COMPARE_PRICE` incluye
  `precio` y `precioAnterior`; `IMAGE_TOO_LARGE`, `valorEnviado`,
  `maximoPermitido` y `dimension`; `INVALID_ORDER_TRANSITION`, las
  `transicionesPermitidas`. Un mensaje que repite la cifra que el usuario acaba
  de escribir es el que se entiende sin pensar.
* **`HAS_DEPENDENTS` trae la lista de lo que bloquea** (hasta 50, con
  `totalBloqueos` si hay más): se muestra la lista, no un «no se puede».
* Un código desconocido cae al mensaje genérico **y muestra el
  `correlationId`** («Cita el código 0f9c2b1e… si lo reportas»). Es lo que
  convierte un incidente en algo rastreable hasta la línea de log.

---

## 7. La sesión

El administrador entra con **usuario y contraseña fijos**. No hay registro, no
hay Google, no hay recuperación por correo: es una decisión deliberada, expuesta
en [autenticación](../backend/autenticacion.md) §1, no un recorte de alcance. Si
alguien pierde la contraseña, se restablece por configuración.

**El panel no muestra en ninguna parte un enlace de «¿olvidaste tu
contraseña?»**. Un enlace que lleva a una pantalla que no existe es peor que no
tenerlo.

### Dónde vive cada token

```text
  Acceso    aud=admin   15 min   en memoria (módulo)   se pierde al recargar: correcto
  Refresco              12 h     cookie httpOnly       invisible a JS, rotativo
```

El acceso vive en una variable de módulo, **jamás en `localStorage`**. Todo lo
que esté en `localStorage` lo lee cualquier script que consiga un XSS, y un
token de administrador robado edita precios. El coste —que recargar lo pierda—
lo cubre el refresco, que es exactamente el motivo de que el backend lo emita.

### Arranque y rehidratación

```text
  carga de la aplicación
        │  estado de sesión: "desconocido"  → se pinta el esqueleto del layout
        ▼
  POST /auth/refrescar        (solo cookie; sin cabecera Authorization)
        ├─ 200 → token en memoria + datos del admin → "autenticado" → ruta pedida
        └─ 401 → "anónimo" → /acceso?next=/ruta/que/pedía
```

Dos detalles que se olvidan siempre:

1. **La pantalla de login no se pinta antes de conocer el resultado.** Si se
   pinta, todo administrador con sesión válida ve un destello del formulario en
   cada recarga y cree que la sesión se cayó.
2. **La llamada de arranque se excluye del interceptor de `401`.** Si no, un
   `401` esperado (no hay sesión) dispara un refresco que produce otro `401` y
   la aplicación entra en bucle.

### Refresco silencioso

```text
  petición ──▶ 401
        │
        ├─ ¿hay ya un refresco en curso?
        │     sí ─▶ se encola y espera a ese          ← "single flight"
        │     no ─▶ POST /auth/refrescar
        │
        ├─ 200 ─▶ guarda el acceso nuevo ─▶ reintenta la petición original UNA vez
        └─ 401 ─▶ limpia memoria, vacía la cola, navega a /acceso?next=…
```

**Un solo refresco en vuelo.** Una pantalla con cuatro consultas paralelas
produce cuatro `401` simultáneos. Sin cola se disparan cuatro refrescos y, como
el refresco **rota** (cada uso invalida el anterior), tres de ellos presentan un
token ya usado: el backend lo interpreta como reúso, revoca la familia entera y
expulsa al administrador. El problema no es de rendimiento, es de corrección.

**Un solo reintento, y solo tras un `401`.** Es seguro porque el `401` lo emite
el filtro antes de ejecutar nada: la petición no llegó a tener efecto. Un
*timeout* de red no da esa garantía, y por eso una mutación que expira **no** se
reintenta sola.

**Refresco proactivo.** Además del reactivo, un temporizador renueva a los 14
minutos (60 s antes de caducar) y se comprueba la caducidad al volver a la
pestaña (`visibilitychange`): un portátil suspendido no ejecuta temporizadores,
y al despertar la primera acción del usuario no debería ser un error.

### Rutas protegidas y expiración

Un componente `RequireAuth` envuelve el layout y redirige a `/acceso?next=…` si
el estado es anónimo. Es **experiencia de uso, no seguridad**: quien llame a la
API sin token recibe `401` igual. Ocultar una ruta no protege nada; lo que
protege es el filtro del servidor.

Cuando el refresco falla de verdad (12 horas sin actividad), la sesión se acabó:
se redirige a `/acceso` conservando `next` para volver donde estaba. El coste
real: **si había un formulario de producto a medio llenar, se pierde**. Se
acepta porque, con refresco proactivo, ese caso solo ocurre tras medio día
inactivo. Lo que no se acepta es la variante silenciosa —pulsar «Guardar», ver
un error rojo genérico y no saber que la sesión murió—: el toast dice
explícitamente que la sesión expiró y que hay que volver a entrar.

---

## 8. Estado: qué vive dónde

| Tipo de estado | Dónde vive | Herramienta |
| --- | --- | --- |
| Datos del servidor (productos, marcas, órdenes) | Caché de consultas | TanStack Query — **nunca** copiados a `useState` |
| Filtros, búsqueda, página y orden de una tabla | **La URL** | `useSearchParams` |
| Valores de un formulario | El formulario | React Hook Form |
| Interfaz local (modal abierto, fila desplegada) | El componente | `useState` |
| Sesión | Un contexto pequeño | `AuthProvider` |
| Todo lo demás | En ningún sitio | Se deriva |

El error más común en un panel es copiar los datos recibidos a `useState` para
«poseerlos». Eso crea una segunda copia que se desactualiza, necesita
sincronización manual y produce el clásico «lo guardé pero la tabla sigue
mostrando el valor viejo». El estado del servidor tiene una sola casa: tras
mutar se invalida la clave y la lista se repinta sola.

**Por qué los filtros van en la URL y no en `useState`.** Porque así recargar no
pierde la vista, «abrir en pestaña nueva» funciona, el botón atrás hace lo que
debe y un compañero puede pegar la URL en un mensaje para señalar exactamente
qué está viendo. El coste es algo más de plomería al leer y escribir los
parámetros; se paga una vez en un hook y se reutiliza en las cinco tablas.

Claves de consulta, en una convención y sin excepciones:

```ts
['products', 'list', query]     ['products', 'detail', id]
['brands', 'list']              ['categories', 'tree']
['orders', 'list', query]       ['files', 'detail', id]
```

Así `invalidateQueries({ queryKey: ['products'] })` tras guardar refresca lista
y detalle de una vez, sin tener que acordarse de cada combinación de filtros que
hay en caché.

`staleTime` por tipo de dato, porque no todo envejece igual:

| Dato | `staleTime` | Motivo |
| --- | --- | --- |
| Marcas, árbol de categorías | 5 min | Cambian una vez por semana y los usa cada formulario |
| Listas y detalles | 0 | Al volver a la pestaña se quiere el dato de ahora |
| Panel de indicadores | 60 s | Son agregados; un minuto de desfase no engaña a nadie |
| Archivo en estado `PROCESANDO` | 0, con sondeo | §13 |

---

## 9. Carga, vacío, error, éxito

Toda pantalla asíncrona maneja **los cuatro**. Una pantalla que no muestra nada
mientras carga es un defecto, no un descuido.

```tsx
if (isPending) return <TableSkeleton rows={10} />;
if (isError)   return <ErrorState error={error} onRetry={refetch} />;
if (!data.items.length) return hasFilters
  ? <EmptyState title="Sin resultados para estos filtros" action={clearFilters} />
  : <EmptyState title="Todavía no hay productos" action={openCreate} />;
return <ProductTable rows={data.items} />;
```

Vacío y error son estados distintos con soluciones distintas: «no hay resultados
para este filtro» invita a limpiar el filtro; «no se pudo contactar al servidor»
invita a reintentar. Y el vacío tiene **dos** variantes que tampoco se pueden
juntar: una tabla filtrada sin resultados y una tabla que nunca tuvo datos. La
segunda es la primera vez que alguien abre el panel, y ahí el botón correcto es
«Crear el primer producto», no «Limpiar filtros».

El esqueleto imita la forma de lo que viene (filas de tabla, rejilla de
tarjetas). Un spinner centrado en una página vacía produce un salto de
maquetación cuando llegan los datos.

---

## 10. Las tablas

Cuatro pantallas son fundamentalmente una tabla: productos, cupones, órdenes y
marcas. Se resuelven con un único `DataTable` y las mismas reglas.

### Paginación en servidor, siempre

El cliente nunca tiene el catálogo completo. Se envían `page` y `pageSize` y se
pinta lo que llega, con `totalItems` y `totalPages` del sobre de paginación.
Traer 2000 productos para filtrar en memoria funciona con el seed de treinta y
se cae el día que el catálogo crece, que es el peor momento para descubrirlo.

`page` empieza en **1**, no en 0. El `pageSize` tiene un máximo y **superarlo se
rechaza, no se recorta** (RN-024): un selector de «filas por página» que ofrezca
un valor por encima del máximo produce un `400` en lugar de una tabla. Los
valores del selector se fijan explícitamente (20 / 40 / 60) y no se calculan.

### Ordenamiento por columna

La cabecera ordenable es un `<button>` dentro del `<th>`, y el `<th>` lleva
`aria-sort="ascending" | "descending" | "none"`. Sin `aria-sort`, un lector de
pantalla anuncia «Precio, botón» y no dice en qué orden está la tabla.

El orden viaja como **un solo parámetro** (`sort=price_asc`), no como dos
(`sortBy` + `sortDir`): un enum cerrado es lo que el servidor puede validar, y
lo que evita que un día llegue una cadena arbitraria a un `ORDER BY`.

El orden debe ser **total**: si se ordena por precio y hay empates, el servidor
desempata por `id`. Sin desempate, dos páginas consecutivas pueden repetir o
saltarse una fila y el usuario ve un producto dos veces sin entender por qué. Es
la misma razón por la que las categorías desempatan por nombre en el
[modelo de dominio](../negocio/modelo-dominio.md).

### Búsqueda con retardo

```ts
const [term, setTerm] = useState(searchParams.get('q') ?? '');
const debounced = useDebounce(term, 350);   // una petición por pausa, no por tecla
```

El campo se controla localmente para que escribir sea fluido, y el valor
*estabilizado* es el que se escribe en la URL y dispara la consulta. Escribir en
la URL en cada tecla llena el historial de basura y deja inservible el botón
atrás.

**Cambiar cualquier filtro o el término reinicia a la página 1.** Sin eso,
filtrar desde la página 5 cae en una página vacía y la aplicación parece rota
cuando en realidad está siendo literal.

Tras cargar los resultados, el recuento se anuncia con `role="status"` («24
productos»). Un usuario de lector de pantalla que escribe en el buscador no
tiene otra forma de enterarse de que la tabla cambió.

### Columnas de la tabla de productos

| Columna | Nota |
| --- | --- |
| Imagen | Variante `miniatura` (160 px), `alt` de la API, 40×40 con `object-fit: contain` |
| SKU | Monoespaciado: se compara visualmente contra etiquetas físicas |
| Nombre | Enlace a la edición |
| Subcategoría | Se muestra **con su categoría**: «Tecnología › Accesorios». «Accesorios» a secas es ambiguo por diseño: el nombre solo es único dentro de su categoría |
| Marca | Puede estar vacía: un producto puede no tener marca |
| Precio | Alineado a la derecha, formato PEN; si hay `precioAnterior`, debajo y tachado |
| Stock | Resaltado si es 0; resaltado distinto si está bajo el umbral de stock bajo |
| Activo | Interruptor, no texto: es la acción más frecuente de la pantalla |

Filtros: búsqueda (nombre y SKU), categoría → subcategoría en cascada, marca,
activo/inactivo, solo sin stock.

---

## 11. Formularios y validación

React Hook Form con resolver de Zod. Un esquema por entidad, en
`features/<x>/schemas/`.

Los esquemas reflejan las reglas **estructurales** del dominio: longitud,
formato, rango, obligatoriedad. No reimplementan reglas de negocio: el panel no
puede saber si un SKU está ocupado ni si un cupón agotó sus usos, y una regla
que vive en dos sitios se desincroniza el día que cambia en uno.

> La validación del panel es experiencia de uso. La del servidor es seguridad.
> El servidor lo revalida todo, siempre.

| Entidad | Reglas estructurales que sí se validan aquí | Regla / código |
| --- | --- | --- |
| Producto | `sku` 1–40 · `nombre` 1–160 · `descripcionCorta` ≤300 · `descripcion` ≤5000 · `precio` > 0 con dos decimales · `stock` entero ≥ 0 · `subcategoriaId` obligatorio | RN-001, RN-003, RN-005 |
| Producto (cruzada) | Si hay `precioAnterior`, debe ser **mayor** que `precio`, con `refine` | RN-004 → `INVALID_COMPARE_PRICE` |
| Marca | `nombre` 1–80 · `descripcion` ≤500 | RN-010 → `DUPLICATE_NAME` |
| Categoría / Subcategoría | `nombre` 1–80 · `orden` entero · la subcategoría exige `categoriaId` | RN-010, RN-014 |
| Cupón | `codigo` en mayúsculas ≤40 · `valor` 1–100 si el tipo es `PORCENTAJE`, > 0 si es `MONTO_FIJO` · `terminaEn` posterior a `iniciaEn` | RN-040 |

Las dos reglas cruzadas —`precioAnterior > precio` y `terminaEn > iniciaEn`— son
las que más se equivocan al capturar y las que peor se explican en un error del
servidor. Validarlas en el formulario, señalando el campo concreto, ahorra el
viaje completo.

Lo que el formulario **no** valida, porque no puede: unicidad de `sku`, `slug` y
`nombre` (`409`), que la subcategoría siga activa (`422 SUBCATEGORY_INACTIVE`) y
que una imagen esté procesada (`422 FILE_NOT_READY`). Esos llegan del servidor y
se tratan como resultados normales, no como fallos inesperados.

Detalles no negociables: cada campo con `<label>` real asociada por `id`; el
error enlazado con `aria-describedby` y anunciado con `role="alert"`; el botón de
guardar deshabilitado mientras la mutación está en vuelo **y** mostrando que
está enviando, porque un botón deshabilitado sin explicación se lee como un
fallo.

---

## 12. El formulario de producto

Es la pantalla más compleja del panel y donde se concentran las decisiones.

```text
┌─ Datos ───────────────────────────────────────────────┐
│ SKU*         [ABC-001]   (bloqueado al editar)        │
│ Nombre*      [                                     ]  │
│ Slug         [auto]      (editable; entonces es tuyo) │
│ Descripción corta  [                                ] │
│ Descripción        [                                ] │
├─ Clasificación ───────────────────────────────────────┤
│ Categoría*     [Tecnología        ▾]                  │
│ Subcategoría*  [Accesorios        ▾]  ← depende de ↑  │
│ Marca          [— sin marca —     ▾]                  │
├─ Precio y stock ──────────────────────────────────────┤
│ Precio*         [ S/ 129.90 ]                         │
│ Precio anterior [ S/ 159.90 ]  → «19 % de descuento»  │
│ Stock*          [ 25 ]                                │
├─ Imágenes ────────────────────────────────────────────┤
│ [ ▣ principal ] [ ▣ ] [ ⟳ procesando ] [ + soltar ]   │
│    alt*: …        alt*: …                             │
├─ Estado ──────────────────────────────────────────────┤
│ ☑ Activo     ☐ Destacado                              │
└───────────────────────────────────────────────────────┘
```

### La cascada categoría → subcategoría

Es la parte que más se hace mal. Cuatro reglas:

1. **El selector de subcategoría empieza deshabilitado**, con el texto «Elige
   primero una categoría». Un desplegable vacío y habilitado hace que el usuario
   lo abra, no vea nada y concluya que no hay datos.
2. **Cambiar la categoría limpia la subcategoría** (`resetField`). Obligatorio:
   si no, se envía un producto cuya subcategoría pertenece a otra categoría, y
   el servidor lo rechaza con un error que el usuario no entiende, porque el
   formulario le estaba enseñando dos valores aparentemente coherentes.
3. **El árbol completo se pide una vez** (`['categories','tree']`, `staleTime` 5
   min) y las subcategorías se filtran en memoria. Pedirlas en cada cambio de
   categoría añade un parpadeo y una petición por cada duda del usuario.
4. **Al editar, ambos selectores llegan rellenos.** El producto guarda
   `subcategoriaId`; la categoría se deduce localizando esa subcategoría en el
   árbol. Que el formulario tenga que deducirla es aceptable; lo que no lo es es
   pedirle al usuario que vuelva a elegir la categoría cada vez que abre una
   edición.

Solo se ofrecen categorías y subcategorías **activas**, con una excepción: la
que el producto ya tiene. Si su subcategoría fue desactivada, se muestra marcada
como inactiva en lugar de desaparecer del desplegable; desaparecer convierte un
guardado inocente en una reclasificación silenciosa.

### SKU y slug

`sku` es único e **inmutable tras la creación** (RN-002): en edición se muestra
deshabilitado y con un texto que explica por qué, no simplemente apagado. Si de
todos modos llegara a enviarse un SKU distinto, el servidor responde `422
SKU_IMMUTABLE` con `valorEnviado` y `valorActual`; eso es una red de seguridad,
no la interfaz esperada.

`slug` se genera del nombre y **no cambia cuando el nombre cambia**: renombrar
«Laptops» no debe romper los enlaces que ya circulan. En el formulario aparece
como campo de solo lectura con un botón «editar»; al editarlo, un aviso deja
claro que a partir de ahí es responsabilidad de quien lo tocó y que los enlaces
antiguos dejan de funcionar.

### Precio y precio anterior

Ambos decimales de dos posiciones. Se formatean para mostrar desde un único
helper (`lib/format.ts`) con `Intl.NumberFormat('es-PE', { style: 'currency',
currency: 'PEN' })`; nunca se construye la cadena a mano con concatenaciones.

Debajo de `precioAnterior` se muestra en vivo el porcentaje de descuento
calculado. Es la forma más rápida de que el usuario detecte que se equivocó de
campo: un «−1900 %» salta a la vista, un número mal tecleado no.

### Imágenes múltiples y ordenables

La primera imagen es la principal: la que va en la tarjeta del listado y en la
vista previa de Open Graph de la tienda. El orden se arrastra **y también se
cambia con dos botones «subir/bajar» por imagen**. Arrastrar y soltar no es
alcanzable por teclado, y un panel donde reordenar exige ratón excluye a quien
no lo usa. Los botones no son un modo degradado: son el modo accesible, y ambos
caminos escriben el mismo estado.

El orden se envía como un array de identificadores de archivo dentro del
guardado del producto, no con un endpoint de reordenar: el orden es un atributo
del producto, no una entidad con vida propia.

### Activar exige al menos una imagen

Un producto se **crea** sin imágenes —es un borrador— pero no se **activa** sin
ellas (RN-009 → `422 PRODUCT_REQUIRES_IMAGE`). Una tarjeta sin imagen rompe la
rejilla de la tienda y no se compra.

La interfaz lo adelanta en vez de esperar el rechazo: mientras no haya ninguna
imagen `LISTO`, la casilla «Activo» está deshabilitada y explica por qué
(«Añade una imagen para poder activarlo»). El borrador se guarda igual, que es
justamente el flujo que la regla permite: capturar los datos hoy y publicar
cuando lleguen las fotos.

### Guardado

Una sola mutación. Tras el éxito: invalidar `['products']`, toast de
confirmación y volver a la lista **conservando los filtros** que había (viven en
la URL, así que basta con volver a la URL anterior). Devolver al usuario a una
lista sin filtrar tras editar el producto número 40 es hacerle repetir el
trabajo de encontrarlo.

Si el servidor responde `VALIDATION_ERROR`, los errores se pintan por campo y la
página **hace scroll al primero**. Un error a 800 píxeles de distancia es un
error invisible.

---

## 13. La subida de imágenes

Es el flujo más particular del panel, porque la API **no devuelve la imagen
lista**. Según [archivos e imágenes](../backend/archivos-imagenes.md) §4,
derivar las variantes cuesta entre uno y tres segundos, así que la subida se
encola:

```text
  POST /admin/archivos   (multipart)
      └─ 202 Accepted  { id, estado: "PROCESANDO" }
                │
                ▼  (en el servidor, en segundo plano)
         deriva · convierte a WebP · sube a R2 · marca LISTO
```

### Lo que ve el usuario

```text
  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
  │    ▣     │   │    ▣     │   │    ⟳     │   │    +     │
  │ principal│   │          │   │procesando│   │  soltar  │
  └──────────┘   └──────────┘   └──────────┘   └──────────┘
     LISTO          LISTO        PROCESANDO    zona de subida
```

1. **Previsualización inmediata, desde el archivo local.** En cuanto se suelta,
   se pinta la miniatura con `URL.createObjectURL(file)`, antes incluso de que
   termine la subida. Es lo que hace que la interfaz se sienta instantánea
   aunque el servidor tarde tres segundos. El objeto se libera con
   `URL.revokeObjectURL` al desmontar o al sustituirlo por la URL del CDN; no
   hacerlo es una fuga de memoria que se nota tras veinte subidas.
2. **Estado `PROCESANDO` explícito.** La tarjeta muestra la previsualización
   local atenuada, un indicador de progreso indeterminado y el texto
   «Procesando…». No se muestra una tarjeta vacía ni un hueco: el usuario acaba
   de soltar un archivo y necesita ver que algo ocurrió.
3. **Sondeo con TanStack Query**, no con un `setInterval` suelto:

   ```ts
   useQuery({
     queryKey: ['files', 'detail', id],
     queryFn: () => fileService.get(id),
     refetchInterval: q => (q.state.data?.status === 'PROCESSING' ? 1500 : false),
   });
   ```

   El sondeo **se detiene solo** al llegar a `LISTO`. Y se abandona tras unos
   60 s con un estado de error accionable —«No pudimos procesar esta imagen.
   Reintentar / Quitar»—: sondear indefinidamente contra un trabajo que falló
   deja una pestaña pidiendo cada 1,5 s para siempre.
4. **Al llegar `LISTO`**, la previsualización local se sustituye por la variante
   `miniatura` del CDN. Ese cambio es la comprobación de que la imagen real
   existe: si la tarjeta se quedara con la copia local, un fallo de subida
   pasaría inadvertido hasta que alguien abriera la tienda.

### Guardar con imágenes todavía en proceso

Aquí hay una contradicción entre dos documentos de backend que el panel tiene
que sobrevivir hasta que se resuelva (§20):
[archivos e imágenes](../backend/archivos-imagenes.md) §4 dice que «un producto
puede guardarse con imágenes aún en proceso», mientras que
[el catálogo de errores](../contrato/catalogo-errores.md) define `422
FILE_NOT_READY` para «se referencia un archivo que aún se está procesando».

El panel asume lo primero —bloquear el guardado convierte tres segundos de
espera en una interrupción del trabajo, y la tienda no muestra imágenes que no
estén `LISTO`— **y trata `FILE_NOT_READY` como un resultado previsto**:

```text
  guardar
    ├─ 200 ─▶ toast: «Producto guardado. Una imagen sigue procesándose
    │          y aparecerá en la tienda en unos segundos.»
    └─ 422 FILE_NOT_READY
            ▶ el formulario NO se cierra ni se limpia
            ▶ mensaje: «Espera a que terminen de procesarse las imágenes»
            ▶ el botón se rehabilita solo cuando todas están LISTO
```

Lo que nunca se hace es perder lo capturado. Un formulario de producto lleno es
varios minutos de trabajo, y descartarlo por un archivo que estará listo en dos
segundos es el peor intercambio posible.

### El `alt` es obligatorio

No es un campo opcional escondido tras un desplegable: es un campo de texto
visible bajo cada miniatura, marcado como obligatorio, y **el formulario no se
envía sin él**. El motivo está en
[archivos e imágenes](../backend/archivos-imagenes.md) §3: una tienda sin textos
alternativos es inaccesible para quien usa lector de pantalla y pierde
posicionamiento en imágenes. Es un campo de negocio, no un adorno técnico.

Con una ayuda concreta bajo el campo: «Describe lo que se ve: "Mochila urbana
negra, vista frontal". No repitas el nombre del producto». Sin esa pista, casi
todos los `alt` acaban siendo el nombre del producto copiado, que para un lector
de pantalla no añade absolutamente nada.

### Arrastrar y soltar

La zona de subida es un `<input type="file" multiple>` **real** con su
`<label>`, sobre el que se añaden los manejadores de arrastre. En ese orden: el
arrastre es la mejora, el input es la base. Una zona construida con un `<div>` y
`onDrop` no se puede usar con teclado y ningún lector de pantalla la anuncia.

Se marca visualmente el estado «soltando aquí» y se cancela `dragover` con
`preventDefault`: sin eso, el navegador abre el archivo en la pestaña y **se
pierde el formulario entero**, que es el peor fallo posible de esta pantalla.

### Validación en el cliente, sin pretender que sustituye a la del servidor

Antes de subir se comprueban tamaño (10 MB) y el tipo que declara el navegador,
y se rechaza en el acto con un mensaje claro. Eso ahorra subir 10 MB por red
móvil para nada.

Lo que el cliente **no** puede comprobar es justo lo que importa: el tipo real
se determina por los bytes mágicos del archivo (RN-070), y las bombas de
descompresión se detectan leyendo la cabecera antes de descomprimir (RN-071).
Eso vive en el servidor y es seguridad. El panel filtra lo evidente y trata
`422 UNSUPPORTED_IMAGE_TYPE`, `IMAGE_TOO_LARGE` e `IMAGE_TOO_SMALL` como
resultados normales. `IMAGE_TOO_LARGE` trae `valorEnviado`, `maximoPermitido` y
`dimension`, así que el mensaje puede ser exacto —«La imagen mide 9000 px de
ancho; el máximo es 8000»— en lugar de «archivo no válido».

Subidas en paralelo: **tres como mucho**. El pool de procesamiento del servidor
está acotado a 2–4 hilos; lanzar diez subidas simultáneas no las acelera, solo
llena la cola y alarga el tiempo de todas.

---

## 14. Las pantallas

| Ruta | Pantalla | Notas |
| --- | --- | --- |
| `/acceso` | Login | Usuario + contraseña. Sin Google, sin recuperación. `autocomplete="username"` y `"current-password"` |
| `/` | Panel de indicadores | §14.5 |
| `/productos` | Tabla de productos | §10 |
| `/productos/nuevo`, `/productos/:id` | Formulario de producto | §12 |
| `/marcas` | Tabla + modal de alta/edición | Entidad de cuatro campos: un modal basta y no se pierde el contexto de la lista |
| `/categorias` | Árbol de dos niveles | §14.1 |
| `/cupones` | Tabla + formulario | §14.2 |
| `/ordenes`, `/ordenes/:numero` | Listado y detalle | §14.3 |

### 14.1 Categorías y subcategorías

Una sola pantalla con un árbol de dos niveles, no dos pantallas con un
desplegable de «categoría padre»:

```text
  ▾ Tecnología                    orden 1   ☑ activa   [editar] [+ subcategoría]
      · Laptops        laptops              ☑
      · Accesorios     accesorios-tecnologia ☑
  ▾ Deportes                      orden 2   ☑ activa   [editar] [+ subcategoría]
      · Accesorios     accesorios-deportes  ☑
```

Ese árbol enseña de un vistazo lo que el modelo exige y una tabla plana esconde:
que «Accesorios» existe dos veces y es legítimo, porque el nombre solo es único
**dentro** de su categoría. El slug, en cambio, es único globalmente, y se
muestra en cada fila porque es lo que acaba en la URL pública de la tienda.

El `orden` se edita arrastrando filas, con la misma alternativa de teclado que
las imágenes. Existe porque el criterio del negocio no es alfabético: «Ofertas»
va primero aunque empiece por O.

Una categoría **no contiene productos**: solo subcategorías
([ADR-0006](../adr/0006-categorias-dos-niveles.md)). El formulario de categoría
no tiene ningún campo relacionado con productos, y el de subcategoría muestra
cuántos productos cuelgan de ella.

Desactivar algo que tiene contenido lo rechaza el servidor con `409
HAS_DEPENDENTS` (RN-011), y **no hay cascada automática**: desactivar una
categoría no desactiva sus subcategorías ni sus productos, porque un clic que
despublica doscientos productos es un incidente que nadie ve hasta que caen las
ventas (RN-012).

El panel **no oculta el botón de desactivar**: lo deja, y cuando el servidor lo
rechaza muestra **la lista concreta de lo que bloquea** —`HAS_DEPENDENTS` la
trae, hasta 50 elementos con `totalBloqueos` si hay más— con enlaces a cada uno.
«No se puede desactivar» a secas obliga al usuario a buscar a ciegas qué se lo
impide; una lista de tres subcategorías con enlace es una tarea que puede
terminar.

### 14.2 Cupones

Tabla con código, tipo, valor, vigencia, usos (`usosActuales / usosMaximos`) y
estado. La columna de estado es **derivada, no el campo `activo`**: un cupón
marcado como activo cuya fecha ya pasó no está vigente, y mostrar «activo» ahí
es mentir. Cuatro estados visibles: vigente, programado, expirado, agotado.

El código se normaliza a mayúsculas mientras se escribe. No es cosmética: es
único en mayúsculas, y dejar que alguien escriba `verano10` para que el servidor
lo rechace por duplicado contra `VERANO10` es un error evitable en el
formulario.

### 14.3 Órdenes

Listado con número, fecha, cliente, total y estado; filtros por estado y rango de
fechas. El detalle muestra las líneas **tal como se guardaron** (nombre, SKU y
precio unitario copiados), no los datos actuales del producto. Si la orden dice
S/ 99.90 y el producto hoy cuesta S/ 129.90, la orden gana: es un hecho
ocurrido, no una vista del catálogo.

El cambio de estado sigue la máquina del dominio (`PENDIENTE → PAGADA → ENVIADA
→ ENTREGADA`, con `CANCELADA` solo antes de enviar; RN-054). **El desplegable
ofrece solo las transiciones válidas desde el estado actual**, no los cinco
estados. Ofrecer una transición imposible para que el servidor la rechace es
obligar al usuario a aprender las reglas a base de errores.

Si aun así llega un `422 INVALID_ORDER_TRANSITION` —dos administradores
trabajando a la vez sobre el mismo pedido—, el error trae `estadoActual` y
`transicionesPermitidas`: con eso el panel refresca el pedido y reconstruye el
desplegable, en lugar de mostrar un mensaje de error y dejar la pantalla
mintiendo.

Cancelar devuelve el stock: es una acción con consecuencias, así que va con
`ConfirmDialog` que dice exactamente qué va a pasar — nunca con `window.confirm`,
que no se puede estilizar ni hacer accesible.

### 14.5 Panel de indicadores

Ventas del periodo, número de órdenes, ticket promedio, top de productos y
productos con stock bajo. Cada tarjeta declara **su periodo** («últimos 30
días»): un número sin periodo es un número que cada persona interpreta a su
manera.

Es la única pantalla donde un dato de hace un minuto es aceptable (`staleTime`
60 s), y la única que podría necesitar un gráfico. Hasta que alguien lo pida,
cifras grandes y una tabla ordenada cumplen el objetivo sin añadir una
dependencia de 150 KB al paquete.

Si un indicador falla, **cae solo esa tarjeta**, no el panel entero: un
`ErrorState` pequeño dentro de la tarjeta y el resto sigue siendo útil.

---

## 15. Accesibilidad

No es una sección de buenas intenciones: son las siete cosas que este panel
concreto rompe si nadie las vigila.

* HTML semántico. Un `<button>` es un `<button>`; un `<div>` con `onClick` no es
  alcanzable por teclado ni se anuncia.
* Cada campo con `<label>` asociada por `id`; los errores con `aria-describedby`
  y `role="alert"`.
* Tablas con `<caption>` (puede estar oculto visualmente), `<th scope="col">` y
  `aria-sort` en la columna ordenada.
* Los modales atrapan el foco, se cierran con `Escape` y **devuelven el foco** al
  elemento que los abrió: tras cerrar el modal de una marca, el foco vuelve al
  botón «Editar» de su fila, no al principio de la página.
* Toda acción de arrastrar —orden de imágenes, orden de categorías— tiene su
  equivalente con botones.
* Los botones de solo icono llevan `aria-label`. Un lápiz y una papelera sin
  etiqueta son dos botones idénticos para un lector de pantalla.
* El indicador de foco visible nunca se elimina. Contraste AA en texto y también
  en los estados de error, que es donde suele colarse el gris claro sobre
  blanco.

---

## 16. Rendimiento

* **Carga diferida en los límites de ruta**, no por componente. Quien solo mira
  órdenes no descarga el formulario de producto con su gestor de imágenes.
* `keepPreviousData` en toda consulta paginada.
* `React.memo` / `useMemo` / `useCallback` **solo** después de que un perfilado
  muestre un coste real. Aplicados por omisión añaden ruido y errores en los
  arrays de dependencias sin comprar nada.
* Las listas se paginan en servidor; la virtualización no hace falta mientras el
  tamaño de página se mantenga en decenas de filas.
* Las miniaturas de la tabla usan la variante de **160 px**, no la de detalle.
  Una tabla de veinte filas con imágenes de 1200 px descarga varios megabytes
  para pintar cuadrados de 40 píxeles.

---

## 17. Entorno, construcción y despliegue

```text
VITE_API_URL=http://localhost:8080/api/v1
```

**Todo lo que esté en una variable `VITE_*` se envía al navegador en texto
plano.** Ningún secreto, ninguna clave, ninguna cadena de conexión: un
`VITE_JWT_SECRET` es un secreto publicado. El panel no necesita ninguno: su
único dato sensible es un token que emite el servidor y que vive en memoria.

Una sola variable, resuelta en un único archivo (`lib/httpClient.ts`). Nada más
del código sabe a qué host habla: apuntar el panel a otro entorno es editar el
`.env` y reiniciar Vite. El backend es único —Java / Spring Boot,
[ADR-0005](../adr/0005-backend-solo-java.md)— así que el panel no tiene ni una
línea condicional por implementación; si alguna vez la necesitara, sería un
defecto del contrato, no algo que se parchea aquí.

El origen del panel tiene que estar en `CORS_ALLOWED_ORIGINS` del backend
(`http://localhost:5173` en local) o ninguna petición con credenciales pasará.

La salida de `vite build` se sirve estática en `admin.tudominio.com`, con el
reparto de caché y el *fallback* a `index.html` de §2, y con `X-Robots-Tag:
noindex` — no porque haya algo secreto en el HTML, sino porque no tiene ningún
sentido que el panel aparezca en un buscador.

---

## 18. Pruebas

Se prueba lo que hace un usuario, no cómo lo hace un componente.

```tsx
it('limpia la subcategoría al cambiar de categoría', async () => {
  render(<ProductForm categories={tree} />);

  await userEvent.selectOptions(screen.getByLabelText(/categoría/i), 'Tecnología');
  await userEvent.selectOptions(screen.getByLabelText(/subcategoría/i), 'Laptops');
  await userEvent.selectOptions(screen.getByLabelText(/categoría/i), 'Deportes');

  expect(screen.getByLabelText(/subcategoría/i)).toHaveValue('');
});
```

Se consulta por rol y por etiqueta —la forma en que la tecnología de apoyo
encuentra las cosas—, así que las pruebas sirven además como comprobación de
accesibilidad.

Lo que hay que cubrir sí o sí, porque es donde están los errores de *este*
panel:

| Caso | Por qué |
| --- | --- |
| Cascada categoría → subcategoría | Es la lógica con estado más enredada del formulario |
| `precioAnterior` menor que `precio` marca error de campo | Regla cruzada, fácil de romper al refactorizar |
| Un `VALIDATION_ERROR` del servidor pinta errores por campo | El mapeo por nombre se rompe en silencio |
| El sondeo de `PROCESANDO` se detiene al llegar `LISTO` | Un sondeo eterno no hace fallar ningún test… salvo este |
| Un `401` con varias peticiones en vuelo dispara un solo refresco | El fallo se manifiesta como expulsión de sesión, no como error visible |
| Cambiar un filtro reinicia a la página 1 | Un clásico que vuelve en cada refactor de la tabla |

Sin instantáneas amplias: se rompen con cualquier cambio cosmético y se aprueban
sin leerlas.

---

## 19. Definición de terminado

* [ ] Los cuatro estados —carga, vacío, error, éxito— manejados, con los dos
      vacíos distinguidos (nunca hubo datos / no hay resultados)
* [ ] Tipado contra el contrato, sin `any` sin justificar
* [ ] Ningún componente llama a `axios`: componente → hook → servicio
* [ ] El estado del servidor vive en TanStack Query, no duplicado en `useState`
* [ ] Filtros, búsqueda, orden y página viven en la URL
* [ ] Los errores se ramifican por código, nunca por mensaje, y no se muestra
      ningún `detail` crudo
* [ ] El token de acceso está en memoria; el refresco es único en vuelo y se
      reintenta una sola vez
* [ ] Toda acción de arrastrar tiene alternativa con teclado
* [ ] El `alt` de cada imagen es obligatorio y se valida antes de enviar
* [ ] Navegable por teclado, con foco visible y foco devuelto al cerrar modales
* [ ] Ningún secreto en ninguna variable `VITE_*`

---

## 20. Lo que el contrato todavía no cubre

Estas piezas las necesita el panel y **no están** en
[`contracts/openapi.yaml`](../../contracts/openapi.yaml), que hoy solo describe
la parte pública del catálogo y el carrito. Se listan aquí para que la
divergencia sea visible y no se resuelva improvisando en el frontend:

| Falta o divergencia | Nota |
| --- | --- |
| Endpoint de acceso del administrador | [autenticación](../backend/autenticacion.md) define el token (`aud=admin`) pero no nombra la ruta; el [plan](../PLAN.md) §7.2 dice `POST /auth/login` |
| Endpoint de refresco | El flujo está descrito; el nombre de la ruta, no. §7 lo llama `POST /auth/refrescar` de forma provisional |
| CRUD de `/admin/products`, `/admin/brands`, `/admin/categories`, `/admin/coupons`, `/admin/orders`, `/admin/dashboard` | Enumerados en el plan, ausentes del contrato |
| Lectura de estado de un archivo (`GET /admin/archivos/{id}`) | La subida (`202` con `PROCESANDO`) está descrita; la lectura que el sondeo necesita, no |
| Campos de producto: `sku`, `marca`, `subcategoria`, imágenes con **tres variantes** y `alt` | El contrato aún describe categorías de un nivel y una sola `imageUrl`, anterior a [ADR-0006](../adr/0006-categorias-dos-niveles.md) y [ADR-0009](../adr/0009-imagenes-r2-webp.md) |
| **Forma del `Problem`** | El contrato no incluye `code` ni `correlationId`, y modela `errors` como un mapa `campo → mensajes[]`; [el catálogo de errores](../contrato/catalogo-errores.md) define `code`, `correlationId` y `errors[]` como array de `{ field, message }`. **El mapeo a campos del formulario depende de esto** |
| **Idioma de los campos** | El contrato nombra los campos en inglés (`compareAtPrice`); el catálogo de errores y las reglas, en español (`precioAnterior`, `subcategoriaId`). Si `errors[].field` no coincide con el nombre del campo del formulario, el resaltado por campo no funciona |
| **`pageSize` máximo** | RN-024 dice 60 por defecto 20; el contrato dice máximo 48 por defecto 12. El selector de filas por página no puede fijarse hasta que coincidan |
| **`FILE_NOT_READY` vs. guardar con imágenes en proceso** | [archivos e imágenes](../backend/archivos-imagenes.md) §4 lo permite; el catálogo de errores define un `422` para ello. §13 describe cómo sobrevive el panel mientras tanto |
| Idioma de las rutas | El contrato usa inglés (`/products`); los documentos de backend usan español (`/auth/registro`, `/admin/archivos`). Conviene unificar antes de generar los tipos |
