# Concurrencia

Cómo usa los hilos el backend. El objetivo es rendimiento bajo carga sin inventar
condiciones de carrera, interbloqueos ni una hambruna del pool de conexiones.

Este dominio tiene **una** condición de carrera que importa de verdad, y no es
una hipótesis de manual: dos compradores pulsando «comprar» sobre la última
unidad. Todo lo demás de este documento existe para no estropear la solución a
esa.

---

## 0. La regla que va antes que las demás

**No paralelices hasta haber medido.** Casi todos los endpoints de aquí hacen una
consulta y devuelven. Repartir eso entre hilos añade planificación, propagación
de contexto y complejidad en el manejo de errores, y lo vuelve más lento.

Paraleliza cuando se cumplan **todas** estas condiciones:

1. Las operaciones son realmente independientes: ninguna necesita el resultado de
   la otra.
2. Están limitadas por E/S (base de datos, HTTP), no por aritmética de CPU.
3. Hay al menos dos, y esperar a la más lenta compensa.
4. **No están dentro de una misma transacción de base de datos** (§5).

En cualquier otro caso, el código secuencial es la respuesta correcta.

Ojo con una asimetría: paralelizar E/S consume **conexiones**, que es el recurso
verdaderamente escaso (§6). Una petición que abre dos consultas en paralelo
necesita dos conexiones a la vez, así que reduce a la mitad cuántas peticiones
concurrentes caben. Ese coste casi nunca aparece en el razonamiento, y suele ser
el decisivo.

---

## 1. El problema real: la última unidad

```text
    stock = 1

    Comprador A                          Comprador B              products.stock
    ───────────────────────────────────────────────────────────────────────────
    SELECT stock  →  1                                                  1
                                         SELECT stock  →  1             1
    ¿1 >= 1?  sí                                                        1
                                         ¿1 >= 1?  sí                   1
    UPDATE stock = 0                                                    0
    COMMIT                                                              0
                                         UPDATE stock = 0               0
                                         COMMIT                         0
    ───────────────────────────────────────────────────────────────────────────
    Dos órdenes confirmadas. Una unidad en el almacén.
```

La forma del error es **comprobar y actuar en dos pasos**: entre el `SELECT` y el
`UPDATE` cabe otra transacción entera. No se arregla con más validación, porque
la validación es correcta las dos veces: se arregla haciendo que comprobar y
actuar sean **una sola operación indivisible**.

> **Estado actual.** Hoy no existe la tabla `orders` ni ningún decremento de
> stock: `CartService.requireStock` solo compara, y eso es una comprobación
> informativa, no una reserva. Esta sección describe cómo se implementa RF-22
> («crear orden»). Es la decisión de diseño que hay que tomar **antes** de
> escribir ese servicio, no después.

### 1.1 Opción A — bloqueo pesimista

Se toma el bloqueo de fila al leer, y nadie más puede tocarla hasta el commit:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select p from Product p where p.id in :ids order by p.id")
List<Product> lockForUpdate(@Param("ids") List<Long> ids);
```

Hibernate emite `SELECT ... FOR UPDATE`. El comprador B se queda esperando en su
`SELECT` hasta que A confirma, y entonces lee `stock = 0` y rechaza.

Correcto. Pero tiene un coste que en Postgres es concreto y poco conocido:

**`FOR UPDATE` bloquea también a quien solo quiere añadir ese producto a su
carrito.** Al insertar una fila en `cart_items` que referencia `products`,
Postgres toma un bloqueo `FOR KEY SHARE` sobre la fila del producto para
garantizar que la clave foránea sigue siendo válida. `FOR UPDATE` entra en
conflicto con `FOR KEY SHARE`. Resultado: mientras una orden está en curso, todo
el que intente meter ese producto en su carrito espera.

Es un bloqueo que nadie pidió, sobre una operación que no tenía por qué estar
involucrada. (El modo más débil `FOR NO KEY UPDATE` no tiene este conflicto, pero
JPA no lo expresa: habría que bajar a SQL nativo.)

### 1.2 Opción B — decremento condicional

Una sola sentencia que comprueba y actúa a la vez:

```java
@Modifying
@Query(value = """
        update products set stock = stock - :qty
        where id = :id and stock >= :qty
        """, nativeQuery = true)
