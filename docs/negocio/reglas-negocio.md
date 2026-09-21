# Reglas de negocio

La autoridad sobre el comportamiento del sistema. Cada regla tiene un
identificador estable, un enunciado verificable y un resultado.

**Cómo se usa**

* Implementando una regla → su enunciado es el criterio de aceptación.
* Escribiendo una prueba → nómbrala por la regla: `crear_conSkuDuplicado_lanzaDuplicateSku_RN002`.
* Cambiando comportamiento → cambia **primero** este archivo.
* ¿La regla no está aquí? No es un requisito. No la inventes: plantéala.

**Marcas de origen**

* **derivada** — ya estaba implícita en el enunciado del reto.
* **decisión** — el reto callaba y este documento lo decide. Son las que vale la
  pena discutir; están marcadas para poder encontrarlas.

**Los valores configurables** viven en configuración, nunca en el código. El
valor mostrado es el predeterminado.

---

## 1. Catálogo — producto

### RN-001 — El producto cuelga de una subcategoría *(decisión)*
Todo producto pertenece a exactamente una subcategoría, y esa subcategoría debe
existir y estar activa al asignarla.

Nunca a una categoría directamente (ADR-0006). Si algunos productos colgaran del
nivel 1 y otros del 2, toda consulta de catálogo tendría dos caminos y todo
recuento dos fuentes.

→ Subcategoría inexistente: `404 SUBCATEGORY_NOT_FOUND`.
→ Existe pero inactiva: `422 SUBCATEGORY_INACTIVE`.

### RN-002 — El SKU es único e inmutable *(decisión)*
No puede haber dos productos con el mismo `sku`, activos o no. Una vez creado,
no cambia: un SKU equivocado es un producto equivocado, se desactiva y se crea
el correcto.

Se normaliza a mayúsculas y sin espacios al guardarlo, así que `pol-001` y
`POL-001` son el mismo y colisionan.

→ Duplicado: `409 DUPLICATE_SKU`.
→ Intento de cambio: `422 SKU_IMMUTABLE`.

### RN-003 — El precio es positivo y con dos decimales *(derivada)*
`precio > 0`, exactamente dos decimales. Más precisión es error de validación,
no se redondea en silencio: redondear dinero calladamente es como empiezan los
descuadres.

→ `400 VALIDATION_ERROR`.

### RN-004 — El precio anterior debe ser mayor que el precio *(decisión)*
Si `precioAnterior` viene, debe ser estrictamente mayor que `precio`. Es lo que
convierte la tienda en «antes S/ 120, ahora S/ 89». Un `precioAnterior` menor
sería un descuento negativo — casi siempre un error de captura.

Para quitar la promoción se envía `precioAnterior: null`.

→ `422 INVALID_COMPARE_PRICE`.

### RN-005 — El stock nunca es negativo *(derivada)*
`stock >= 0`. Un stock negativo significa que se vendió lo que no había, y el
sistema debe impedirlo antes, no registrarlo después (ver RN-030).

→ `400 VALIDATION_ERROR`.

### RN-006 — Un producto inactivo no se vende *(decisión)*
Con `activo = false` no aparece en el catálogo público, no se puede agregar al
carrito y no se puede comprar. **Sí** sigue visible en el panel y **sí** sigue
apareciendo en las órdenes pasadas.

Si ya está en el carrito de alguien cuando se desactiva, la línea permanece
visible marcada como no disponible y el checkout la rechaza (RN-032). No se
borra sola: un carrito que pierde cosas sin avisar es peor que uno que explica.

→ Al agregar: `422 PRODUCT_INACTIVE`.

### RN-007 — Los productos no se borran *(derivada)*
`DELETE /admin/productos/{id}` hace una **eliminación lógica** (RN-086): el
producto desaparece del panel y de la tienda, pero la fila sigue ahí. Un
producto referenciado por órdenes no puede desaparecer sin romper el histórico.

Eliminar no es lo mismo que **despublicar**. Despublicar (`PATCH .../estado`)
lo retira de la tienda y lo deja a mano para volver a publicarlo; eliminar lo
saca de la vista de todos y lo manda a la papelera. Confundirlos es lo que hace
que la gente despublique cuando quería borrar y al revés.

→ `204 No Content`, idempotente.

