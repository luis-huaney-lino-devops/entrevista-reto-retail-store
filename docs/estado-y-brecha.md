# Estado y brecha

**Qué existe hoy y qué falta.** Este documento manda sobre cualquier otro en lo
que respecta al estado: el resto de `docs/` describe el objetivo.

Actualizado tras cerrar la identidad del cliente de la tienda: registro,
acceso, acceso con Google, direcciones con ubigeo y favoritos.

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
| Identidad de la tienda: registro, acceso, Google, sesión con refresco rotativo | **Hecho** |
| Ubigeo, direcciones del cliente y favoritos | **Hecho** |
| Clientes: verificación de correo y recuperación de contraseña | **Falta** (necesita correo) |
| Checkout: crear la orden desde la tienda | **Falta** |
| Correo | **Falta** |
| Tienda (Next.js) | **Falta** |

**53 de las 68 reglas** del catálogo están implementadas. Las restantes
pertenecen al checkout y al correo.

> Ojo con la palabra «clientes»: la **tabla** `cliente` y todo lo que el panel
> hace con ella —ficha, historial, bloqueo, conversaciones— están hechos, y
> ahora también lo está que el cliente se cree una cuenta y entre desde la
> tienda. Lo que falta de identidad es lo que **necesita correo**: verificar la
> dirección (RN-063) y recuperar la contraseña (RN-066).

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
- Crear una cuenta en la tienda con correo y contraseña, y que el endpoint
  responda lo mismo exista o no el correo (RN-061).
- Entrar, y que la sesión se renueve sola con una cookie `httpOnly` que rota en
  cada uso y tumba la familia entera si alguien reutiliza una vieja.
- Entrar con Google: se verifica la firma contra el JWKS del proveedor, y si el
  correo ya tiene cuenta **se vincula** en vez de crear otra (RN-064).
- Editar el perfil, y establecer la primera contraseña si se entró con Google
  sin que se pida «la actual», que nunca existió (RN-065).
- Elegir departamento, provincia y distrito de tres listas encadenadas, y
  guardar direcciones con esas coordenadas o sin ellas.
- Tener favoritos que sobreviven al cambio de dispositivo, con un corazón que
  se puede pulsar dos veces sin que pase nada.

Lo verifican tres pruebas de extremo a extremo contra PostgreSQL real:
`pruebas/humo-api.py` (**125-128 comprobaciones**), `pruebas/humo-chat.py`
(**60**) y `pruebas/humo-cuenta.py` (**108**). Las tres son idempotentes:
montan el escenario que necesitan en vez de confiar en el estado que dejó la
anterior.

> El total de `humo-api.py` no es fijo y no es un fallo: su tramo de
> cancelación solo corre si la orden que le toca admite `CANCELADA`, y las
> ejecuciones repetidas van dejando las órdenes sembradas en estados que ya no
> lo admiten. Lo que hay que mirar es `FALLAN 0`.

---

## 3. Reglas implementadas

