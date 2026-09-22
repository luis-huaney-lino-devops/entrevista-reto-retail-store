'use client'

import { useRouter, useSearchParams } from 'next/navigation'
import { useEffect, useRef, useState } from 'react'
import { Search, X } from 'lucide-react'

import { clases } from '@/lib/formato'

/**
 * Caja de busqueda de la cabecera.
 *
 * Escribe en la URL, no en `useState`, y eso tiene cinco consecuencias que se
 * notan: la vista es compartible, el boton atras funciona, recargar no pierde
 * la busqueda, el servidor recibe el texto en la peticion inicial y devuelve el
 * HTML ya filtrado, y la pagina puede indexarse o no segun convenga.
 *
 * Detalles del comportamiento:
 * - Retardo de 350 ms antes de escribir en la URL mientras se teclea.
 * - `router.replace` y no `push`: no hay que ensuciar el historial con una
 *   entrada por cada letra.
 * - Al enviar con Enter, `push`: esa si es una navegacion deliberada.
 */

export function Buscador({ className, autoFoco = false }: { className?: string; autoFoco?: boolean }) {
  const router = useRouter()
  const parametros = useSearchParams()
  const inicial = parametros.get('texto') ?? ''
  const [texto, setTexto] = useState(inicial)
  const primerRender = useRef(true)

  // Si la URL cambia por fuera (atras, un enlace), el campo la sigue.
  useEffect(() => {
    setTexto(inicial)
  }, [inicial])

  useEffect(() => {
    if (primerRender.current) {
      primerRender.current = false
      return
    }
    if (texto === inicial) return

    const t = setTimeout(() => {
      const p = new URLSearchParams(parametros.toString())
      if (texto.trim()) p.set('texto', texto.trim())
      else p.delete('texto')
      // Cambiar la busqueda vuelve siempre a la pagina 1.
      p.delete('pagina')
      router.replace(`/productos?${p.toString()}`, { scroll: false })
    }, 350)

    return () => clearTimeout(t)
    // `parametros` se lee dentro pero no debe disparar el efecto: haria un
    // bucle de navegacion.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [texto])

  return (
    <form
      role="search"
      onSubmit={(e) => {
        e.preventDefault()
        const p = new URLSearchParams()
        if (texto.trim()) p.set('texto', texto.trim())
        router.push(`/productos${p.toString() ? `?${p.toString()}` : ''}`)
      }}
      className={clases('relative flex w-full items-center', className)}
    >
      <label htmlFor="buscador-tienda" className="sr-only">
        Buscar productos
      </label>
      <Search size={16} aria-hidden className="pointer-events-none absolute left-3 text-texto-suave" />
      <input
        id="buscador-tienda"
        type="search"
        value={texto}
        autoFocus={autoFoco}
        onChange={(e) => setTexto(e.target.value)}
        placeholder="Buscar cemento, taladro, pintura..."
        autoComplete="off"
        className="h-10 w-full rounded-marca border border-borde bg-white pl-9 pr-9 text-sm text-texto transition placeholder:text-texto-suave hover:border-borde-fuerte focus:border-tinta-claro"
      />
      {texto && (
        <button
          type="button"
          onClick={() => setTexto('')}
          aria-label="Limpiar la busqueda"
          className="absolute right-2 rounded p-1 text-texto-suave transition hover:text-texto"
        >
          <X size={15} aria-hidden />
        </button>
      )}
    </form>
  )
}