### RN-008 — El slug se deriva del nombre y no cambia *(decisión)*
Se genera del nombre (minúsculas, sin acentos, guiones) al crear. **Renombrar el
producto no cambia el slug**: los enlaces que ya circulan y el posicionamiento
ganado sobreviven. Si el slug derivado existe, se añade sufijo numérico.

El administrador puede editarlo a mano, y entonces asume la consecuencia.

→ Slug manual duplicado: `409 DUPLICATE_SLUG`.

### RN-009 — Todo producto necesita al menos una imagen para publicarse *(decisión)*
Se puede **crear** sin imágenes (borrador), pero no **activar**. Una tarjeta sin
imagen en la rejilla rompe la cuadrícula y no se compra.

→ `422 PRODUCT_REQUIRES_IMAGE`.

---

## 2. Catálogo — marca, categoría, subcategoría

### RN-010 — Nombres únicos *(derivada)*
Marca y categoría: nombre único globalmente. Subcategoría: nombre único
**dentro de su categoría**. «Accesorios» puede existir bajo Tecnología y bajo
Deportes.

→ `409 DUPLICATE_NAME`.

### RN-011 — No se borra lo que tiene contenido *(decisión)*
* Una categoría con subcategorías no se puede desactivar.
* Una subcategoría con productos activos no se puede desactivar.
* Una marca con productos activos no se puede desactivar.

Primero se vacía o se reasigna. Evita que un clic deje productos colgando de
algo invisible.

→ `409 HAS_DEPENDENTS`, y el `details` lista qué lo bloquea (hasta 50).

### RN-012 — Desactivar en cascada es visible, no automático *(decisión)*
Desactivar una categoría **no** desactiva sus subcategorías ni sus productos. Lo
impide RN-011 hasta que el administrador actúe.

La alternativa —cascada automática— es cómoda y peligrosa: un clic despublica
doscientos productos y nadie se entera hasta que caen las ventas.

### RN-013 — La marca es opcional *(decisión)*
Un producto puede no tener marca. Muchos productos de tienda no la tienen, y
obligar a inventar una «Genérica» ensucia los datos y el filtro.

### RN-014 — El orden del menú es explícito *(decisión)*
Categorías y subcategorías tienen `orden` entero. El criterio de negocio no es
alfabético: «Ofertas» va primero aunque empiece por O. Ante empate se desempata
por nombre ascendente, para que el listado sea **total**.

---

## 3. Búsqueda y listado del catálogo

### RN-020 — Solo se muestra lo publicable *(derivada)*
El catálogo público devuelve únicamente productos `activo = true` cuya
subcategoría y categoría también estén activas, y con al menos una imagen lista.

El panel no aplica este filtro: el administrador ve todo.

### RN-021 — Búsqueda por nombre y descripción corta *(decisión)*
`?q=` busca en nombre y descripción corta, sin distinguir mayúsculas ni
acentos: buscar «bicicleta» encuentra «Bicicleta» y «BICICLETA».

Menos de 2 caracteres tras recortar se ignora, como si no viniera.

### RN-022 — Filtros combinables *(derivada)*
`subcategoria`, `categoria`, `marca`, `precioMin`, `precioMax`, `soloConStock`.
Se combinan con Y lógico. Un filtro ausente no restringe.

Filtrar por categoría incluye los productos de **todas** sus subcategorías: es
lo que el comprador espera al pulsar «Tecnología».

→ `precioMin > precioMax`: `422 INVALID_PRICE_RANGE`.

### RN-023 — Ordenamiento acotado *(decisión)*
Campos permitidos: `relevancia` (por defecto con búsqueda), `precio`,
`nombre`, `novedad`. Cualquier otro se **rechaza**, no se ignora: un error de
tipeo del cliente debe ser visible, no devolver calladamente otro orden.

Toda ordenación añade `id` como última clave para que sea **total**. Sin eso, la
paginación puede repetir o saltar filas cuando hay empates.

→ `400 VALIDATION_ERROR`.

### RN-024 — Paginación acotada *(derivada)*
`page` empieza en 1. `pageSize` por defecto **12**, máximo **48**.

Los valores salen de la rejilla, no de un CRUD genérico: 12 encaja en 3×4 y en
4×3, que son las dos disposiciones de la tarjeta de producto. 48 es cuatro
páginas de golpe, suficiente para quien quiere recorrer sin paginar.

