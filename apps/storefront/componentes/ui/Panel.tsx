'use client'

import { useCallback, useEffect, useRef, type ReactNode } from 'react'
import { X } from 'lucide-react'

import { clases } from '@/lib/formato'

/**
 * Panel lateral modal.
 *
 * Es un dialogo de verdad, no un `div` que aparece:
 *
 * - **atrapa el foco** mientras esta abierto (Tab circula dentro),
 * - **se cierra con Escape**,
 * - **devuelve el foco** al elemento que lo abrio,
 * - bloquea el desplazamiento del fondo.
 *
 * Sin esas cuatro cosas, quien navega con teclado sale del panel sin darse
 * cuenta y sigue tabulando por una pagina que no puede ver.
 */

type Propiedades = {
  abierto: boolean
  alCerrar: () => void
  titulo: string
  children: ReactNode
  pie?: ReactNode
  /** `lado` derecho por defecto; el filtro en movil entra por la izquierda. */
  lado?: 'derecha' | 'izquierda'
}

const FOCALIZABLES =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'

export function Panel({ abierto, alCerrar, titulo, children, pie, lado = 'derecha' }: Propiedades) {
  const caja = useRef<HTMLDivElement>(null)
  const anterior = useRef<HTMLElement | null>(null)

  const alPulsarTecla = useCallback(
    (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault()
        alCerrar()
        return
      }
      if (e.key !== 'Tab' || !caja.current) return

      const focos = Array.from(caja.current.querySelectorAll<HTMLElement>(FOCALIZABLES)).filter(
        (el) => el.offsetParent !== null,
      )
      if (focos.length === 0) return
      const primero = focos[0]
      const ultimo = focos[focos.length - 1]
      if (!primero || !ultimo) return

      if (e.shiftKey && document.activeElement === primero) {
        e.preventDefault()
        ultimo.focus()
      } else if (!e.shiftKey && document.activeElement === ultimo) {
        e.preventDefault()
        primero.focus()
      }
    },
    [alCerrar],
  )

  useEffect(() => {
    if (!abierto) return

    anterior.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const desbordeOriginal = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    document.addEventListener('keydown', alPulsarTecla)

    // El primer foco va al contenedor, no al boton de cerrar: quien abre el
    // panel quiere oir su titulo, no "cerrar".
    const t = setTimeout(() => caja.current?.focus(), 30)

    return () => {
      clearTimeout(t)
      document.removeEventListener('keydown', alPulsarTecla)
      document.body.style.overflow = desbordeOriginal
      anterior.current?.focus()
    }
  }, [abierto, alPulsarTecla])

  if (!abierto) return null

  return (
    <div className="fixed inset-0 z-[60] flex" role="presentation">
      <button
        type="button"
        aria-label="Cerrar"
        tabIndex={-1}
        onClick={alCerrar}
        className="absolute inset-0 cursor-default bg-tinta/40 backdrop-blur-[1px]"
      />
      <div
        ref={caja}
        role="dialog"
        aria-modal="true"
        aria-label={titulo}
        tabIndex={-1}
        className={clases(
          'relative flex h-full w-full max-w-md flex-col bg-white shadow-l outline-none animate-desliza-derecha',
          lado === 'derecha' ? 'ml-auto' : 'mr-auto',
        )}
      >
        <header className="flex items-center justify-between gap-3 border-b border-borde px-5 py-4">
          <h2 className="font-marca text-base font-semibold text-tinta">{titulo}</h2>
          <button
            type="button"
            onClick={alCerrar}
            aria-label="Cerrar panel"
            className="rounded-marca p-1.5 text-texto-suave transition hover:bg-superficie-alt hover:text-texto"
          >
            <X size={18} aria-hidden />
          </button>
        </header>

        <div className="flex-1 overflow-y-auto overscroll-contain">{children}</div>

        {pie && <footer className="border-t border-borde bg-superficie-alt px-5 py-4">{pie}</footer>}
      </div>
    </div>
  )
}
