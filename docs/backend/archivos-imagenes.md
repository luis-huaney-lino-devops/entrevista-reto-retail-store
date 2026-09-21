# Archivos e imágenes

Subir una imagen, convertirla a WebP, generar los tamaños que la tienda
necesita y guardarla en Cloudflare R2.

El administrador sube un JPEG de 4 MB salido de un móvil. La tienda sirve un
WebP de 40 KB. Entre esas dos cosas hay una tubería que conviene diseñar
explícitamente, porque el 90% del rendimiento percibido de un e-commerce son
sus imágenes.

---

## 1. Por qué R2

Cloudflare R2 expone **la misma API que S3**, así que el código es el SDK de AWS
apuntando a otro endpoint. No hay librería propietaria ni acoplamiento: mover
esto a S3, MinIO o Backblaze es cambiar tres variables de entorno.

Lo que R2 aporta frente a S3 para este caso: **no cobra por egreso**. En una
tienda las imágenes se descargan muchas más veces de las que se suben, y el
egreso es justo lo que encarece S3.

En desarrollo se puede usar el mismo R2 con un bucket aparte, o MinIO local. El
código no distingue.

---

## 2. La tubería de subida

```text
  El administrador suelta un archivo
            │
            ▼
   ┌────────────────────┐
   │ 1. VALIDAR         │  tipo real (no la extensión), tamaño, dimensiones
   └─────────┬──────────┘
             ▼
   ┌────────────────────┐
   │ 2. NORMALIZAR      │  reorientar según EXIF, quitar metadatos
   └─────────┬──────────┘
             ▼
   ┌────────────────────┐
   │ 3. DERIVAR         │  miniatura · tarjeta · detalle · original acotado
   └─────────┬──────────┘
             ▼
   ┌────────────────────┐
   │ 4. CONVERTIR       │  todo a WebP, calidad 82
   └─────────┬──────────┘
             ▼
   ┌────────────────────┐
   │ 5. SUBIR A R2      │  clave con hash del contenido
   └─────────┬──────────┘
             ▼
   ┌────────────────────┐
   │ 6. REGISTRAR       │  fila en `archivos`, dentro de la transacción
   └────────────────────┘
```

### 1. Validar — por contenido, no por extensión

```text
  ✗  archivo.nombre.endsWith(".jpg")
  ✓  leer los primeros bytes y comprobar la firma real
```

Un `.jpg` puede ser cualquier cosa. La extensión la elige quien sube, así que no
es un dato: es una sugerencia. Se leen los bytes mágicos y se acepta solo JPEG,
PNG, WebP y AVIF.

| Límite | Valor | Por qué |
| --- | --- | --- |
| Tamaño | 10 MB | Por encima, casi siempre es un archivo equivocado |
| Dimensión máxima | 8000 × 8000 | Defensa contra la *decompression bomb*: un PNG de 2 KB puede declarar 50000×50000 y reventar la memoria al descomprimirse |
| Dimensión mínima | 200 × 200 | Debajo se ve mal en la tarjeta |
| Píxeles totales | 40 MP | Segundo cerrojo del mismo riesgo |

**Las dimensiones se comprueban leyendo la cabecera, antes de descomprimir.**
Comprobarlas después de cargar la imagen en memoria es comprobarlas cuando el
daño ya ocurrió.

### 2. Normalizar

**Reorientar según EXIF.** Una foto de móvil suele venir con los píxeles de
lado y una etiqueta que dice «gíralo 90°». Los navegadores la respetan; muchas
librerías de procesamiento no. Si se redimensiona sin aplicar la orientación, la
miniatura sale tumbada. Se aplica la rotación y se elimina la etiqueta.

**Eliminar el resto de metadatos.** El EXIF lleva modelo de cámara, fecha y a
veces **coordenadas GPS**. Publicar eso es una fuga de privacidad involuntaria,
y además son kilobytes en cada descarga.

### 3. Derivar

Cuatro tamaños, cada uno con un uso concreto:

| Variante | Ancho | Dónde se usa |
| --- | --- | --- |
| `miniatura` | 160 px | Tabla del admin, mini-carrito |
| `tarjeta` | 480 px | Rejilla de productos |
| `detalle` | 1200 px | Página de producto |
| `original` | máx. 2400 px | Zoom; acotado, no es el archivo subido |

