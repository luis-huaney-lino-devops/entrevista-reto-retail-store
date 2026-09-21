# Caché

Se cachea para resolver un problema medido. Toda caché es una segunda copia de la
verdad, y toda segunda copia puede estar equivocada.

En una tienda esa segunda copia tiene consecuencias comerciales concretas: un
precio obsoleto es una reclamación, y un stock obsoleto es una venta de algo que
no existe. Así que aquí la pregunta no es «¿qué puedo cachear?» sino «¿qué puedo
permitirme que esté desactualizado, y durante cuánto?».

---

## 0. Antes de cachear: dónde ya hay caché

El error más caro es añadir una caché en el sitio equivocado. Este sistema tiene
cuatro niveles, y el de la aplicación es el **tercero**:

```text
  1. Navegador / CDN        Cache-Control sobre las imágenes de R2
     └─ ver archivos-imagenes.md §3 — inmutables, max-age=31536000

  2. Next.js (ISR)          La portada y el detalle se regeneran cada 60 s
     └─ ver PLAN.md §2.3 — el HTML ya renderizado no vuelve a pedir nada

  3. Caffeine (en proceso)  ◄── de esto trata este documento
     └─ el árbol de categorías y poco más

  4. PostgreSQL             shared_buffers: 24 productos caben de sobra
     └─ una tabla pequeña y caliente ya está en RAM sin que nadie lo pida
```

Dos consecuencias que hay que asumir antes de escribir una anotación:

**El nivel 2 absorbe casi todo el tráfico del storefront.** Una portada con
`revalidate: 60` llama a la API **una vez por minuto**, no una vez por visita.
Cachear en la API lo que ya cachea el ISR es cachear una caché: el beneficio real
es una consulta menos por minuto.

**El nivel 4 no es despreciable.** `products` con 24 filas ocupa menos de una
página de 8 KB y vive permanentemente en `shared_buffers`. La consulta no toca
disco. Lo que una caché en proceso ahorra frente a eso no es E/S: es **el viaje
completo** —tomar una conexión del pool, abrir transacción, planificar, mapear a
entidades, mapear a DTO— y sobre todo **la conexión**, que es el recurso escaso
de verdad ([concurrencia.md](concurrencia.md) §6).

Ese es el argumento honesto para cachear aquí. No «la base es lenta», que sería
falso, sino «hay un recurso acotado que se libera».

---

## 1. Por qué Caffeine en proceso y no Redis

| | |
| --- | --- |
| Implementación | Caffeine, a través de la abstracción `@Cacheable` de Spring |
| Alcance | El proceso. Nada compartido entre instancias |

**Nada de Redis.** Una caché en proceso es lo correcto para este sistema por tres
razones verificables, no por preferencia:

1. **Un solo desplegable.** Solo el backend Java llega a producción
   ([ADR-0003](../adr/0003-backend-unico-en-despliegue.md)), así que no hay dos
   copias que puedan discrepar.
2. **Los datos caben holgadamente.** 6 categorías y una portada. Son kilobytes.
3. **El VPS tiene 2 GB** compartidos entre Postgres, Next.js y la API. Un
   contenedor más para cachear kilobytes sería gastar el recurso que falta para
   resolver un problema que no existe.

Meter un salto de red y otro servicio que operar añadiría modos de fallo —¿qué
hace la API si Redis no responde?— sin resolver nada.

**Cuándo deja de ser cierto:** el día que corran dos instancias de la API detrás
de Caddy. Entonces cada una tendrá su copia, y una edición en el panel se verá en
una instancia y no en la otra durante el TTL. Ver §8.

> **Falta una dependencia.** `pom.xml` no incluye hoy
> `spring-boot-starter-cache` ni `com.github.ben-manes.caffeine:caffeine`. Este
> documento describe la caché que corresponde a este sistema; implementarla
> empieza por añadir esas dos dependencias y un `@EnableCaching`.

---

## 2. Qué se puede cachear

Tres condiciones, todas obligatorias:

1. **Se lee mucho más de lo que se escribe.**
2. **Tolera estar algo desactualizado** — y puedes decir durante cuánto.
3. **El espacio de claves está acotado.** Si no puedes contar las claves
   posibles, no es cacheable.

### El registro

Toda caché del sistema está listada aquí. Una caché que no esté en esta tabla no
existe; añadirla significa añadir una fila, con su justificación y su ventana de
desactualización.

