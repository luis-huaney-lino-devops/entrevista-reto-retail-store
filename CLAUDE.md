# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Lo primero que hay que entender

El idioma del proyecto es **español, en las cuatro capas**: tablas, entidades,
DTOs, rutas y campos JSON (ADR-0011). La única excepción es el `code` de los
errores, que es una constante de protocolo y se queda en inglés, como
`invalid_grant` en OAuth.

Convención de nombres en la base de datos, sin excepciones:

- Tablas en **singular**: `producto`, `orden`, `item_orden`. Así `id_<tabla>` se
  lee literal.
- Clave primaria: `id_<tabla>` — `id_producto`, `id_conversacion`.
- Clave foránea: `fk_id_<tabla_referenciada>` — `fk_id_categoria`.
- Restricciones con prefijo: `pk_`, `fk_`, `uq_`, `ck_`, `ix_`.

[`docs/estado-y-brecha.md`](docs/estado-y-brecha.md) manda sobre cualquier otro
documento en lo que respecta al estado. Léelo antes de asumir que algo existe o
que no existe.

---

## Comandos

### Entorno (obligatorio en cada sesión de shell)

El `java` del PATH es 23 y `JAVA_HOME` apunta a temurin17; el proyecto necesita
**21**. Maven no está en el PATH del sistema.

```bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
export PATH="$JAVA_HOME/bin:/c/Users/luisl/scoop/apps/maven/current/bin:$PATH"
```

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:USERPROFILE\scoop\apps\maven\current\bin;$env:PATH"
```

### Base de datos

```bash
docker compose -f deploy/docker-compose.dev.yml up -d db
```

Crea `retailstore_java` en **`localhost:5433`** (no 5432: es habitual tener un
PostgreSQL local ocupando ese puerto). Usuario y clave `retail`/`retail`.
Flyway aplica `database/migrations/` al arrancar la aplicación.

Para empezar de cero —obligatorio si has tocado una migración ya aplicada—:

```bash
docker compose -f deploy/docker-compose.dev.yml down -v
docker compose -f deploy/docker-compose.dev.yml up -d db
```

Mailpit (captura de correo en desarrollo) corre aparte: SMTP en `1025`,
interfaz web en `http://localhost:8025`.

### Backend

```bash
cd backend/java
DB_PUERTO_HOST=5433 mvn spring-boot:run   # http://localhost:8080 · Swagger en /swagger
mvn test                                   # 158 pruebas unitarias
mvn -Dtest=ProductoTest test
mvn -Dtest=ProductoTest#calculaDescuento test
```

> **No uses `./mvnw`.** El wrapper no existe en el repositorio; usa `mvn` con el
> PATH de arriba.

### Panel de administración

```bash
cd apps/admin
npm install
npm run dev        # http://localhost:5173 · entra en /productos
npm run build      # tsc -b && vite build
npm run typecheck
```

No hay `lint` configurado; el `tsc -b` del build es la red que hay.

`apps/storefront/` **solo contiene un README**: la tienda pública todavía no
está construida.

### Pruebas de extremo a extremo

Las tres necesitan la API levantada y son **idempotentes**: se pueden repetir
sobre la misma base sin limpiarla.

```bash
python pruebas/humo-api.py     # 127 comprobaciones: catálogo, panel, órdenes, tablero
python pruebas/humo-chat.py    # 60: chat por WebSocket, adjuntos, notificaciones, papelera
python pruebas/humo-cuenta.py  # 105: ubigeo, cuenta, Google, direcciones, favoritos
```

Merecen la pena: han encontrado **seis** defectos que ninguna prueba unitaria
podía ver —una revocación de token deshecha por un rollback, campos de
auditoría caducados en la respuesta, una difusión antes del commit, un producto
que nunca se persistía, una galería que se reinsertaba y una clave de adjunto
que colisionaba consigo misma. Cuando cambies algo que cruce capas, córrelas.

---

## Arquitectura

### Monorepo

```
backend/java/          Spring Boot 3.5 · Java 21 · el único backend (ADR-0005)
apps/admin/            React 19 + Vite 6, CSR — construido
apps/storefront/       Next.js App Router, SSR/ISR — sin construir
database/migrations/   Flyway, dueño del esquema (V001–V008)
contracts/openapi.yaml Contrato REST que consumen los dos frontends
docs/                  La especificación
pruebas/               Humo de extremo a extremo, en Python
deploy/                Docker Compose y Caddy
```

`_empleados-v1/` y `docs copy/` son restos de un proyecto anterior (gestión de
empleados sobre Oracle). **No forman parte de este proyecto.**

### Capas del backend

```
Controlador → DTO → Servicio → Repositorio → PostgreSQL
```

Paquetes por funcionalidad, no por capa: `com.retailstore.api.{catalogo, carrito,
chat, cliente, cuenta, cupon, notificacion, orden, panel, seguridad, ubigeo, archivo,
comun, config}`.
Cada módulo tiene dentro `dominio/`, `dto/`, `repositorio/`, `servicio/`, `web/`.

La lógica que pertenece a una entidad vive en la entidad
(`Producto.porcentajeDescuento()`, `Cupon.aplicaA()`,
`Carrito.agregarOIncrementar()`), no en el servicio. El servicio orquesta.

### Reglas: una sola autoridad

[`docs/negocio/reglas-negocio.md`](docs/negocio/reglas-negocio.md) es **el único
documento que numera** reglas `RN-###`. Los demás las referencian y nunca las
definen ni renumeran; ya ocurrió una vez que dos documentos usaran el mismo
número para cosas distintas.

Las pruebas se nombran por la regla que cubren:
`crear_conSkuDuplicado_lanzaDuplicateSku_RN002`.