int decrementStock(@Param("id") Long id, @Param("qty") int qty);
```

```java
if (productRepository.decrementStock(item.getProductId(), item.getQuantity()) != 1) {
    throw new InsufficientStockException(...);   // revierte la transacción entera
}
```

**La respuesta está en el número de filas afectadas.** `1` significa «había stock
y ya está descontado»; `0` significa «no había», y no hay ningún instante entre
ambas cosas.

### 1.3 Por qué funciona en Postgres: READ COMMITTED y la reevaluación

Lo que hace correcta a la opción B no es obvio, y conviene entenderlo porque
**depende del nivel de aislamiento**.

Bajo `READ COMMITTED` —el valor por omisión de Postgres— cuando la transacción de
B llega a una fila que A tiene bloqueada, B **espera**. Cuando A confirma,
Postgres no deja que B escriba a ciegas sobre su instantánea vieja: **vuelve a
leer la versión nueva de la fila y reevalúa la cláusula `WHERE` contra ella**
(el mecanismo se llama *EvalPlanQual*).

```text
  A:  UPDATE ... WHERE stock >= 1      → stock: 1 → 0
  B:  UPDATE ... WHERE stock >= 1      → espera a que A confirme
      A hace COMMIT
      B relee la fila: stock = 0
      B reevalúa: 0 >= 1  →  falso  →  0 filas afectadas  →  rechazo correcto
```

Si el nivel fuera `REPEATABLE READ` o `SERIALIZABLE`, Postgres **no** reevalúa:
aborta la transacción de B con `SQLSTATE 40001` («could not serialize access due
to concurrent update»), y haría falta lógica de reintento en la aplicación.

Conclusión operativa: **se trabaja en `READ COMMITTED` y no se sube el nivel.**
Subirlo no hace el sistema más correcto aquí; lo hace más frágil, porque
introduce una clase de error que hay que reintentar.

### 1.4 Comparación y recomendación

| | A · `SELECT ... FOR UPDATE` | B · `UPDATE ... WHERE stock >= ?` |
| --- | --- | --- |
| Sentencias | 2 | **1** |
| Ventana de bloqueo | Desde el `SELECT` hasta el `COMMIT` | La duración del `UPDATE` |
| ¿Bloquea añadir al carrito? | **Sí** (§1.1) | **No**: un `UPDATE` que no toca la clave usa el modo débil |
| Comprobar y actuar | Dos pasos, correctos porque hay bloqueo | **Un paso, atómico por construcción** |
| Reintentos en `READ COMMITTED` | No hacen falta | No hacen falta |
| Riesgo de interbloqueo | Sí, si no se ordena (§1.5) | Sí, si no se ordena (§1.5) |
| Permite decidir usando otros campos leídos bajo el mismo bloqueo | **Sí** | No |

**Recomendación: opción B para el decremento de stock.** Es una sentencia,
mantiene el bloqueo el mínimo tiempo posible, no interfiere con los carritos de
otros compradores y hace imposible por construcción el error de §1.

La opción A se reserva para un caso que hoy no existe: una **reserva de stock**
que necesite mantener la fila estable mientras se hacen más cosas con ella
(consultar un almacén externo, por ejemplo). Ese día, el bloqueo estaría
justificado por algo que la opción B no puede dar.

### 1.5 El orden canónico, o cómo no provocar un interbloqueo

Una orden tiene varias líneas. Si A descuenta el producto 7 y luego el 3,
mientras B descuenta el 3 y luego el 7, cada uno espera al otro. Postgres lo
detecta pasado `deadlock_timeout` (1 segundo por omisión) y **mata una de las dos
transacciones** con `SQLSTATE 40P01`.

La regla vale para las dos opciones y es de una línea:

> **Siempre se bloquean o actualizan las filas en un orden total y fijo** — aquí,
> `product_id` ascendente.

```java
order.getItems().stream()
     .sorted(comparingLong(OrderItem::getProductId))   // ← esto evita el interbloqueo
     .forEach(item -> decrementOrFail(item));
