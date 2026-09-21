# Java 21 / Spring Boot 3.5

La realización en Java del [estándar de backend](estandar-backend.md). Léelo
primero: aquel dice *qué* regla rige; este dice *cómo* se escribe. Cuando este
documento muestra código, es código que está en el repositorio, no un ejemplo
inventado.

---

## 1. Stack real

Verificable en [`backend/java/pom.xml`](../../backend/java/pom.xml).

| | | Por qué |
| --- | --- | --- |
| Java | **21 (LTS)** | Records, `switch` con patrones, texto en bloque, hilos virtuales disponibles si hicieran falta |
| Spring Boot | **3.5.14** | `ProblemDetail` nativo (RFC 7807), Jakarta EE 10, soporte de `Specification.allOf` |
| Construcción | Maven | |
| Web | `spring-boot-starter-web` (MVC, Tomcat) | |
| Persistencia | `spring-boot-starter-data-jpa` (Hibernate 6) | Specifications tipadas, `@EntityGraph` |
| Base de datos | **PostgreSQL 17** (`postgresql`, ámbito `runtime`) | [ADR-0004](../adr/0004-postgresql-sobre-sqlite.md) |
| Migraciones | `flyway-core` + `flyway-database-postgresql` | El esquema es de Flyway, no del ORM |
| Validación | `spring-boot-starter-validation` (Jakarta Bean Validation) | |
| Observabilidad | `spring-boot-starter-actuator` | Solo `health` expuesto |
| Documentación | `springdoc-openapi-starter-webmvc-ui` 2.8.9 | `/swagger`, `/v3/api-docs` |
| Pruebas | `spring-boot-starter-test` (JUnit 5, Mockito, AssertJ, MockMvc) | Ver [pruebas.md](pruebas.md) |
| Pool | HikariCP | Viene con Boot; no se configura porque los valores por omisión sirven para este tráfico |

Lo que **no** está y es una ausencia deliberada:

| Ausente | Razón |
| --- | --- |
| MapStruct | Dos agregados, mapeo manual explícito ([estándar](estandar-backend.md) §3) |
| Lombok | Los `record` cubren los DTO; las entidades JPA necesitan getters controlados y un constructor protegido. Lombok añadiría un procesador de anotaciones para ahorrar getters que el IDE genera |
| Spring Security | No hay aún autenticación. Ver [autenticacion.md](autenticacion.md) y la deuda D-2 |
| Testcontainers | No hay pruebas de integración. Ver [pruebas.md](pruebas.md) §4 |
| `mvnw` (wrapper) | El `Dockerfile` usa la imagen `maven:3.9-eclipse-temurin-21`. **Contrapartida real:** una compilación fuera de Docker depende del Maven instalado en la máquina. Añadir el wrapper es barato y está pendiente |

---

## 2. Estructura del proyecto

```text
backend/java/
├── pom.xml
├── Dockerfile                 build desde la RAÍZ del repo (necesita database/migrations)
└── src/
    ├── main/
    │   ├── java/com/retailstore/api/
    │   │   ├── RetailStoreApplication.java
    │   │   ├── catalog/{domain,dto,mapper,repository,service,web}
    │   │   ├── cart/{domain,dto,mapper,repository,service,web}
    │   │   ├── common/{error,pagination}
    │   │   └── config/
    │   └── resources/
    │       └── application.yml
    └── test/java/com/retailstore/api/
        ├── catalog/{domain,service,web}
        └── cart/{domain,service}

database/migrations/           fuera del módulo: el pom las copia al classpath
├── V001__catalog_schema.sql
├── V002__catalog_seed.sql
└── V003__cart_schema.sql
```

El árbol de pruebas **refleja el de producción**. `ProductServiceTest` vive en
`catalog/service`, junto a la clase que prueba. Un árbol `unit/` + `integration/`
separado por tipo obliga a buscar la prueba de una clase en otro sitio, y el
día que se añadan pruebas de integración se resolverá con una convención de
nombre (`*IT.java`), no con un directorio paralelo.

### Las migraciones viven fuera del módulo Java

