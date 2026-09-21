# Pruebas

Qué se prueba, cómo, y —sobre todo— **qué no está probado hoy**. Un documento
de pruebas que solo describe el plan ideal es un documento que nadie usa para
decidir si un cambio es seguro.

Complementa el [estándar de backend](estandar-backend.md) §12 (definición de
terminado) y [java-spring-boot.md](java-spring-boot.md).

---

## 1. La pirámide real, hoy

```text
        ╱ Integración ╲      0 pruebas   ← NO EXISTE. Es la deuda D-1
       ╱────────────────╲
      ╱  Rebanada web    ╲   4 pruebas   @WebMvcTest sobre ProductController
     ╱────────────────────╲
    ╱   Unitarias puras    ╲  25 pruebas  sin Spring, sin base de datos
   ╱────────────────────────╲
```

**29 pruebas en total**, todas en `mvn test`, todas en menos de diez segundos.
Ninguna necesita Docker, red ni una base de datos.

| Archivo | Pruebas | Qué demuestra |
| --- | --- | --- |
| `catalog/domain/ProductTest` | 4 | El cálculo del descuento y sus tres casos nulos; `hasStock` |
| `catalog/service/ProductSortTest` | 4 | Lista blanca de orden, valor por omisión, desempate por `id` |
| `catalog/service/ProductServiceTest` | 3 | Traducción de página 1-based, rechazo del rango invertido, `404` por slug |
| `catalog/web/ProductControllerTest` | 4 | Rutas, forma del sobre paginado, forma de los errores |
| `cart/domain/CouponTest` | 5 | Descuento por porcentaje y fijo, tope al subtotal, vigencia, usos |
| `cart/service/CartPricingCalculatorTest` | 4 | Subtotal, aplicación e ignorado del cupón, carrito vacío |
| `cart/service/CartServiceTest` | 5 | Agregar, acumular contra stock, rechazar exceso, ítem inexistente, cupón inexistente |

Esta pirámide está **invertida respecto a lo que este proyecto necesita**, y la
razón es concreta: el catálogo se apoya en Specifications de JPA, en Flyway y en
PostgreSQL, y ninguna de las tres cosas se ejecuta en una prueba unitaria. §4
explica exactamente qué queda sin demostrar.

---

## 2. Pruebas unitarias

Sin base de datos, sin HTTP, sin contexto de Spring. Los colaboradores son
dobles de prueba. Si una prueba «unitaria» necesita un contenedor, es una prueba
de integración con la etiqueta equivocada.

### Las dos formas que se usan

**Dominio puro — sin dobles, porque no hay colaboradores.**

```java
class ProductTest {

    @Test
    void discountPercentIsRoundedFromCompareAtPrice() {
        Product product = productWithPrices("129.90", "159.90");

        assertThat(product.discountPercent()).isEqualTo(19); // 30 / 159.90 = 18.76 %
    }
}
```

Esta es la prueba más barata que existe: construye un objeto y comprueba un
método. Que `Product.discountPercent()` sea comprobable así es consecuencia
directa de que la regla viva en el dominio y no en el servicio
([estándar](estandar-backend.md) §1).

**Servicio — repositorios simulados con Mockito.**

```java
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock private CartRepository cartRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CouponRepository couponRepository;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneOffset.UTC);
        cartService = new CartService(cartRepository, productRepository, couponRepository,
                new CartPricingCalculator(clock), clock);
        ...
    }
}
```

Dos decisiones visibles ahí:

* **El servicio se construye con `new`**, no con `@InjectMocks` ni con un
  contexto de Spring. Es posible porque la inyección es por constructor
  ([estándar](estandar-backend.md) §2); si hubiera campos `@Autowired`, esta
  línea no existiría y habría que levantar un contexto para probar una regla de
  negocio.
