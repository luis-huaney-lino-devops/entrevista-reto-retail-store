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

/**
 * A partir de cuantas unidades deja de avisarse.
 *
 * Avisar de que quedan 80 no informa: solo ensena a ignorar el mensaje, y
 * entonces tampoco se lee cuando quedan dos.
 */
const UMBRAL_POCAS = 5

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

        {/* Tres estados, no uno. Antes solo se avisaba cuando la cantidad YA
            superaba el stock, asi que quien iba a pedir la ultima unidad no se
            enteraba hasta chocar con el tope del selector sin explicacion.

            El numero sale de la ultima respuesta del servidor y puede quedarse
            viejo si otra persona compra a la vez; por eso el servidor revalida
            al confirmar (RN-051) y el proveedor vuelve a leer el carrito cuando
            responde INSUFFICIENT_STOCK. Esto es una ayuda, no la garantia. */}
        {item.stockDisponible === 0 ? (
          <p className="text-xs font-medium text-peligro">
            Se quedo sin stock. Quitalo para poder confirmar la compra.
          </p>
        ) : item.cantidad > item.stockDisponible ? (
          <p className="text-xs font-medium text-peligro">
            Solo quedan {item.stockDisponible}{' '}
            {item.stockDisponible === 1 ? 'unidad' : 'unidades'}: baja la cantidad para continuar.
          </p>
        ) : item.cantidad === item.stockDisponible ? (
          <p className="text-xs font-medium text-aviso">
            Llevas la ultima {item.stockDisponible === 1 ? 'unidad' : `existencia (${item.stockDisponible})`}.
          </p>
        ) : item.stockDisponible <= UMBRAL_POCAS ? (
          <p className="text-xs text-aviso">
            Quedan {item.stockDisponible} {item.stockDisponible === 1 ? 'unidad' : 'unidades'}.
          </p>
        ) : null}

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