```xml
<resource>
    <directory>${project.basedir}/../../database/migrations</directory>
    <targetPath>db/migration</targetPath>
    <includes><include>*.sql</include></includes>
</resource>
```

El esquema es del **repositorio**, no del backend. Por eso está en
`database/migrations/` en la raíz y Maven lo copia al classpath al empaquetar.
El `Dockerfile` tiene como contexto de compilación la raíz del repositorio
precisamente por esto:

```bash
docker build -f backend/java/Dockerfile -t retail-store-api-java .
```

Contrapartida: el `pom.xml` depende de una ruta relativa fuera del módulo, y
compilar `backend/java/` aislado del repositorio no funciona. Es un costo
aceptable frente a la alternativa —duplicar el SQL dentro del módulo— que
garantiza que las dos copias se desincronicen.

---

## 3. Controladores

```java
@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Productos")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {   // por constructor
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "Listado paginado con búsqueda, filtros y orden")
    public PageResponse<ProductSummaryResponse> search(
            @Valid @ParameterObject ProductSearchRequest request) {
        return productService.search(request);
    }
}
```

Tres detalles que no son evidentes:

**`@ParameterObject` con un `record` de parámetros.** Los nueve parámetros de
consulta de `GET /products` van agrupados en `ProductSearchRequest` en lugar de
nueve `@RequestParam`. Así los valores por omisión y la validación viven en un
solo sitio, la firma del controlador no crece, y `@ParameterObject` de springdoc
sigue documentando cada parámetro por separado en Swagger. Sin esa anotación,
Swagger mostraría un único parámetro de tipo objeto y el contrato se leería mal.

**`@Valid` sobre el objeto de consulta, no solo sobre el cuerpo.** Un
`pageSize=500` se rechaza antes de llegar al servicio.

**El tipo de retorno es el DTO, no `ResponseEntity`.** Spring serializa y
responde `200`. `ResponseEntity` solo aparece cuando hay algo que decir sobre la
respuesta que el cuerpo no expresa —`201` con `CartController.create()`— y así
su presencia significa algo en lugar de ser ruido en todas las firmas.

Una consecuencia de que el DTO viaje desnudo, sin envoltorio `{ "data": ... }`:
el listado devuelve `PageResponse<T>` y el detalle devuelve el objeto directo.
Es lo que declara [`contracts/openapi.yaml`](../../contracts/openapi.yaml) y lo
que el frontend espera. Añadir un envoltorio uniforme tendría sentido si
hubiera metadatos que acompañaran a toda respuesta; hoy no los hay y el
envoltorio sería una capa sin contenido.

---

## 4. DTOs

```java
public record ProductSearchRequest(
        @Size(max = 100, message = "La búsqueda admite hasta 100 caracteres.") String search,
        @Size(max = 100, message = "Categoría inválida.") String category,
        @DecimalMin(value = "0", message = "El precio mínimo no puede ser negativo.") BigDecimal minPrice,
        @DecimalMin(value = "0", message = "El precio máximo no puede ser negativo.") BigDecimal maxPrice,
        Boolean inStock,
        Boolean featured,
        String sort,
        @Min(value = 1, message = "La página empieza en 1.") Integer page,
        @Min(value = 1, message = "El tamaño de página mínimo es 1.")
        @Max(value = MAX_PAGE_SIZE, message = "El tamaño de página máximo es 48.") Integer pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 12;
    public static final int MAX_PAGE_SIZE = 48;

    public ProductSearchRequest {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
    }
}
```

**El constructor compacto corre antes que Bean Validation.** Spring construye el
record enlazando los parámetros y *después* valida la instancia. Por eso los
valores por omisión se asignan ahí: si se aplicaran en el servicio, un
`pageSize` nulo llegaría a la validación como nulo y habría que decidir si
`@Min(1)` lo rechaza (no lo hace: las anotaciones de rango ignoran `null`, y esa
asimetría es precisamente la fuente de confusión que se evita).

**Las envolturas (`Integer`, `Boolean`) son deliberadas, no un descuido.** Con
`int pageSize` no habría forma de distinguir «no se envió el parámetro» de «se
envió 0». `Boolean inStock` distingue tres estados: no filtrar, filtrar por con
stock, filtrar por sin stock — y `Boolean.TRUE.equals(...)` en el servicio deja
explícito que solo el primero se implementa hoy.