* **`CartPricingCalculator` es real, no un doble.** Es una función pura sobre el
  carrito; simularlo obligaría a declarar el resultado esperado del cálculo en
  cada prueba y dejaría de comprobarse que el servicio y el calculador encajan.
  Se simula lo que tiene E/S; no se simula la aritmética.

### El reloj fijo

```java
private final Clock fixedClock = Clock.fixed(Instant.parse("2026-09-21T12:00:00Z"), ZoneOffset.UTC);
private final CartPricingCalculator calculator = new CartPricingCalculator(fixedClock);
```

Todas las pruebas que tocan vigencia de cupones fijan el reloj. Sin eso,
`isUsableFalseWhenExpired` dependería de la fecha del sistema y se rompería sola
el día que las fechas del seed quedasen fuera de rango. Es la contrapartida de
haber inyectado un `Clock` (ver [java-spring-boot.md](java-spring-boot.md) §5) y
es donde se cobra.

`CouponTest` sí usa `Instant.now()`: construye los cupones con desplazamientos
relativos (`-30 días`, `+90 días`), lo que lo hace estable sin fijar el reloj.
Es aceptable, pero **el criterio general es fijar el reloj**: un desplazamiento
relativo deja de ser evidente en cuanto la regla involucra más de una fecha.

### El olor que hay en estas pruebas, y por qué está

`CouponTest`, `CartPricingCalculatorTest` y `CartServiceTest` asignan campos por
reflexión:

```java
private static void setField(Object target, String field, Object value) {
    Field f = Coupon.class.getDeclaredField(field);
    f.setAccessible(true);
    f.set(target, value);
}
```

Es un olor y hay que llamarlo por su nombre. La causa es legítima —`Coupon` no
tiene constructor público y los `@Id` no tienen setter, a propósito
([java-spring-boot.md](java-spring-boot.md) §7)— pero el efecto es que estas
pruebas **dependen de los nombres de los campos privados**. Renombrar
`minSubtotal` compila y rompe las pruebas en ejecución, con un mensaje de
reflexión que no dice qué pasó.

La salida correcta es un *object mother* por agregado, en `src/test/java`, con
la reflexión encapsulada en un solo lugar:

```java
CouponFixture.percent(10).minSubtotal("50.00").validFor(90).build()
ProductFixture.valid().price("129.90").stock(3).build()
CartFixture.persisted().with(product, 2).build()
```

Así una prueba declara solo el campo del que trata —que es lo que hace legible
su intención— y un renombrado rompe un archivo en lugar de doce. Está pendiente;
con 29 pruebas todavía no duele, y a las 80 sí.

### Nomenclatura

Las pruebas actuales usan `metodo_escenario_expectativa` en inglés, en camelCase:

```text
addItemRejectsQuantityAboveAvailableStock
searchRejectsInvertedPriceRangeBeforeHittingTheDatabase
discountPercentIsNullWhenCompareAtPriceIsNotHigher
```

**Lo que falta:** el identificador de la regla. La convención que se adopta de
aquí en adelante es añadir el sufijo `_RN###`:

```text
addItemRejectsQuantityAboveAvailableStock_RN022
searchRejectsInvertedPriceRange_RN008
toSortAddsIdAsTieBreaker_RN006
```

No es burocracia. Hace dos cosas concretas: una prueba fallida apunta directo a
la regla rota sin tener que leer el cuerpo, y `grep RN022` encuentra todo lo que
la cubre. La métrica que de verdad importa en §6 se apoya en esto.

Se renombran conforme se toquen, no en un commit masivo que ensucie el historial
sin cambiar comportamiento.

### Lo que cada regla necesita

No una prueba: cuatro. Ejemplo con RN-022 (la cantidad acumulada no supera el
stock), sobre un producto con `stock = 3`:

| | Caso | Estado actual |
| --- | --- | --- |
| Éxito | pedir 2 → aceptado | `addItemAddsLineWhenRequestedQuantityFitsInStock` |
| Límite justo en el borde | pedir 3 → aceptado | **Falta** |
| Límite justo pasado | pedir 4 → `409` | Cubierto por el caso de 5, no por el de 4 |
| Acumulación | 2 + 2 → `409` | `addItemAccumulatesQuantityAgainstStockOnSecondCall` |

