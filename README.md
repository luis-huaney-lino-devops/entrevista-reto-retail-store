# Retail Store

E-commerce del reto técnico. Monorepo:

| Carpeta | Qué es | Estado |
| --- | --- | --- |
| **`backend/java/`** | API REST en Spring Boot 3.5 / Java 21. Es el único backend ([ADR-0005](docs/adr/0005-backend-solo-java.md)) | Administración, catálogo, órdenes, clientes, chat y métricas |
| **`apps/admin/`** | Panel de administración. React + Vite, renderizado en el cliente | Funcional |
| **`apps/storefront/`** | Tienda pública. Next.js con SSR/ISR | Sin construir |
| **`database/migrations/`** | Esquema y datos, aplicados por Flyway al arrancar. Fuente única del esquema | `V001`–`V008` |
| **`contracts/openapi.yaml`** | Contrato REST. **Generado** desde la aplicación | Al día |
| **`pruebas/humo-api.py`** | Extremo a extremo contra la API en marcha: catálogo, panel, órdenes, tablero | 123 comprobaciones |
| **`pruebas/humo-chat.py`** | Extremo a extremo del chat por WebSocket, los adjuntos, las notificaciones y la papelera | 57 comprobaciones |
| **`docs/`** | Especificación, reglas de negocio y decisiones | — |

`_empleados-v1/` y `docs copy/` son restos de un proyecto anterior y no forman
parte de esto.

---

## Levantar el proyecto

### 1. Base de datos

```bash
docker compose -f deploy/docker-compose.dev.yml up -d db
```

Levanta PostgreSQL 17 con la base `retailstore_java`, usuario y clave
`retail`/`retail`, **en el puerto 5433 del anfitrión**.

> **Por qué 5433 y no 5432.** Es habitual tener un PostgreSQL instalado en la
> máquina ocupando el 5432. Cuando eso pasa, la aplicación se conecta a ese
> otro sin avisar y falla con «autenticación password falló», que no dice nada
> del problema real. Cambia el puerto con `DB_PUERTO_HOST` si te estorba.

### 2. Backend

```bash
cd backend/java
mvn spring-boot:run
```

- API en `http://localhost:8080`
- Swagger en `http://localhost:8080/swagger`
- Salud en `http://localhost:8080/health`

Detalles, variables de entorno y arquitectura: [`backend/java/README.md`](backend/java/README.md).

### 3. Panel de administración

```bash
cd apps/admin
npm install
npm run dev
```

En `http://localhost:5173`. Acceso inicial: **`admin` / `AdminRetail2026!`**
(lo crea la migración `V004`; cámbialo antes de exponer esto a cualquier red).

Detalles: [`apps/admin/README.md`](apps/admin/README.md).

---

## Comprobar que todo funciona

Con la base y el backend en marcha:

```bash
python pruebas/humo-api.py
```

Ejercita catálogo público, acceso al panel, rotación de tokens, alta de
catálogo, subida y conversión de imágenes, reglas de dependencias, carrito,
cupones, permisos por rol, CORS, órdenes, vistas de producto y el tablero.
Termina con `PASAN n FALLAN 0`.

Pruebas unitarias del backend:

```bash
cd backend/java && mvn test
```

---

## Convenciones

Todo el dominio está en español, en las cuatro capas
([ADR-0011](docs/adr/0011-dominio-en-espanol-convencion-nombres.md)):

| Qué | Convención | Ejemplo |
| --- | --- | --- |
| Tabla | español, **singular** | `producto`, `item_carrito` |
| Clave primaria | `id_<tabla>` | `producto.id_producto` |
| Clave foránea | `fk_id_<tabla>` | `producto.fk_id_subcategoria` |
| Entidad, servicio, DTO | español | `Producto`, `ProductoServicio` |
| Rutas y campos JSON | español | `/api/v1/admin/productos`, `precioAnterior` |
| `code` de error | inglés | `INSUFFICIENT_STOCK` |

---

## Despliegue

Ver `deploy/docker-compose.prod.yml` y `deploy/Caddyfile`.

```bash
cd deploy
cp .env.example .env    # completar POSTGRES_PASSWORD, JWT_SECRETO, dominios
docker compose -f docker-compose.prod.yml up -d --build
```

> **Antes de desplegar**: `JWT_SECRETO` es obligatorio y necesita al menos 32
> bytes; con el valor de desarrollo, cualquiera que lea el repositorio puede
> firmar tokens de administrador. `JWT_COOKIE_SEGURA=true` sobre HTTPS.

---

## Documentación

- [`docs/estado-y-brecha.md`](docs/estado-y-brecha.md) — **qué existe y qué falta**. Manda sobre cualquier otro documento en lo que respecta al estado.
- [`docs/negocio/reglas-negocio.md`](docs/negocio/reglas-negocio.md) — las reglas `RN-###`. Única autoridad sobre la numeración.
- [`docs/negocio/modelo-dominio.md`](docs/negocio/modelo-dominio.md) — entidades e invariantes, sin tecnología.
- [`docs/contrato/catalogo-errores.md`](docs/contrato/catalogo-errores.md) — todos los códigos de error.
- [`docs/adr/`](docs/adr/) — decisiones de arquitectura, una por archivo.

## Uso de IA

Este proyecto se construyó con apoyo de IA (Claude). Todo el código está
revisado y es explicable: cada decisión no obvia lleva un comentario que dice
por qué, y las de alcance están en `docs/adr/`.