**Los mensajes están en español y son del usuario final.** No dicen
«`pageSize` must be less than or equal to 48»: dicen «El tamaño de página máximo
es 48.» El frontend los muestra tal cual bajo el campo.

**`MAX_PAGE_SIZE` es una constante usable en la anotación** porque
`@Max` exige un `long` constante en compilación. Eso obliga a repetir el `48` en
el texto del mensaje: las anotaciones no interpolan constantes. Es la razón por
la que este valor no puede ser configuración sin cambiar de mecanismo —una
restricción real del framework, no una decisión—.

---

## 5. Servicios

```java
@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public PageResponse<ProductSummaryResponse> search(ProductSearchRequest request) {
        validatePriceRange(request);                                    // RN-008

        Pageable pageable = PageRequest.of(
                request.page() - 1,                                     // RN-007: 1-based afuera
                request.pageSize(),
                ProductSort.fromParam(request.sort()).toSort());         // RN-005 + RN-006

        Page<Product> page = productRepository.findAll(buildSpecification(request), pageable);
        return PageResponse.from(page, CatalogMapper::toSummary);
    }
}
```

### El modo transaccional se declara en la clase

`ProductService` y `CategoryService` son `@Transactional(readOnly = true)` a
nivel de clase: solo leen, y un método nuevo hereda el modo correcto.
`CartService` es `@Transactional` (escritura) a nivel de clase y marca `get()`
como `@Transactional(readOnly = true)`.

Se usa `org.springframework.transaction.annotation.Transactional`, no la de
Jakarta: es la que entiende `readOnly` y las propagaciones de Spring.

### La página es 1-based hacia afuera, 0-based hacia dentro

```text
   URL:  ?page=1  ?page=2  ?page=3
            │        │        │
            ▼        ▼        ▼
   Pageable: 0        1        2       ← request.page() - 1
            │        │        │
            ▼        ▼        ▼
   PageResponse: 1    2        3       ← page.getNumber() + 1
```

La conversión ocurre en exactamente dos sitios: `ProductService.search()` al
entrar y `PageResponse.from()` al salir. Que sea 1-based hacia afuera es una
decisión de usabilidad —`?page=1` es la primera página, como cualquiera espera,
y el `?page=0` de Spring Data confunde a quien escribe la URL a mano—. La
contrapartida es este `±1`, que es exactamente el tipo de error que se cuela
cuando la conversión está repartida: por eso está encapsulada y probada
(`searchTranslatesOneBasedPageAndSortIntoPageable`).

### El reloj es una dependencia, no una llamada estática

```java
@Configuration
public class ClockConfig {
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

`CartPricingCalculator` y `CartService` reciben un `Clock` por constructor y
llaman a `clock.instant()`. Nunca `Instant.now()`.

La razón es directa: la vigencia de un cupón depende del instante actual, y con
`Instant.now()` la única forma de probar «este cupón ya caducó» sería esperar o
manipular la fecha del sistema. Con un `Clock` inyectado, la prueba fija
`Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneOffset.UTC)` y el
comportamiento es determinista para siempre.

Hay una excepción por corregir: `Product.@PrePersist` llama a `Instant.now()`
directamente, porque un *callback* de JPA no recibe dependencias inyectadas. La
salida correcta es la auditoría de Spring Data (`@CreatedDate`, `@LastModifiedDate`
y un `DateTimeProvider` construido sobre el mismo `Clock`); está anotado como
deuda D-6 en el [estándar](estandar-backend.md) §13.

### La lista blanca de ordenamientos

```java
public enum ProductSort {
    NEWEST("newest", Sort.by(DESC, "createdAt")),
    PRICE_ASC("price_asc", Sort.by(ASC, "price")),
    ...