El error por uno en un límite es el fallo de regla más común y el que ninguna
prueba del camino feliz encuentra. Hoy hay tres de los cuatro casos de RN-022;
el que falta es justo el del borde inclusivo, que es el que decide si el
comprador puede vaciar el stock o se queda a una unidad.

### Lo que no se prueba

El comportamiento del framework (que `@NotNull` rechace nulos), los getters, ni
interacciones con dobles que solo reformulan la implementación. Una prueba que
se rompe cada vez que se refactoriza sin cambiar el comportamiento es un costo
de mantenimiento sin retorno.

Hay una excepción deliberada, y es útil distinguirla:

```java
verifyNoInteractions(productRepository);
```

en `searchRejectsInvertedPriceRangeBeforeHittingTheDatabase` **no** verifica la
implementación: verifica una propiedad del comportamiento —que la regla RN-008
rechaza *antes* de gastar una consulta—. La diferencia entre una verificación
útil y una frágil es si la aserción describe algo que le importa a alguien fuera
de la clase.

---

## 3. La rebanada web

`ProductControllerTest` usa `@WebMvcTest`, que levanta **solo** la capa web:
enrutamiento, enlace de parámetros, Jackson y el `@RestControllerAdvice`. Sin
JPA, sin base de datos, sin servicios reales.

```java
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private ProductService productService;

    @Test
    void pageSizeAboveLimitReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("pageSize", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Solicitud inválida"))
                .andExpect(jsonPath("$.errors.pageSize").isArray());
    }
}
```

`@MockitoBean` es la anotación de Spring Boot 3.4+; sustituye a la
`@MockBean` obsoleta. Si aparece `@MockBean` en una prueba nueva, es una prueba
copiada de un proyecto viejo.

**Qué demuestra esta rebanada, que las pruebas unitarias no pueden:**

| Prueba | Lo que solo se ve aquí |
| --- | --- |
| `listReturnsPagedEnvelope` | Que `PageResponse` se serializa como `items`/`page`/`totalItems` y no con la forma interna de Spring Data |
| `unknownSlugReturnsProblemDetail404` | Que el advice se aplica de verdad y el `Content-Type` es `application/problem+json` |
| `pageSizeAboveLimitReturns400WithFieldErrors` | Que `@Valid` sobre el `@ParameterObject` se dispara, y que el mapa `errors` llega con la forma del contrato |
| `nonNumericPriceReturns400` | Que un fallo de **enlace** (no de validación) produce el mismo formato, con `"Formato inválido."` en vez del mensaje del framework |

Las dos últimas son las valiosas. Un fallo de conversión recorre un camino
distinto dentro de Spring que un fallo de Bean Validation
([java-spring-boot.md](java-spring-boot.md) §8), y sin esta prueba la diferencia
solo se descubre cuando el frontend recibe una forma de error que no sabe leer.

**Lo que falta en esta capa:** no hay `@WebMvcTest` de `CartController`. Los
cuatro tipos de cuerpo de petición del carrito (`AddCartItemRequest`,
`UpdateCartItemRequest`, `ApplyCouponRequest`) no tienen ni una prueba que
verifique su validación a través de HTTP, y el `409` con la extensión
`availableStock` —que el frontend necesita para ofrecer «ajustar a 3»— no está
probado en ningún nivel. Es el hueco más barato de tapar de toda esta lista.

---

## 4. Lo que NO existe: pruebas de integración

**No hay ni una sola prueba de integración. No hay Testcontainers. No hay
`@SpringBootTest`. La aplicación completa nunca se ha arrancado en una prueba
automática.**

Esto es más grave aquí que en un proyecto CRUD corriente, y conviene ser
específico sobre por qué.

### Lo que queda sin demostrar

