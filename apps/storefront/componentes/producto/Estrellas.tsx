import { Star } from 'lucide-react'

import { clases } from '@/lib/formato'

/**
 * Calificacion en estrellas.
 *
 * El numero sale de las opiniones reales del producto (RN-093): es la media de
 * las que estan vivas, y la API la recalcula en cada escritura. Por eso **si**
 * va al JSON-LD como `aggregateRating`, pero solo cuando hay al menos una
 * opinion -ver `lib/jsonld.tsx`-. Un producto sin ninguna vale 0, y cero no
 * significa «malo» sino «todavia nadie»: ahi no se pinta nada.
 *
 * Las estrellas son decorativas (`aria-hidden`); lo que se anuncia es el texto.
 *
 * `mostrarCifra` existe para la tarjeta de una opinion suelta: «5.0» al lado de
 * cinco estrellas llenas no anade nada, y en una lista de resenas se repite
 * tantas veces como opiniones haya.
 */

type Propiedades = {
  promedio: number | null
  conteo?: number | null
  tamano?: number
  mostrarCifra?: boolean
  className?: string
}

export function Estrellas({ promedio, conteo, tamano = 13, mostrarCifra = true, className }: Propiedades) {
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
      {mostrarCifra && (
        <span aria-hidden className="cifra text-xs text-texto-suave">
          {promedio.toFixed(1)}
          {conteo ? ` (${conteo})` : ''}
        </span>
      )}
    </span>
  )
}