Por encima del máximo se **rechaza**, no se recorta: quien pide 500 productos
debe enterarse de que la API no lo hará, en vez de recibir 60 y creer que tiene
todo.

→ `400 VALIDATION_ERROR`.

### RN-025 — Productos relacionados *(decisión)*
Hasta 4 productos de la **misma subcategoría**, excluyendo el actual,
priorizando los que tienen stock. Si no llegan a 4, se completa con la misma
categoría. Si aun así no hay, se devuelve menos — nunca se rellena con productos
sin relación.

### RN-026 — Los comodines del usuario no son comodines *(decisión)*
El término de búsqueda se escapa antes de construir el `LIKE`: un `%` o un `_`
escritos por el comprador se buscan **literalmente**, no actúan como patrón.

Sin escapar, buscar `%` devuelve el catálogo entero y buscar `_` devuelve
cualquier cosa con al menos un carácter. No es solo un resultado raro: es un
recorrido completo de tabla que cualquiera puede provocar desde la barra de
búsqueda.

Ya está implementado en `ProductSpecifications.escapeLike`.

---

## 4. Carrito

### RN-030 — No se puede agregar más de lo que hay *(derivada)*
La cantidad de una línea nunca supera el stock del producto. Se valida sobre el
total resultante, no sobre lo que se añade: con 3 en el carrito y 3 de stock,
añadir 1 más se rechaza.

→ `409 INSUFFICIENT_STOCK`, con `disponible` y `solicitado` en el cuerpo.

### RN-031 — Un producto, una línea *(derivada)*
Agregar un producto que ya está en el carrito **suma** a la línea existente, no
crea otra.

### RN-032 — El carrito refleja el precio vigente *(decisión)*
La línea no guarda precio. Si el precio cambia mientras el carrito está abierto,
el comprador ve el nuevo — que es el que se le cobrará.

Guardar el precio en la línea crearía la expectativa de respetarlo, y eso es una
promesa de negocio que nadie tomó.

### RN-033 — Toda mutación devuelve el carrito completo *(decisión)*
Agregar, cambiar cantidad, eliminar, aplicar cupón: todas devuelven el carrito
entero con los totales recalculados.

Un solo contrato y un solo sitio donde vive la regla de precios. La alternativa
—devolver solo la línea— obliga al cliente a recalcular totales, que es
duplicar la lógica de dinero en el navegador.

### RN-034 — Cantidad entre 1 y 99; eliminar es `DELETE` *(decisión)*
La cantidad de una línea está entre **1 y 99**. **Enviar 0 en un `PATCH` es un error
de validación**, no una forma de borrar: para eliminar la línea está
`DELETE /carts/{cartId}/items/{itemId}`.

Sobrecargar `PATCH quantity: 0` con el significado «bórralo» parece cómodo y
oculta la intención: el mismo verbo haría dos cosas distintas según el valor, y
el cliente que se equivoca en un cálculo borra una línea creyendo que la
actualiza. La interfaz sí puede mostrar un botón «−» que, al llegar a 1, llame a
`DELETE`; esa traducción es del cliente, no del contrato.

→ `400 VALIDATION_ERROR`.

### RN-035 — El carrito no caduca solo *(decisión)*
Un carrito activo vive indefinidamente. Los carritos `ACTIVO` sin tocar durante
90 días se purgan por tarea programada.

### RN-036 — El carrito sobrevive al acceso *(decisión)*
Si alguien tenía carrito anónimo e inicia sesión, ese carrito **se le asocia**.
Si ya tenía uno de una sesión anterior, se **fusionan**: cantidades que suman,
acotadas por el stock.

Descartar cualquiera de los dos pierde una intención de compra real. Fusionar es
más trabajo y es lo que el comprador espera.

### RN-037 — Un ítem solo se modifica desde su carrito *(decisión)*
`PATCH` y `DELETE` sobre una línea exigen que esa línea pertenezca al carrito
de la ruta. Si no, `404 CART_ITEM_NOT_FOUND`.

Comprobar solo el id del ítem permitiría a cualquiera con un id válido
modificar el carrito de otra persona. El carrito se identifica por un UUID que
no se adivina, pero el id de línea es un entero secuencial: sin esta
comprobación, el UUID deja de proteger nada.

---

## 5. Cupones