```

Es el mismo principio que el orden total de la paginación
([persistencia-postgres.md](persistencia-postgres.md) §5.1): cuando dos procesos
recorren el mismo conjunto, tienen que recorrerlo igual.

### 1.6 Por qué `@Version` es la herramienta equivocada aquí

El bloqueo optimista (`@Version`, `WHERE version = :esperada`) responde a la
pregunta **«¿alguien ha cambiado esta fila desde que la leí?»**.

La pregunta de este dominio es otra: **«¿queda stock?»**. Y las respuestas no
coinciden. Con 50 unidades y dos compradores simultáneos, `@Version` haría fallar
al segundo con un conflicto de concurrencia — un error para el usuario en una
situación en la que **no hay ningún conflicto real**: hay stock de sobra para
ambos.

`@Version` es la herramienta correcta para la edición del catálogo desde el
panel: dos administradores abriendo el mismo producto y guardando, donde el
segundo debe enterarse de que pisa el trabajo del primero. Ese es su sitio, y
hoy `products` ni siquiera tiene columna `version`; habrá que añadirla cuando
exista el panel.

| Problema | Herramienta |
| --- | --- |
| ¿Queda stock? (recurso que se consume) | Decremento condicional |
| ¿Alguien editó este producto mientras yo lo editaba? | `@Version` |
| ¿Este cupón sigue teniendo usos? | Incremento condicional (§1.7) |

### 1.7 El cupón tiene exactamente la misma forma

`coupons.used_count` frente a `max_uses` es un recurso finito que se consume, o
sea, el mismo problema con otro nombre. La invariante I-14 (`usosActuales <=
usosMaximos`) se aplica con la misma sentencia:

```sql
UPDATE coupons
   SET used_count = used_count + 1
 WHERE id = :id
   AND is_active
   AND (max_uses IS NULL OR used_count < max_uses)
```

Cero filas afectadas → el cupón se agotó entre que se aplicó al carrito y se
confirmó la orden → la orden se rechaza o se recalcula sin descuento, pero **no
se emite**.

Hoy esto no está implementado en ningún sitio: `CartService.applyCoupon`
comprueba `isUsableFor(...)`, que es una lectura, y nadie incrementa
`used_count`. Es una invariante declarada y no aplicada, y el sitio donde
aplicarla es la transacción de la orden.

### 1.8 La transacción de la orden, completa

```text
  BEGIN                                            ← READ COMMITTED
    1. leer el carrito con sus líneas
    2. revalidar que todo producto sigue activo
    3. copiar nombre y precio VIGENTES a order_items   (foto, no referencia)
    4. por cada línea, en orden de product_id:
         UPDATE products SET stock = stock - q WHERE id = ? AND stock >= q
         si afecta 0 filas → excepción → ROLLBACK de TODO
    5. si hay cupón:
         UPDATE coupons SET used_count = used_count + 1 WHERE ... (§1.7)
         si afecta 0 filas → excepción → ROLLBACK
    6. INSERT de orders + order_items
    7. marcar el carrito como CONVERTED
  COMMIT
                                                   ← y solo aquí:
    enviar el correo de confirmación (correo.md §2)