    /** El id como desempate mantiene estable la paginación cuando hay valores repetidos. */
    public Sort toSort() {
        return sort.and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
```

Un `enum` y no un `Map<String, Sort>` porque el conjunto es cerrado y conocido
en compilación: añadir un orden es añadir una constante, y el compilador obliga
a darle su `Sort`. El cliente envía `price_asc`, nunca un nombre de campo ni de
columna.

`toSort()` añade siempre `id ASC`. Sin ese desempate, `ORDER BY price` sobre
diez productos con el mismo precio no define un orden total, y PostgreSQL es
libre de devolverlos en distinto orden en cada consulta: el producto de la fila
12 puede aparecer en la página 1 y también en la 2, o en ninguna. Es un fallo
que no se reproduce en desarrollo con datos de ejemplo bien repartidos y que
aparece en producción en cuanto hay precios repetidos.

---

## 6. Repositorios y Specifications

```java
public interface ProductRepository
        extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // @EntityGraph trae la categoría en la misma consulta: evita el N+1 al mapear cada producto.
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findBySlugAndActiveTrue(String slug);

    @EntityGraph(attributePaths = "category")
    List<Product> findTop4ByCategoryIdAndActiveTrueAndIdNotOrderByRatingAvgDescIdAsc(
            Long categoryId, Long excludedId);
}
```

**Sobrescribir `findAll(Specification, Pageable)` solo para anotarlo** es el
truco que evita el N+1 del listado. Sin él, la consulta trae los productos y
después una consulta por producto para su categoría: 12 productos son 13
consultas. Con él, es una.

**`findTop4By...OrderByRatingAvgDescIdAsc`** es un nombre largo y es lo correcto
aquí. Expresa el tope, el filtro y el orden —con su desempate por `id`— sin una
línea de implementación. Si el criterio de «relacionado» se sofisticara (misma
marca, rango de precio parecido), este nombre dejaría de servir y pasaría a ser
una `@Query` o una Specification; hoy no lo es.

### Filtros componibles

```java
public final class ProductSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    public static Specification<Product> matchesText(String term) {
        String pattern = "%" + escapeLike(term.trim().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.<String>get("name")), pattern, LIKE_ESCAPE),
                cb.like(cb.lower(root.<String>get("description")), pattern, LIKE_ESCAPE));
    }

    /** Evita que % y _ escritos por el usuario actúen como comodines. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
```

**Por qué Specifications y no una `@Query` con `(:param IS NULL OR ...)`.** El
listado tiene seis filtros opcionales: 64 combinaciones. Con una consulta JPQL
única llena de `IS NULL OR`, PostgreSQL recibe siempre el mismo SQL con
parámetros nulos y el plan que elige tiene que servir para todas las
combinaciones a la vez: normalmente acaba siendo un escaneo. Con Specifications,
el SQL emitido contiene **solo los predicados que el usuario pidió**, y el
planificador puede usar `ix_products_active_featured` o `ix_products_price`
según el caso.

Contrapartida honesta, y es la razón de la deuda D-1: una Specification es
código que construye un árbol de criterios, y **una prueba unitaria con un
repositorio simulado no ejecuta nada de ese árbol**. Las pruebas actuales
verifican que se construye un `Specification` y se pasa al repositorio; no
verifican que el SQL resultante sea correcto ni que filtre lo que debe. Eso solo
se demuestra contra PostgreSQL ([pruebas.md](pruebas.md) §4).

**`escapeLike` y el tercer argumento de `cb.like`.** El escape solo funciona si
se declara el carácter de escape en la llamada: `cb.like(expr, pattern)` sin
tercer argumento ignoraría las barras invertidas y un usuario que busque `50%`
seguiría provocando un comodín. Los dos van juntos siempre.

**Un límite conocido:** `LIKE '%término%'` con comodín inicial no puede usar un
índice B-tree, y `description` es `text`. Con el catálogo de esta demo
(decenas de productos) es irrelevante. Con decenas de miles, la salida es el
índice GIN de búsqueda de texto completo que PostgreSQL trae de serie —y es una
de las razones por las que se eligió PostgreSQL
([ADR-0004](../adr/0004-postgresql-sobre-sqlite.md))—. Se deja anotado aquí en
lugar de resolverse antes de tener el problema.

### El sobre de paginación

```java
public record PageResponse<T>(List<T> items, int page, int pageSize,
                              long totalItems, int totalPages) {

    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
```

`PageResponse` existe porque el `Page` de Spring Data **no es un contrato de
API**: su serialización JSON incluye `pageable`, `sort`, `first`, `last`,
`numberOfElements` y un `content` en lugar de `items`, y su forma ha cambiado
entre versiones de Spring Data. Exponerlo directamente es acoplar el contrato
público a los detalles internos de una librería, y es exactamente el tipo de
acoplamiento que aparece como una ruptura del frontend tras una actualización de
dependencias.

---

## 7. Dominio

Las entidades llevan comportamiento, no solo campos.

```java
/** Porcentaje de descuento redondeado, o null si no hay un precio "antes" mayor al actual. */
public Integer discountPercent() {
    if (compareAtPrice == null || compareAtPrice.compareTo(price) <= 0) {
        return null;
    }
    return compareAtPrice.subtract(price)
            .multiply(BigDecimal.valueOf(100))
            .divide(compareAtPrice, 0, RoundingMode.HALF_UP)
            .intValue();
}
```

`compareTo` y no `equals` (RN-004 y §8 del [estándar](estandar-backend.md)), y
`divide` con escala y modo explícitos: sin ellos, `129.90 / 159.90` lanza
`ArithmeticException` por ser periódico. Es el error que aparece en producción
con el primer precio que no divide exacto.

### Convenciones de las entidades JPA

| Convención | Por qué |
| --- | --- |
| Constructor `protected` sin argumentos con el comentario `// requerido por JPA` | Hibernate lo necesita para instanciar por reflexión. `protected` en vez de `public` impide que el código de aplicación cree entidades a medio construir |
| Constructor público con los campos obligatorios | Una entidad recién creada es válida. No hay un camino que produzca un `Product` sin precio |
| Getters, sin setters salvo donde el dominio lo exige | `CartItem.setQuantity` existe porque cambiar la cantidad **es** una operación del dominio. No hay `Product.setPrice`: el precio se cambiará por un caso de uso de administración, no por un setter |
| Sin `@Id` con setter | El id lo asigna la base. Las pruebas que necesitan uno lo inyectan por reflexión y dejan constancia (`// Los campos @Id no tienen setter a proposito`) |
| Sin `equals`/`hashCode` generados | Ver abajo |