### RN-040 — Vigencia y estado *(derivada)*
Un cupón aplica solo si está activo y la fecha actual está entre `iniciaEn` y
`terminaEn`.

→ `422 COUPON_NOT_APPLICABLE`.

### RN-041 — Compra mínima *(derivada)*
Si el cupón tiene `subtotalMinimo`, el subtotal debe alcanzarlo. El mensaje dice
cuánto falta: un rechazo sin cifra no es accionable.

→ `422 COUPON_MIN_NOT_MET`, con `subtotalActual` y `subtotalMinimo`.

### RN-042 — Límite de usos *(derivada)*
`usosActuales < usosMaximos`. El contador se incrementa **al crear la orden**,
no al aplicar el cupón al carrito: aplicarlo no es comprarlo, y descontar antes
agota cupones que nadie usó.

→ `422 COUPON_EXHAUSTED`.

### RN-043 — Un cupón por carrito *(decisión)*
Aplicar uno nuevo reemplaza al anterior. Acumular descuentos es una política que
nadie pidió y abre la puerta a combinaciones que regalan producto.

### RN-044 — El descuento nunca supera el subtotal *(decisión)*
Un cupón de S/ 100 sobre un carrito de S/ 60 descuenta 60, no 100. El total
nunca es negativo.

### RN-045 — Un cupón que deja de aplicar se ignora, pero no se desvincula *(decisión)*
Si el comprador quita productos y el subtotal baja del mínimo del cupón, el
cupón **deja de descontar pero sigue aplicado** al carrito. Al volver a agregar
productos, el descuento reaparece solo.

La alternativa —desvincularlo en cuanto deja de aplicar— obligaría al comprador
a reescribir el código por una acción que él mismo puede deshacer en el
siguiente clic. El carrito debe informar de que el cupón está aplicado pero no
activo, y por qué.

---

## 6. Órdenes

### RN-050 — La orden se crea en una transacción *(derivada)*
Crear una orden es atómico: revalida stock y precios, descuenta stock,
incrementa el uso del cupón, copia los datos y marca el carrito como
`CONVERTIDO`. Si algo falla, no queda nada a medias.

### RN-051 — Se revalida todo al confirmar *(decisión)*
En el momento del checkout se vuelven a comprobar: que cada producto siga
activo, que haya stock, que el cupón siga siendo aplicable, y se recalculan los
totales **desde cero en el servidor**.

El servidor **nunca** confía en los totales que envíe el cliente. Es la regla más
importante del checkout: aceptar un total del navegador es aceptar que alguien
compre a cualquier precio.

→ Producto inactivo: `422 PRODUCT_INACTIVE`, con la línea.
→ Sin stock: `409 INSUFFICIENT_STOCK`.

### RN-052 — La orden copia, no referencia *(decisión)*
Nombre del producto, SKU y precio unitario se **copian** a la línea de la orden.
Una orden es un hecho ocurrido: si mañana el producto sube de precio o se
renombra, la orden del mes pasado debe seguir diciendo lo que se compró y pagó.

### RN-053 — Importes inmutables *(derivada)*
Ningún endpoint modifica los importes de una orden creada. Corregir una venta se
hace con una nota de crédito o una cancelación, nunca editando el histórico.

### RN-054 — Transiciones de estado válidas *(decisión)*

```text
PENDIENTE ──▶ PAGADA ──▶ ENVIADA ──▶ ENTREGADA
    │            │
    └────────────┴──▶ CANCELADA
```

`ENTREGADA` y `CANCELADA` son terminales. No se retrocede.

→ Transición inválida: `422 INVALID_ORDER_TRANSITION`.

### RN-055 — Cancelar devuelve el stock *(decisión)*
Cancelar una orden repone el stock de sus líneas, en la misma transacción. Solo
se puede cancelar antes de `ENVIADA`.

### RN-056 — El correo de contacto es obligatorio *(derivada)*
Aunque el comprador no tenga cuenta. Es la única vía para confirmarle el pedido.

### RN-057 — El número de orden es legible y no adivinable *(decisión)*
Formato `ORD-AAAAMM-XXXXXX`, con sufijo aleatorio, no secuencial.

Un número secuencial (`ORD-000123`) revela el volumen de ventas a cualquiera que
haga un pedido, y permite tantear los pedidos de otros.

---

## 7. Clientes e identidad

### RN-060 — El correo identifica al cliente *(decisión)*
Único, en minúsculas. Es la identidad de la cuenta.