```

Todo o nada. Que el paso 4 pueda fallar a mitad y deshacer los decrementos
anteriores es precisamente lo que aporta la transacción; hacerlo «a mano» con
compensaciones sería reimplementarla peor.

El paso 3 es una decisión del dominio, no una optimización: la orden guarda una
**copia** del nombre y el precio. Un `JOIN` al producto para mostrar una orden
vieja se paga en la primera auditoría contable.

Y el correo va **fuera**: una llamada SMTP dentro de esta transacción retendría
una conexión de base de datos durante segundos esperando a un servidor ajeno
(§4.3).

### 1.9 Cancelar devuelve el stock

```sql
UPDATE products SET stock = stock + :qty WHERE id = :id
```

Sin condición, porque un incremento siempre es válido: no hay nada que
comprobar. Lo que sí hay que garantizar es que **no se ejecute dos veces**, y eso
lo da el estado de la orden: la cancelación solo se permite desde `PENDIENTE` o
`PAGADA`, y el cambio de estado va en la misma transacción que la devolución.

---

## 2. Por qué el carrito no necesita bloqueo

Tres propiedades del carrito, las tres deliberadas:

1. **Tiene un solo dueño.** El `cartId` es un UUID que vive en el `localStorage`
   de un navegador ([ADR-0002](../adr/0002-carrito-servidor-fuente-de-verdad.md)).
   Por diseño, no hay dos personas operando sobre el mismo carrito.
2. **No consume nada.** Cambiar una cantidad de 2 a 3 no agota un recurso. El
   peor resultado de dos pestañas compitiendo es que gane la última escritura, y
   eso es correcto: la última es la que el usuario vio.
3. **No guarda ningún valor derivado.** `cart_items` no almacena precio ni total;
   `CartPricingCalculator` los recalcula desde el precio vigente en **cada**
   lectura. Por lo tanto no existe la categoría de bug «el total guardado ya no
   coincide con las líneas».

La orden es lo contrario en las tres: afecta a un recurso compartido y finito
(stock, usos de cupón), la ven varios compradores a la vez y **congela**
cantidades y precios. Por eso una necesita control de concurrencia y la otra no.

### 2.1 La única carrera que el carrito sí tiene

`CartService.addItem` hace esto:

```java
int requested = cart.findItemFor(product).map(CartItem::getQuantity).orElse(0) + quantity;
requireStock(product, requested);
cart.addOrIncrement(product, quantity);          // ← busca, y si no está, inserta
```

Es **comprobar y actuar** otra vez, ahora sobre la existencia de la línea. Dos
peticiones `POST /carts/{id}/items` con el mismo producto —un doble clic, o el
reintento de la UI optimista— pueden entrar a la vez, las dos no encontrar la
línea, y las dos insertar.

**El árbitro correcto es la restricción**, no el código:

```sql
CONSTRAINT uq_cart_items_cart_product UNIQUE (cart_id, product_id)
```

La segunda inserción falla con `SQLSTATE 23505`, que Spring traduce a
`DataIntegrityViolationException`. Es el mismo razonamiento que el modelo de
dominio aplica a las carreras de unicidad: ninguna comprobación previa puede
evitarlas, así que la restricción es el único juez posible.

Qué hacer con esa excepción **sí** es una decisión de producto:

| Opción | Cuándo |
| --- | --- |
| **Reintentar una vez** (releer el carrito, que ahora sí tiene la línea, e incrementar) | **Preferida.** Desde el punto de vista del comprador, dos clics deben sumar dos unidades. El reintento produce justo eso |
| Devolver `409` | Correcto pero peor: obliga al usuario a repetir una acción que el sistema podía completar |

El reintento va **fuera** de la transacción que falló: una transacción que ha
lanzado una violación de restricción está marcada para revertir y no acepta más
sentencias.

### 2.2 El stock en el carrito es informativo, y hay que decirlo

`requireStock` al agregar al carrito **no reserva nada**. Dos compradores pueden
tener la misma última unidad en sus respectivos carritos, y los dos ven que está
disponible. El primero que confirme se la lleva; el segundo recibe un `409` al
crear la orden.

Parece un defecto y es una decisión. Reservar stock en el carrito exige decidir
cuánto dura la reserva, liberar las caducadas con un proceso programado y
explicarle al comprador por qué algo «se le venció». Es un sistema de reservas
completo, fuera del alcance de este proyecto.

Lo que sí es innegociable es que **el arbitraje final ocurra en la orden** (§1) y
que el mensaje de error sea claro. La comprobación del carrito existe para que el
90% de los casos falle pronto y con buena explicación, no para garantizar nada.

---

## 3. El modelo de petición: hilos virtuales

```yaml
spring:
  threads:
    virtual:
      enabled: true      # ← recomendado; hoy NO está en application.yml