| Caché | Clave | TTL | Tamaño máx. | Desactualización máxima | Por qué |
| --- | --- | --- | --- | --- | --- |
| `catalog:categories` | constante (`'active'`) | 10 min | 1 | 10 min sin invalidación; 0 con ella | Es el menú de la cabecera: se lee en **cada** renderizado de página, en la tienda y en el panel. Cambia unas pocas veces al año. La ganancia más clara del sistema |
| `catalog:featured` | `pageSize` | 60 s | 4 | 60 s | La portada. Una consulta con filtros y orden que se repite idéntica para todos los visitantes. TTL corto porque las tarjetas muestran precio y disponibilidad (§2.2) |

Eso es todo. Dos cachés.

Cuando el esquema incorpore marcas y subcategorías
([persistencia-postgres.md](persistencia-postgres.md) §1), la lista de marcas
entra aquí con exactamente el mismo perfil que `catalog:categories`: se lee en
cada página de filtros, cambia casi nunca, y son decenas de filas.

### 2.1 `catalog:categories` en detalle

```java
@Cacheable(cacheNames = "catalog:categories", sync = true)
public List<CategoryResponse> findActive() { ... }
```

La clave es constante porque **el método no tiene parámetros**: devuelve siempre
«las categorías activas, ordenadas por nombre». Con `maximumSize(1)` la caché
entera es una entrada.

El valor cacheado es el **DTO ya mapeado** (`List<CategoryResponse>`), no las
entidades. Tres razones:

* Una entidad JPA cacheada fuera de su sesión es un problema esperando: si tiene
  asociaciones perezosas sin inicializar, quien la lea desde la caché recibe
  `LazyInitializationException`; si las tiene inicializadas, estás cacheando un
  grafo sin control de tamaño.
* El DTO es inmutable (`record`), así que compartir la misma instancia entre
  peticiones concurrentes es seguro. **Una caché de objetos mutables es estado
  compartido mutable**, que es exactamente lo que [concurrencia.md](concurrencia.md)
  §9 prohíbe.
* Es lo que se serializa a JSON: el acierto de caché salta también el mapeo.

Punto que hay que respetar y es fácil de romper: la lista devuelta tiene que ser
inmutable de verdad. `List.copyOf(...)` o `stream().toList()` (que ya es lo que
hace `CategoryService`). Devolver un `ArrayList` mutable desde una caché permite
que una petición lo modifique y se lo encuentre modificado la siguiente.

### 2.2 `catalog:featured`, y el compromiso que lleva dentro

La portada pide productos destacados, y cada tarjeta muestra `stock` e `inStock`.
Cachear eso significa aceptar que **la portada puede anunciar como disponible
algo que acaba de agotarse**.

Se acepta, con dos condiciones que lo vuelven honesto:

* **El TTL es de 60 s**, no de 10 minutos. La ventana es pequeña y conocida.
* **La tarjeta no es la autoridad.** El stock se verifica de verdad en dos
  puntos que nunca leen de caché: al agregar al carrito (`CartService.requireStock`)
  y, sobre todo, al crear la orden, donde el decremento es atómico
  ([concurrencia.md](concurrencia.md) §1). Lo peor que produce una tarjeta
  obsoleta es un mensaje de «se agotó» al pulsar el botón, que es un resultado
  correcto aunque sea decepcionante.

Lo que **no** se puede hacer es dejar que esa tarjeta cacheada llegue a ser la
base de una decisión de venta. Mientras el camino de compra revalide, la caché es
una pista de renderizado y no una fuente de verdad.

Honestamente: con ISR de 60 s en la portada (nivel 2 de §0), esta caché ahorra
poco en el storefront. Se gana el sitio cuando la portada se pide desde fuera del
ISR — el panel, un cliente móvil futuro, o un renderizado que se saltó la caché
de Next.

---

## 3. Qué NO se cachea, a propósito

Esta lista es tan importante como la otra, porque cada entrada es una idea que
suena bien y no lo es.

