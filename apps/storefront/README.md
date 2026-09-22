# Tienda pública — `apps/storefront`

Next.js 15 (App Router) + TypeScript en modo estricto + Tailwind. Consume la API
REST de `backend/java`; **no contiene ni una regla de negocio** (ADR-0001).

```bash
npm install
npm run dev        # http://localhost:3000
npm run build      # Next compila y comprueba los tipos
npm run typecheck
```

`.env.local` sale de `.env.example`. La API tiene que estar levantada en
`http://localhost:8080` para que el catálogo se renderice.

---

## Rutas

| Ruta | Render | Qué es |
| --- | --- | --- |
| `/` | ISR 60 s | Portada: categorías, destacados, ofertas, novedades |
| `/productos` | SSR dinámico | Catálogo filtrable. `noindex, follow` si hay filtros |
| `/productos/[slug]` | ISR 300 s + `generateStaticParams` | Ficha: galería, Markdown, relacionados, JSON-LD |
| `/c/[categoria]` | ISR 600 s | Hub de categoría: subcategorías y lo más valorado |
| `/c/[categoria]/[subcategoria]` | ISR 600 s | Aterrizaje indexable del listado |
| `/carrito` | Cliente | Resumen, cantidades, cupón |
| `/favoritos` | Cliente | Corazones guardados |
| `/comparar` | Cliente | Tabla de hasta 4 productos |
| `/acceso`, `/registro` | Cliente | Acceso y alta, con Google opcional |
| `/mi-cuenta`, `/mi-cuenta/direcciones`, `/mi-cuenta/pedidos` | Cliente | Zona privada |
| `/sitemap.xml`, `/robots.txt` | Generados desde la API | SEO |

---

## Por qué Next y no una SPA

Next.js **es** React; se dice explícitamente para que no se lea como «no usó
React». Lo que aporta aquí es que el catálogo llega renderizado: el comprador
que entra por un enlace en 4G recibe HTML con nombre, precio e imagen en el
primer viaje, y el rastreador no se encuentra un `<div id="root">` vacío.

El manejo de estado que pide el reto está donde se ve: el carrito es un
`useReducer` con una tabla de transiciones explícita
(`funcionalidades/carrito/reductor.ts`).

---

## Las cinco decisiones que explican el código

**Por defecto todo es Server Component.** `'use client'` está en las hojas, no
en los layouts: la tarjeta de producto se renderiza en el servidor y solo sus
tres botones son una isla. Marcar el layout como cliente convertiría todo su
subárbol en cliente y se perdería la razón de haber elegido Next.

**Dos URL de API, en dos archivos.** `lib/api.servidor.ts` lleva `server-only`:
si alguien lo importa desde un componente cliente, falla la compilación en lugar
de filtrar la URL interna al HTML. `lib/api.cliente.ts` es el del navegador, con
`credentials: 'include'` para que viaje la cookie de refresco.

**El token de acceso vive en memoria**, nunca en `localStorage`, y la sesión se
recupera al arrancar con un único refresco (rota: si salen tres a la vez, el
backend lo lee como robo y revoca la familia). Un `401` al arrancar no es un
error: es el visitante normal.

**El carrito es optimista y el servidor es la fuente de verdad.** El reducer
recalcula `totalLinea` y `subtotal` —aritmética— pero **nunca el descuento del
cupón**, que es una regla de negocio. Cada mutación lleva un `seq`: las
respuestas rezagadas se descartan para que el carrito no retroceda solo.

**Nada que dependa de `localStorage` se renderiza con datos en el servidor.**
Los contadores de la cabecera aparecen tras montar; el primer render del cliente
es idéntico al del servidor y la hidratación cuadra. `suppressHydrationWarning`
no es una solución: silencia el aviso y deja el problema.

---

## Estructura

```text
app/                    rutas (segmentos de URL, en español)
componentes/
  disposicion/          cabecera, pie, menús, buscador
  producto/             tarjeta, rejilla, galería, precio, markdown
  carrito/              panel lateral, línea, selector de cantidad
  filtros/              panel de filtros, orden, paginación
  cuenta/               formulario de dirección, mapa, zona privada
  ui/                   botón, campo, panel modal, estados
funcionalidades/
  carrito/              reductor + provider + api
  sesion/               provider de sesión
  favoritos/            localStorage con subida al iniciar sesión
  comparador/           solo cliente, localStorage
  avisos/               toasts
lib/                    tipos, clientes HTTP, formato, errores, imágenes, JSON-LD
```

---

## Detalles que costaría descubrir solo

**El `POST /carritos/{id}/items` devuelve la línea nueva con `id: null`.** Una
línea sin id no se puede modificar después, porque el `PATCH` y el `DELETE` van
a ese id. `funcionalidades/carrito/api.ts` lo detecta y vuelve a pedir el
carrito completo. Es una vuelta de más, solo en el alta.

**`orden` es una lista cerrada de cinco valores** (`recientes`, `precio_asc`,
`precio_desc`, `nombre_asc`, `calificacion`). Cualquier otro da `400`.

**`tamanoPagina` no puede superar 48** y `cantidad` de una línea no puede
superar 99. Los dos topes están en `lib/tipos.ts`.

**No hay filtro de ofertas en la API.** La portada pide la página máxima y se
queda con los que traen `porcentajeDescuento`. No es un cálculo: es una
selección de lo que ya vino.

**El sembrado deja categorías técnicas** (`categoria-prueba-*`,
`categoria-gemela-*`) sin imagen ni contenido. `esCategoriaDeTienda` las excluye
de los menús y del sitemap.

**Leaflet toca `window` al inicializarse**, así que el mapa se carga con
`next/dynamic` y `ssr: false`; y su icono por defecto se construye con SVG en
`data:` porque las rutas de sus PNG no sobreviven al empaquetado.

---

## Lo que queda abierto

**`notFound()` devuelve `200` en lugar de `404`.** Un producto que no existe
renderiza correctamente `app/not-found.tsx` —el usuario ve la página de «no
encontramos esta página»— pero el estado HTTP que sale es `200`. Lo que el
buscador necesita ver es un `404`, así que esto es una deuda real y conviene no
darla por resuelta.

No viene del código de la tienda: se reproduce con una ruta mínima de cinco
líneas que solo llama a `notFound()`, en Next 15.5.25 y en 15.3.5, con
`error.tsx` y sin él. Una ruta que no existe en absoluto (`/nada-de-nada`) sí da
`404`, así que el router funciona; lo que no propaga el estado es `notFound()`
desde dentro de una página. Se dejó la llamada tal cual —es la API documentada y
es lo correcto— para que el día que Next arregle la propagación no haya que
tocar nada.

Se descartó pinchar una versión anterior: la que se probó (15.3.5) arrastra
CVE-2025-66478 y tampoco arreglaba el estado.

**No hay checkout.** La API no publica `POST /ordenes` para el cliente, así que
el botón «Continuar la compra» está deshabilitado y lo dice. Inventar un flujo
de pago que no existe habría sido peor que no tenerlo.

**No hay pruebas automatizadas.** El reducer del carrito es una función pura y
es lo primero que debería cubrirse con Vitest: `SINCRONIZADO` con `seq` viejo,
el rollback de `FALLO` y que `AGREGAR` sume en vez de duplicar la línea.

**Sin verificar a mano:** el acceso con Google (hace falta un
`NEXT_PUBLIC_GOOGLE_CLIENT_ID` real) y el guardado de direcciones contra la API
de cuenta, que apareció mientras se construía esta tienda.
