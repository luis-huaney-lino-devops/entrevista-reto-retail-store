# Estado y brecha

**Qué existe hoy y qué falta.** Este documento manda sobre cualquier otro en lo
que respecta al estado: el resto de `docs/` describe el objetivo.

Actualizado tras cerrar el panel de clientes, el chat de atención por
WebSocket, las notificaciones y la papelera.

---

## 1. Resumen

| Bloque | Estado |
| --- | --- |
| Esquema en español con la convención `id_<tabla>` / `fk_id_<tabla>` | **Hecho** (`V001`–`V008`) |
| Identidad del panel: acceso, JWT con audiencia, refresco rotativo, límite de intentos | **Hecho** |
| Administradores: alta, edición, roles, restablecer contraseña | **Hecho** |
| Marcas, categorías, subcategorías: CRUD y reglas de dependencia | **Hecho** |
| Productos: CRUD, SKU, precios, stock, galería, publicación | **Hecho** |
| Archivos: subida, validación por contenido, WebP, cuatro variantes, R2 o disco | **Hecho** |
| Cupones: CRUD y evaluación en el carrito | **Hecho** |
| Carrito de la tienda | **Hecho** |
| Órdenes: consulta y cambio de estado desde el panel | **Hecho** |
| Vistas de producto y tablero con gráficos | **Hecho** |
| Eliminación lógica con papelera y restauración (RN-086) | **Hecho** (`V006`) |
| Clientes: ficha, historial de compras, bloqueo desde el panel | **Hecho** (`V007`) |
| Chat de atención al cliente por WebSocket, con adjuntos revisados | **Hecho** (`V008`) |
| Notificaciones del panel, empujadas en vivo | **Hecho** (`V008`) |
| Panel de administración (React) | **Hecho** |
| Clientes: registro, acceso, Google, recuperación | **Falta** |
| Checkout: crear la orden desde la tienda | **Falta** |
| Correo | **Falta** |
| Tienda (Next.js) | **Falta** |

**48 de las 68 reglas** del catálogo están implementadas. Las 20 restantes
pertenecen a la identidad del cliente, al checkout y al correo.

> Ojo con la palabra «clientes»: la **tabla** `cliente` y todo lo que el panel
> hace con ella —ficha, historial, bloqueo, conversaciones— están hechos. Lo
> que falta es que el cliente pueda **crearse una cuenta y entrar** desde la
> tienda: registro, verificación por correo, Google, recuperación.

> Las órdenes están **a medias a propósito**: las tablas, la máquina de estados
> y el panel están hechos; lo que falta es quien las crea, que es el checkout de
> la tienda. El histórico de demostración lo siembra `V005` para que el tablero
> muestre datos reales desde el primer arranque.

---

## 2. Lo que se puede hacer hoy

Con la base y el backend en marcha:

- Entrar al panel, y que la sesión sobreviva a una recarga.
- Crear la jerarquía completa: marca → categoría → subcategoría → producto.
- Subir una foto JPEG o PNG y que salga convertida a WebP en cuatro tamaños.
- Publicar un producto, y que la tienda lo devuelva por su slug.
- Despublicarlo, y que deje de existir para la tienda pero siga en el panel.
- Buscar, filtrar por precio, marca o subcategoría, ordenar y paginar.
- Crear un carrito, agregar productos, aplicar un cupón y ver los totales.
- Consultar las órdenes, moverlas de estado y cancelar devolviendo el stock.
- Ver el tablero: ventas por día, órdenes por estado, productos más vistos y
  más vendidos, y stock bajo.
- Eliminar cualquier cosa y recuperarla: desaparece del panel y de la tienda,
  queda en la papelera con quién y cuándo, y vuelve **desactivada** (RN-086).
- Ver la ficha de un cliente con sus compras, y bloquearle el acceso sin perder
  nada de lo que hizo.
- Chatear con un cliente en vivo desde la orden o desde su ficha, adjuntando
  una imagen o un PDF que el servidor revisa antes de aceptar (RN-088).
- Enterarse de lo que pasa sin recargar: la campana recibe los avisos por el
  mismo WebSocket, y un aviso de stock se cierra solo al reponer (RN-089).

Lo verifican dos pruebas de extremo a extremo contra PostgreSQL real:
`pruebas/humo-api.py` (**123 comprobaciones**) y `pruebas/humo-chat.py`
(**57**). Las dos son idempotentes: montan el escenario que necesitan en vez de
confiar en el estado que dejó la anterior.

---

## 3. Reglas implementadas