| Bloque | Reglas | Dónde |
| --- | --- | --- |
| Catálogo | RN-001 … RN-009 | `Producto`, `ServicioProducto`, restricciones de `V001` |
| Jerarquía | RN-010 … RN-014 | `ServicioMarca`, `ServicioCategoria`, `ServicioSubcategoria`, `Dependientes` |
| Búsqueda | RN-020 … RN-026 | `ProductoSpecs`, `OrdenProducto`, `BusquedaProductoPeticion` |
| Carrito | RN-030 … RN-035, RN-037 | `Carrito`, `ServicioCarrito` |
| Cupones | RN-040, RN-041, RN-043, RN-044, RN-045 | `Cupon`, `CalculadoraCarrito` |
| Contraseñas y acceso | RN-062, RN-067, RN-068 | `PoliticaContrasena`, `ServicioAccesoPanel`, `ServicioAccesoTienda`, `LimitadorIntentos` |
| Identidad del cliente | RN-060, RN-061, RN-064, RN-065 | `Cliente`, `ServicioAccesoTienda`, `ServicioAccesoGoogle`, `VerificadorTokenGoogle`, `ServicioCuenta` |
| Direcciones y favoritos | RN-086 | `ServicioDireccion`, `ServicioFavorito`, `ServicioUbigeo` |
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
| RN-061 (el registro no revela si el correo existe) | El correo «alguien intentó registrarse con tu correo» no se envía | La respuesta ya es idéntica en código, cuerpo y tiempo -se gasta el BCrypt en los dos caminos-. Lo que falta es el aviso al dueño legítimo, y necesita el bloque de correo |
| RN-063 (comprar no exige verificar el correo) | La mitad que **sí** exige verificación -cambiar la contraseña, ver el historial- no se aplica | No hay forma de verificar: el endpoint de verificación y el correo llegan con el bloque 2. Exigirlo hoy dejaría a todo el mundo sin poder cambiar su contraseña |
| RN-064 (vinculación solo con correo verificado) | Se aplica además al **crear**, no solo al vincular | Una cuenta creada por Google nace con `emailVerificado = true`; crearla con un correo que Google no verificó sería marcar como verificado algo que nadie verificó, y ocupar el correo de otra persona. Es más estricto que la tabla de `autenticacion.md` §4 a propósito |
| RN-065 (nunca sin forma de entrar) | Solo la mitad de «establecer contraseña»; falta desvincular Google | No hay endpoint de desvinculación en el contrato de la tienda. Cuando lo haya, es donde entra `LAST_LOGIN_METHOD` |
| RN-083 (efectos externos tras el commit) | Solo cubre el WebSocket | El correo todavía no existe. Las difusiones del chat y de las notificaciones sí van tras el commit (`RegistroSesionesWs.enviar`), y eso arregló un `409` por bloqueo optimista. La subida a R2 sigue yendo **antes** a propósito: así no queda una fila apuntando a un objeto inexistente |

---

## 4. Lo que falta, en orden

El orden no es arbitrario: cada bloque necesita el anterior.

### Bloque 1 — Verificación de correo y recuperación de contraseña

Lo único que queda de la identidad del cliente, y va después del correo porque
**es** correo: el enlace de verificación (RN-063) y el de recuperación
(RN-066), los dos sobre la tabla `token_cliente`, que hoy no está mapeada
porque ningún endpoint la usaría.

Reglas: RN-063 (la mitad que exige verificación), RN-066.
Diseño ya escrito: [`backend/autenticacion.md`](backend/autenticacion.md) §3 y §5.

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
| D-12 | `contracts/openapi.yaml` no cubre todavía `/cuenta`, `/ubigeo` ni los favoritos | El contrato vive en el código y en Swagger (`/swagger`), que sí los sirve. Hay que regenerar el YAML — ver D-4 |
| D-13 | La IP del limitador sale del **primer** valor de `X-Forwarded-For` | Si el proxy añade la IP real detrás de lo que mandó el cliente, el primer valor lo elige el cliente y el límite por IP se puede esquivar cambiando una cabecera. El límite por correo -que es el que protege la cuenta- no se esquiva así. Lo correcto es leer desde la derecha descartando los proxies conocidos; está igual en el panel y en la tienda |
| D-15 | `humo-chat.py` da por hecho que el **primer** cliente de `GET /admin/clientes` tiene compras | La lista va por nombre ascendente, y desde que la tienda puede registrar clientes, cualquier cuenta nueva sin órdenes cuyo nombre ordene antes que «Ana Torres» tumba esas dos comprobaciones. Era seguro cuando los clientes solo venían de la semilla. El arreglo es elegir un cliente con `ordenes > 0` en vez de `items[0]`, y está en un archivo que esta tanda no podía tocar |
| D-14 | `token_cliente` está en el esquema y sin mapear | Es la tabla de los tokens de un solo uso. No se mapea porque no hay endpoint que los consuma: llega con el bloque de verificación y recuperación |

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
