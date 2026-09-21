# Backend Java

API REST del e-commerce. Spring Boot 3.5, Java 21, PostgreSQL 17.

---

## 1. Levantarlo

### Requisitos

- **JDK 21.** No 17 ni 23: el `pom.xml` fija `java.version=21` y Spring Boot 3.5
  no compila con menos.
- **Maven 3.9+.** No hay wrapper (`./mvnw`) en el repositorio.
- **Docker**, solo para la base de datos.

Si tienes varias versiones de Java instaladas:

```bash
# bash
export JAVA_HOME="/c/Program Files/Java/jdk-21"
export PATH="$JAVA_HOME/bin:$PATH"
```

```powershell
# PowerShell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

### Arrancar

```bash
docker compose -f ../../deploy/docker-compose.dev.yml up -d db
mvn spring-boot:run
```

| Dónde | Qué |
| --- | --- |
| `http://localhost:8080/api/v1/...` | La API |
| `http://localhost:8080/swagger` | Swagger UI |
| `http://localhost:8080/v3/api-docs.yaml` | El contrato OpenAPI |
| `http://localhost:8080/health` | Salud, para el balanceador |

Flyway aplica `database/migrations/` al arrancar. La migración `V004` crea el
administrador **`admin` / `AdminRetail2026!`**.

### Empezar de cero

```bash
docker compose -f ../../deploy/docker-compose.dev.yml down -v
docker compose -f ../../deploy/docker-compose.dev.yml up -d db
mvn clean spring-boot:run
```

El `clean` importa: Maven copia las migraciones a `target/classes/db/migration`
y no borra las que ya no existen. Si renombras una migración sin limpiar,
Flyway encuentra dos con la misma versión y se niega a arrancar.

---

## 2. Variables de entorno

Todas tienen un valor por omisión que funciona en local. Se definen en
[`src/main/resources/application.yml`](src/main/resources/application.yml) con la
forma `${VARIABLE:valor por omisión}`.

### Cómo se fijan

```bash
# bash: solo para este comando
DB_URL=jdbc:postgresql://otro:5432/bd mvn spring-boot:run

# bash: para toda la sesión
export JWT_SECRETO="..."
```

```powershell
# PowerShell
$env:JWT_SECRETO = '...'
mvn spring-boot:run
```

```bash
# Como argumento de Spring, sin tocar el entorno
mvn spring-boot:run -Dspring-boot.run.arguments=--app.jwt.secreto=...
```

En Docker van en `environment:` del servicio; ver `deploy/docker-compose.prod.yml`.

### Cuáles hay

| Variable | Por omisión | Para qué |
| --- | --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5433/retailstore_java` | Conexión. **5433**, no 5432: ver el README raíz |
| `DB_USER` / `DB_PASSWORD` | `retail` / `retail` | Credenciales |
| `PORT` | `8080` | Puerto de escucha |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Orígenes del panel y la tienda, separados por coma |
| **`JWT_SECRETO`** | valor de desarrollo | Clave HMAC. **Mínimo 32 bytes.** Obligatorio en producción |
| `JWT_COOKIE_SEGURA` | `false` | `true` en producción: marca `Secure` en la cookie de refresco |
| `ALMACEN_TIPO` | `local` | `local` (disco) o `r2` (Cloudflare) |
| `ALMACEN_DIRECTORIO` | `./datos/archivos` | Raíz en disco cuando el tipo es `local` |
| `ALMACEN_URL_PUBLICA` | `http://localhost:8080/archivos` | Prefijo de las URL de imagen |
| `R2_ENDPOINT` | — | `https://<cuenta>.r2.cloudflarestorage.com` |
| `R2_BUCKET`, `R2_ACCESS_KEY_ID`, `R2_SECRET_ACCESS_KEY` | — | Credenciales de R2 |
| `SUBIDA_MAX_ARCHIVO` | `12MB` | Tope del contenedor. El de negocio son 10 MB y lo aplica `ProcesadorImagen` |

> Con `ALMACEN_TIPO=r2` y credenciales incompletas, **la aplicación se niega a
> arrancar**. Es deliberado: aceptar subidas que van a fallar una por una,
> cuando alguien ya está usando el panel, es peor que no levantar.

---

## 3. Qué hay en cada carpeta