### El caso que vale la pena leer entero: identidad de entidades

`Cart.addOrIncrement` tiene que decidir si un producto ya está en el carrito
(RN-020). La implementación **no** usa `equals`:

```java
private static boolean sameProduct(Product a, Product b) {
    if (a == b)               return true;
    if (a == null || b == null) return false;
    Long idA = a.getId();
    return idA != null && idA.equals(b.getId());
}
```

Por qué no `a.getId().equals(b.getId())`: lanza `NullPointerException` si alguno
no está persistido todavía. Y por qué no `Objects.equals(a.getId(), b.getId())`,
que es el arreglo obvio: pasaría a tratar como **iguales** dos productos
distintos sin id, porque `null == null`. Con eso, agregar dos productos nuevos
al carrito sumaría cantidad sobre una sola línea en vez de crear dos.

La regla correcta es la que está escrita: son el mismo producto si son la misma
instancia, o si ambos están persistidos y comparten id. **Sin id no se puede
afirmar que sean el mismo.** Es la razón por la que las entidades JPA no llevan
un `equals`/`hashCode` generado por el IDE sobre todos los campos: ese `equals`
cambia de resultado cuando la entidad se persiste, lo que rompe cualquier `Set`
o `Map` que la contuviera.

---

## 8. Manejo de errores

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String TYPE_BASE = "https://retailstore.dev/errors/";

    @ExceptionHandler(InsufficientStockException.class)
    public ProblemDetail handleInsufficientStock(InsufficientStockException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "insufficient-stock",
                "Stock insuficiente", ex.getMessage());
        problem.setProperty("availableStock", ex.getAvailableStock());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Error no controlado", ex);      // una sola vez, con la traza completa
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Error interno",
                "Ocurrió un error inesperado. Inténtalo nuevamente.");
    }
}
```

**`ProblemDetail` es nativo de Spring 6**, no una clase propia. Eso significa
que el `Content-Type` correcto (`application/problem+json`), la forma de RFC
7807 y la extensión por propiedades vienen resueltas por el framework, y que
`spring.mvc.problemdetails.enabled: true` hace que **los errores del propio
framework** —un JSON mal formado, un método HTTP no soportado— salgan con la
misma forma que los nuestros. Un cliente ve un solo formato de error, venga de
donde venga.

### Por qué extiende `ResponseEntityExceptionHandler`

Es lo que permite sobrescribir el tratamiento de las excepciones del framework
en lugar de dejarlas con la forma por omisión:

```java
@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex, HttpHeaders headers,
        HttpStatusCode status, WebRequest request) {
    Map<String, List<String>> errors = new LinkedHashMap<>();
    ex.getBindingResult().getFieldErrors().forEach(fieldError -> addFieldError(errors, fieldError));
    return handleExceptionInternal(ex, validationProblem(errors), headers, status, request);
}
```

Hay **dos** manejadores de validación y la diferencia importa:

| Excepción | Cuándo la lanza Spring |
| --- | --- |
| `MethodArgumentNotValidException` | `@Valid` sobre un `@RequestBody` o sobre un objeto de parámetros enlazado |
| `HandlerMethodValidationException` | Validación sobre parámetros sueltos del método (Spring 6.1+) |

Cubrir solo la primera deja los errores de la segunda con la forma por omisión
del framework, y entonces el frontend recibe dos formatos distintos para el
mismo tipo de fallo. Los dos producen la misma estructura `errors`.

**El mapa de errores es un `LinkedHashMap`**: preserva el orden de los campos
tal como el binding los reportó, de modo que la respuesta es estable entre
ejecuciones. Un `HashMap` daría un orden arbitrario y toda prueba que compare la
respuesta completa sería intermitente.

**Un fallo de conversión se reescribe:**

```java
String message = fieldError.isBindingFailure() ? "Formato inválido." : fieldError.getDefaultMessage();
```

`minPrice=abc` no produce un `@DecimalMin` fallido sino un fallo de enlace, y su
mensaje por omisión menciona el tipo de destino y a veces la clase de la
excepción. Ese texto no se muestra a un usuario ni se filtra a un atacante.

### La advertencia sobre `@ExceptionHandler(Exception.class)`

Capturar `Exception` es correcto **hoy**, porque no hay nada que capturar por
debajo que merezca otro trato. En cuanto se añada Spring Security dejará de
serlo: `AccessDeniedException` y `AuthenticationException` caerían aquí y se
convertirían en `500`, ocultando un `403` o un `401` legítimo y llenando el log
de errores de una denegación normal.

**Antes de añadir Spring Security hay que añadir manejadores específicos para
esas dos excepciones.** Está anotado como deuda D-3 en el
[estándar](estandar-backend.md) §13, y se menciona aquí porque es el sitio donde
se va a tocar.

---

## 9. Configuración

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/retailstore_java}
    username: ${DB_USER:retail}
    password: ${DB_PASSWORD:retail}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate   # el esquema lo gobierna Flyway; Hibernate solo verifica el mapeo
    properties:
      hibernate:
        jdbc:
          time_zone: UTC
  flyway:
    locations: classpath:db/migration
  mvc:
    problemdetails:
      enabled: true

server:
  port: ${PORT:8080}
  forward-headers-strategy: framework   # detrás de Caddy
  error:
    include-stacktrace: never
    include-message: never

management:
  endpoints:
    web:
      base-path: /
      exposure:
        include: health

app:
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173}
```