| Qué | Por qué una prueba unitaria no puede | Riesgo real |
| --- | --- | --- |
| **Las Specifications** | `ProductServiceTest` verifica que se construye un `Specification` y se pasa al repositorio. **El árbol de criterios nunca se traduce a SQL ni se ejecuta.** Un predicado que filtra al revés, o que apunta a un atributo inexistente, pasa la prueba | Alto. Seis filtros opcionales, ninguno ejecutado nunca |
| **El escape de `LIKE`** | `escapeLike` es una función de cadena y podría probarse por unidad —hoy ni eso—. Pero que `cb.like(expr, pattern, '\\')` realmente escape en PostgreSQL depende del driver y del dialecto | Medio. Una búsqueda de `50%` puede devolver el catálogo entero |
| **`ddl-auto: validate`** | Es una comprobación de arranque. Sin arrancar la aplicación, un desajuste entre entidad y migración se descubre al desplegar | Alto. Es exactamente el fallo que `validate` existe para atrapar, y nada lo ejercita |
| **Las migraciones de Flyway** | Nunca se ejecutan en CI. Una migración con un error de sintaxis de PostgreSQL compila, empaqueta y falla al arrancar en el VPS | Alto |
| **Las restricciones** | `uq_cart_items_cart_product`, `ck_cart_items_quantity`, `ck_products_price`: ninguna se ha disparado nunca en una prueba | Medio |
| **La paginación con `OFFSET` real** | `ProductServiceTest` usa un `PageImpl` construido a mano. El desempate por `id` de RN-006 protege contra un fallo que **solo** se manifiesta con `OFFSET` sobre filas con claves de orden repetidas | Medio. Se probó la intención, no el efecto |
| **`@EntityGraph` y el N+1** | Con un repositorio simulado no hay consultas que contar | Medio |
| **UUID generado en Java** | `Cart` usa `GenerationType.UUID`, y la migración tiene además `DEFAULT gen_random_uuid()`. Que las dos cosas convivan sin pisarse no está comprobado | Bajo |
| **`timestamptz` y UTC** | Que `hibernate.jdbc.time_zone: UTC` haga lo que se espera solo se ve escribiendo y leyendo una fila | Bajo |

El patrón es claro: **las pruebas actuales cubren bien las reglas de negocio y
no cubren nada de la persistencia.** Y la persistencia es donde este backend
tiene su lógica menos trivial.

### El plan, cuando se haga

PostgreSQL real vía Testcontainers. **Nunca H2.** H2 difiere de PostgreSQL en
la semántica de `IDENTITY`, en el tipo `uuid`, en el comportamiento de `LIKE`
con `ESCAPE`, en la comparación de `numeric` y en los códigos de error de
violación de restricción — que es precisamente el conjunto de cosas que estas
pruebas existirían para verificar. Probar contra H2 y desplegar contra
PostgreSQL es probar otra aplicación.

```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
abstract class IntegrationTestBase {

    @Container
    @ServiceConnection                       // Boot 3.1+: cablea el datasource solo
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");
    // static: un contenedor para todo el conjunto. PostgreSQL arranca en ~2 s,
    // pero uno por clase multiplicaría eso por el número de clases sin ganar aislamiento
    // que el rollback por prueba no dé ya.
}
```

Dos puntos que deciden si esto sirve o no:

**La misma imagen que producción.** `postgres:17-alpine`, la que está en
[`deploy/docker-compose.prod.yml`](../../deploy/docker-compose.prod.yml). Un
contenedor con otra versión mayor probaría un comportamiento que no es el
desplegado.

**Flyway aplica las migraciones reales.** No un esquema de pruebas mantenido
aparte: `database/migrations/` tal cual. Un esquema de pruebas paralelo se
desvía en semanas, y entonces las pruebas pasan contra un esquema que no existe
en ningún sitio. Esto hace que la prueba de integración verifique, gratis, que
las migraciones se aplican y que `ddl-auto: validate` está conforme — dos de los
huecos de la tabla anterior tapados por el mero hecho de arrancar.