| No se cachea | Por qué |
| --- | --- |
| **El carrito** | Cambia en cada interacción y es **por usuario**: una clave por carrito multiplica el espacio de claves por el número de visitantes, con una tasa de aciertos que tiende a cero. Y es el objeto sobre el que el usuario está mirando en tiempo real: un total obsoleto es un error visible e inmediato |
| **Los listados con filtros** | La clave sería `(search × category × minPrice × maxPrice × inStock × featured × sort × page × pageSize)`. Prácticamente ilimitada. La memoria se llenaría de entradas de un solo uso mientras la tasa de aciertos ronda cero. La solución correcta son los índices ([persistencia-postgres.md](persistencia-postgres.md) §7) |
| **El stock** | Un dato obsoleto aquí **vende algo que no existe**. El stock solo se lee con valor vinculante dentro de la transacción que lo decrementa; cachearlo es contradecir esa garantía |
| **La validez de un cupón** | `used_count` y `max_uses` son un contador. Cachear la respuesta de «este cupón aplica» reparte más descuentos de los autorizados, e invalida la invariante I-14. Es el mismo razonamiento que el stock: **nunca se cachea un recurso finito que se consume** |
| **El detalle de producto por slug** | Plausible, y descartado **hasta medirlo**. Es una búsqueda por índice único (`uq_products_slug`, ~1 ms), el acceso se reparte por todo el catálogo, la carga de invalidación en cada edición es real, y la página ya está bajo ISR. Se revisa con números, no con intuición |
| **Decisiones de autorización** | Baratas de calcular desde el token. Cachearlas arriesga servir un permiso revocado, que es la única categoría de desactualización con consecuencia de seguridad |
| **Cualquier cosa por usuario** | Multiplica el espacio de claves por el número de usuarios y hunde la tasa de aciertos. Si algún día hiciera falta (el carrito de un cliente identificado, por ejemplo), sería una caché con su fila en §2 y su TTL en segundos, no en minutos |

Regla que resume media tabla: **no se cachea nada que se consuma** — stock,
usos de cupón, cantidades. Se cachea lo que se *describe* — nombres, categorías,
estructura.

---

## 4. Cache-aside, y solo cache-aside

La aplicación, no la caché, es dueña de la lectura:

```text
      lectura                                escritura
        │                                        │
        ▼                                        ▼
   ┌──────────┐                           ┌──────────────┐
   │¿en caché?│──sí──► devolver           │ base de datos│  ← siempre primero
   └────┬─────┘                           └──────┬───────┘
        no                                       │ COMMIT
        ▼                                        ▼
   ┌──────────────┐                        ┌───────────┐
   │ base de datos│─► guardar ─► devolver  │ invalidar │  ← solo DESPUÉS del commit
   └──────────────┘                        └───────────┘
```

No se usan *write-through* ni *write-behind*: ambos ponen la caché en el camino
de escritura, donde un fallo de caché se convierte en un fallo de escritura. Una
tienda que no puede guardar un producto porque falló una caché ha cambiado un
problema de rendimiento por uno de disponibilidad.

---

## 5. Invalidación

### 5.1 Después del commit. Nunca dentro.

Expulsar dentro de la transacción abre una ventana en la que la caché queda
vacía, **una lectura concurrente la repuebla leyendo el estado anterior** (que es
el único visible para ella, porque la escritura aún no ha confirmado), y luego la
transacción confirma. La entrada cacheada queda describiendo el estado viejo, y
sobrevive hasta que venza el TTL.

```text
  MAL                                          BIEN
  ───                                          ────
  BEGIN                                        BEGIN
    UPDATE categories SET name = 'Tecno'         UPDATE categories SET name = 'Tecno'
    expulsar de caché        ◄── ventana       COMMIT
    │                                          expulsar de caché    ◄── seguro
    │  (otro hilo lee, ve 'Tecnología',
    │   repuebla la caché con el valor viejo)
  COMMIT
    → la caché miente durante 10 minutos
```

El caso simétrico es peor todavía: si la transacción **revierte** después de
expulsar, la caché queda poblada con datos que nunca se escribieron.

