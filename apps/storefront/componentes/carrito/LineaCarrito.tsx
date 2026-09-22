'use client'

import Image from 'next/image'
import Link from 'next/link'
import { Trash2 } from 'lucide-react'

import { clases, dinero } from '@/lib/formato'
import { TAMANOS, variante } from '@/lib/imagenes'
import type { ItemCarrito } from '@/lib/tipos'
import { useAccionesCarrito } from '@/funcionalidades/carrito/ProveedorCarrito'

import { SelectorCantidad } from './SelectorCantidad'

/**
 * Una linea del carrito. La usan el panel lateral y la pagina `/carrito`.
 *
 * Mientras la linea no tiene `id` —el instante entre el clic optimista y la
 * respuesta del servidor— los controles quedan deshabilitados: no se puede
 * hacer un `PATCH` a un id que todavia no existe.
 */

type Propiedades = {
  item: ItemCarrito
  compacta?: boolean
  provisional?: boolean
}

export function LineaCarrito({ item, compacta = false, provisional = false }: Propiedades) {
  const { fijarCantidad, quitar } = useAccionesCarrito()
  const imagen = variante(item.imagen, 'miniatura')
  const sinId = item.id === null

  return (
    <li
      className={clases(
        'flex gap-3 py-3.5',
        compacta ? 'px-5' : 'px-0',
        provisional && 'opacity-60 transition-opacity',
      )}
    >
      <Link
        href={`/productos/${item.slug}`}
        className="relative h-16 w-16 shrink-0 overflow-hidden rounded-marca border border-borde bg-white sm:h-20 sm:w-20"
      >
        {imagen ? (
          <Image src={imagen} alt="" fill sizes={TAMANOS.miniatura} className="object-contain p-1.5" />
        ) : null}
      </Link>

      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        <div className="flex items-start justify-between gap-2">
          <Link
            href={`/productos/${item.slug}`}
            className="line-clamp-2 text-sm font-semibold leading-snug text-texto hover:text-marca-oscura"
          >
            {item.nombre}
          </Link>
          <button
            type="button"
            onClick={() => void quitar(item.id ?? 0)}
            disabled={sinId}
            aria-label={`Quitar "${item.nombre}" del carrito`}
            className="shrink-0 rounded p-1 text-texto-suave transition hover:bg-peligro-suave hover:text-peligro disabled:opacity-40"
          >
            <Trash2 size={15} aria-hidden />
          </button>
        </div>

        <p className="cifra text-xs text-texto-suave">{dinero(item.precioUnitario)} por unidad</p>

        {item.cantidad > item.stockDisponible && (
          <p className="text-xs font-medium text-aviso">
            Solo quedan {item.stockDisponible} {item.stockDisponible === 1 ? 'unidad' : 'unidades'}.
          </p>
        )}

        <div className="mt-auto flex items-center justify-between gap-2 pt-1">
          <SelectorCantidad
            valor={item.cantidad}
            maximo={item.stockDisponible}
            etiqueta={item.nombre}
            tamano="sm"
            deshabilitado={sinId}
            alCambiar={(c) => (item.id === null ? undefined : fijarCantidad(item.id, c))}
          />
          <p className="cifra text-sm font-bold text-tinta">{dinero(item.totalLinea)}</p>
        </div>
      </div>
    </li>
  )
}
