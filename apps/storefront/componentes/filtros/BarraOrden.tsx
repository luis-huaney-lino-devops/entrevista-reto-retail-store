'use client'

import { useRouter, useSearchParams } from 'next/navigation'
import { useTransition } from 'react'

import { clases } from '@/lib/formato'
import { ETIQUETAS_ORDEN, ORDENES, type Orden } from '@/lib/tipos'

/**
 * Selector de orden.
 *
 * La lista es cerrada: son los cinco valores que acepta la API. Un valor fuera
 * de ella da `400 VALIDATION_ERROR`, no un orden por defecto silencioso, asi que
 * aqui tampoco se inventa ninguno.
 */
export function BarraOrden({ total }: { total: number }) {
  const router = useRouter()
  const parametros = useSearchParams()
  const [pendiente, iniciar] = useTransition()
  const actual = (parametros.get('orden') ?? '') as Orden | ''

  function cambiar(valor: string) {
    const p = new URLSearchParams(parametros.toString())
    if (valor) p.set('orden', valor)
    else p.delete('orden')
    p.delete('pagina')
    iniciar(() => router.push(`/productos?${p.toString()}`, { scroll: false }))
  }

  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <p className="text-sm text-texto-suave" aria-live="polite">
        <span className="cifra font-semibold text-texto">{total}</span>{' '}
        {total === 1 ? 'producto' : 'productos'}
      </p>

      <label className={clases('flex items-center gap-2 text-sm', pendiente && 'opacity-70')}>
        <span className="text-texto-suave">Ordenar por</span>
        <select
          value={actual}
          onChange={(e) => cambiar(e.target.value)}
          className="h-9 cursor-pointer rounded-marca border border-borde bg-white px-2.5 text-sm text-texto hover:border-borde-fuerte"
        >
          <option value="">Relevancia</option>
          {ORDENES.map((o) => (
            <option key={o} value={o}>
              {ETIQUETAS_ORDEN[o]}
            </option>
          ))}
        </select>
      </label>
    </div>
  )
}
