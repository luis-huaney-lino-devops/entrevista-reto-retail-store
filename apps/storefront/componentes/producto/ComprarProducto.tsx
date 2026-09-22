'use client'

import { useState } from 'react'
import { ArrowLeftRight, Heart, Loader2, ShoppingCart } from 'lucide-react'

import { clases } from '@/lib/formato'
import type { ProductoResumen } from '@/lib/tipos'
import { useAccionesCarrito } from '@/funcionalidades/carrito/ProveedorCarrito'
import { useComparador } from '@/funcionalidades/comparador/ProveedorComparador'
import { useFavoritos } from '@/funcionalidades/favoritos/ProveedorFavoritos'
import { SelectorCantidad } from '@/componentes/carrito/SelectorCantidad'

/**
 * El bloque de compra de la ficha: cantidad, anadir, favorito y comparar.
 *
 * Es la unica isla grande de la ficha; el resto de la pagina —galeria aparte—
 * se renderiza en el servidor. El error tipico seria marcar la ficha entera con
 * `'use client'` porque el boton necesita estado: lo correcto es que el boton
 * sea la isla.
 *
 * El producto llega como objeto plano desde el servidor. Solo cruzan la
 * frontera datos serializables: ni funciones, ni clases, ni tokens.
 */

type Propiedades = {
  producto: ProductoResumen
  stock: number
}

export function ComprarProducto({ producto, stock }: Propiedades) {
  const { agregar } = useAccionesCarrito()
  const { esFavorito, alternar: alternarFavorito, montado: favMontado } = useFavoritos()
  const { estaComparando, alternar: alternarComparador, montado: cmpMontado } = useComparador()
  const [cantidad, setCantidad] = useState(1)
  const [enviando, setEnviando] = useState(false)

  const favorito = favMontado && esFavorito(producto.id)
  const comparando = cmpMontado && estaComparando(producto.id)

  async function alAgregar() {
    if (enviando || !producto.hayStock) return
    setEnviando(true)
    try {
      await agregar(producto, cantidad)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="space-y-4">
      {producto.hayStock ? (
        <div className="flex flex-wrap items-center gap-3">
          <SelectorCantidad
            valor={cantidad}
            maximo={stock}
            etiqueta={producto.nombre}
            alCambiar={(c) => setCantidad(Math.max(1, c))}
          />

          <button
            type="button"
            onClick={() => void alAgregar()}
            disabled={enviando}
            className="inline-flex h-12 flex-1 min-w-[200px] items-center justify-center gap-2 rounded-marca bg-marca px-6 text-[15px] font-semibold text-white transition-colors hover:bg-marca-oscura disabled:opacity-60"
          >
            {enviando ? <Loader2 size={18} className="animate-spin" aria-hidden /> : <ShoppingCart size={18} aria-hidden />}
            {enviando ? 'Anadiendo...' : 'Anadir al carrito'}
          </button>
        </div>
      ) : (
        <p className="rounded-marca border border-borde bg-superficie-alt px-4 py-3 text-sm font-medium text-texto-medio">
          Este producto esta agotado. Puedes guardarlo en favoritos y te sera mas facil encontrarlo cuando vuelva.
        </p>
      )}

      <div className="flex flex-wrap gap-2">
        <button
          type="button"
          onClick={() => void alternarFavorito(producto)}
          aria-pressed={favorito}
          className={clases(
            'inline-flex h-10 items-center gap-2 rounded-marca border px-4 text-sm font-semibold transition-colors',
            favorito
              ? 'border-marca/40 bg-marca-suave text-marca-oscura'
              : 'border-borde bg-white text-texto-medio hover:border-borde-fuerte hover:text-texto',
          )}
        >
          <Heart size={16} className={favorito ? 'fill-marca text-marca' : ''} aria-hidden />
          {favorito ? 'En favoritos' : 'Guardar en favoritos'}
        </button>

        <button
          type="button"
          onClick={() => alternarComparador(producto)}
          aria-pressed={comparando}
          className={clases(
            'inline-flex h-10 items-center gap-2 rounded-marca border px-4 text-sm font-semibold transition-colors',
            comparando
              ? 'border-tinta-claro/40 bg-tinta-suave text-tinta-claro'
              : 'border-borde bg-white text-texto-medio hover:border-borde-fuerte hover:text-texto',
          )}
        >
          <ArrowLeftRight size={16} aria-hidden />
          {comparando ? 'En el comparador' : 'Comparar'}
        </button>
      </div>
    </div>
  )
}
