'use client'

import Link from 'next/link'
import { ShoppingCart } from 'lucide-react'

import { clases, dinero } from '@/lib/formato'
import { estiloBoton } from '@/componentes/ui/Boton'
import { Panel } from '@/componentes/ui/Panel'
import { useAccionesCarrito, useCarrito } from '@/funcionalidades/carrito/ProveedorCarrito'

import { LineaCarrito } from './LineaCarrito'

/**
 * El panel lateral del carrito, que se abre al anadir algo.
 *
 * Los totales se atenuan mientras hay una mutacion en vuelo (`pendientes > 0`):
 * el descuento del cupon lo calcula el servidor y mostrarlo como definitivo
 * antes de que responda seria mentir por 200 ms. Es honesto y cuesta una clase
 * de CSS.
 */

export function PanelCarrito() {
  const { carrito, panelAbierto, pendientes, montado } = useCarrito()
  const { cerrarPanel } = useAccionesCarrito()

  if (!montado) return null

  const items = carrito?.items ?? []
  const provisional = pendientes > 0

  return (
    <Panel
      abierto={panelAbierto}
      alCerrar={cerrarPanel}
      titulo={`Tu carrito${carrito && carrito.totalUnidades > 0 ? ` (${carrito.totalUnidades})` : ''}`}
      pie={
        items.length > 0 ? (
          <div className="space-y-3">
            <dl className={clases('space-y-1.5 text-sm', provisional && 'opacity-60 transition-opacity')}>
              <div className="flex justify-between">
                <dt className="text-texto-medio">Subtotal</dt>
                <dd className="cifra font-semibold text-texto">{dinero(carrito?.subtotal ?? 0)}</dd>
              </div>
              {carrito && carrito.descuento > 0 && (
                <div className="flex justify-between text-exito">
                  <dt>Descuento{carrito.cuponAplicado ? ` (${carrito.cuponAplicado})` : ''}</dt>
                  <dd className="cifra font-semibold">-{dinero(carrito.descuento)}</dd>
                </div>
              )}
              <div className="flex justify-between border-t border-borde pt-1.5 text-base">
                <dt className="font-semibold text-tinta">Total</dt>
                <dd className="cifra font-bold text-tinta">{dinero(carrito?.total ?? 0)}</dd>
              </div>
            </dl>

            <Link href="/carrito" onClick={cerrarPanel} className={clases(estiloBoton('primario', 'lg'), 'w-full')}>
              Ver el carrito
            </Link>
            <button
              type="button"
              onClick={cerrarPanel}
              className="w-full text-center text-[13px] font-medium text-texto-medio underline-offset-2 hover:underline"
            >
              Seguir comprando
            </button>
          </div>
        ) : undefined
      }
    >
      {items.length === 0 ? (
        <div className="flex h-full flex-col items-center justify-center gap-3 px-8 py-16 text-center">
          <ShoppingCart size={38} strokeWidth={1.5} className="text-borde-fuerte" aria-hidden />
          <p className="font-marca text-base font-semibold text-texto">Tu carrito esta vacio</p>
          <p className="text-sm text-texto-suave">Anade productos y apareceran aqui.</p>
          <Link href="/productos" onClick={cerrarPanel} className={clases(estiloBoton('sutil', 'md'), 'mt-1')}>
            Ver el catalogo
          </Link>
        </div>
      ) : (
        <ul className="divide-y divide-borde">
          {items.map((item) => (
            <LineaCarrito key={item.id ?? `nuevo-${item.productoId}`} item={item} compacta provisional={provisional} />
          ))}
        </ul>
      )}
    </Panel>
  )
}
