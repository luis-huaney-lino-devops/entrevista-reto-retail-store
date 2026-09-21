import { Star } from 'lucide-react'

export function Estado({ activo }: { activo: boolean }) {
  return (
    <span className={`insignia ${activo ? 'verde' : 'gris'}`}>{activo ? 'Publicado' : 'Borrador'}</span>
  )
}

export function Disponibilidad({ activa }: { activa: boolean }) {
  return <span className={`insignia ${activa ? 'verde' : 'gris'}`}>{activa ? 'Activa' : 'Inactiva'}</span>
}

export function Destacado() {
  return (
    <span className="insignia ambar sin-punto" title="Aparece en la portada">
      <Star size={10} fill="currentColor" />
      Destacado
    </span>
  )
}

/** Importes en soles. Intl evita reinventar el separador decimal y el símbolo. */
export function soles(importe: number): string {
  return new Intl.NumberFormat('es-PE', {
    style: 'currency',
    currency: 'PEN',
    minimumFractionDigits: 2,
  }).format(importe)
}

export function fecha(iso: string | null): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' }).format(
    new Date(iso),
  )
}

export function fechaCorta(iso: string | null): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium' }).format(new Date(iso))
}

export function hora(iso: string | null): string {
  if (!iso) return '—'
  return new Intl.DateTimeFormat('es-PE', { timeStyle: 'short' }).format(new Date(iso))
}

/**
 * «hace 3 min», «ayer». Para listas donde lo que importa es lo reciente que
 * es algo, no el día exacto: en una bandeja de avisos, «hace 3 min» se lee de
 * un vistazo y «21 sept 2026, 14:58» hay que calcularlo.
 */
export function desdeAhora(iso: string | null): string {
  if (!iso) return '—'
  const segundos = Math.round((Date.now() - new Date(iso).getTime()) / 1000)
  if (segundos < 60) return 'ahora mismo'

  // De mayor a menor: la primera que quepa es la que mejor se lee. Al revés
  // habría que recorrerlas todas para quedarse con la última.
  const escalas: [Intl.RelativeTimeFormatUnit, number][] = [
    ['year', 31536000],
    ['month', 2592000],
    ['week', 604800],
    ['day', 86400],
    ['hour', 3600],
    ['minute', 60],
  ]

  const formato = new Intl.RelativeTimeFormat('es-PE', { numeric: 'auto' })
  for (const [unidad, tamano] of escalas) {
    if (segundos >= tamano) {
      return formato.format(-Math.floor(segundos / tamano), unidad)
    }
  }
  return 'ahora mismo'
}