```

Con Java 21 y esta línea, cada petición recibe su propio hilo virtual. El JDBC
bloqueante deja de inmovilizar un hilo de plataforma, así que unos cientos de
peticiones concurrentes dejan de necesitar unos cientos de hilos del sistema
operativo. El código bloqueante sigue siendo código bloqueante — esa es la idea:
no hay reescritura reactiva.

Dos cosas que en Java 21 todavía muerden:

**Fijación (*pinning*).** Un hilo virtual que se bloquea dentro de un bloque
`synchronized` **fija** su hilo portador, y el beneficio se evapora: el portador
queda ocupado igual que antes. Hay que usar `ReentrantLock`. (JDK 24 elimina esta
limitación; el objetivo aquí es 21, así que sigue aplicando.)

**`ThreadLocal`.** Los hilos virtuales se crean por petición y nunca se reutilizan
de un pool, así que no arrastran estado entre peticiones — bien. Pero tampoco lo
heredan las tareas a las que se reparte trabajo: el contexto de seguridad y el
identificador de correlación hay que propagarlos explícitamente (§5.2).

Nota sobre el `Dockerfile`: fija `-XX:+UseSerialGC` para convivir con Postgres y
Next.js en un VPS de 2 GB. Es la elección correcta para un heap pequeño, y tiene
una contrapartida que conviene tener escrita: es un recolector de un solo hilo,
así que sus pausas crecen con el tamaño del heap. Si algún día se sube
`MaxRAMPercentage`, hay que volver a mirar esto.

---

## 4. Transacciones

### 4.1 Fronteras

| Servicio | Anotación | Por qué |
| --- | --- | --- |
| `CartService` | `@Transactional` (clase) | Toda mutación del carrito es una unidad |
| `CartService.get` | `@Transactional(readOnly = true)` | Lectura pura, sobrescribe la de clase |
| `ProductService`, `CategoryService` | `@Transactional(readOnly = true)` (clase) | Solo leen |

`readOnly = true` no es cosmética en Postgres: además de que Hibernate se salta
la comprobación de cambios y las instantáneas, el driver emite `SET TRANSACTION
READ ONLY`, así que **una escritura accidental falla en el motor**.

### 4.2 Lo que rompe una frontera sin avisar

**Autoinvocación.** Llamar a un método `@Transactional` desde otro método del
mismo bean **no pasa por el proxy**, así que la anotación no hace nada. Es el
error de Spring más antiguo que existe y sigue apareciendo. Si hacen falta dos
fronteras, hacen falta dos beans.

**`open-in-view: false`.** Ya está configurado, y es lo correcto. Implica que el
mapeo a DTO tiene que ocurrir **dentro** de la transacción, que es exactamente lo
que hacen `ProductService.search` y `CartService.toResponse`. Ver
[persistencia-postgres.md](persistencia-postgres.md) §4.

**Excepciones comprobadas.** Spring revierte por omisión solo ante
`RuntimeException` y `Error`. Una excepción comprobada **confirma** la
transacción salvo que se declare `rollbackFor`. Las excepciones de dominio de
este proyecto (`InsufficientStockException`, `InvalidCouponException`) son no
comprobadas, y eso las hace correctas por omisión.

### 4.3 Lo que nunca va dentro de una transacción

Una transacción abierta **retiene una conexión del pool**. Cualquier cosa que la
alargue reduce la capacidad de todo el sistema (§6).

| Nunca dentro | Dónde va |
| --- | --- |
| Envío de correo (SMTP) | Encolado tras el commit — [correo.md](correo.md) §2 |
| Subida a R2 y procesamiento de imágenes | Fuera de la petición, en un pool acotado — [archivos-imagenes.md](archivos-imagenes.md) §4 |
| Cualquier llamada HTTP a un tercero | Fuera; y con su propio tiempo de espera |
| Expulsión de caché | Tras el commit — [cache.md](cache.md) §5 |
| `Thread.sleep`, bucles de reintento | Fuera. Reintentar dentro de una transacción muerta no funciona (§2.1) |

La forma de verlo: **la transacción empieza lo más tarde posible y termina lo
antes posible**. Validar la petición, resolver lo que se pueda resolver sin base
de datos, y solo entonces abrirla.

---

## 5. Nunca repartir trabajo dentro de una transacción

**Una conexión de base de datos no es segura para hilos, y la `Session` de
Hibernate es explícitamente de un solo hilo.** Además, Spring ata la conexión al
hilo actual (`TransactionSynchronizationManager`), así que un hilo hijo ni
siquiera *ve* la transacción del padre: o abre la suya —y entonces no ve lo que
el padre aún no ha confirmado—, o comparte la `Session` y corrompe la caché de
primer nivel en silencio.

```text
  MAL                                    BIEN
  ───                                    ────
  @Transactional                         Cada tarea abre su propia transacción
    fork A ─┐                            corta de solo lectura, o el reparto
    fork B ─┴─ misma Session/conexión    ocurre completamente fuera de toda
               ✗ comportamiento             transacción.
                 indefinido
