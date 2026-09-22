'use client'

import { useCallback, useEffect, useId, useRef, useState } from 'react'
import Link from 'next/link'
import { ChevronDown, LayoutGrid, LayoutList } from 'lucide-react'

import { clases } from '@/lib/formato'
import type { Categoria } from '@/lib/tipos'

/**
 * Menu de categorias de la cabecera.
 *
 * **Es la unica puerta al arbol del catalogo.** Antes habia ademas una tira
 * horizontal bajo la cabecera con las categorias en fila; con diez no cabia y
 * habia que arrastrarla, asi que se quito y todo vive aqui: "Todo el catalogo"
 * primero, y despues las diez categorias con sus subcategorias, visibles de una
 * sola vez en columnas.
 *
 * Los datos llegan ya resueltos desde el servidor —la cabecera los pide con ISR
 * de 10 minutos— y esta isla solo se ocupa de abrirlo y cerrarlo. Lo que viaja
 * al navegador es el desplegable, no la peticion.
 *
 * Teclado, que es donde estos menus se caen:
 *
 * - `ArrowDown` sobre el boton abre **y mete el foco** en la primera opcion;
 *   `Enter` y espacio abren y dejan el foco donde estaba, que es lo que espera
 *   quien viene tabulando.
 * - Dentro del panel, `ArrowDown`/`ArrowUp` recorren los enlaces y `Home`/`End`
 *   van a los extremos.
 * - `Escape` cierra **y devuelve el foco al boton**. Cerrar dejando el foco en
 *   un elemento que ya no existe manda al usuario al principio del documento.
 * - Pulsar fuera cierra, y el boton anuncia su estado con `aria-expanded`.
 */

export function MenuCategorias({ categorias }: { categorias: Categoria[] }) {
  const [abierto, setAbierto] = useState(false)
  const caja = useRef<HTMLDivElement>(null)
  const boton = useRef<HTMLButtonElement>(null)
  const panel = useRef<HTMLDivElement>(null)
  const idPanel = useId()

  /** Cierra y decide si el foco vuelve al boton. Solo vuelve cuando el cierre
   *  lo pidio el teclado: tras un clic fuera, robar el foco es agresivo. */
  const cerrar = useCallback((devolverFoco: boolean) => {
    setAbierto(false)
    if (devolverFoco) boton.current?.focus()
  }, [])

  useEffect(() => {
    if (!abierto) return

    const fuera = (e: MouseEvent) => {
      if (caja.current && !caja.current.contains(e.target as Node)) setAbierto(false)
    }
    const escape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') cerrar(true)
    }
    document.addEventListener('mousedown', fuera)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('mousedown', fuera)
      document.removeEventListener('keydown', escape)
    }
  }, [abierto, cerrar])

  function enlaces(): HTMLAnchorElement[] {
    return Array.from(panel.current?.querySelectorAll<HTMLAnchorElement>('a[href]') ?? [])
  }

  function abrirYEnfocar() {
    setAbierto(true)
    // El panel aun no existe en este pintado: el foco va en el siguiente.
    requestAnimationFrame(() => enlaces()[0]?.focus())
  }

  function tecladoEnPanel(e: React.KeyboardEvent) {
    const items = enlaces()
    if (items.length === 0) return
    const actual = items.indexOf(document.activeElement as HTMLAnchorElement)

    if (e.key === 'ArrowDown') {
      e.preventDefault()
      items[actual < 0 || actual === items.length - 1 ? 0 : actual + 1]?.focus()
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      items[actual <= 0 ? items.length - 1 : actual - 1]?.focus()
    } else if (e.key === 'Home') {
      e.preventDefault()
      items[0]?.focus()
    } else if (e.key === 'End') {
      e.preventDefault()
      items[items.length - 1]?.focus()
    }
  }

  return (
    <div ref={caja} className="relative">
      <button
        ref={boton}
        type="button"
        onClick={() => setAbierto((v) => !v)}
        onKeyDown={(e) => {
          // `Enter` y espacio los resuelve el `onClick` del propio boton, que
          // para eso es un `<button>`. Aqui solo ArrowDown, que ademas entra.
          if (e.key === 'ArrowDown' && !abierto) {
            e.preventDefault()
            abrirYEnfocar()
          }
        }}
        aria-expanded={abierto}
        aria-haspopup="true"
        aria-controls={idPanel}
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
          ref={panel}
          id={idPanel}
          onKeyDown={tecladoEnPanel}
          // Un clic en cualquier enlace navega y el panel ya no pinta nada.
          onClick={() => setAbierto(false)}
          className="absolute left-0 top-full z-50 mt-1.5 max-h-[72vh] w-[min(92vw,820px)] overflow-y-auto rounded-marca border border-borde bg-white p-4 shadow-l animate-entrada"
        >
          <Link
            href="/productos"
            className="mb-3 flex items-center gap-2.5 rounded-marca border border-borde bg-superficie-alt px-3 py-2.5 text-sm font-semibold text-tinta transition hover:border-borde-fuerte hover:text-marca-oscura"
          >
            <LayoutList size={16} aria-hidden />
            Todo el catalogo
            <span className="ml-auto text-[12px] font-medium text-texto-suave">
              {categorias.length} {categorias.length === 1 ? 'categoria' : 'categorias'}
            </span>
          </Link>

          {categorias.length > 0 && (
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
          )}
        </div>
      )}
    </div>
  )
}
