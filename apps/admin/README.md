# Panel de administración

React 19 + Vite + TypeScript. Renderizado en el cliente.

---

## 1. Levantarlo

```bash
npm install
npm run dev
```

En `http://localhost:5173`, que abre en **`/productos`**. Necesita el backend en marcha
(ver [`backend/java/README.md`](../../backend/java/README.md)).

Acceso inicial: **`admin` / `AdminRetail2026!`**.

| Comando | Qué hace |
| --- | --- |
| `npm run dev` | Servidor de desarrollo con recarga en caliente |
| `npm run build` | Comprueba tipos y compila a `dist/` |
| `npm run preview` | Sirve `dist/` para revisar la compilación |
| `npm run typecheck` | Solo TypeScript, sin compilar |

---

## 2. Variables de entorno

Una sola, y esa es la idea: **el panel habla con un backend y se configura en un
único sitio.**

| Variable | Por omisión | Para qué |
| --- | --- | --- |
| `VITE_API_URL` | `http://localhost:8080/api/v1` | URL base de la API |

### Cómo se fija

```bash
cp .env.example .env.local
# editar VITE_API_URL
```

Vite carga `.env.local` automáticamente y **no** se versiona. Para una
compilación puntual:

```bash
VITE_API_URL=https://api.midominio.com/api/v1 npm run build
```

```powershell
$env:VITE_API_URL = 'https://api.midominio.com/api/v1'; npm run build
```

> **Todo lo que empieza por `VITE_` acaba dentro del JavaScript que descarga el
> navegador.** Es texto público. Nunca pongas ahí una clave de API ni una
> contraseña: las variables sin ese prefijo quedan fuera del bundle, y ese es
> justamente el mecanismo para lo que no debe viajar.

Al cambiar un `.env` hay que **reiniciar** `npm run dev`: Vite las lee al
arrancar, no en cada recarga.

---

## 3. Qué hay en cada carpeta

```
src/
├── main.tsx              arranque: React Query, router y contexto de sesión
├── App.tsx               rutas; decide entre acceso y panel
├── estilos.css           todo el CSS, con variables
│
├── api/
│   ├── cliente.ts        fetch con token, refresco y traducción de errores
│   ├── tipos.ts          los tipos del contrato
│   └── recursos.ts       un objeto por recurso con sus endpoints
│
├── sesion/
│   └── SesionContexto.tsx  quién está dentro; entrar y salir
│
├── tiemporeal/
│   └── TiempoReal.tsx      el WebSocket del panel: una conexión para todo
│
├── componentes/            piezas reutilizables
│   ├── Disposicion.tsx     barra lateral, barra superior y marco
│   ├── Campo.tsx           etiqueta + control + error, por campo
│   ├── Toast.tsx           avisos flotantes; traduce un ErrorApi
│   ├── Aviso.tsx           el mismo error, pero fijo en el formulario
│   ├── Dialogo.tsx         modal con Escape, bloqueo de fondo y pie
│   ├── Confirmar.tsx       la confirmación previa a lo que no se deshace
│   ├── Campana.tsx         notificaciones, empujadas por el WebSocket
│   ├── BotonIcono.tsx      una acción de fila
│   ├── Combobox.tsx        select con buscador y teclado
│   ├── EditorTexto.tsx     TipTap; guarda Markdown
│   ├── CampoImagen.tsx     una imagen, con soltar y vista previa
│   ├── GaleriaImagenes.tsx varias, reordenables arrastrando
│   ├── subida.ts           límites y validación previa
│   ├── Paginacion.tsx
│   ├── SinDatos.tsx        estado vacío de una tabla
│   └── Insignia.tsx        estados, formato de soles, fechas y «hace 3 min»
│
└── paginas/                una por sección del menú
```

---

## 4. Arquitectura, y por qué así

### El token de acceso vive en memoria

En una variable de módulo de `api/cliente.ts`, nunca en `localStorage`. Lo que
está en `localStorage` lo lee cualquier script que consiga un XSS; una variable
de módulo desaparece al recargar la pestaña.

Lo que sobrevive a la recarga es la **cookie de refresco**, que es `httpOnly` y
que este código no puede leer — pero el navegador sí la envía. Por eso al
arrancar se pide `/admin/refrescar`: si la cookie sigue viva, la sesión se
recupera sin volver a pedir la contraseña.

Consecuencia práctica: **todas las peticiones llevan `credentials: 'include'`.**
Sin eso el navegador no envía la cookie a otro origen y cada F5 expulsaría a
quien está trabajando.

### Un 401 reintenta una vez