```

Las escrituras no se paralelizan nunca: **una transacción, un hilo, una
conexión.**

### 5.1 El único candidato real de este sistema, y por qué se descarta

`Page<Product>` implica dos consultas independientes: la página y el conteo.
Cumple las condiciones 1, 2 y 3 de §0. Aun así, **no se paraleliza**:

* El conteo sobre 24 filas —o sobre unos pocos miles— cuesta menos de un
  milisegundo. Lo que se ahorra es ruido.
* Cada petición pasaría a **retener dos conexiones a la vez**, así que un pool de
  10 pasaría a servir 5 peticiones concurrentes en vez de 10. Esa es la condición
  de §0 que falla.
* Spring Data ya se salta el conteo cuando la primera página vuelve incompleta
  (`PageableExecutionUtils`), así que en una parte de los casos ni siquiera hay
  dos consultas.

Es un buen ejemplo de la regla: la paralelización que parecía obvia está mal
porque el recurso escaso no era el tiempo.

### 5.2 Si algún día hiciera falta repartir

Con un ámbito que sea **dueño** de cada tarea que lanza, para que ninguna
sobreviva a la petición:

```java
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var a = scope.fork(() -> repositorio.consultaA());   // transacción propia
    var b = scope.fork(() -> repositorio.consultaB());   // transacción propia
    scope.joinUntil(Instant.now().plusSeconds(5));
    scope.throwIfFailed();
    return combinar(a.get(), b.get());
}
```

Si una rama falla, la otra se cancela y el ámbito lanza: sin tareas huérfanas.
**`StructuredTaskScope` es API de vista previa en Java 21** y exige
`--enable-preview`. La alternativa sin vista previa:

```java
var a = CompletableFuture.supplyAsync(() -> repositorio.consultaA(), executor);
var b = CompletableFuture.supplyAsync(() -> repositorio.consultaB(), executor);
CompletableFuture.allOf(a, b).orTimeout(5, TimeUnit.SECONDS).join();
```

— con la salvedad de que `allOf` **no cancela a la hermana** cuando una falla, así
que la perdedora corre hasta terminar reteniendo su conexión. Aceptable para dos
consultas cortas; no para consultas caras.

Y en ambos casos: el contexto de seguridad y el identificador de correlación
viven en almacenamiento local al hilo y **no se heredan**. O se capturan antes de
repartir y se restauran dentro, o —preferible— el reparto se mantiene en la capa
de acceso a datos, por debajo de donde ese contexto importa.

---

## 6. Dimensionado: el pool es el límite real

El error más caro disponible aquí:

> «Los hilos virtuales son baratos, así que puedo atender 10 000 peticiones
> concurrentes.»

Puedes **crear** 10 000 hilos virtuales. No puedes crear 10 000 conexiones a
Postgres: cada conexión es **un proceso del sistema operativo** en el servidor,
con su memoria; el `max_connections` por omisión es 100, y este Postgres comparte
2 GB de VPS con Next.js y la API.

Diez mil hilos virtuales bloqueados sobre un pool de 10 conexiones significa
9 990 esperando — y como los hilos virtuales son baratos, **nada ejerce
contrapresión**. La cola crece, la latencia supera el tiempo de espera del
cliente y el servicio se degrada bajo una carga que un pool de hilos acotado
sencillamente habría rechazado.

**Los hilos virtuales eliminan el límite de hilos. No eliminan el límite de
conexiones: solo dejan de hacerlo visible.** Hay que acotarlo explícitamente:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 3000      # ms esperando una conexión, no 30 000
      validation-timeout: 2000
      max-lifetime: 1800000
```

> Hoy `application.yml` **no configura Hikari**, así que rige el valor por
> omisión: `maximum-pool-size: 10` (razonable) y `connection-timeout: 30000`
> (demasiado). Treinta segundos esperando una conexión significa treinta segundos
> de peticiones acumulándose antes de que nada falle. Vale más fallar en tres.

**Guía de dimensionado:** empezar cerca de `(2 × núcleos) + discos efectivos` y
medir. Con 1–2 vCPU, 10 es lo correcto. Un pool más grande **no es más rápido**:
pasado el punto en que Postgres puede atender peticiones en paralelo, las
conexiones extra solo añaden cambios de contexto y memoria del lado del servidor.

Qué vigilar, por orden de utilidad:

1. **Tiempo de espera para obtener conexión** (`hikari.connections.acquire`) — la
   señal más temprana de presión.
2. **Utilización del pool** — sostenida por encima del ~80%, hay que redimensionar
   o reducir el tiempo de retención.
3. **Percentiles de latencia** — p99, no la media. La media esconde exactamente el
   problema que estás buscando.

