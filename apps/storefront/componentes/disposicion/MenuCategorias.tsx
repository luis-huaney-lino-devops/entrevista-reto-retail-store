'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { ChevronDown, LayoutGrid } from 'lucide-react'

import { clases } from '@/lib/formato'
import type { Categoria } from '@/lib/tipos'

/**
 * Menu de categorias de la cabecera.
 *
 * Los datos llegan ya resueltos desde el servidor —el layout los pide con ISR
 * de 10 minutos— y esta isla solo se ocupa de abrirlo y cerrarlo. Lo que viaja
 * al navegador es el desplegable, no la peticion.
 *
 * Se cierra con Escape y al pulsar fuera, y el boton anuncia su estado con
 * `aria-expanded`. Un desplegable que no se cierra con Escape es una trampa
 * para quien navega con teclado.
 */

export function MenuCategorias({ categorias }: { categorias: Categoria[] }) {
  const [abierto, setAbierto] = useState(false)
  const caja = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!abierto) return

    const fuera = (e: MouseEvent) => {
      if (caja.current && !caja.current.contains(e.target as Node)) setAbierto(false)
    }
    const escape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setAbierto(false)
    }
    document.addEventListener('mousedown', fuera)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('mousedown', fuera)
      document.removeEventListener('keydown', escape)
    }
  }, [abierto])

  if (categorias.length === 0) return null

  return (
    <div ref={caja} className="relative">
      <button
        type="button"
        onClick={() => setAbierto((v) => !v)}
        aria-expanded={abierto}
        aria-haspopup="true"
        className={clases(
          'inline-flex h-10 items-center gap-2 rounded-marca px-3 text-sm font-semibold transition',
          abierto ? 'bg-tinta text-white' : 'text-tinta hover:bg-tinta-suave',
        )}
      >
        <LayoutGrid size={17} aria-hidden />
        <span>Categorias</span>
        <ChevronDown size={15} aria-hidden className={clases('transition-transform', abierto && 'rotate-180')} />
      </button>

      {abierto && (
        <div
          className="absolute left-0 top-full z-50 mt-1.5 max-h-[70vh] w-[min(92vw,720px)] overflow-y-auto rounded-marca border border-borde bg-white p-4 shadow-l animate-entrada"
          onClick={() => setAbierto(false)}
        >
          <ul className="grid gap-x-6 gap-y-4 sm:grid-cols-2 lg:grid-cols-3">
            {categorias.map((c) => (
              <li key={c.id}>
                <Link
                  href={`/c/${c.slug}`}
                  className="font-marca text-sm font-semibold text-tinta hover:text-marca-oscura"
                >
                  {c.nombre}
                </Link>
                {c.subcategorias.length > 0 && (
                  <ul className="mt-1.5 space-y-1">
                    {c.subcategorias.map((s) => (
                      <li key={s.id}>
                        <Link
                          href={`/c/${c.slug}/${s.slug}`}
                          className="text-[13px] text-texto-medio hover:text-marca-oscura"
                        >
                          {s.nombre}
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
