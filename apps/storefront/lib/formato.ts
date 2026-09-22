/**
 * Formato de importes y numeros.
 *
 * Un unico helper y un `locale` fijado explicitamente. Si se dejara el del
 * sistema, el servidor formatearia con el del contenedor y el navegador con el
 * suyo: ademas de una incoherencia visual, seria un desajuste de hidratacion
 * (el ICU de Node y el del navegador difieren hasta en el espacio que separa
 * `S/` del numero).
 */

const MONEDA = new Intl.NumberFormat('es-PE', {
  style: 'currency',
  currency: 'PEN',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

export function dinero(v: number | null | undefined): string {
  if (v === null || v === undefined || !Number.isFinite(v)) return MONEDA.format(0)
  return MONEDA.format(v)
}

const ENTERO = new Intl.NumberFormat('es-PE')

export function entero(v: number): string {
  return ENTERO.format(v)
}

/** Fecha absoluta. Nada de "hace 3 minutos" en servidor: el servidor y el
 *  cliente renderizan en instantes distintos y eso rompe la hidratacion. */
export function fecha(iso: string | null | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  return new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeZone: 'America/Lima' }).format(d)
}

/** Recorta un texto por palabras, sin cortar a mitad. */
export function recortar(texto: string | null | undefined, maximo: number): string {
  if (!texto) return ''
  const limpio = texto.replace(/\s+/g, ' ').trim()
  if (limpio.length <= maximo) return limpio
  const corte = limpio.slice(0, maximo)
  const ultimo = corte.lastIndexOf(' ')
  return `${(ultimo > maximo * 0.6 ? corte.slice(0, ultimo) : corte).trimEnd()}...`
}

/** Une clases condicionales sin arrastrar una dependencia por tres lineas. */
export function clases(...partes: (string | false | null | undefined)[]): string {
  return partes.filter(Boolean).join(' ')
}