Si el tiempo de espera sube, la primera pregunta **no** es «¿el pool es muy
pequeño?» sino **«¿por qué se retiene cada conexión tanto tiempo?»**. La respuesta
casi siempre está en §4.3.

---

## 7. El trabajo intensivo en CPU necesita un pool ACOTADO

El procesamiento de imágenes es la excepción a todo lo anterior: no está limitado
por E/S, está limitado por CPU y por memoria. Redimensionar cuatro variantes de
una imagen de 4 MB cuesta entre 1 y 3 segundos de cómputo real.

**Aquí más paralelismo no da más rendimiento: da más contención.** Con 2 vCPU,
diez conversiones simultáneas no van diez veces más rápido; van todas más lentas,
y además multiplican por diez el pico de memoria, que en un VPS de 2 GB es la
forma habitual de que el proceso muera.

```text
  E/S (consultas, HTTP)        →  hilos virtuales, tantos como hagan falta
                                  (el límite lo pone el pool de conexiones)

  CPU (imágenes)               →  pool de plataforma ACOTADO: 2–4 hilos
                                  (IMAGE_PROCESSING_THREADS = 3)
```

Los detalles —la cola, el estado `PROCESANDO`, la respuesta `202`— están en
[archivos-imagenes.md](archivos-imagenes.md) §4. Lo que importa aquí es la regla:
**el trabajo de CPU va en un pool de hilos de plataforma con un tamaño fijo y
pequeño, nunca en hilos virtuales y nunca sin límite.**

Un hilo virtual no aporta nada a una tarea que no se bloquea: el beneficio de los
hilos virtuales es liberar el portador mientras se espera, y aquí no se espera,
se calcula.

Y **nunca en el `ForkJoinPool` común** (el de `parallelStream()`): está
dimensionado a `núcleos - 1`, lo comparte todo el proceso, y una tarea larga ahí
dentro bloquea a cualquier otra cosa que use *streams* paralelos.

---

## 8. Tiempos de espera y cancelación

**Toda operación de E/S tiene un plazo.** Sin plazos, una consulta patológica no
falla: se queda, reteniendo su conexión, hasta que el pool se agota y cae todo lo
demás con ella.

### 8.1 El presupuesto

| Capa | Valor | Por qué ese |
| --- | --- | --- |
| Espera de conexión (Hikari) | **3 s** | El más corto de todos, a propósito (§8.2) |
| Sentencia SQL (`statement_timeout`) | **5 s** | Ninguna consulta de este sistema debería acercarse |
| Espera por un bloqueo (`lock_timeout`) | **3 s** | Un bloqueo de §1 dura milisegundos; 3 s significa que algo va mal |
| Transacción ociosa (`idle_in_transaction_session_timeout`) | **10 s** | Mata la fuga clásica: una transacción abierta que nadie cierra |
| Petición HTTP completa | **10 s** | |
| Proxy (Caddy) | **30 s** | El más largo: el borde no debe cortar antes que la aplicación |

### 8.2 Por qué la espera de pool es la más corta

Es deliberado y contraintuitivo. Si el pool está agotado, **fallar rápido es
mejor que hacer cola detrás de trabajo que a su vez está a punto de expirar**.
Una espera de pool de 30 s con consultas que expiran a los 5 s produce una cola
de peticiones que van a fracasar de todas formas, pero que mientras tanto
retienen memoria y sockets.

### 8.3 Cómo se fijan en Postgres

Lo más fiable es fijarlos **del lado del servidor**, por sesión, en la URL:

```text
jdbc:postgresql://db:5432/retailstore_java?options=-c%20statement_timeout%3D5000%20-c%20lock_timeout%3D3000%20-c%20idle_in_transaction_session_timeout%3D10000
```

La alternativa desde JPA es `jakarta.persistence.query.timeout`, que acaba en
`Statement.setQueryTimeout`. Funciona, pero conviene saber cómo: **pgjdbc lo
implementa abriendo una segunda conexión para enviar la petición de cancelación**.
Si el pool está agotado —que es justo el escenario en que esto importa—, esa
segunda conexión es precisamente lo que no hay. `statement_timeout` lo resuelve
el servidor solo, sin depender de nada del cliente.

