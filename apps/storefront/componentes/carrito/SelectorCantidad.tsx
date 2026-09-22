'use client'

import { useEffect, useState } from 'react'
import { Minus, Plus } from 'lucide-react'

import { clases } from '@/lib/formato'
import { CANTIDAD_MAXIMA } from '@/lib/tipos'

/**
 * Selector de cantidad.
 *
 * Es un `<input type="number">` con su `<label>` y dos botones con
 * `aria-label`. Un `<div>` con `onClick` sencillamente no existe para el
 * teclado.
 *
 * Bajar de 1 **quita la linea**, no la deja en cero: una linea de cero
 * articulos no significa nada para el comprador. Quien decide eso es quien usa
 * el componente, a traves de `alCambiar(0)`.
 */

type Propiedades = {
  valor: number
  alCambiar: (cantidad: number) => void
  maximo?: number
  etiqueta: string
  tamano?: 'sm' | 'md'
  deshabilitado?: boolean
}

export function SelectorCantidad({
  valor,
  alCambiar,
  maximo = CANTIDAD_MAXIMA,
  etiqueta,
  tamano = 'md',
  deshabilitado = false,
}: Propiedades) {
  // Estado propio para que el campo se pueda vaciar mientras se escribe sin
  // que el carrito interprete "" como 0 y borre la linea.
  const [texto, setTexto] = useState(String(valor))

  useEffect(() => {
    setTexto(String(valor))
  }, [valor])

  const tope = Math.min(maximo, CANTIDAD_MAXIMA)
  const alto = tamano === 'sm' ? 'h-8' : 'h-10'
  const ancho = tamano === 'sm' ? 'w-8' : 'w-10'

  function confirmar(bruto: string) {
    const n = Number.parseInt(bruto, 10)
    if (!Number.isFinite(n) || n < 1) {
      alCambiar(0)
      return
    }
    alCambiar(Math.min(n, tope))
  }

  return (
    <div className={clases('inline-flex items-center rounded-marca border border-borde bg-white', alto)}>
      <button
        type="button"
        onClick={() => alCambiar(valor - 1)}
        disabled={deshabilitado}
        aria-label={`Disminuir la cantidad de ${etiqueta}`}
        className={clases(
          'inline-flex h-full items-center justify-center rounded-l-marca text-texto-medio transition hover:bg-superficie-alt hover:text-texto disabled:opacity-40',
          ancho,
        )}
      >
        <Minus size={14} aria-hidden />
      </button>

      <label className="sr-only" htmlFor={`cantidad-${etiqueta.replace(/\W+/g, '-')}`}>
        Cantidad de {etiqueta}
      </label>
      <input
        id={`cantidad-${etiqueta.replace(/\W+/g, '-')}`}
        type="number"
        inputMode="numeric"
        min={1}
        max={tope}
        value={texto}
        disabled={deshabilitado}
        onChange={(e) => setTexto(e.target.value)}
        onBlur={(e) => confirmar(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') {
            e.preventDefault()
            confirmar((e.target as HTMLInputElement).value)
          }
        }}
        className={clases(
          'cifra h-full border-x border-borde bg-transparent text-center text-sm font-semibold text-texto [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none',
          tamano === 'sm' ? 'w-10' : 'w-12',
        )}
      />

      <button
        type="button"
        onClick={() => alCambiar(Math.min(valor + 1, tope))}
        disabled={deshabilitado || valor >= tope}
        aria-label={`Aumentar la cantidad de ${etiqueta}`}
        className={clases(
          'inline-flex h-full items-center justify-center rounded-r-marca text-texto-medio transition hover:bg-superficie-alt hover:text-texto disabled:opacity-40',
          ancho,
        )}
      >
        <Plus size={14} aria-hidden />
      </button>
    </div>
  )
}