```
src/main/java/com/retailstore/api/
├── RetailStoreApplication.java   arranque
│
├── comun/          lo que usan todos los módulos
│   ├── auditoria/  quién creó y modificó cada fila, y bloqueo optimista
│   ├── error/      catálogo de códigos y el único traductor excepción → HTTP
│   ├── limite/     ventana deslizante de intentos (RN-068)
│   ├── paginacion/ el sobre de página del contrato
│   └── texto/      generación de slugs
│
├── config/         reloj inyectable, CORS, OpenAPI
│
├── seguridad/      identidad del panel: JWT, refresco rotativo, administradores
├── catalogo/       marcas, categorías, subcategorías y productos
├── archivo/        subida, conversión a WebP y almacenamiento
├── cupon/          descuentos
├── carrito/        carrito de la tienda
├── orden/          órdenes: consulta y máquina de estados
└── panel/          las cifras del tablero
```

Cada módulo repite la misma estructura interna:

| Subcarpeta | Responsabilidad |
| --- | --- |
| `dominio/` | Entidades JPA **con sus reglas dentro**. `Producto.activar()` sabe que necesita una imagen |
| `repositorio/` | Acceso a datos. Spring Data + `Specification` para los filtros combinables |
| `servicio/` | Orquestación y transacciones. No contiene reglas que pertenezcan a una entidad |
| `dto/` | Lo que entra y lo que sale. Nunca se expone una entidad |
| `web/` | Controladores delgados: reciben, validan forma y delegan |

### Por qué paquetes por funcionalidad y no por capa

Con `controller/`, `service/`, `repository/` en la raíz, tocar el carrito
significa abrir tres carpetas y buscar el archivo correcto en cada una.
Agrupando por funcionalidad, todo lo del carrito está junto y el acoplamiento
entre módulos se ve: si `catalogo` empieza a importar de `carrito`, salta a la
vista en los `import`.

---

## 4. Decisiones que atraviesan el código

**Las reglas de negocio viven en la entidad, no en el servicio.**
`Producto.cambiarPrecios()` rechaza un precio anterior menor que el precio;
`Cupon.descuentoPara()` acota el descuento al subtotal. El servicio orquesta:
busca, delega y guarda. La prueba de que funciona es que `ProductoTest` y
`CuponTest` no necesitan ni base de datos ni Spring.

**Un solo traductor de errores.** `ManejadorGlobalErrores` es el único sitio
donde una excepción se convierte en HTTP. Ningún controlador lleva `try/catch`.
Toda respuesta de error sale como `application/problem+json` con `code` y
`correlationId`.

**Las violaciones de restricción se traducen por nombre de restricción**
(`uq_producto_sku` → `DUPLICATE_SKU`), nunca analizando el texto del driver:
ese texto cambia entre versiones de PostgreSQL.

**El reloj es un bean.** Nadie llama a `Instant.now()`. La vigencia de un
cupón, la caducidad de un token y las fechas de auditoría pasan por el `Clock`
inyectado, y por eso una prueba puede fijar el instante en lugar de comparar
contra un margen de tolerancia.

**El ORM no es dueño del esquema.** `ddl-auto: validate`. Si el mapeo y las
tablas discrepan, la aplicación no arranca — y eso es lo que se quiere.

**Dinero siempre `BigDecimal` / `numeric(12,2)`**, comparado con `compareTo` y
nunca con `equals`: `100.00` y `100.0` son el mismo importe con distinta escala.

---

## 5. Pruebas

```bash
mvn test                                              # todas
mvn -Dtest=ProductoTest test                          # una clase
mvn -Dtest=ProductoTest#calculaDescuento test         # un método
```

**113 pruebas unitarias**, ninguna necesita base de datos. Se nombran por la
regla que cubren —`publicarSinImagenLanza..._RN009`— para que un fallo diga qué
regla de negocio se rompió, no solo qué método.

| Clase | Qué fija |
| --- | --- |
| `ProductoTest` | Descuento, precios inválidos, publicación con imagen, identidad |
| `CuponTest` | Vigencia, mínimo, usos, tope del descuento |
| `CarritoTest` | Un producto una línea, y que dos entidades sin id no se confundan |
| `CalculadoraCarritoTest` | Subtotales y el cupón que deja de aplicar sin desvincularse |
| `SlugTest` | Acentos, ñ, sufijos numéricos |
| `OrdenProductoTest` | Lista blanca de ordenamientos y desempate por id |
| `ProductoSpecsTest` | Escapado de comodines del usuario |
| `LimitadorIntentosTest` | Ventana deslizante, con el reloj adelantado a mano |
| `ServicioJwtTest` | Firma, emisor, caducidad y **audiencia** |
| `PoliticaContrasenaTest` | Longitud mínima y lista de bloqueo |
| `InspectorImagenTest` | Detección por bytes mágicos y lectura de cabecera |
| `ProcesadorImagenTest` | Conversión real a WebP con el binario nativo |
| `EstadoOrdenTest` | Transiciones válidas, estados terminales y ausencia de ciclos |

