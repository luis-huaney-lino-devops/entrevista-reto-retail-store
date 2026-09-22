# Retail Store

E-commerce del reto técnico. Monorepo:

| Carpeta | Qué es | Puerto en local |
| --- | --- | --- |
| **`backend/java/`** | API REST en Spring Boot 3.5 / Java 21. Es el único backend ([ADR-0005](docs/adr/0005-backend-solo-java.md)) | `8080` |
| **`apps/storefront/`** | Tienda pública. Next.js 15 App Router, SSR/ISR | `3000` |
| **`apps/admin/`** | Panel de administración. React 19 + Vite 6, renderizado en el cliente | `5173` |
| **`database/migrations/`** | Esquema (`V001`) y datos de semilla (`V002`), aplicados por Flyway al arrancar. Fuente única del esquema | — |
| **`contracts/openapi.yaml`** | Contrato REST. **Generado** desde la aplicación | — |
| **`pruebas/`** | Extremo a extremo en Python contra la API en marcha | — |
| **`deploy/`** | Docker Compose y la configuración de nginx del despliegue | — |
| **`docs/`** | Especificación, reglas de negocio y decisiones | — |

---

## Requisitos

| Herramienta | Versión | Para qué |
| --- | --- | --- |
| **Java** | **21** exactamente | El backend. Con 17 no compila; con 23 falla al arrancar |
| **Maven** | 3.9+ | Construir el backend. **No uses `./mvnw`**: el wrapper no está en el repositorio |
| **Node.js** | 20+ | Los dos frontends |
| **Docker** | cualquiera reciente | Solo para levantar PostgreSQL |
| **Python** | 3.10+ | Solo para las pruebas de extremo a extremo |

Si tienes varias versiones de Java, fija la 21 en **cada sesión de terminal**
antes de tocar el backend:

```bash
# Git Bash / WSL / Linux / macOS
export JAVA_HOME="/c/Program Files/Java/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"
java -version    # tiene que decir 21
```

```powershell
# PowerShell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
```

---

## Levantar el proyecto

Cada pieza se levanta por separado, en **su propia terminal**. La única que es
obligatoria siempre es la base de datos.

### Paso 0 — Base de datos (obligatorio)

```bash
docker compose -f deploy/docker-compose.dev.yml up -d db
```

PostgreSQL 17 con la base `retailstore_java`, usuario y clave `retail`/`retail`,
**en el puerto 5433 del anfitrión**.

> **Por qué 5433 y no 5432.** Es habitual tener un PostgreSQL instalado en la
> máquina ocupando el 5432. Cuando eso pasa, la aplicación se conecta a ese
> otro sin avisar y falla con «autenticación password falló», que no dice nada
> del problema real. Cambia el puerto con `DB_PUERTO_HOST` si te estorba.

No hay que crear tablas ni restaurar ningún volcado: **Flyway aplica `V001` y
`V002` al arrancar el backend**, y `V002` trae el catálogo completo, el ubigeo
del Perú, cupones, clientes y el administrador.

Para empezar de cero —obligatorio si tocas una migración ya aplicada—:

```bash
docker compose -f deploy/docker-compose.dev.yml down -v
docker compose -f deploy/docker-compose.dev.yml up -d db
```

### Backend solo

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
| **Imágenes** | http://localhost:8080/archivos/… |
| **Chat (WebSocket)** | `ws://localhost:8080/ws/chat` |

La primera vez tarda: Maven se baja las dependencias y Flyway aplica las dos
migraciones. Cuando veas `Started RetailStoreApplication`, está listo.

No necesita ningún `.env`: los valores por defecto de
[`application.yml`](backend/java/src/main/resources/application.yml) ya apuntan
a la base del paso 0. Las variables que sí importan en producción están en
[`deploy/dokploy.env.example`](deploy/dokploy.env.example).

Dependencias principales: Spring Boot Web, Data JPA, Security, Validation y
WebSocket; Flyway; PostgreSQL JDBC; springdoc-openapi (Swagger); AWS SDK S3
(para Cloudflare R2). Todas en [`backend/java/pom.xml`](backend/java/pom.xml).

### Tienda pública sola

Necesita el backend en marcha.

```bash
cd apps/storefront
cp .env.example .env.local     # los valores por defecto ya sirven en local
npm install
npm run dev
```

| | |
| --- | --- |
| **Punto de entrada** | `apps/storefront/app/page.tsx` (portada) y `app/layout.tsx` (raíz) |
| **URL** | http://localhost:3000 |

Dependencias principales: Next.js 15, React 19, Tailwind, `react-markdown`,
`react-leaflet` (el mapa del formulario de dirección).

### Panel de administración solo

Necesita el backend en marcha.

```bash
cd apps/admin
cp .env.example .env.local     # los valores por defecto ya sirven en local
npm install
npm run dev
```