| Bloque | Reglas | Dónde |
| --- | --- | --- |
| Catálogo | RN-001 … RN-009 | `Producto`, `ServicioProducto`, restricciones de `V001` |
| Jerarquía | RN-010 … RN-014 | `ServicioMarca`, `ServicioCategoria`, `ServicioSubcategoria`, `Dependientes` |
| Búsqueda | RN-020 … RN-026 | `ProductoSpecs`, `OrdenProducto`, `BusquedaProductoPeticion` |
| Carrito | RN-030 … RN-035, RN-037 | `Carrito`, `ServicioCarrito` |
| Cupones | RN-040, RN-041, RN-043, RN-044, RN-045 | `Cupon`, `CalculadoraCarrito` |
| Contraseñas y acceso | RN-062, RN-067, RN-068 | `PoliticaContrasena`, `ServicioAccesoPanel`, `LimitadorIntentos` |
| Imágenes | RN-070 … RN-075 | `InspectorImagen`, `ProcesadorImagen`, `ServicioArchivo` |
| Órdenes | RN-053, RN-054, RN-055 | `Orden`, `EstadoOrden`, `ServicioOrden` |
| Eliminación lógica | RN-007, RN-086, RN-087 | `EntidadEliminable`, `ServicioPapelera`, `V006`, `Confirmar.tsx` |
| Chat y adjuntos | RN-088 | `ServicioChat`, `InspectorAdjunto`, `ManejadorChatWs` |
| Notificaciones | RN-089 | `ServicioNotificacion`, `AvisosDeStock`, `uq_notificacion_pendiente` |
| Transversales | RN-080 … RN-085 | `ManejadorGlobalErrores`, `Correlacion`, `@Transactional`, `BigDecimal`, `Producto.vistas`, `RegistroSesionesWs` |

### Parciales, con su motivo

| Regla | Qué falta | Por qué |
| --- | --- | --- |
| RN-042 (límite de usos del cupón) | Nadie incrementa `usos_actuales` | Se incrementa al **confirmar la compra**, y no hay checkout todavía. `Cupon.registrarUso()` ya existe y está probado |
| RN-070 (validar por contenido) | AVIF se rechaza en lugar de aceptarse | La JVM no trae decodificador de AVIF. Aceptarlo a medias sería peor: se rechaza con un mensaje claro |
| RN-083 (efectos externos tras el commit) | Solo cubre el WebSocket | El correo todavía no existe. Las difusiones del chat y de las notificaciones sí van tras el commit (`RegistroSesionesWs.enviar`), y eso arregló un `409` por bloqueo optimista. La subida a R2 sigue yendo **antes** a propósito: así no queda una fila apuntando a un objeto inexistente |

---

## 4. Lo que falta, en orden

El orden no es arbitrario: cada bloque necesita el anterior.

### Bloque 1 — Clientes e identidad de la tienda

Tablas `cliente`, `identidad_externa`, `token_un_solo_uso`. Registro que no
revela si el correo existe (RN-061), verificación por correo, recuperación de
contraseña, acceso con Google por Authorization Code + PKCE con la
comprobación de `email_verified` (RN-064).

Reglas: RN-060 … RN-066.
Diseño ya escrito: [`backend/autenticacion.md`](backend/autenticacion.md).

### Bloque 2 — Correo

Plantillas, envío asíncrono, Mailpit en desarrollo. Va después de clientes
porque su primer uso es el correo de verificación.

Regla: RN-083.
Diseño ya escrito: [`backend/correo.md`](backend/correo.md).

### Bloque 3 — Checkout

Lo único que falta de las órdenes: **quién las crea**. Transacción que revalida
todo, descuenta stock, incrementa el uso del cupón y **copia** nombres y
precios en las líneas.

Las tablas, la máquina de estados, el panel y la devolución de stock al
cancelar ya están.

Reglas: RN-050, RN-051, RN-052, RN-056, RN-057, y cierra RN-042.

### Bloque 4 — Tienda (Next.js)

Portada, listado, ficha, carrito y checkout. SSR para el catálogo,
cliente para el carrito.
Diseño ya escrito: [`frontend/estandar-storefront.md`](frontend/estandar-storefront.md).

### Bloque 5 — Fusión de carritos

RN-036: al iniciar sesión, el carrito anónimo se funde con el del cliente.
Necesita clientes y carrito, los dos ya listos salvo el enlace
`carrito.fk_id_cliente`.

---

## 5. Deuda conocida

Cosas que funcionan y que conviene tener escritas.

