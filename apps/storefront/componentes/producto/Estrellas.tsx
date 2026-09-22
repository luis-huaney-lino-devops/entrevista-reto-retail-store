import { Star } from 'lucide-react'

import { clases } from '@/lib/formato'

/**
 * Calificacion en estrellas.
 *
 * Se pintan en la interfaz porque el dato viene del sembrado, pero **no van al
 * JSON-LD**: declarar una valoracion agregada en datos estructurados sin
 * sistema de resenas seria afirmarle al buscador algo que no ocurrio, y las
 * penalizaciones por datos estructurados falsos son reales.
 *
 * Las estrellas son decorativas (`aria-hidden`); lo que se anuncia es el texto.
 */

type Propiedades = {
  promedio: number | null
  conteo?: number | null
  tamano?: number
  className?: string
}

export function Estrellas({ promedio, conteo, tamano = 13, className }: Propiedades) {
  if (promedio === null || promedio === undefined) return null
  const llenas = Math.round(promedio)

  return (
    <span className={clases('inline-flex items-center gap-1', className)}>
      <span className="sr-only">
        {promedio.toFixed(1)} de 5{conteo ? `, ${conteo} valoraciones` : ''}
      </span>
      <span aria-hidden className="flex items-center gap-px">
        {[1, 2, 3, 4, 5].map((i) => (
          <Star
            key={i}
            size={tamano}
            className={i <= llenas ? 'fill-marca text-marca' : 'text-borde-fuerte'}
            strokeWidth={1.8}
          />
        ))}
      </span>
      <span aria-hidden className="cifra text-xs text-texto-suave">
        {promedio.toFixed(1)}
        {conteo ? ` (${conteo})` : ''}
      </span>
    </span>
  )
}