| | |
| --- | --- |
| **Punto de entrada** | `apps/admin/src/main.tsx` → `src/App.tsx` |
| **URL** | http://localhost:5173 — entra por `/productos` |
| **Usuario** | `admin` |
| **Contraseña** | `AdminRetail2026!` |

> La contraseña la siembra `V002`. **Cámbiala antes de exponer esto a
> cualquier red.**

Dependencias principales: React 19, Vite 6, React Router 7, TanStack Query,
Recharts (el tablero), TipTap (el editor de descripciones).

### Los tres a la vez

Cuatro terminales:

```bash
# 1
docker compose -f deploy/docker-compose.dev.yml up -d db
# 2
cd backend/java && mvn spring-boot:run
# 3
cd apps/storefront && npm run dev
# 4
cd apps/admin && npm run dev
```

O, si solo quieres ver la aplicación funcionando sin instalar Java ni Node,
todo en Docker:

```bash
docker compose -f deploy/docker-compose.dev.yml --profile java up -d --build
```

---

## Comprobar que funciona

Con la base y el backend en marcha. Las tres son **idempotentes**: se pueden
repetir sobre la misma base sin limpiarla.

```bash
python pruebas/humo-api.py     # catálogo, panel, órdenes, tablero
python pruebas/humo-chat.py    # chat por WebSocket, adjuntos, notificaciones
python pruebas/humo-cuenta.py  # ubigeo, cuenta, Google, direcciones, favoritos
```

Lo que se mira es **`FALLAN 0`**, no el total: en `humo-api.py` el tramo de
cancelación solo corre si la orden que le toca admite `CANCELADA`, así que el
recuento baja cuando no corre.

Pruebas unitarias del backend:

```bash
cd backend/java
mvn test                                    # todas
mvn -Dtest=ProductoTest test                # una clase
mvn -Dtest=ProductoTest#calculaDescuento test   # un caso
```

Comprobación de tipos de los frontends (no hay linter; el `tsc` del build es la
red que hay):

```bash
cd apps/admin      && npm run typecheck
cd apps/storefront && npm run typecheck
```

---

## Compilar para producción

```bash
cd backend/java     && mvn package -DskipTests   # target/retail-store-api.jar
cd apps/storefront  && npm run build             # .next/ (salida standalone)
cd apps/admin       && npm run build             # dist/ (estático)
```

---

## Despliegue

En producción corre en un VPS con Dokploy: un solo servicio Compose con la API,
la tienda, el panel y un nginx de entrada que reparte por dominio.

**Guía completa: [`deploy/DOKPLOY.md`](deploy/DOKPLOY.md).**

```bash
# Equivalente en local, sin Dokploy
docker compose -f deploy/docker-compose.dokploy.yml up -d --build
```

> `JWT_SECRETO` es obligatorio y necesita al menos 32 bytes; con el valor de
> desarrollo, cualquiera que lea el repositorio puede firmar tokens de
> administrador.

---

## Convenciones

Todo el dominio está en español, en las cuatro capas
([ADR-0011](docs/adr/0011-dominio-en-espanol-convencion-nombres.md)):

| Qué | Convención | Ejemplo |
| --- | --- | --- |
| Tabla | español, **singular** | `producto`, `item_carrito` |
| Clave primaria | `id_<tabla>` | `producto.id_producto` |
| Clave foránea | `fk_id_<tabla>` | `producto.fk_id_subcategoria` |
| Entidad, servicio, DTO | español | `Producto`, `ServicioProducto` |
| Rutas y campos JSON | español | `/api/v1/admin/productos`, `precioAnterior` |
| `code` de error | inglés | `INSUFFICIENT_STOCK` |

Los errores siguen RFC 9457 (`application/problem+json`) con extensiones `code`
y `correlationId`. Los clientes se ramifican por `code`, nunca por `detail`.

---

## Documentación

- [`docs/estado-y-brecha.md`](docs/estado-y-brecha.md) — **qué existe y qué falta**. Manda sobre cualquier otro documento en lo que respecta al estado.
- [`docs/negocio/reglas-negocio.md`](docs/negocio/reglas-negocio.md) — las reglas `RN-###`. Única autoridad sobre la numeración.
- [`docs/negocio/modelo-dominio.md`](docs/negocio/modelo-dominio.md) — entidades e invariantes, sin tecnología.
- [`docs/contrato/catalogo-errores.md`](docs/contrato/catalogo-errores.md) — todos los códigos de error.
- [`docs/adr/`](docs/adr/) — decisiones de arquitectura, una por archivo.
- [`deploy/DOKPLOY.md`](deploy/DOKPLOY.md) — el despliegue y sus trampas.

## Uso de IA

Este proyecto se construyó con apoyo de IA (Claude). Todo el código está
revisado y es explicable: cada decisión no obvia lleva un comentario que dice
por qué, y las de alcance están en `docs/adr/`.