| # | Qué | Impacto |
| --- | --- | --- |
| D-1 | El límite de intentos vive en la memoria del proceso | Con varias instancias el límite se multiplica por el número de instancias. Se sustituye por Redis sin tocar quien lo usa |
| D-2 | Las imágenes de la semilla apuntan a `picsum.photos` | No existen en el almacén. Son marcadores para que la tienda se vea poblada; una subida real las reemplaza |
| D-3 | No hay limpieza de objetos huérfanos en R2 | Una transacción que revierte tras subir deja objetos sin fila. Ocupan espacio y no rompen nada; falta la tarea programada de `archivos-imagenes.md` §5 |
| D-4 | `contracts/openapi.yaml` se genera, no se escribe | El diseño se discute en `docs/`; el contrato refleja lo implementado. Hay que regenerarlo al cambiar un endpoint |
| D-5 | Sin pruebas de integración con base de datos | Lo cubre `pruebas/humo-api.py`, que es una prueba de extremo a extremo y no corre en `mvn test`. Testcontainers cerraría el hueco |
| D-6 | El contador de vistas es un `UPDATE` por visita | Con mucho tráfico es contención sobre la fila de los productos populares. La salida es acumular en memoria y volcar cada cierto tiempo, no quitar la métrica |
| D-7 | El histórico de órdenes de `V005` es de demostración | Son datos sembrados, no ventas reales. En un despliegue de verdad esa migración no debería aplicarse |
| D-8 | Las notificaciones son del equipo, no por administrador | Leerla la marca leída para todos. Es lo que se quiere con un equipo pequeño; cuando crezca se añade una tabla de lecturas sin tocar quien las produce |
| D-9 | `InspectorAdjunto` **no es un antivirus** | Cubre las formas conocidas de que un PDF ejecute algo al abrirlo, no un fallo del lector. Por eso el PDF se sirve con `Content-Disposition: attachment` y `nosniff` |
| D-10 | La papelera no caduca | Lo eliminado se queda ahí para siempre. Hace falta una política de retención antes de que la tabla crezca de verdad |
| D-11 | El chat no tiene histórico paginado | Un hilo se carga entero. Con conversaciones largas habrá que paginar hacia atrás |

### Defectos cerrados

Los tres que estaban abiertos en la versión anterior de este documento:

- **`GenerationType.IDENTITY` sobre un UUID** — corregido; `Carrito` usa `GenerationType.UUID`.
- **`Instant.now()` dentro de `@PrePersist`** — eliminado. Las fechas las pone la auditoría de Spring Data desde el `Clock` inyectable.
- **Comparación de entidades por id sin comprobar nulos** — el patrón correcto está en `Carrito.esElMismoProducto` y en `Producto.esMismo`, y hay una prueba que falla si alguien lo «simplifica» con `Objects.equals`.

Y dos encontrados y corregidos, que merecen quedar escritos:

- **La respuesta de una actualización devolvía la auditoría anterior.** El DTO
  se armaba dentro de la transacción, y `actualizadoPor`, `actualizadoEn` y
  `version` los escribe el listener de auditoría cuando Hibernate vuelca, que
  ocurre después. El panel mostraba «modificado por sistema» justo después de
  que `admin` lo modificara. Se arregla volcando antes de mapear. Lo detectó la
  prueba de humo; ninguna unitaria podía verlo porque no hay transacción.


- **La revocación de una familia de tokens se deshacía con el rechazo.** Al
  detectar la reutilización de un refresco había que revocar la familia *y*
  devolver 401; ambas cosas en la misma transacción significan que el rollback
  anula la revocación, y la sesión robada seguía viva. Lo arregla
  `RevocacionInmediata` con `REQUIRES_NEW`. Lo detectó la prueba de humo, no
  las unitarias: es un fallo que solo existe con una transacción real.

Y cuatro más de esta tanda, todos encontrados por las pruebas de humo:

- **El mensaje se difundía antes del commit.** Quien lo recibía respondía al
  instante, y su respuesta leía una conversación que todavía no se había
  escrito: `ObjectOptimisticLockingFailureException` en un hilo que nadie más
  estaba tocando. Se difunde en `afterCommit` (RN-083).

- **`saveAndFlush` sobre una entidad gestionada hacía `merge`**, que persiste
  una *copia* de los hijos nuevos. El `Mensaje` original se quedaba sin id y el
  WebSocket entregaba `{"id": null}`. Dieciséis llamadas pasaron a `flush()`.

- **`ServicioProducto.crear` nunca persistía el producto.** El barrido anterior
  quitó también el `save()` de la única entidad que sí era nueva: la API
  devolvía `201` con `id: null` y no insertaba nada. El fallo se veía además en
  cadena —el SKU duplicado ya no chocaba, porque el primero no existía.

- **Guardar un producto sin tocarle las fotos fallaba con `500`.**
  `reemplazarImagenes` vaciaba la colección y la reconstruía, y Hibernate
  ejecuta los `INSERT` antes que los `DELETE` en el mismo volcado: reenviar la
  misma imagen chocaba contra `uq_producto_imagen`. Ahora reconcilia.

- **Enviar dos veces el mismo adjunto daba `500`.** La clave del objeto era el
  hash de su contenido, así que el segundo envío colisionaba con
  `uq_adjunto_clave`. El catálogo deduplica imágenes a propósito; el chat no
  puede, porque cada adjunto pertenece a un mensaje y solo a uno.