→ Ver RN-061: el duplicado **no** se reporta como error.

### RN-061 — El registro no revela si el correo existe *(decisión)*
`POST /auth/registro` responde `202` con el mismo cuerpo exista o no el correo.
Si existe, en vez del correo de verificación se envía uno de «alguien intentó
registrarse con tu correo».

Responder `409 correo ya registrado` convertiría el endpoint en un comprobador
de quién tiene cuenta en la tienda: revela hábitos de compra.

### RN-062 — Contraseña de al menos 10 caracteres *(decisión)*
Sin exigir mayúsculas, dígitos ni símbolos. Se rechazan las más comunes con una
lista de bloqueo. El razonamiento está en
[autenticacion.md](../backend/autenticacion.md) §3.

Hash BCrypt coste 12, con prefijo de algoritmo.

→ `400 VALIDATION_ERROR`.

### RN-063 — Comprar no exige verificar el correo *(decisión)*
Se puede comprar sin verificar. **Sí** lo exigen: cambiar la contraseña y ver el
historial de pedidos.

Bloquear la compra sacrifica ventas reales por un riesgo que no existe: el
pedido ya lleva un correo de contacto.

### RN-064 — Vinculación con Google solo con correo verificado *(decisión)*
Si el correo de Google coincide con una cuenta existente, se vinculan **solo si
Google reporta `email_verified = true`**.

Sin esa comprobación bastaría crear una cuenta de Google declarando el correo de
la víctima para apropiarse de su cuenta en la tienda.

→ `422 EMAIL_NOT_VERIFIED_BY_PROVIDER`.

### RN-065 — Nunca sin forma de entrar *(decisión)*
No se puede desvincular Google si el cliente no tiene contraseña, ni quitar la
contraseña sin identidad externa. Invariante I-12.

→ `422 LAST_LOGIN_METHOD`.

### RN-066 — Recuperación: respuesta idéntica *(decisión)*
`POST /auth/recuperar` responde `202` con el mismo cuerpo y tiempo comparable
exista o no el correo.

Token de 30 minutos, un solo uso, del que se guarda **el hash**. Al
restablecer se revocan todas las sesiones y se avisa por correo.

### RN-067 — Fallos de acceso indistinguibles *(decisión)*
Correo inexistente, contraseña incorrecta y cuenta desactivada devuelven el
mismo `401 INVALID_CREDENTIALS`, con el mismo mensaje y tiempo comparable —
verificando contra un hash señuelo cuando el correo no existe.

### RN-068 — Límite de intentos *(decisión)*

| Endpoint | Límite |
| --- | --- |
| `/auth/acceso` | 5 por correo / 15 min · 20 por IP / 15 min |
| `/auth/recuperar` | 3 por correo / 15 min · 10 por IP / hora |
| `/auth/registro` | 5 por IP / hora |

→ `429 TOO_MANY_REQUESTS` con `Retry-After`.

---

## 8. Archivos e imágenes

### RN-070 — Se valida el contenido, no la extensión *(decisión)*
El tipo se determina leyendo los bytes. Solo JPEG, PNG, WebP y AVIF.

→ `422 UNSUPPORTED_IMAGE_TYPE`.

### RN-071 — Límites de tamaño y dimensión *(decisión)*
Máximo 10 MB, 8000×8000 px y 40 megapíxeles totales. Mínimo 200×200 px. Las
dimensiones se comprueban **antes de descomprimir** (defensa contra bombas de
descompresión).

→ `422 IMAGE_TOO_LARGE` / `IMAGE_TOO_SMALL`.

### RN-072 — Todo se convierte a WebP *(decisión)*
Cualquier formato admitido se convierte a WebP calidad 82, se le aplica la
orientación EXIF y se le eliminan los metadatos — que pueden incluir
coordenadas GPS.

### RN-073 — Tres variantes por imagen *(decisión)*
`miniatura` 160 px, `tarjeta` 480 px, `detalle` 1200 px, más un original
acotado a 2400. Se mantiene la proporción y **no se amplía**.

### RN-074 — El texto alternativo es obligatorio *(decisión)*
Toda imagen asociada a un producto necesita `alt`. Sin él la tienda es
inaccesible para lectores de pantalla y pierde posicionamiento.

→ `400 VALIDATION_ERROR`.