Las que más condicionan el código nuevo:

| Regla | Qué obliga |
| --- | --- |
| RN-086 | **Nada se borra**: `DELETE` marca `eliminado_en` y `eliminado_por` |
| RN-087 | Toda eliminación se confirma en un diálogo antes de salir del panel |
| RN-083 | Efectos externos —correo, R2, **WebSocket**— siempre tras el commit |
| RN-088 | Adjuntos de chat: solo imagen o PDF, revisados por contenido |
| RN-089 | Una alerta se publica una vez y se cierra sola al resolverse |

### Decisiones que atraviesan todo

Los ADR están en [`docs/adr/`](docs/adr/):

| ADR | Decisión | Consecuencia práctica |
| --- | --- | --- |
| 0005 | Un solo backend, Java | No hay .NET. Si ves una referencia, es un resto |
| 0006 | Categorías de **dos** niveles | `categoria` → `subcategoria` → `producto` |
| 0007 | Productos sin variantes | Un producto = un SKU, un precio, un stock |
| 0008 | Clientes y administradores en **tablas separadas** | Nunca un campo `rol` en una sola tabla de usuarios |
| 0009 | Imágenes convertidas a WebP al subir | Cuatro variantes; nunca se amplía |
| 0010 | *Reemplazado por el 0011* | Solo sigue vigente que `errors[].field` coincida exacto |
| 0011 | Español en las cuatro capas | El `code` del error es la excepción |

### Contrato de errores

RFC 9457 (`application/problem+json`) con extensiones `code` y `correlationId`.
Los clientes se ramifican por `code`, nunca por `detail`. Catálogo completo en
[`docs/contrato/catalogo-errores.md`](docs/contrato/catalogo-errores.md).

---

## Trampas conocidas

Las cinco primeras costaron una sesión entera cada una. Léelas antes de tocar
persistencia.

**`saveAndFlush` sobre una entidad *gestionada* hace `merge`, y `merge`
persiste una copia de los hijos nuevos.** El original se queda sin id, así que
lo que difundas o devuelvas llevará `id: null`. Para lo ya gestionado usa
`repositorio.flush()`; `save()` **solo** para entidades realmente nuevas.

**Y al revés: una entidad nueva necesita `save()`.** `flush()` a secas no
escribe nada porque nadie la ha metido en la sesión. `ServicioProducto.crear`
tuvo exactamente ese fallo: devolvía `201` con `id: null` y no insertaba nada.

**Difundir por WebSocket dentro de la transacción provoca bloqueos
optimistas.** El receptor responde antes del commit y lee una versión vieja. Se
resuelve con `TransactionSynchronizationManager.registerSynchronization(...)` →
`afterCommit`. Es RN-083 en concreto.

**Vaciar y reconstruir una colección con `orphanRemoval` rompe los índices
únicos.** Hibernate ejecuta los `INSERT` antes que los `DELETE` en el mismo
volcado, así que reenviar la misma fila choca. Hay que **reconciliar**: quitar
lo que se va, reordenar lo que se queda, añadir lo que llega. El patrón está en
`Producto.reemplazarImagenes`.

**Una clave derivada solo del hash del contenido deduplica, y eso no siempre se
quiere.** El catálogo sí deduplica imágenes a propósito; los adjuntos del chat
no pueden, porque cada uno pertenece a un mensaje. Ver `ServicioAdjunto.clave`.

**`@SQLRestriction("eliminado_en is null")` no se aplica a `find(id)`.** Ahí
Hibernate va directo a la clave primaria. Por eso los servicios comprueban
`estaEliminado()` al buscar por id.

**Las migraciones aplicadas no se editan.** Flyway calcula el checksum del
archivo completo: cambiar hasta un comentario rompe el arranque. En desarrollo,
`down -v` y a empezar; en algo ya desplegado, una migración nueva.

**El ORM no es dueño del esquema.** `ddl-auto: validate`. Si el mapeo y las
tablas discrepan, la aplicación se niega a arrancar — y eso es lo deseado.

**El bean de CORS tiene que llamarse `corsConfigurationSource`.** Spring
Security lo resuelve por ese nombre exacto; con cualquier otro, todo `preflight`
se va en `403` sin decir por qué.

**Revocar un token dentro de la transacción que va a hacer rollback no revoca
nada.** La revocación de una familia de refresco va en `REQUIRES_NEW`
(`RevocacionInmediata`), o el propio rechazo la deshace.

**Comparar entidades por id revienta con entidades no persistidas.**
`a.getId().equals(b.getId())` lanza `NullPointerException`, y «arreglarlo» con
`Objects.equals` hace que dos entidades nuevas se consideren iguales. El patrón
correcto es `esMismo()` (`Producto`, `Archivo`).

**Dinero siempre `BigDecimal` / `numeric(12,2)`**, nunca `double`. Comparar con
`compareTo`, no con `equals`.

**No se pueden traer dos colecciones `List` en el mismo grafo**
(`MultipleBagFetchException`). Una de las dos tiene que ser `Set` — por eso
`Mensaje.adjuntos` es un `LinkedHashSet`.

---

## Antes de escribir código

1. Busca la regla `RN-###` que gobierna lo que vas a construir. Si no existe,
   no es un requisito: plantéalo en lugar de inventarlo.
2. Comprueba en `docs/estado-y-brecha.md` si aquello de lo que depende ya
   existe.
3. Si cambias comportamiento, cambia **primero** `docs/negocio/reglas-negocio.md`.
4. Si un documento describe algo que el código no tiene, no es un error del
   documento: es trabajo pendiente. Actualizar `estado-y-brecha.md` es parte de
   cerrar un bloque.