### Qué iría aquí, en orden de valor

1. **Cada filtro de `ProductSpecifications` contra datos sembrados**, uno por
   uno y luego combinados. Es el mayor hueco: seis predicados sin ejecutar.
2. **La búsqueda con `%` y `_` literales.** Un producto llamado `Descuento 50%`
   y una búsqueda de `50%` deben devolver ese producto y no el catálogo.
3. **Paginación estable con precios repetidos.** Sembrar diez productos al mismo
   precio, pedir las páginas 1 y 2 ordenadas por precio, y comprobar que la
   unión son diez productos distintos. Es la prueba que demuestra RN-006, y es
   imposible de escribir sin un `OFFSET` real.
4. **La unicidad de `uq_cart_items_cart_product`** bajo dos peticiones
   concurrentes que agregan el mismo producto: la comprobación previa pierde la
   carrera y la restricción es el único árbitro correcto. Es el caso que el
   [modelo de dominio](../negocio/modelo-dominio.md) declara como excepción
   deliberada a «la restricción es una red de seguridad, no una estrategia».
5. **Conteo de consultas en el listado**, para que el N+1 no vuelva en silencio
   el día que alguien añada un campo al mapeador.
6. **El arranque de la aplicación completa** (`@SpringBootTest` sin más): atrapa
   un `ddl-auto: validate` disconforme, una migración rota y un bean mal
   configurado, y es la prueba con mejor relación valor/esfuerzo de toda la
   lista.

### El costo, dicho de frente

Testcontainers requiere Docker en la máquina de desarrollo y en CI, y añade
entre 5 y 15 segundos al arranque del conjunto. Es real y es el motivo habitual
por el que estas pruebas se posponen. A cambio, es lo único que convierte
«creemos que las Specifications filtran bien» en «sabemos que filtran bien», y
la alternativa actual —descubrirlo en el VPS— cuesta más.

---

## 5. Datos de prueba

| Nivel | Origen | Regla |
| --- | --- | --- |
| Unitario | Constructores en la propia prueba, hoy con reflexión | Migrar a *object mothers* (§2) |
| Integración (futuro) | Migraciones Flyway + siembra por prueba | **Nunca** `V002__catalog_seed.sql` |
| Manual | `V002__catalog_seed.sql` | Para que una persona explore la tienda |

El seed de desarrollo **no** se usa como datos de prueba. Existe para que la
tienda se vea poblada al abrirla; acoplar las pruebas a él significa que cambiar
un producto de ejemplo rompe la compilación, y entonces nadie vuelve a tocar el
seed.

Cada prueba de integración revierte su transacción o limpia en orden inverso de
dependencias. Ninguna depende de los datos de otra ni del orden de ejecución: si
las pruebas pasan en un orden y fallan en otro, lo que hay es estado compartido,
no un fallo intermitente.

### Pruebas de mapeadores

El mapeo es manual ([estándar](estandar-backend.md) §3) y esa decisión tiene un
precio: un campo añadido a `Product` y olvidado en `CatalogMapper` no falla en
compilación. La contraparte acordada es una prueba por mapeador que construya
una entidad con **todos** los campos poblados y compruebe que ninguno del DTO
queda nulo. Hoy no existe; es la deuda que paga la decisión de no usar
MapStruct.

---

## 6. Cobertura

La cobertura de líneas es un detector de humo, no un objetivo. Un 100 % sobre
código sin aserciones no demuestra nada, y perseguir un número produce pruebas
escritas para tocar líneas en vez de para comprobar comportamiento.

| Capa | Objetivo | Por qué |
| --- | --- | --- |
| Dominio (`*/domain/`) | ≥ 90 % | Ahí viven las reglas; es la capa más barata de probar |
| Servicios (`*/service/`) | ≥ 85 % | Orquestación y reglas que necesitan datos |
| Controladores (`*/web/`) | ≥ 70 % | Delgados; lo que importa es la forma del contrato |
| Mapeadores (`*/mapper/`) | ≥ 90 % | Triviales, pero las omisiones de campo son silenciosas (§5) |
| `config/` | — | No es significativamente comprobable por unidad |