### RN-075 — Las imágenes se reemplazan, no se editan *(decisión)*
Un archivo es inmutable. Cambiar la imagen sube una nueva y desreferencia la
anterior, que se purga a los 30 días. Así una URL cacheada nunca devuelve una
imagen distinta.

---

## 9. Transversales

### RN-080 — Los errores no filtran detalles internos *(derivada)*
Ninguna traza, SQL, cadena de conexión ni versión de framework llega al cliente.
Todo fallo inesperado es `500 INTERNAL_ERROR` con identificador de correlación.

### RN-081 — Toda respuesta lleva identificador de correlación *(decisión)*
`X-Correlation-Id` en toda respuesta, presente en cada línea de log de esa
petición.

### RN-082 — Las escrituras son atómicas *(derivada)*
Ninguna operación deja un cambio a medio aplicar.

### RN-083 — Los efectos externos van después del commit *(decisión)*
Correos, subidas a R2 y **envíos por WebSocket** se disparan tras confirmar la
transacción, nunca dentro. Lo contrario envía correos con tokens que no existen.

El WebSocket lo demuestra en segundos y no en producción: si el mensaje se
difunde dentro de la transacción, el receptor lo recibe, responde al instante y
su respuesta lee una versión de la conversación que aún no se ha escrito. El
resultado es un `409 CONFLICT` por bloqueo optimista en una conversación que
nadie estaba editando.

La auditoría es la excepción: va **dentro**, porque es parte del hecho.

### RN-084 — Todo importe es decimal exacto *(derivada)*
`numeric(12,2)` en la base, `BigDecimal` en Java. Nunca `double` ni `float`.
Los precios llevan IGV incluido y se desglosa en el resumen.

### RN-085 — Las vistas de producto se cuentan en el servidor *(decisión)*
Abrir la ficha de un producto en la tienda incrementa su contador de vistas. El
contador es acumulado, no una serie temporal, y **no se expone en la tienda**:
solo lo ve el panel.

Se cuenta en el servidor y no con analítica del navegador porque la cifra se usa
para decidir qué promocionar y qué retirar, y un bloqueador de anuncios no puede
alterar una decisión de inventario.

Se guarda un acumulado y no una fila por visita porque un catálogo público
genera visitas sin límite, y la pregunta que hay que responder —«qué se mira
más»— no necesita el detalle. El día que haga falta la evolución en el tiempo se
añade la tabla de eventos y esta columna se queda como total.

No se expone en la tienda porque «1 243 personas vieron esto» es una afirmación
sobre el negocio que nadie ha decidido publicar.

### RN-086 — Nada se borra: se marca como eliminado *(decisión)*
Toda entidad administrable —producto, categoría, subcategoría, marca, cupón,
administrador— lleva `eliminado_en` y `eliminado_por`. `DELETE` los rellena;
no ejecuta un `DELETE` de SQL.

Tres razones, en orden de peso:

1. **El histórico se rompe al borrar.** Una orden de hace seis meses apunta a
   un producto. Borrarlo de verdad deja la orden hablando de algo que no
   existe, o impide borrarlo por la clave foránea —y entonces el botón miente.
2. **Hay que saber quién fue.** «Esto estaba aquí la semana pasada» es una
   pregunta que se hace sola en cuanto hay más de un administrador. Sin
   `eliminado_por` no hay respuesta.
3. **Los errores se deshacen.** Se restaura desde la papelera
   (`POST /admin/papelera/restaurar`) y vuelve **desactivado**: restaurar no es
   republicar, y volver a la tienda tiene que ser una decisión aparte.

Lo eliminado desaparece de toda consulta —`@SQLRestriction` en la entidad— y
consultarlo por id da `404`, igual que si no existiera. Lo único que lo ve es
la papelera.

**El nombre y el slug se liberan al eliminar**; el SKU, el código de cupón y el
usuario de administrador **no**. La diferencia no es capricho: los tres últimos
aparecen copiados en el histórico —líneas de orden, cupón aplicado, autor de un
mensaje— y reutilizarlos haría que dos cosas distintas se llamaran igual en un
registro que ya no se puede cambiar.

Quedan fuera, a propósito: `orden` y `carrito` (no se eliminan nunca; una orden
se cancela), `conversacion` (se cierra) y `archivo` (es inmutable y solo se
retira cuando ya no lo referencia nadie).