En Spring:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onCategoryChanged(CategoryChangedEvent event) {
    cacheManager.getCache("catalog:categories").clear();
}
```

o, más manual, `TransactionSynchronizationManager.registerSynchronization(...)`
con `afterCommit`. Es el mismo mecanismo que usa el envío de correo
([correo.md](correo.md) §2) y por la misma razón.

### 5.2 La trampa de `@CacheEvict`

Esto **parece** correcto y no lo es:

```java
@Transactional
@CacheEvict(cacheNames = "catalog:categories", allEntries = true)
public void rename(Long id, String name) { ... }
```

Spring aplica el `@CacheEvict` **cuando el método retorna**, no cuando la
transacción confirma. Si este método es el que abre la transacción, retorno y
commit casi coinciden y parece funcionar. Pero en cuanto lo llama otro método
`@Transactional` —un caso de uso que renombra la categoría y además reordena el
menú—, la transacción exterior sigue abierta cuando el `@CacheEvict` ya se
ejecutó, y estamos exactamente en el diagrama «MAL» de arriba.

`@CacheEvict(beforeInvocation = true)` empeora el problema: expulsa **antes** de
ejecutar, ampliando la ventana a todo el método.

La regla que evita razonar sobre esto cada vez: **la expulsión se engancha a
`AFTER_COMMIT`, siempre.** `@CacheEvict` queda para operaciones sin transacción.

### 5.3 Reglas de expulsión

1. **Expulsar en toda escritura que toque datos cacheados**: crear, actualizar
   **y desactivar** por igual. Olvidar el camino de desactivación es el fallo
   clásico de caché obsoleta — y aquí es especialmente fácil de olvidar porque
   desactivar una categoría *es* un `UPDATE`, no un `DELETE`.
2. **Expulsar, no actualizar.** Escribir el valor nuevo en la caché crea dos
   caminos de código que deben coincidir en cuál es el valor correcto. La
   expulsión tiene uno solo: el siguiente lector va a la base.
3. **Expulsar de más antes que de menos.** Tirar `catalog:categories` entera ante
   cualquier escritura de categoría es barato (una entrada) y siempre correcto.
   La precisión aquí no compra nada.
4. **El TTL es la red de seguridad, no la estrategia.** La corrección viene de la
   expulsión; el TTL acota el daño cuando se escapa un camino. Los dos
   mecanismos, siempre. Una caché cuya única garantía es el TTL está afirmando
   «puedo mentir hasta 10 minutos en cualquier momento», y eso hay que decidirlo
   a propósito, no heredarlo por descuido.

---

## 6. Protección contra estampida

Cuando una clave muy solicitada vence bajo carga, todas las peticiones
concurrentes fallan a la vez y golpean la base simultáneamente: **el momento en
que la caché debía ayudar es justo el momento en que deja de hacerlo**.

```text
  t=0s    catalog:categories vence
  t=0s    llegan 40 peticiones concurrentes
          └─ sin protección: 40 consultas + 40 conexiones pedidas al pool de 10
             → 30 esperando, latencia disparada, y el pool es el cuello
               (concurrencia.md §6)
