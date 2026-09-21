# ADR-0009: Imágenes en Cloudflare R2, convertidas a WebP al subir

## Contexto
El catálogo necesita imágenes de producto, logos de marca e imágenes de categoría. El
administrador sube lo que tenga a mano —normalmente un JPEG de varios megas salido de un
móvil— y la tienda necesita algo ligero, en varios tamaños y servido rápido.

Las imágenes son el grueso del peso de un e-commerce y lo que más afecta al rendimiento
percibido.

## Decisión
- **Almacenamiento: Cloudflare R2**, usado a través de la API S3.
- **Procesamiento al subir:** validar por contenido, reorientar según EXIF, eliminar
  metadatos, derivar tres variantes (160 / 480 / 1200 px) y **convertir todo a WebP**.
- **Servicio:** la API devuelve URLs de un dominio CDN propio; el navegador las pide
  directamente a R2. El backend nunca sirve los bytes.

## Consecuencias
- **R2 no cobra egreso.** En una tienda las imágenes se descargan muchas más veces de
  las que se suben, y el egreso es justo lo que encarece S3.
- Al ser API S3, el código es el SDK de AWS apuntando a otro endpoint. Mover esto a S3,
  MinIO o Backblaze es cambiar tres variables de entorno. Sin acoplamiento propietario.
- WebP pesa entre un 25% y un 35% menos que un JPEG equivalente. Se guarda **solo** WebP:
  mantener un JPEG de respaldo duplicaría el almacenamiento para cubrir navegadores que
  ya no están en uso.
- **El procesamiento cuesta entre 1 y 3 segundos por imagen**, así que no cabe dentro de
  la petición HTTP. Va a una cola con un pool acotado, y el panel muestra el archivo como
  «procesando» hasta que termina. Es complejidad real que la decisión trae consigo.
- La clave del objeto incluye el hash del contenido, lo que da deduplicación y permite
  cachear para siempre (`immutable`): una URL nunca cambia de contenido.
- **Se pierde el original tal como se subió**: se guarda acotado a 2400 px. Si alguna vez
  hiciera falta el archivo intacto, habría que guardarlo aparte y asumir el costo.
- Añade una dependencia externa al despliegue. Si R2 no responde, no se pueden subir
  imágenes nuevas; las existentes siguen sirviéndose desde el CDN.