`idle_in_transaction_session_timeout` es el que más problemas evita en la
práctica: mata cualquier sesión que deje una transacción abierta sin actividad, y
con ella todos los bloqueos que estuviera reteniendo.

### 8.4 La verdad sobre la cancelación

En un Spring MVC bloqueante **no hay token de cancelación**. Cuando un comprador
cierra la pestaña, el servidor no se entera: la consulta sigue, la transacción
sigue, la conexión sigue ocupada, y el servidor solo lo descubre al intentar
escribir la respuesta en un socket cerrado.

No hay forma de arreglar esto sin cambiar de modelo de ejecución, y no compensa
cambiarlo por esto. **La defensa es la de §8.1: plazos en todas las capas.** Una
petición abandonada cuesta, como mucho, su presupuesto.

Escrito así de claro porque la alternativa es suponer que existe una propagación
de cancelación que no existe, y diseñar sobre esa suposición.

---

## 9. Estado compartido

* Servicios, repositorios y controladores son **singletons y deben ser sin
  estado**. Un campo mutable en un singleton es una condición de carrera con mecha
  larga: funciona en desarrollo con un usuario y falla en producción de forma
  irreproducible. Hoy se cumple: `CartService`, `ProductService` y
  `CategoryService` solo tienen dependencias finales.
* **El estado por petición vive en parámetros de método**, nunca en un campo de
  instancia.
* **Los objetos inmutables se comparten libremente**, y eso es lo que hace que
  valga la pena insistir en la inmutabilidad. Ejemplos de este código: el bean
  `Clock`, los `record` de DTO, `ProductSpecifications` (clase final con métodos
  estáticos y constructor privado), `CatalogMapper` y `CartMapper`.
* **Las entidades JPA no se comparten entre hilos.** Pertenecen a su `Session` y
  mueren con ella. Ese es otro motivo para que la caché guarde DTO y no entidades
  ([cache.md](cache.md) §2.1).
* Donde el estado mutable compartido sea inevitable: colección concurrente o
  cerrojo explícito, nunca un `HashMap` protegido por buenas intenciones.
  Caffeine es seguro para hilos.
* **`ReentrantLock`, no `synchronized`** (§3, fijación).

---

## 10. Lista de revisión

* [ ] ¿Esto es paralelo siquiera? Si sí, ¿cuál de las cuatro condiciones de §0 lo justifica?
* [ ] ¿Cuántas conexiones retiene una petición a la vez?
* [ ] ¿Algún «comprobar y actuar» sobre un recurso que se consume? (Stock, usos de cupón.)
* [ ] ¿El decremento de stock es condicional y se verifica el número de filas afectadas?
* [ ] ¿Las filas se bloquean o actualizan siempre en el mismo orden (`product_id` ascendente)?
* [ ] ¿Se sigue en `READ COMMITTED`? Si no, ¿hay lógica de reintento para `40001`?
* [ ] ¿Se usa `@Version` para algo que es realmente un recurso finito? (No debe.)
* [ ] ¿Hay algún reparto de trabajo dentro de una transacción? (Debe ser que no.)
* [ ] ¿Hay envío de correo, subida a R2 o llamada HTTP dentro de una transacción? (No.)
* [ ] ¿La expulsión de caché ocurre después del commit?
* [ ] ¿El pool de conexiones está acotado y con `connection-timeout` explícito?
* [ ] ¿Están fijados `statement_timeout`, `lock_timeout` e `idle_in_transaction_session_timeout`?
* [ ] ¿Están el tiempo de espera del pool y la latencia p99 en algún panel?
* [ ] ¿Algún campo mutable de instancia en un singleton?
* [ ] ¿Algún `synchronized` alrededor de E/S bloqueante?
* [ ] ¿El trabajo de CPU está en un pool acotado y fuera de la petición?

---

## Documentos relacionados

* [Persistencia — PostgreSQL](persistencia-postgres.md) — transacciones, `readOnly`, índices y el orden total de la paginación.
* [Caché](cache.md) — invalidación después del commit, y el pool como recurso que la caché libera.
* [Archivos e imágenes](archivos-imagenes.md) — el pool acotado de procesamiento.
* [Envío de correo](correo.md) — el envío encolado tras el commit.
* [Modelo de dominio](../negocio/modelo-dominio.md) — las invariantes I-6, I-8, I-9 e I-14, que son las que este documento hace cumplir.