Se mantiene la proporción; nunca se recorta automáticamente. Recortar por el
centro decapita productos altos, y un e-commerce con fotos mal recortadas parece
descuidado. El encaje lo resuelve el CSS con `object-fit`.

**No se amplía.** Si el original tiene 600 px de ancho, la variante `detalle`
tiene 600, no 1200 interpolados: ampliar añade peso sin añadir nitidez.

### 4. Convertir a WebP

WebP con calidad **82**. Pesa entre un 25% y un 35% menos que un JPEG
equivalente y lo soportan todos los navegadores vigentes.

Se guarda **solo WebP**, sin copia JPEG de respaldo. Mantener dos formatos
duplica el almacenamiento y la complejidad para cubrir navegadores que ya no
están en uso. Si algún día hiciera falta, se añade AVIF como formato adicional y
se sirve con `<picture>`.

### 5. Subir a R2

La clave incluye el **hash del contenido**:

```text
  productos/2026/09/a3f2c81e9b4d.../detalle.webp
  └── tipo ──┘└─ fecha ┘└─ sha256 ─┘└─ variante ─┘
```

Tres propiedades de esto:

* **Dedupe.** Subir dos veces la misma imagen produce la misma clave.
* **Caché eterna.** Como la clave cambia si el contenido cambia, se puede servir
  con `Cache-Control: public, max-age=31536000, immutable` sin miedo a servir
  algo obsoleto.
* **Sin colisiones de nombre.** Dos administradores que suban `foto.jpg` no se
  pisan.

Todas las variantes de una imagen se suben **antes** de escribir la fila en la
base. Si la subida falla, no queda una fila apuntando a un objeto inexistente.

### 6. Registrar

La fila en `archivos` se escribe dentro de la transacción del producto. Si el
producto no se guarda, tampoco su imagen.

El caso contrario —objeto en R2 sin fila— sí puede ocurrir si la transacción
revierte después de subir. Son objetos huérfanos: ocupan espacio y no rompen
nada. Un trabajo programado los limpia (§5).

---

## 3. Servir las imágenes

**El backend no sirve imágenes.** Devuelve URLs; el navegador las pide
directamente a R2.

```text
  ✗  navegador ──▶ API ──▶ R2 ──▶ API ──▶ navegador
  ✓  navegador ──▶ API (datos del producto, con URLs)
     navegador ──▶ R2  (las imágenes, en paralelo)
```

Pasar los bytes por la API consume su memoria y su ancho de banda para una tarea
que R2 hace mejor, y anula el CDN.

### Bucket público con dominio propio

El bucket se expone en `cdn.tudominio.com`. Las URLs son estables y cacheables:

```json
{
  "imagenes": [
    {
      "miniatura": "https://cdn.tudominio.com/productos/2026/09/a3f2c8.../miniatura.webp",
      "tarjeta":   "https://cdn.tudominio.com/productos/2026/09/a3f2c8.../tarjeta.webp",
      "detalle":   "https://cdn.tudominio.com/productos/2026/09/a3f2c8.../detalle.webp",
      "alt": "Mochila urbana negra, vista frontal"
    }
  ]
}
```

Son imágenes de catálogo: públicas por naturaleza. Firmar URLs aquí añadiría
complejidad y **rompería el cacheo**, que es precisamente lo que se busca.

Si algún día hubiera archivos privados (facturas, por ejemplo), esos sí irían
con URL firmada de vida corta y en un bucket distinto.

### El texto alternativo es obligatorio

`alt` no es opcional en el formulario del admin. Una tienda sin textos
alternativos es inaccesible para quien usa lector de pantalla y pierde
posicionamiento en imágenes. Es un campo de negocio, no un adorno técnico.

---

## 4. Dónde ocurre el procesamiento

Redimensionar cuatro variantes de una imagen de 4 MB cuesta entre 1 y 3
segundos. Eso **no** puede pasar dentro de la petición.

```text
  POST /admin/archivos
    ├─ valida (rápido)
    ├─ guarda el original en un área temporal
    ├─ encola el procesamiento
    └─ responde 202 con el id del archivo y estado PROCESANDO

  (en segundo plano)
    └─ deriva, convierte, sube, marca LISTO
```

El panel muestra la fila con un indicador de «procesando» y la refresca. Un
producto puede guardarse con imágenes aún en proceso; la tienda no las muestra
hasta que estén `LISTO`.

