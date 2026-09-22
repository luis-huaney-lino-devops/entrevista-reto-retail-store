/**
 * Variantes de imagen del CDN.
 *
 * El backend convierte a WebP al subir y guarda cuatro variantes (ADR-0009).
 * Las URL terminan en `-<variante>.webp`, asi que pedir otra variante es
 * sustituir ese sufijo.
 *
 * Por que una sustitucion de cadena y no `next/image` con su optimizador: pasar
 * estas imagenes por el optimizador de Next seria redimensionar en el servidor
 * Node algo que el pipeline ya redimensiono. Con esto los bytes viajan directos
 * del CDN al navegador.
 *
 * Y por que no se asume que la variante existe: el sembrado no siempre genero
 * las cuatro (hay productos cuya `detalle` apunta al archivo de `tarjeta`). Si
 * la URL no encaja con el patron, se devuelve tal cual en vez de fabricar una
 * direccion que daria 404.
 */

export type Variante = 'miniatura' | 'tarjeta' | 'detalle' | 'original'

const PATRON = /-(miniatura|tarjeta|detalle|original)\.webp$/i

export function variante(url: string | null | undefined, cual: Variante): string | null {
  if (!url) return null
  if (!PATRON.test(url)) return url
  return url.replace(PATRON, `-${cual}.webp`)
}

/** La primera URL util de un mapa de variantes (categorias y marcas). */
export function deArchivo(
  archivo: { url: string; urls: Partial<Record<string, string>> } | null | undefined,
  cual: Variante,
): string | null {
  if (!archivo) return null
  const clave = cual.toUpperCase()
  return archivo.urls?.[clave] ?? archivo.url ?? null
}

/**
 * `sizes` para `next/image`.
 *
 * Es donde se equivoca todo el mundo: mal puesto, el navegador descarga la
 * imagen de 1200 px para pintar una tarjeta de 240.
 */
export const TAMANOS = {
  tarjeta: '(max-width: 640px) 50vw, (max-width: 1024px) 33vw, 260px',
  tarjetaAncha: '(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 320px',
  detalle: '(max-width: 1024px) 100vw, 600px',
  miniatura: '80px',
  categoria: '(max-width: 640px) 45vw, (max-width: 1024px) 30vw, 200px',
} as const