→ `DELETE`: `204 No Content`. Restaurar algo no eliminado: `409`.

### RN-087 — Toda eliminación se confirma antes *(decisión)*
Ningún `DELETE` sale del panel sin que el administrador haya confirmado en un
diálogo que dice **qué** se va a eliminar y **qué pasa después**. No un «¿estás
seguro?» a secas: el diálogo nombra la cosa y recuerda que queda en la papelera.

Es una regla de producto y no de implementación porque cambia el resultado: un
borrado accidental cuesta una restauración y una explicación, y el clic de más
es más barato que las dos cosas.

### RN-088 — Un adjunto de chat es imagen o PDF, y se revisa antes *(decisión)*
El chat admite exactamente dos cosas: una imagen —que se convierte a WebP como
las del catálogo, con sus metadatos EXIF borrados— o un PDF.

Lo que decide es el **contenido**, no la extensión ni el `Content-Type` que
declare el navegador (RN-070). Un ejecutable renombrado a `.pdf` se rechaza.

El PDF se revisa además por dentro y se rechaza si trae contenido activo:
`/JavaScript`, `/JS`, `/Launch`, `/EmbeddedFile`, `/OpenAction`, `/AA`,
`/RichMedia` o `/XFA`. El error dice **qué construcción** lo delató, porque
«archivo no válido» no le sirve a quien tiene que volver a generarlo.

Esto **no es un antivirus**: es la lista de las formas conocidas de que un PDF
ejecute algo al abrirlo. Un PDF malicioso que explote un fallo del lector pasa
igual, y por eso se sirve siempre con `Content-Disposition: attachment` y
`X-Content-Type-Options: nosniff`: el navegador lo descarga, no lo interpreta.

Un adjunto pertenece a **un** mensaje. Uno ya enviado no se puede reutilizar en
otro: si se pudiera, alguien que adivine un id colgaría en su conversación un
archivo de otra.

→ Tipo no admitido: `422 UNSUPPORTED_ATTACHMENT_TYPE`.
→ PDF con contenido activo: `422 DANGEROUS_ATTACHMENT`.

### RN-089 — Una alerta se publica una vez y se cierra sola *(decisión)*
Las notificaciones del panel llevan clave de unicidad. Mientras una siga sin
leer, la misma alerta no se duplica: «producto 42 sin stock» es una
notificación, no una por cada vez que alguien guarda el producto.

Y cuando el hecho que la motivó deja de ser cierto, **se cierra sin que nadie
la lea**: reponer stock retira el aviso de agotado, y abrir una conversación
retira el de mensaje pendiente. Si hubiera que cerrarlas a mano, la bandeja se
llenaría de alertas ya resueltas y dejaría de mirarse — que es la única forma
de que un sistema de avisos falle del todo.

Las notificaciones son del **equipo**, no de cada administrador: leerla la
marca leída para todos. Con un equipo pequeño es lo que se espera; lo que ya
atendió alguien no tiene que volver a aparecerle a otro.

---

## Índice de reglas