Línea por línea, lo que no es obvio:

| Clave | Por qué está |
| --- | --- |
| `open-in-view: false` | Ver [estándar](estandar-backend.md) §7. Es el valor que Boot **no** trae por omisión |
| `ddl-auto: validate` | El ORM nunca es dueño del esquema |
| `hibernate.jdbc.time_zone: UTC` | Sin esto, el driver usa la zona de la JVM y un contenedor con `TZ` distinta escribe instantes desplazados |
| `problemdetails.enabled: true` | Hace que los errores del framework salgan en RFC 7807 |
| `forward-headers-strategy: framework` | Detrás de Caddy, sin esto la aplicación cree que el esquema es `http` y construye URLs absolutas mal |
| `include-stacktrace: never` | Segunda barrera por si algo esquiva el advice |
| `management.base-path: /` | `/health` en la raíz: es la URL exacta que consulta el `healthcheck` del compose de producción (`wget -qO- http://localhost:8080/health`) |
| `exposure.include: health` | Solo `health`. `env`, `beans` o `configprops` expuestos revelarían la configuración, secretos incluidos |

### Propiedades tipadas

```java
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
```

`List.copyOf` deja la lista realmente inmutable: un `record` con una `List`
dentro es inmutable en la referencia, no en el contenido, y quien recibiera el
bean podría modificar la política de CORS en caliente.

