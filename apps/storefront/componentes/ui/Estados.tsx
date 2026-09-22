import type { ReactNode } from 'react'
import { PackageOpen } from 'lucide-react'

import { clases } from '@/lib/formato'

/**
 * Los estados que no son "exito": vacio y esqueleto.
 *
 * El esqueleto **imita la rejilla**, con el mismo numero de tarjetas y la misma
 * proporcion de imagen. Un spinner centrado produce un salto de maquetacion
 * cuando llegan los datos, y ese salto lo penaliza la metrica de CLS.
 */

export function EsqueletoTarjeta() {
  return (
    <div className="overflow-hidden rounded-marca border border-borde bg-white">
      <div className="aspect-square animate-pulse bg-superficie-alt" />
      <div className="space-y-2 p-3.5">
        <div className="h-3 w-1/3 animate-pulse rounded bg-superficie-alt" />
        <div className="h-4 w-5/6 animate-pulse rounded bg-superficie-alt" />
        <div className="h-5 w-1/2 animate-pulse rounded bg-superficie-alt" />
      </div>
    </div>
  )
}

export function EsqueletoRejilla({ cuantas = 8 }: { cuantas?: number }) {
  return (
    <div
      aria-hidden
      className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4 lg:grid-cols-4"
    >
      {Array.from({ length: cuantas }, (_, i) => (
        <EsqueletoTarjeta key={i} />
      ))}
    </div>
  )
}

type PropiedadesVacio = {
  titulo: string
  descripcion?: string
  icono?: ReactNode
  accion?: ReactNode
  className?: string
}

export function SinResultados({ titulo, descripcion, icono, accion, className }: PropiedadesVacio) {
  return (
    <div
      className={clases(
        'flex flex-col items-center justify-center gap-3 rounded-marca border border-dashed border-borde-fuerte bg-white px-6 py-14 text-center',
        className,
      )}
    >
      <div className="text-borde-fuerte" aria-hidden>
        {icono ?? <PackageOpen size={38} strokeWidth={1.5} />}
      </div>
      <h2 className="font-marca text-base font-semibold text-texto">{titulo}</h2>
      {descripcion && <p className="max-w-md text-sm text-texto-suave">{descripcion}</p>}
      {accion && <div className="mt-1">{accion}</div>}
    </div>
  )
}
