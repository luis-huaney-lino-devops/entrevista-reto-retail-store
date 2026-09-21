/**
 * Validación previa a la subida.
 *
 * <p>El servidor valida igual —y es quien manda—, pero comprobar aquí el tipo
 * y el tamaño evita subir diez megas por una carretera lenta para recibir un
 * 422. El mensaje también llega antes: quien suelta un PDF se entera al
 * soltarlo, no cinco segundos después.
 */

export const TIPOS_ADMITIDOS = ['image/jpeg', 'image/png', 'image/webp']
export const BYTES_MAXIMOS = 10 * 1024 * 1024
export const LADO_MINIMO = 200

/** @returns el motivo del rechazo, o null si el archivo sirve */
export function motivoDeRechazo(archivo: File): string | null {
  if (!TIPOS_ADMITIDOS.includes(archivo.type)) {
    return 'Solo se admiten imágenes JPEG, PNG o WebP.'
  }
  if (archivo.size > BYTES_MAXIMOS) {
    return `«${archivo.name}» pesa ${pesoLegible(archivo.size)}; el máximo son 10 MB.`
  }
  return null
}

export function pesoLegible(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} kB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

/**
 * Quita la extensión y los separadores del nombre del archivo.
 * `foto_producto-01.jpg` → `foto producto 01`.
 */
export function nombreLegible(nombreArchivo: string): string {
  return nombreArchivo
    .replace(/\.[^.]+$/, '')
    .replace(/[_-]+/g, ' ')
    .trim()
}