Si el token de acceso caduca a mitad de una acción —dura 15 minutos—, el
cliente refresca y repite la petición en lugar de devolver al formulario de
acceso. Una sola vez: si el refresco tampoco vale, la sesión terminó de verdad
y se limpia el estado.

### Los errores se traducen una vez, en el borde

`api/cliente.ts` convierte el `application/problem+json` del backend en un
`ErrorApi` con `codigo`, `message` y `errores[]`. A partir de ahí ninguna
pantalla interpreta respuestas HTTP: pinta `<AvisoError>` y, si el error trae
fallos por campo, `<Campo>` los muestra junto a su input.

**Eso funciona porque el nombre del campo del formulario y el del contrato son
la misma cadena** (ADR-0011). El backend responde `{"field": "precioAnterior"}`
y el formulario tiene un campo `precioAnterior`: no hay tabla de traducción que
mantener, y por tanto no hay forma de que se desincronice.

### TanStack Query para el estado del servidor

Lo que viene de la API no se guarda en `useState`: lo gestiona React Query, que
se encarga de caché, invalidación y estados de carga. Tras una mutación se
invalida la clave afectada y la lista se refresca sola.

Dos valores por omisión, deliberados:

- `refetchOnWindowFocus: false` — el panel lo usan pocas personas y los datos
  cambian poco; refrescar al volver a la pestaña solo produce parpadeos.
- `retry: false` — un 401, un 403 o un 409 no se arreglan repitiendo. El único
  reintento que tiene sentido es el del refresco, y ese ya está en el cliente.

### CSS a mano

El panel tiene una tabla, un formulario y un diálogo. Traer una librería de
componentes de 300 kB para eso cuesta más —en descarga, en dependencias y en
aprender su forma de hacer las cosas— que escribir 400 líneas de CSS con
variables.

### Los avisos van por toast, y los de formulario también se quedan

Una acción que termina bien —guardar, publicar, subir— avisa con un toast que
desaparece solo. Un error **no** desaparece solo: se queda hasta que lo cierren,
porque si el aviso de que algo falló se va a los tres segundos, quien estaba
mirando otra cosa no se entera.

Cuando el error trae fallos por campo, el toast dice cuántos y el formulario los
pinta junto a cada input. Repetir la lista completa en el toast solo añade ruido.

### El editor guarda Markdown, no HTML

Lo que se escribe en la descripción acaba renderizado en la tienda para
cualquier visitante. Con HTML almacenado habría que sanearlo en el servidor y
confiar en que el saneador no se quede corto; con Markdown, el conjunto de lo
que puede producirse está acotado por el renderizador.

La barra no tiene fuentes ni colores a propósito: una descripción que cada
quien maqueta a su gusto deja la tienda con seis tipografías. Lo que hace falta
es estructura.

### El tablero y el editor se cargan aparte

`recharts` y TipTap pesan más que todo el resto junto. Con `React.lazy`, quien
entra a corregir un precio no descarga la librería de gráficos, y el formulario
de acceso no descarga ninguna de las dos:

```
index.js        400 kB   el panel
Tablero.js      417 kB   solo al abrir el tablero
EditorTexto.js  475 kB   solo al editar un producto
```

### Una sola conexión en vivo para todo el panel

`tiemporeal/TiempoReal.tsx` abre **un** WebSocket al entrar y lo mantiene
mientras dure la sesión. Por él llegan tanto los mensajes del chat como las
notificaciones.

Una conexión por página sería más fácil de escribir y peor de usar: entrar y
salir de Conversaciones abriría y cerraría sockets continuamente, y la campana
se quedaría muda en cuanto el administrador estuviera mirando otra sección.

La credencial va en un **ticket de un solo uso** que dura treinta segundos. El
navegador no deja poner cabeceras en el apretón de manos de un WebSocket, así
que la credencial tiene que ir en la URL — y las URL acaban escritas en los
registros del servidor y del proxy. Un ticket que ya se gastó no sirve de nada
a quien lo lea ahí.

Cuando la conexión se cae reintenta con espera creciente, y mientras tanto el
panel **lo dice**: un punto gris en la cabecera del hilo y un aviso al pie de
la campana. Un contador en cero porque el canal está muerto es peor que un
contador que avisa de que no sabe.

### Enviar por REST, recibir por WebSocket

La respuesta del administrador se manda con un `POST`, no por el socket, aunque
el socket esté abierto. Así el mensaje queda guardado aunque la conexión en
vivo se haya caído sin que el navegador se haya enterado todavía, y el propio
servidor lo devuelve por el socket a quien esté mirando el hilo.

El socket sí acepta escrituras —el manejador las pasa al mismo `ServicioChat`—
porque el lado del cliente en la tienda lo necesitará. Pero teniendo las dos
vías, para el panel la fiable es la de siempre.