El valor por omisión cuando falta es la **lista vacía**, es decir, ningún origen
permitido. Es la elección segura: un fallo de configuración deja la API sin
CORS, que se detecta al instante en el navegador, en vez de dejarla abierta a
todos, que no se detecta nunca.

Se habilita con `@EnableConfigurationProperties(CorsProperties.class)` en
`WebConfig` y no con `@ConfigurationPropertiesScan`: quien lee `WebConfig` ve de
dónde vienen sus valores sin buscar en otro archivo.

---

## 10. Construcción y ejecución

```bash
# Desarrollo local (requiere PostgreSQL 17 en localhost:5432)
docker compose -f deploy/docker-compose.dev.yml up -d db
mvn -f backend/java/pom.xml spring-boot:run

# Pruebas
mvn -f backend/java/pom.xml test

# Empaquetado + imagen (contexto de build = raíz del repo)
docker build -f backend/java/Dockerfile -t retail-store-api-java .
```

Hoy `mvn test` basta porque **solo hay pruebas unitarias**. Cuando existan
pruebas de integración se engancharán a `verify` mediante Failsafe con el
sufijo `*IT`, y entonces el comando de la puerta de calidad pasa a ser
`mvn verify`: `test` por sí solo daría verde sin haber ejecutado ninguna. Ver
[pruebas.md](pruebas.md) §4.

### Notas de la imagen

```dockerfile
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"
```

| Decisión | Razón |
| --- | --- |
| `jre-alpine`, no `jdk` | La imagen final no compila; el JDK sobra y es superficie de ataque |
| Usuario no root | Un proceso comprometido no es root dentro del contenedor |
| `MaxRAMPercentage=75` | La JVM por omisión mira la memoria del **host**, no el límite del contenedor, y se autoconfigura para un heap que no cabe. El resultado es el `OOMKilled` que el orquestador reporta sin explicación |
| `UseSerialGC` | Con uno o dos vCPU y un heap pequeño, G1 gasta más en coordinación de lo que ahorra en pausas. Conviven Next.js, PostgreSQL y esta API en un VPS de 2 GB |
| Dependencias antes que el código | `dependency:go-offline` en una capa propia: cambiar una línea de Java no vuelve a descargar todo Maven |

---

## 11. Resumen: dónde vive cada regla del estándar

| Regla del [estándar](estandar-backend.md) | Realización en este stack |
| --- | --- |
| Inyección por constructor | Constructores explícitos, sin `@Autowired` en ningún archivo |
| Entidades fuera del límite HTTP | `record` en `*/dto/`, mapeo estático en `*/mapper/` |
| Validación estructural declarativa | Bean Validation en los `record` de petición |
| Validación de negocio en el servicio | `ProductService.validatePriceRange`, `CartService.requireStock` |
| Límite transaccional en el servicio | `@Transactional` de Spring, en la clase |
| Manejo central de errores | `GlobalExceptionHandler` + `ProblemDetail` |
| Paginación acotada | `ProductSearchRequest` + `PageResponse` |
| Orden total | `ProductSort.toSort()` con desempate por `id` |
| Sin N+1 | `@EntityGraph` explícito + `open-in-view: false` |
| El ORM no es dueño del esquema | Flyway + `ddl-auto: validate` |
| Tiempo comprobable | `Clock` como bean, `Clock.fixed` en pruebas |
| Sin secretos versionados | `${VAR:valor-dev}` en `application.yml` |