**El pool de procesamiento está acotado** (2–4 hilos). Es trabajo intensivo en
CPU y memoria: sin límite, diez subidas simultáneas tumban el servicio. A
diferencia de la E/S, aquí más paralelismo no da más rendimiento — solo más
contención.

---

## 5. Huérfanos y limpieza

Dos tipos de basura, con tratamiento distinto:

| Tipo | Cómo aparece | Qué se hace |
| --- | --- | --- |
| Objeto sin fila | La transacción revirtió tras subir | Trabajo semanal: lista R2, compara con `archivos`, borra lo que tenga más de 24 h sin fila |
| Fila sin referencia | Se cambió la imagen de un producto | Se marca `desreferenciadoEn`; se borra a los 30 días |

Los 30 días no son arbitrarios: dan margen para deshacer un cambio accidental. Y
el umbral de 24 h en los objetos evita borrar algo que se está subiendo ahora
mismo.

---

## 6. Configuración

| Variable | Para qué |
| --- | --- |
| `R2_ACCOUNT_ID` | Cuenta de Cloudflare |
| `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` | Credenciales S3 |
| `R2_BUCKET` | Nombre del bucket |
| `R2_ENDPOINT` | `https://<cuenta>.r2.cloudflarestorage.com` |
| `R2_PUBLIC_BASE_URL` | `https://cdn.tudominio.com` — lo que se devuelve en la API |
| `IMAGE_MAX_UPLOAD_BYTES` | `10485760` (10 MB) |
| `IMAGE_WEBP_QUALITY` | `82` |
| `IMAGE_PROCESSING_THREADS` | `3` |

`R2_ENDPOINT` y `R2_PUBLIC_BASE_URL` son distintos a propósito: el primero es
por donde se escribe (privado, con credenciales), el segundo por donde se lee
(público, por CDN).

---

## 7. Herramienta de procesamiento

| Opción | Veredicto |
| --- | --- |
| **imgscalr / Java 2D** | Solo Java puro, sin binarios nativos. Suficiente para redimensionar, pero no escribe WebP |
| **TwelveMonkeys ImageIO + webp-imageio** | Añade lectura/escritura WebP a ImageIO. **Elegida**: se queda en la JVM, sin proceso externo ni dependencia del sistema |
| libvips / ImageMagick por proceso | Más rápido y con mejor calidad, pero exige el binario instalado en la imagen Docker y ejecutar procesos externos desde la API |

Se elige TwelveMonkeys por autocontención: el `jar` lleva todo y el despliegue
no depende de qué haya instalado en el sistema. Si el volumen creciera hasta
hacer del procesamiento un cuello de botella real, libvips es el siguiente paso
— y entonces será una decisión respaldada por una medición.

---

## 8. Pruebas

| Qué | Cómo |
| --- | --- |
| Rechaza un archivo que no es imagen | Un `.txt` renombrado a `.jpg` debe dar `400` |
| Rechaza una bomba de descompresión | PNG pequeño con dimensiones enormes declaradas |
| Aplica la orientación EXIF | Imagen con EXIF `Orientation=6`: la salida debe estar derecha |
| Elimina los metadatos | La salida no contiene EXIF ni GPS |
| No amplía | Original de 300 px: `detalle` debe medir 300, no 1200 |
| Dedupe | Subir dos veces produce la misma clave |
| Huérfanos | Una transacción revertida deja el objeto, y la limpieza lo elimina |

La bomba de descompresión y la orientación EXIF son las dos que siempre faltan y
las dos que fallan en producción con imágenes reales.

---

## 9. Lista de revisión

* [ ] ¿El tipo se valida por contenido y no por extensión?
* [ ] ¿Las dimensiones se comprueban antes de descomprimir?
* [ ] ¿Se aplica la orientación EXIF y se eliminan los metadatos?
* [ ] ¿Se convierte todo a WebP?
* [ ] ¿La clave incluye el hash del contenido?
* [ ] ¿El procesamiento está fuera de la petición y con pool acotado?
* [ ] ¿La API devuelve URLs y no bytes?
* [ ] ¿El `alt` es obligatorio?
* [ ] ¿Hay limpieza de huérfanos?
* [ ] ¿Las credenciales de R2 vienen del entorno?