El número que de verdad importa: **cada `RN-###` implementada del
[estándar](estandar-backend.md) §5 aparece en al menos un nombre de prueba.**
Eso se comprueba con `grep` y es la métrica por la que CI debería fallar, porque
es la única que no se puede satisfacer escribiendo pruebas vacías.

Estado honesto: hoy **ninguna** prueba lleva el identificador en el nombre, así
que esa métrica daría cero. Se adopta hacia adelante (§2).

---

## 7. Integración continua

Orden previsto, de lo más barato a lo más caro, para que un fallo salga en
segundos y no tras arrancar un contenedor:

```text
1. Compilar                      advertencias como errores
2. Pruebas unitarias             ~10 s, sin Docker        ← fallan primero
3. Pruebas de rebanada web       @WebMvcTest
4. Pruebas de integración        Testcontainers + PostgreSQL 17     [PENDIENTE]
5. Umbrales de cobertura + grep de cobertura de RN                  [PENDIENTE]
```

Hoy solo existen los pasos 1–3, y se ejecutan con `mvn test`. Cuando el paso 4
exista, la puerta pasa a ser `mvn verify`: las pruebas de integración se
enganchan a esa fase mediante Failsafe con el sufijo `*IT`, y `mvn test` por sí
solo daría verde sin haberlas ejecutado — un falso verde es peor que no tener la
prueba.

---

## 8. Lista de revisión

Para cada cambio de backend:

* [ ] ¿Cada `RN-###` que toca este cambio tiene una prueba que lo nombra?
* [ ] ¿Éxito, ambos límites y violación — los cuatro casos?
* [ ] ¿La aserción es sobre el `type` del problema y el estado, no sobre el texto del `detail`?
* [ ] ¿Se fijó el `Clock` si la regla depende del tiempo?
* [ ] ¿Los importes se comparan con `isEqualByComparingTo`, no con `isEqualTo`?
* [ ] ¿Se simuló solo lo que tiene E/S, y no la aritmética?
* [ ] ¿Hay una prueba de rebanada web si cambió la forma de una respuesta o de un error?
* [ ] ¿Hay prueba de integración si se tocó una Specification, una migración o un `@EntityGraph`?
* [ ] ¿Las pruebas pasan en orden aleatorio, sin estado compartido?
* [ ] ¿Hay algo aquí que pruebe Spring en vez de nuestro código?

Sobre la segunda casilla de importes: `assertThat(total).isEqualTo(new BigDecimal("180.00"))`
falla si el valor es `180.0`, porque `BigDecimal.equals` compara también la
escala. Las pruebas actuales usan `isEqualByComparingTo` en todos los casos y es
la forma correcta — igual que el código usa `compareTo`
([estándar](estandar-backend.md) §8).

---

## 9. Resumen de deudas de prueba

| # | Deuda | Prioridad |
| --- | --- | --- |
| P-1 | Cero pruebas de integración: Specifications, Flyway, `ddl-auto: validate` y restricciones sin ejercitar | **Alta** |
| P-2 | Sin `@WebMvcTest` de `CartController`: el `409` con `availableStock` no está probado en ningún nivel | **Alta** (barata) |
| P-3 | Ningún nombre de prueba lleva su `RN-###` | Media |
| P-4 | Fixtures por reflexión repartidas en tres archivos, acopladas a nombres de campos privados | Media |
| P-5 | Sin pruebas de mapeadores: la decisión de no usar MapStruct no tiene su contraparte | Media |
| P-6 | Falta el caso del límite inclusivo de RN-022 (`quantity == stock`) | Baja |
| P-7 | Sin medición de cobertura ni puerta en CI | Baja |
