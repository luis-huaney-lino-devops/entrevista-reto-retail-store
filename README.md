<div align="center">

# Retail Store

**E-commerce de ferretería.** Tienda pública, panel de administración y una API REST que gobierna las reglas.

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?style=for-the-badge&logo=react&logoColor=black)
![Next.js](https://img.shields.io/badge/Next.js-15-000000?style=for-the-badge&logo=nextdotjs&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-6-646CFF?style=for-the-badge&logo=vite&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5.7-3178C6?style=for-the-badge&logo=typescript&logoColor=white)
![Tailwind](https://img.shields.io/badge/Tailwind-3.4-06B6D4?style=for-the-badge&logo=tailwindcss&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white)
![nginx](https://img.shields.io/badge/nginx-009639?style=for-the-badge&logo=nginx&logoColor=white)

**[Tienda](https://store.luis-alberto-huaney-lino.online)** ·
**[Panel](https://admin-store.luis-alberto-huaney-lino.online)** ·
**[API](https://api-store.luis-alberto-huaney-lino.online/swagger)**

</div>

---

## Verlo funcionando

Está desplegado. No hace falta instalar nada para probarlo:

| | Enlace | Cómo entrar |
| --- | --- | --- |
| **Tienda** | [store.luis-alberto-huaney-lino.online](https://store.luis-alberto-huaney-lino.online) | Abierta. Se puede comprar **sin cuenta** (RN-063) |
| **Panel** | [admin-store.luis-alberto-huaney-lino.online](https://admin-store.luis-alberto-huaney-lino.online) | `admin` / `AdminRetail2026!` |
| **API** | [api-store.luis-alberto-huaney-lino.online/swagger](https://api-store.luis-alberto-huaney-lino.online/swagger) | Swagger, generado desde el código |
| **Salud** | [/health](https://api-store.luis-alberto-huaney-lino.online/health) | — |

### Un recorrido de cinco minutos

1. En la **tienda**, añade un par de productos al carrito y aplica el cupón `BIENVENIDA10`
   (10 % desde S/ 50). Fíjate en que el total lo recalcula el servidor en cada cambio.
2. Pulsa **Continuar la compra**, rellena los datos y confirma. Llegas a `/pedido/ORD-…`
   con el número del pedido.
3. En el **panel**, entra en **Órdenes**: ahí está la que acabas de hacer. Cámbiala de
   estado y comprueba que solo admite las transiciones válidas (RN-054).
4. Desde esa orden, **abre una conversación** con quien compró. El chat va por WebSocket:
   lo que escribas aparece al otro lado sin recargar.
5. En **Productos**, despublica uno y mira cómo desaparece de la tienda pero sigue en el
   panel. Bórralo y recupéralo desde la **Papelera** (RN-086: nada se borra de verdad).

> Los tres dominios comparten dominio registrable a propósito: la cookie de refresco va
> con `SameSite=Lax`, y desde otro dominio el navegador dejaría de mandarla.

---

## Qué es

Un catálogo real de ferretería —10 categorías, 26 subcategorías, 219 marcas y 113 productos con foto— con
carrito, cupones, cuentas de cliente, órdenes, opiniones, un chat en tiempo real entre cliente y
administración, y un panel donde se gestiona todo.

El dominio está **en español en las cuatro capas**: tablas, entidades, DTOs y campos JSON
([ADR-0011](docs/adr/0011-dominio-en-espanol-convencion-nombres.md)). La única excepción es el `code` de
los errores, que es constante de protocolo y se queda en inglés, como `invalid_grant` en OAuth.

---

## Arquitectura

```
┌─────────────────────┐   ┌─────────────────────┐
│  Tienda pública     │   │  Panel de admin     │
│  Next.js · SSR/ISR  │   │  React+Vite · SPA   │
│  :3000              │   │  :5173              │
└──────────┬──────────┘   └──────────┬──────────┘
           │        HTTPS · REST     │
           │        + WebSocket      │
           └────────────┬────────────┘
                        ▼
           ┌────────────────────────┐
           │   API REST             │   Controlador → DTO → Servicio
           │   Spring Boot · :8080  │              → Repositorio
           └────────────┬───────────┘
                        ▼
           ┌────────────────────────┐
           │   PostgreSQL 17        │   Flyway es el dueño del esquema
           └────────────────────────┘
```

**Un solo backend** ([ADR-0005](docs/adr/0005-backend-solo-java.md)) y **cero lógica de negocio en los
frontends**: no hay Route Handlers de Next haciendo de backend. Toda regla vive en la API, que es lo que
pedía el reto al separar frontend y backend.

### Las capas del backend

```
Controlador  ──►  DTO  ──►  Servicio  ──►  Repositorio  ──►  PostgreSQL
    HTTP        validación   orquesta         JPA
```

Los paquetes se organizan **por funcionalidad, no por capa**: `catalogo`, `carrito`, `orden`, `chat`,
`cuenta`, `cupon`, `opinion`, `seguridad`, `panel`, `notificacion`, `ubigeo`, `archivo`. Cada uno tiene
dentro su `dominio/`, `dto/`, `repositorio/`, `servicio/` y `web/`. Así un cambio en órdenes se toca en
un sitio, en vez de repartirse por cuatro carpetas de `controllers/`, `services/`, `dtos/`…

**La lógica que pertenece a una entidad vive en la entidad**, no en el servicio:
`Producto.porcentajeDescuento()`, `Cupon.aplicaA()`, `Carrito.agregarOIncrementar()`. El servicio
orquesta y transacciona; no calcula.

---

## Por qué cada tecnología

### Por qué Next.js en la tienda y Vite en el panel

No es capricho: **son dos problemas distintos** ([ADR-0001](docs/adr/0001-ssr-storefront-csr-admin.md)).

|  | Tienda pública | Panel de administración |
| --- | --- | --- |
| **Quién entra** | Cualquiera, muchos desde el móvil | Un puñado de administradores, tras login |
| **Qué importa** | Que Google la indexe y que pinte rápido a la primera | Interacción densa: tablas, formularios, filtros |
| **SEO** | Crítico | Irrelevante — está detrás de login |
| **Decisión** | **Next.js App Router**, SSR + ISR | **React + Vite**, SPA pura (CSR) |

En la tienda, el HTML del catálogo llega **ya renderizado**: el buscador lo lee y el usuario ve producto
antes de que cargue un solo kilobyte de JavaScript. `generateMetadata` da títulos y Open Graph reales por
producto, e ISR (`revalidate`) sirve páginas estáticas que se refrescan solas sin reconstruir el sitio.

En el panel eso no aporta nada —nadie indexa una tabla de pedidos— y estorba: un SPA de Vite arranca en
milisegundos y no paga el coste de serializar estado servidor→cliente en cada navegación.

> Next.js **es** React, así que el requisito del reto se cumple. Se documenta explícitamente para que no
> se lea como «no usó React».

### Backend

| Librería | Para qué | Por qué esta |
| --- | --- | --- |
| **Spring Boot 3.5** | El armazón | Estándar de facto en Java. Inyección de dependencias, transacciones y configuración sin escribir infraestructura |
| **Spring Data JPA** / Hibernate | Persistencia | Repositorios declarativos. `ddl-auto: validate`: el ORM **no** es dueño del esquema, solo verifica que el mapeo cuadra |
| **Spring Security** | Autenticación y autorización | Filtros JWT propios (`FiltroJwtPanel`, `FiltroJwtTienda`) sobre su cadena. Sin sesiones de servidor |
| **Flyway** | Esquema y datos | **Fuente única del esquema.** Versionado, con checksum, reproducible. Nada de `create-drop` |
| **Bean Validation** | Validar la entrada | 51 `@Size`, 41 `@NotBlank`, 29 `@Min`… declarativo en el DTO, no `if` repartidos por el servicio |
| **springdoc-openapi** | Swagger | El contrato se **genera** desde el código, así que no puede quedar desfasado |
| **AWS SDK S3** | Imágenes en Cloudflare R2 | R2 habla protocolo S3 ([ADR-0009](docs/adr/0009-imagenes-r2-webp.md)). Un solo cliente para los dos |
| **PostgreSQL JDBC** | Driver | `numeric(12,2)` para dinero, `timestamptz`, índices parciales ([ADR-0004](docs/adr/0004-postgresql-sobre-sqlite.md)) |

### Tienda (`apps/storefront/`)

| Librería | Para qué |
| --- | --- |
| **Next.js 15** (App Router) | Server Components para el catálogo; Client Components solo donde hay estado |
| **React 19** | La base |
| **Tailwind CSS** | Estilos. Sin hojas globales que se pisen entre páginas |
| **react-markdown** + **remark-gfm** | Descripciones de producto, que el panel escribe en Markdown |
| **react-leaflet** | Mapa para elegir la dirección de envío |
| **server-only** | Marca `api.servidor.ts`: si alguien lo importa desde un componente cliente, **falla la compilación** en vez de filtrar la URL interna al HTML |

### Panel (`apps/admin/`)

| Librería | Para qué |
| --- | --- |
| **React 19 + Vite 6** | SPA y recarga instantánea en desarrollo |
| **React Router 7** | Enrutado en el cliente |
| **TanStack Query** | **Estado del servidor**: caché, revalidación e invalidación. Es lo que evita un `useEffect` con `fetch` en cada pantalla |
| **Recharts** | Gráficas del tablero |
| **TipTap** | Editor de descripciones, que guarda Markdown |
| **lucide-react** | Iconos |

### Manejo del estado

Deliberadamente **sin Redux ni Zustand**: no hacen falta.

| Qué | Dónde vive | Cómo |
| --- | --- | --- |
| **Carrito** | En el **servidor** | [ADR-0002](docs/adr/0002-carrito-servidor-fuente-de-verdad.md). `item_carrito` **no guarda el precio**: se recalcula siempre desde el producto vigente, en un único sitio (`CalculadoraCarrito`). El cliente nunca envía importes |
| Carrito en pantalla | `ProveedorCarrito` + `reductor.ts` | **UI optimista**: el `useReducer` aplica el cambio al instante, la petición va en paralelo y se reconcilia con la respuesta del servidor —o se revierte si falla—. Se siente inmediato sin que el navegador decida el precio |
| Sesión, favoritos, comparador, avisos | Context por funcionalidad | Un proveedor pequeño por cosa, en `funcionalidades/` |
| Datos del panel | **TanStack Query** | Caché por clave, invalidación tras mutar |
| Tiempo real | `TiempoReal.tsx` | WebSocket con reconexión de espera creciente |

---

## Decisiones de arquitectura

Una por archivo en [`docs/adr/`](docs/adr/). Las que más condicionan el código:

| ADR | Decisión | Por qué |
| --- | --- | --- |
| [0001](docs/adr/0001-ssr-storefront-csr-admin.md) | Next SSR en la tienda, Vite SPA en el panel | SEO y primer pintado importan en una; en la otra no |
| [0002](docs/adr/0002-carrito-servidor-fuente-de-verdad.md) | El carrito vive en el servidor | Precio y stock no se negocian en el cliente |
| [0005](docs/adr/0005-backend-solo-java.md) | Un solo backend | Dos backends es dos veces la superficie de error |
| [0006](docs/adr/0006-categorias-dos-niveles.md) | Categorías de **dos** niveles | Un árbol infinito complica consultas que nadie pidió |
| [0007](docs/adr/0007-productos-sin-variantes.md) | Productos sin variantes | Un producto = un SKU, un precio, un stock |
| [0008](docs/adr/0008-cuentas-solo-clientes.md) | Clientes y administradores en **tablas separadas** | Nunca un campo `rol` decidiendo si alguien puede borrar el catálogo |
| [0009](docs/adr/0009-imagenes-r2-webp.md) | WebP en cuatro variantes | Se convierte al subir; nunca se amplía |
| [0011](docs/adr/0011-dominio-en-espanol-convencion-nombres.md) | Español en las cuatro capas | Un solo vocabulario entre negocio y código |

### Reglas que atraviesan todo

Las numera [`docs/negocio/reglas-negocio.md`](docs/negocio/reglas-negocio.md), que es la **única**
autoridad sobre la numeración `RN-###`.

| Regla | Qué obliga |
| --- | --- |
| **RN-086** | **Nada se borra.** `DELETE` marca `eliminado_en` y `eliminado_por`; hay papelera |
| **RN-083** | Los efectos externos —correo, R2, **WebSocket**— siempre **después** del commit |
| **RN-088** | Adjuntos del chat: solo imagen o PDF, comprobados **por contenido**, no por extensión |

### Errores y validaciones

Contrato **RFC 9457** (`application/problem+json`) con extensiones `code` y `correlationId`:

```json
{
  "type": "https://retailstore.dev/errors/insufficient-stock",
  "title": "Conflicto",
  "status": 409,
  "detail": "Solo quedan 3 unidades de «Cemento Sol 42.5 kg».",
  "code": "INSUFFICIENT_STOCK",
  "correlationId": "a3f2c81e"
}
```

Los clientes se ramifican por `code`, **nunca** por `detail` —que es texto para humanos y puede cambiar—.
Catálogo completo en [`docs/contrato/catalogo-errores.md`](docs/contrato/catalogo-errores.md).

La validación es declarativa en el DTO (Bean Validation) y `ManejadorGlobalErrores` la traduce a ese
formato, añadiendo un array `errors` con `field` coincidiendo **exacto** con el nombre del campo JSON,
para que el formulario pinte el error junto al input correcto.

Las violaciones de restricción de la base se traducen **por nombre de restricción**
(`uq_producto_sku` → `DUPLICATE_SKU`), nunca analizando el texto del driver: ese texto cambia entre
versiones de PostgreSQL y dejaría de coincidir en una actualización menor.

---

## API

Base `/api/v1`. Swagger en `/swagger` — **generado desde el código**, no escrito a mano.

| Público | Cuenta del cliente | Panel (`/admin`) |
| --- | --- | --- |
| `/productos` | `/cuenta` | `/productos` `/categorias` `/subcategorias` `/marcas` |
| `/carritos` | `/cuenta/direcciones` | `/ordenes` `/clientes` `/cupones` |
| `/ubigeo` | `/cuenta/favoritos` | `/administradores` `/archivos` |
| `/conversaciones` | `/cuenta/opiniones` | `/conversaciones` `/notificaciones` |
| | | `/metricas` `/papelera` |

**Autenticación**: JWT de acceso de vida corta (15 min) + *refresh token* en cookie `HttpOnly`. El panel
caduca a las 12 h; la tienda a los 30 días, porque obligar a un comprador a entrar de nuevo cada mañana
cuesta ventas y su sesión no puede hacer lo que la de un administrador. Detalle en
[`docs/backend/autenticacion.md`](docs/backend/autenticacion.md).

**Tiempo real**: el chat va por WebSocket en `/ws/chat`, autenticado con un *ticket* de un solo uso —el
navegador no puede poner cabeceras en el handshake, así que el token no viaja en la URL de forma
reutilizable—.

---

## Cómo levantarlo

Cada pieza en **su propia terminal**. La base de datos es la única obligatoria siempre.

### Requisitos

| Herramienta | Versión | Para qué |
| --- | --- | --- |
| **Java** | **21** exactamente | El backend. Con 17 no compila; con 23 falla al arrancar |
| **Maven** | 3.9+ | **No uses `./mvnw`**: el wrapper no está en el repositorio |
| **Node.js** | 20+ | Los dos frontends |
| **Docker** | reciente | Solo para PostgreSQL |
| **Python** | 3.10+ | Solo para las pruebas de extremo a extremo |

**Fija la 21 en cada terminal antes de tocar el backend.** No es opcional si
tienes varias instaladas: Maven compila con una JVM y **arranca con la de
`JAVA_HOME`**, asi que con `JAVA_HOME` en 17 sale

```
UnsupportedClassVersionError: ... class file version 65.0,
this version of the Java Runtime only recognizes class file versions up to 61.0
```

que es «compilado con 21, intentando arrancar con 17». No es un error del
codigo.

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"   # Git Bash / WSL / Linux / macOS
export PATH="$JAVA_HOME/bin:$PATH"
java -version    # tiene que decir 21
```

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'   # PowerShell
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

Para no repetirlo nunca mas, una sola vez:

```powershell
[Environment]::SetEnvironmentVariable('JAVA_HOME','C:\Program Files\Java\jdk-21','User')
```

y abre una terminal nueva. Si ya te fallo una vez, anade `mvn clean`: en
`target/` quedaron clases compiladas con otra version.

### 1 · Base de datos

```bash
docker compose -f deploy/docker-compose.dev.yml up -d db
```

PostgreSQL 17, base `retailstore_java`, usuario `retail`/`retail`, **puerto 5433**.

> **Por qué 5433 y no 5432.** Es habitual tener un PostgreSQL ocupando el 5432. Cuando pasa, la
> aplicación se conecta a *ese* sin avisar y falla con «autenticación password falló», que no dice nada
> del problema real.

No hay que crear tablas ni restaurar volcados: **Flyway aplica las migraciones al arrancar el backend** y
`V002` trae el catálogo completo, el ubigeo del Perú, cupones, clientes y el administrador.

Para empezar de cero: `down -v` y volver a levantar.

### 2 · Backend

```bash
cd backend/java
mvn spring-boot:run
```

| | |
| --- | --- |
| **Punto de entrada** | `backend/java/src/main/java/com/retailstore/api/RetailStoreApplication.java` |
| **API** | http://localhost:8080/api/v1 |
| **Swagger** | http://localhost:8080/swagger |
| **Salud** | http://localhost:8080/health |

Listo cuando veas `Started RetailStoreApplication`. No necesita ningún `.env`: los valores por defecto de
`application.yml` ya apuntan a la base del paso 1.

### 3 · Tienda pública

```bash
cd apps/storefront
cp .env.example .env.local
npm install
npm run dev
```

Punto de entrada `app/page.tsx` (portada) y `app/layout.tsx` (raíz) · http://localhost:3000

### 4 · Panel de administración

```bash
cd apps/admin
cp .env.example .env.local
npm install
npm run dev
```

Punto de entrada `src/main.tsx` → `src/App.tsx` · http://localhost:5173 · entra por `/productos`

**Usuario `admin` · contraseña `AdminRetail2026!`** (la siembra `V002`; cámbiala antes de exponerlo).

### Todo en Docker, sin instalar Java ni Node

```bash
docker compose -f deploy/docker-compose.dev.yml --profile java up -d --build
```

---

## Pruebas

```bash
cd backend/java && mvn test        # unitarias
```

Extremo a extremo, con la base y el backend en marcha. Son **idempotentes**: se repiten sobre la misma
base sin limpiarla.

```bash
python pruebas/humo-api.py     # catálogo, panel, órdenes, tablero
python pruebas/humo-chat.py    # chat por WebSocket, adjuntos, notificaciones
python pruebas/humo-cuenta.py  # ubigeo, cuenta, Google, direcciones, favoritos
```

Lo que se mira es **`FALLAN 0`**, no el total.

Han encontrado **seis** defectos que ninguna prueba unitaria podía ver: una revocación de token deshecha
por un rollback, campos de auditoría caducados en la respuesta, una difusión de WebSocket antes del
commit, un producto que nunca se persistía, una galería que se reinsertaba y una clave de adjunto que
colisionaba consigo misma.

Comprobación de tipos (no hay linter; el `tsc` del build es la red que hay):

```bash
cd apps/admin && npm run typecheck
cd apps/storefront && npm run typecheck
```

---

## Estructura

```
backend/java/          Spring Boot 3.5 · Java 21 · el único backend
  src/main/java/com/retailstore/api/
    catalogo/ carrito/ orden/ cuenta/ chat/ opinion/
    cupon/ notificacion/ panel/ seguridad/ ubigeo/ archivo/
    comun/               errores, auditoría, paginación
    config/              CORS, Jackson, OpenAPI
apps/storefront/       Next.js 15 · App Router · SSR/ISR
  app/                   rutas
  componentes/           presentación
  funcionalidades/       estado por funcionalidad (carrito, sesión, favoritos…)
  lib/                   clientes HTTP, formato, tipos
apps/admin/            React 19 + Vite 6 · SPA
  src/paginas/           una por pantalla
  src/api/               cliente HTTP
  src/sesion/            contexto de sesión
  src/tiemporeal/        WebSocket
database/migrations/   Flyway · dueño del esquema
contracts/openapi.yaml Contrato REST generado
deploy/                Compose y nginx del despliegue
docs/                  Especificación, reglas y ADR
pruebas/               Humo de extremo a extremo, en Python
```

---

## Despliegue

En producción corre en un VPS con **Dokploy**: un solo servicio Compose con la API, la tienda, el panel y
un **nginx de entrada que reparte por dominio**; Traefik termina el TLS.

```
                    ┌─ store.…        → tienda   (Next.js)
Traefik ─► nginx ───┼─ admin-store.…  → panel    (Vite + nginx)
  (TLS)   (Host)    └─ api-store.…    → API      (Spring Boot)
```

Guía completa y trampas conocidas: **[`deploy/DOKPLOY.md`](deploy/DOKPLOY.md)**.

```bash
# Equivalente en local, sin Dokploy
docker compose -f deploy/docker-compose.dokploy.yml up -d --build
```

> `JWT_SECRETO` es obligatorio y necesita al menos 32 bytes: con el valor de desarrollo, cualquiera que
> lea el repositorio puede firmar tokens de administrador.

---

## Convenciones

| Qué | Convención | Ejemplo |
| --- | --- | --- |
| Tabla | español, **singular** | `producto`, `item_carrito` |
| Clave primaria | `id_<tabla>` | `producto.id_producto` |
| Clave foránea | `fk_id_<tabla>` | `producto.fk_id_subcategoria` |
| Restricciones | `pk_` `fk_` `uq_` `ck_` `ix_` | `uq_producto_sku` |
| Entidad, servicio, DTO | español | `Producto`, `ServicioProducto` |
| Rutas y campos JSON | español | `/api/v1/admin/productos`, `precioAnterior` |
| `code` de error | inglés | `INSUFFICIENT_STOCK` |

Las pruebas se nombran por la regla que cubren:
`crear_conSkuDuplicado_lanzaDuplicateSku_RN002`.

---

## Documentación

- [`docs/estado-y-brecha.md`](docs/estado-y-brecha.md) — **qué existe y qué falta**. Manda sobre cualquier otro documento en lo que respecta al estado
- [`docs/negocio/reglas-negocio.md`](docs/negocio/reglas-negocio.md) — las reglas `RN-###`
- [`docs/negocio/modelo-dominio.md`](docs/negocio/modelo-dominio.md) — entidades e invariantes, sin tecnología
- [`docs/contrato/catalogo-errores.md`](docs/contrato/catalogo-errores.md) — todos los códigos de error
- [`docs/adr/`](docs/adr/) — decisiones de arquitectura
- [`deploy/DOKPLOY.md`](deploy/DOKPLOY.md) — el despliegue

## Uso de IA

Este proyecto se construyó con apoyo de IA (Claude). Todo el código está revisado y es explicable: cada
decisión no obvia lleva un comentario que dice **por qué**, y las de alcance están en `docs/adr/`.