```

Con `catalog:categories` leída en cada renderizado de página, esto es un fallo
realista. Y nótese que el daño no es «40 consultas»: es **40 conexiones
solicitadas a un pool de 10**.

Solo quien llega primero debe cargar; el resto espera ese resultado.

```java
@Cacheable(cacheNames = "catalog:categories", sync = true)
```

`sync = true` delega en `Cache.get(key, mappingFunction)` de Caffeine, que
garantiza que el cargador **corre una vez por clave**; el resto de hilos esperan
su resultado. Sin `sync = true`, `@Cacheable` hace un consultar-luego-cargar
clásico y estampida alegremente.

Nunca a mano:

```java
var valor = cache.get(clave);          // ✗ esto es la estampida escrita a mano
if (valor == null) {
    valor = cargar();
    cache.put(clave, valor);
}
```

---

## 7. Configuración

**Toda caché está acotada en tamaño y en tiempo.** Una caché sin límite es una
fuga de memoria con buenas intenciones.

```java
@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    CacheManager cacheManager() {
        var manager = new CaffeineCacheManager();
        manager.setAllowNullValues(true);              // ver §7.2

        manager.registerCustomCache("catalog:categories",
            Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(Duration.ofMinutes(10))
                .recordStats()                          // obligatorio: §7.3
                .build());

        manager.registerCustomCache("catalog:featured",
            Caffeine.newBuilder()
                .maximumSize(4)
                .expireAfterWrite(Duration.ofSeconds(60))
                .recordStats()
                .build());

        return manager;
    }
}
```

### 7.1 `expireAfterWrite`, no `expireAfterAccess`

Bajo `expireAfterAccess` una clave muy solicitada **nunca** se refresca: cada
lectura renueva su vida, así que puede quedar obsoleta indefinidamente. Y
`catalog:categories` es precisamente la clave más solicitada del sistema, es
decir, la que más tiempo llevaría mintiendo.

`expireAfterWrite` garantiza una edad máxima absoluta, que es lo que permite
escribir la columna «desactualización máxima» de §2.

### 7.2 Los nulos

Con `allowNullValues(true)` —el valor por omisión de `CaffeineCacheManager`—
Spring guarda un centinela (`NullValue`) cuando el método devuelve `null`. Eso es
lo que se quiere: un slug inexistente pedido en bucle no se convierte en una
consulta por petición, que es el ataque más barato que existe contra una caché
sin negativos.

La contrapartida, que hay que conocer: **un recurso creado después queda
«inexistente» hasta que venza el TTL o alguien expulse.** Si mañana se cachea el
detalle de producto por slug, la creación de un producto tiene que expulsar
también su clave, no solo actualizar la lista.

Con las dos cachés actuales no se da el caso: ninguna devuelve `null`, devuelven
listas (vacías como mucho). Queda escrito para la siguiente.

### 7.3 `recordStats()` no es opcional

Sin él, Micrometer no tiene nada que exponer y §8 no existe. Una caché sin
estadísticas es una suposición con sintaxis.

---

## 8. Métricas — innegociables

Con `recordStats()` activo y `spring-boot-starter-actuator` (ya está en el
`pom.xml`), Micrometer publica las métricas por caché:

| Métrica | Qué te dice |
| --- | --- |
| `cache.gets{result="hit"}` / `{result="miss"}` | **Tasa de aciertos.** Sostenida por debajo del ~70%, la caché no se está ganando el sitio: se quita, o la clave está mal elegida |
| `cache.evictions` | Muchas expulsiones con pocos aciertos = el espacio de claves es demasiado grande. Es el modo de fallo del listado con filtros, por si alguien lo intentara |
| `cache.size` frente a `maximumSize` | Si el límite está apretando |
| Tiempo de carga (el `miss` medido) | **Lo que cuesta realmente un fallo.** Este es el número que justifica la caché, o la condena |

Se revisan **antes** de añadir una caché y después. «Se sentía más rápido» no es
una medición, y una caché que resulta no ayudar se borra en vez de conservarse
por cortesía.

Dos métricas de fuera de la caché que cierran el argumento, porque son el recurso
que realmente se está ahorrando:

* Tiempo de espera para obtener una conexión de Hikari.
* Utilización del pool.

Si la caché de categorías funciona, esas dos bajan. Si no bajan, la caché estaba
resolviendo un problema que no existía.

---

## 9. Cuándo esta decisión deja de ser correcta

Se documenta para que la revisión tenga un disparador y no una intuición:

| Señal | Qué hacer |
| --- | --- |
| Corre **más de una instancia** de la API | Cada proceso tiene su copia. Una edición en el panel se ve en una instancia y no en la otra durante el TTL. Salidas: bajar el TTL a segundos (barato, suficiente para datos que casi no cambian), o mover la caché a Redis (caro de operar; solo si la incoherencia es visible para los usuarios) |
| La tasa de aciertos de una caché baja del 70% | Se quita esa caché, o se corrige la clave |
| Aparece la tentación de cachear un listado con filtros | Se revisa §3 y se mira el plan de la consulta. La respuesta casi siempre es un índice |
| El tiempo de espera del pool sube y la caché no ayuda | El problema no era la caché: es que alguna transacción retiene la conexión demasiado tiempo ([concurrencia.md](concurrencia.md) §6) |

---

## 10. Lista de revisión

* [ ] ¿El coste que evita está medido, o supuesto?
* [ ] ¿Está la caché en la tabla de registro de §2, con su justificación?
* [ ] ¿Está acotada en tamaño **y** en tiempo?
* [ ] ¿`expireAfterWrite`, y no `expireAfterAccess`?
* [ ] ¿El valor cacheado es inmutable (un DTO, no una entidad JPA)?
* [ ] ¿Todo camino de escritura que toca estos datos expulsa — incluida la desactivación?
* [ ] ¿La expulsión ocurre **después del commit**, y no en un `@CacheEvict`?
* [ ] ¿El cargador es a prueba de estampida (`sync = true`)?
* [ ] ¿Está decidido qué pasa con los `null`?
* [ ] ¿`recordStats()` activo y las métricas expuestas?
* [ ] ¿Cuánto puede desactualizarse esto en el peor caso, y está escrito en §2?
* [ ] ¿Hay algo cacheado que se **consuma** (stock, usos de cupón)? (No debe haberlo.)
* [ ] ¿Hay algo cacheado que sostenga una decisión de seguridad? (No debe haberlo.)

---

## Documentos relacionados

* [Persistencia — PostgreSQL](persistencia-postgres.md) — las consultas que la caché evita, y los índices que hacen innecesario cachear el resto.
* [Concurrencia](concurrencia.md) — el pool de conexiones, que es el recurso que la caché libera.
* [Archivos e imágenes](archivos-imagenes.md) — la caché del CDN, que es la que más tráfico ahorra de todas.
* [Envío de correo](correo.md) — el otro sitio donde algo se encola después del commit, y por el mismo motivo.