`ProcesadorImagenTest` ejecuta `cwebp` de verdad. Es más lento que un doble de
prueba y detecta lo único que de verdad puede fallar aquí: que el binario
nativo no esté disponible en la plataforma de despliegue.

### La prueba de extremo a extremo

```bash
python ../../pruebas/humo-api.py     # 123 comprobaciones
python ../../pruebas/humo-chat.py    # 57 comprobaciones
```

Necesitan la base y el backend en marcha, y son **idempotentes**: montan el
escenario que necesitan en vez de confiar en el que dejó la ejecución anterior.

Cubren lo que las unitarias no pueden: CORS, cookies, códigos HTTP reales,
transacciones, la rotación de tokens, la devolución de stock al cancelar una
orden, el WebSocket del chat y el rechazo de un PDF con contenido activo.

Han encontrado **seis** defectos invisibles para una prueba unitaria —todos
ellos porque solo existen con una transacción de verdad o con una conexión de
verdad—. Están listados en `docs/estado-y-brecha.md` §5.

---

## 6. Trampas conocidas

**Las migraciones aplicadas no se editan.** Flyway calcula el checksum del
archivo completo: cambiar un comentario de una migración ya aplicada rompe el
arranque. Para corregir algo se añade una migración nueva.

**El bean de CORS tiene que llamarse `corsConfigurationSource`.** Spring
Security lo busca por ese nombre exacto. Con cualquier otro, la aplicación
arranca sin quejarse y toda preflight responde 403.

**`@ExceptionHandler(Exception.class)` se traga `AccessDeniedException`.** Hay
que manejarla explícitamente, o ningún 403 llega al cliente. Ya está hecho; no
lo deshagas.

**Una revocación seguida de una excepción se deshace.** Al detectar la
reutilización de un token de refresco hay que revocar la familia *y* rechazar
la petición; si ambas cosas van en la misma transacción, el rollback anula la
revocación y la sesión robada sigue viva. Por eso existe `RevocacionInmediata`,
con `REQUIRES_NEW`.

**Los nombres de método derivados de Spring Data usan palabras inglesas.**
`findConDetallePorId` no compila: el separador es `By`. Es la única excepción a
ADR-0011 y se resuelve con `findConDetalleById` o con `@Query`.

**Una actualización tiene que volcar antes de mapear.** `actualizadoPor`,
`actualizadoEn` y `version` los escribe el listener de auditoría cuando
Hibernate vuelca, no al mutar la entidad: un DTO construido antes del volcado
devuelve los valores anteriores, y el panel muestra «modificado por sistema»
justo después de que alguien lo modificara. Por eso los servicios volcan antes
de construir la respuesta.

**Pero volcar es `flush()`, no `saveAndFlush()`.** Sobre una entidad ya
gestionada, `save()` hace `merge`, y `merge` persiste una *copia* de los hijos
nuevos: el objeto que tienes en la mano se queda sin id, y lo que devuelvas o
difundas llevará `id: null`. `save()` es **solo** para entidades realmente
nuevas —y ahí es obligatorio, porque `flush()` a secas no escribe algo que
nadie ha metido en la sesión.

**Difundir por WebSocket dentro de la transacción provoca bloqueos
optimistas.** Quien recibe el mensaje responde antes del commit, y su respuesta
lee una versión que todavía no se ha escrito. Va en `afterCommit`, vía
`TransactionSynchronizationManager` (RN-083). Está en `RegistroSesionesWs`.

**Vaciar y reconstruir una colección con `orphanRemoval` rompe los índices
únicos.** Hibernate ejecuta los `INSERT` antes que los `DELETE` en el mismo
volcado: reenviar la misma fila choca. Hay que reconciliar —quitar lo que se
va, mover lo que se queda, añadir lo que llega—, como en
`Producto.reemplazarImagenes`.

**`@SQLRestriction` no se aplica a `find(id)`.** Filtra las consultas HQL y de
criterios, pero al buscar por clave primaria Hibernate va directo a la fila. Por
eso los servicios comprueban `estaEliminado()` después de un `findById`.

**No se pueden traer dos colecciones `List` en el mismo grafo.** Da
`MultipleBagFetchException`; una de las dos tiene que ser `Set`. Por eso
`Mensaje.adjuntos` es un `LinkedHashSet` y no una lista.
