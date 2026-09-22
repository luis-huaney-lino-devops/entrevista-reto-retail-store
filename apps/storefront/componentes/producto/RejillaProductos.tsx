import { clases } from '@/lib/formato'
import type { ProductoResumen } from '@/lib/tipos'

import { TarjetaProducto } from './TarjetaProducto'

/**
 * La rejilla de productos.
 *
 * Es una **lista** (`<ul>`/`<li>`) y no una sucesion de `<div>`: asi un lector
 * de pantalla anuncia "lista de 12 elementos" y se puede navegar por ella.
 *
 * `items-stretch` con `h-full` en la tarjeta mantiene todas las tarjetas de una
 * fila a la misma altura aunque los nombres ocupen distinto: si no, el precio
 * de cada una queda a una altura y la rejilla parece rota.
 */

type Propiedades = {
  productos: ProductoResumen[]
  /** Precarga la imagen de la primera tarjeta. Solo en la rejilla principal. */
  conPrioridad?: boolean
  /** Atenua la rejilla mientras el servidor re-renderiza con otros filtros. */
  atenuada?: boolean
  columnas?: 'normal' | 'ancha'
  className?: string
}

export function RejillaProductos({
  productos,
  conPrioridad = false,
  atenuada = false,
  columnas = 'normal',
  className,
}: Propiedades) {
  return (
    <ul
      className={clases(
        'grid items-stretch gap-3 sm:gap-4',
        columnas === 'ancha'
          ? 'grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5'
          : 'grid-cols-2 sm:grid-cols-3 lg:grid-cols-4',
        atenuada && 'pointer-events-none opacity-55 transition-opacity',
        className,
      )}
    >
      {productos.map((p, i) => (
        <li key={p.id} className="flex">
          <TarjetaProducto producto={p} prioritaria={conPrioridad && i === 0} className="w-full" />
        </li>
      ))}
    </ul>
  )
}