| ID | Regla | Resultado | Origen |
| --- | --- | --- | --- |
| RN-001 | Producto en subcategoría activa | `404` / `422` | decisión |
| RN-002 | SKU único e inmutable | `409` / `422` | decisión |
| RN-003 | Precio positivo, 2 decimales | `400` | derivada |
| RN-004 | Precio anterior mayor que precio | `422` | decisión |
| RN-005 | Stock no negativo | `400` | derivada |
| RN-006 | Producto inactivo no se vende | `422` | decisión |
| RN-007 | Productos no se borran (ver RN-086) | `204` | derivada |
| RN-008 | Slug estable al renombrar | `409` | decisión |
| RN-009 | Imagen obligatoria para publicar | `422` | decisión |
| RN-010 | Nombres únicos | `409` | derivada |
| RN-011 | No desactivar lo que tiene contenido | `409` | decisión |
| RN-012 | Sin cascada automática | — | decisión |
| RN-013 | Marca opcional | — | decisión |
| RN-014 | Orden explícito del menú | — | decisión |
| RN-020 | Solo lo publicable en la tienda | — | derivada |
| RN-021 | Búsqueda sin acentos ni mayúsculas | — | decisión |
| RN-022 | Filtros combinables | `422` | derivada |
| RN-023 | Ordenamiento acotado y total | `400` | decisión |
| RN-024 | Página ≤ 60, rechazada no recortada | `400` | derivada |
| RN-025 | Relacionados por subcategoría | — | decisión |
| RN-026 | Los comodines del usuario se escapan | — | decisión |
| RN-030 | No superar el stock | `409` | derivada |
| RN-031 | Un producto, una línea | — | derivada |
| RN-032 | Precio vigente en el carrito | — | decisión |
| RN-033 | Toda mutación devuelve el carrito | — | decisión |
| RN-034 | Cantidad entre 1 y 99; `DELETE` elimina | `400` | decisión |
| RN-035 | Carrito sin caducidad, purga a 90 días | — | decisión |
| RN-036 | Fusión de carritos al acceder | — | decisión |
| RN-037 | Un ítem solo se toca desde su carrito | `404` | decisión |
| RN-040 | Vigencia del cupón | `422` | derivada |
| RN-041 | Compra mínima | `422` | derivada |
| RN-042 | Usos contados al comprar | `422` | derivada |
| RN-043 | Un cupón por carrito | — | decisión |
| RN-044 | Descuento no supera el subtotal | — | decisión |
| RN-045 | Cupón que deja de aplicar sigue vinculado | — | decisión |
| RN-050 | Orden atómica | — | derivada |
| RN-051 | Revalidación total en checkout | `409` / `422` | decisión |
| RN-052 | La orden copia, no referencia | — | decisión |
| RN-053 | Importes inmutables | — | derivada |
| RN-054 | Transiciones válidas | `422` | decisión |
| RN-055 | Cancelar devuelve stock | — | decisión |
| RN-056 | Correo de contacto obligatorio | `400` | derivada |
| RN-057 | Número de orden no adivinable | — | decisión |
| RN-060 | Correo identifica al cliente | — | decisión |
| RN-061 | Registro no revela existencia | `202` | decisión |
| RN-062 | Contraseña ≥ 10 caracteres | `400` | decisión |
| RN-063 | Comprar sin verificar | — | decisión |
| RN-064 | Vinculación solo con correo verificado | `422` | decisión |
| RN-065 | Nunca sin forma de entrar | `422` | decisión |
| RN-066 | Recuperación con respuesta idéntica | `202` | decisión |
| RN-067 | Fallos de acceso indistinguibles | `401` | decisión |
| RN-068 | Límite de intentos | `429` | decisión |
| RN-070 | Validar por contenido | `422` | decisión |
| RN-071 | Límites de tamaño y dimensión | `422` | decisión |
| RN-072 | Conversión a WebP | — | decisión |
| RN-073 | Tres variantes | — | decisión |
| RN-074 | Texto alternativo obligatorio | `400` | decisión |
| RN-075 | Imágenes inmutables | — | decisión |
| RN-080 | Sin detalles internos en errores | `500` | derivada |
| RN-081 | Correlación en toda respuesta | — | decisión |
| RN-082 | Escrituras atómicas | — | derivada |
| RN-083 | Efectos externos tras el commit | — | decisión |
| RN-084 | Importes decimales exactos | — | derivada |
| RN-085 | Las vistas de producto se cuentan en el servidor | — | decisión |
| RN-086 | Nada se borra: se marca como eliminado | `204` | decisión |
| RN-087 | Toda eliminación se confirma antes | — | decisión |
| RN-088 | Adjunto de chat: imagen o PDF, revisado | `422` | decisión |
| RN-089 | Una alerta se publica una vez y se cierra sola | — | decisión |

---

## Preguntas abiertas

1. **IGV.** Los precios lo incluyen y se desglosa en el resumen. ¿Es 18% fijo o
   configurable? ¿Hay productos exentos?
2. **Stock reservado.** Hoy el stock se descuenta al crear la orden. ¿Hace falta
   reservarlo mientras el comprador está en el checkout? Con pagos reales sí;
   sin ellos, no.
3. **Devoluciones.** Fuera de alcance. Si entran, `CANCELADA` no basta: hace
   falta un estado `DEVUELTA` con su propio movimiento de stock.
4. **Histórico de precios.** No se guarda. La orden copia el precio, así que el
   histórico contable existe; el de catálogo no.
5. **Múltiples imágenes por marca o categoría.** Hoy una sola. ¿Hará falta un
   banner además del logo?