### Eliminar siempre pregunta, y el diálogo dice qué pasa después

`useConfirmar()` devuelve una promesa: `if (!(await confirmar(...))) return`.
Es a propósito que sea una función y no un componente que cada página monta.
Con un componente hay que acordarse de añadirlo, y el día que alguien lo olvide
el botón borra sin preguntar; así, quien borra tiene que esperar la respuesta
para continuar (RN-087).

El texto no es «¿estás seguro?». Nombra la cosa y explica que queda en la
papelera con tu nombre y la fecha — porque la duda delante de un botón rojo
acaba en un correo preguntando si se puede recuperar.

El botón que confirma **no** tiene el foco inicial: quien pulsa Intro por
inercia no debe acabar borrando algo.

### Las acciones de fila son iconos

Una tabla con «Editar · Desactivar · Eliminar» escrito en cada fila dedica más
ancho a repetir las mismas tres palabras que a los datos. El nombre sigue en
`aria-label` y en `title`, así que está para el lector de pantalla y para quien
pasa el ratón.

En pantallas con ratón los iconos se atenúan y se revelan al pasar por encima
de la fila. En táctil no hay `hover`, así que ahí se quedan siempre visibles:
la regla va dentro de `@media (hover: hover)` justamente para eso.

### Ocultar por rol no es seguridad

Un `ADMINISTRADOR` no ve la sección de administradores porque ofrecerle un
botón que solo devuelve 403 es mala interfaz. **La autorización la aplica el
backend**, que rechaza la petición aunque alguien escriba la URL a mano.

---

## 5. Qué se puede hacer desde aquí

| Sección | Operaciones |
| --- | --- |
| Tablero | Ventas por día, órdenes por estado, productos más vistos y más vendidos, stock bajo |
| Productos | Listar con búsqueda por nombre o SKU, filtro por estado y orden; crear, editar, publicar y despublicar; galería con arrastrar y soltar; descripción con editor de texto |
| Categorías | Crear, editar, ordenar, activar y desactivar. Con imagen |
| Subcategorías | Igual, y mover una subcategoría a otra categoría. Con imagen |
| Marcas | Crear, editar, activar y desactivar. Con logo |
| Órdenes | Consultar, ver el detalle y mover de estado. Cancelar devuelve el stock. Desde el detalle se ve quién la hizo y se abre el chat con esa persona |
| Clientes | Listar con búsqueda, ver la ficha con su historial de compras y sus conversaciones, escribirle, y bloquearle el acceso sin perder nada |
| Conversaciones | Bandeja por estado y chat en vivo. Adjuntar imagen o PDF. Abrir un hilo lo marca leído; cerrarlo impide que el cliente siga escribiendo |
| Papelera | Todo lo eliminado, con quién lo eliminó y cuándo. Restaurar lo devuelve desactivado |
| Cupones | Crear y editar; ver si están vigentes de verdad y cuántos usos llevan |
| Administradores | Solo superadministradores: crear cuentas y restablecer contraseñas |
| Mi cuenta | Cambiar la propia contraseña |

**Las imágenes no tienen sección propia.** Se suben desde el formulario que las
necesita —producto, marca, categoría, subcategoría— arrastrándolas sobre su
zona. Un almacén de imágenes separado obliga a subir primero, recordar cuál era
y volver al formulario a elegirla: tres pasos para lo que es uno.

Algunas cosas **no** se pueden hacer, y es a propósito:

- **Cambiar el SKU de un producto.** Es inmutable (RN-002): identifica el
  producto en inventario y en las órdenes ya emitidas.
- **Borrar de verdad cualquier cosa.** `Eliminar` hace una eliminación lógica
  (RN-086): desaparece del panel y de la tienda, pero la fila se queda con
  `eliminado_en` y `eliminado_por`, y se recupera desde la papelera. Un producto
  referenciado por una orden no puede desaparecer sin romper el histórico.

  Eliminar y **despublicar** no son lo mismo y el panel no los mezcla:
  despublicar lo retira de la tienda y lo deja a mano; eliminar lo saca de la
  vista de todos.

- **Adjuntar en el chat algo que no sea imagen o PDF.** Lo decide el contenido,
  no la extensión, y un PDF con JavaScript, `/Launch` o archivos incrustados se
  rechaza diciendo qué construcción lo delató (RN-088).
- **Desactivar algo que tiene contenido activo.** Una categoría con
  subcategorías o una subcategoría con productos se niegan con un `409` que
  lista qué lo bloquea (RN-011). Desactivar no cae en cascada: un clic que
  despublica doscientos productos es cómodo y peligroso.
- **Publicar un producto sin imagen** (RN-009).
