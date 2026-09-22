'use client'

import { useState } from 'react'
import { ArrowLeftRight, Check, Heart, Loader2, ShoppingCart } from 'lucide-react'

import { clases } from '@/lib/formato'
import type { ProductoResumen } from '@/lib/tipos'
import { useAccionesCarrito } from '@/funcionalidades/carrito/ProveedorCarrito'
import { useComparador } from '@/funcionalidades/comparador/ProveedorComparador'
import { useFavoritos } from '@/funcionalidades/favoritos/ProveedorFavoritos'

/**
 * Las acciones rapidas de la tarjeta: anadir, favorito y comparar.
 *
 * Es la **isla** de la tarjeta. El resto —imagen, nombre, precio, insignias— se
 * renderiza en el servidor y llega hecho en el HTML; aqui solo viaja el
 * JavaScript de tres botones.
 *
 * Consume `useAccionesCarrito` y no el estado del carrito: las acciones nunca
 * cambian de identidad, asi que una rejilla de 24 tarjetas no se repinta cuando
 * sube una cantidad en el panel lateral.
 *
 * El icono de comparar son **dos flechas opuestas** (`ArrowLeftRight`), el
 * simbolo de intercambio de toda la vida. La balanza que habia antes se lee
 * como "pesar", "justicia" o "moderacion" segun quien mire; las dos flechas
 * dicen "enfrentar esto con aquello" y no dicen otra cosa. Es el mismo icono en
 * la tarjeta, en la ficha, en la cabecera y en `/comparar`.
 *
 * Accesibilidad: los tres son botones de solo icono, asi que los tres llevan
 * `aria-label`, y el estado (favorito si/no, comparando si/no) va en
 * `aria-pressed`, no solo en el color. Aparecen al pasar el raton **y** al
 * recibir el foco: `group-focus-within` es lo que los hace alcanzables con
 * teclado.
 */

type Propiedades = {
  producto: ProductoResumen
  /** En la ficha y en el carrito el boton de anadir se pinta aparte. */
  sinAgregar?: boolean
}

export function AccionesRapidas({ producto, sinAgregar = false }: Propiedades) {
  const { agregar } = useAccionesCarrito()
  const { esFavorito, alternar: alternarFavorito, montado: favMontado } = useFavoritos()
  const { estaComparando, alternar: alternarComparador, montado: cmpMontado } = useComparador()
  const [enviando, setEnviando] = useState(false)

  // Hasta que los providers montan, el estado se pinta como "no marcado": es
  // lo mismo que renderizo el servidor, asi que la hidratacion cuadra.
  const favorito = favMontado && esFavorito(producto.id)
  const comparando = cmpMontado && estaComparando(producto.id)

  async function alAgregar() {
    if (enviando) return
    setEnviando(true)
    try {
      await agregar(producto, 1)
    } finally {
      setEnviando(false)
    }
  }

  return (
    <div className="flex items-center gap-1.5">
      <BotonIcono
        etiqueta={favorito ? `Quitar "${producto.nombre}" de favoritos` : `Guardar "${producto.nombre}" en favoritos`}
        presionado={favorito}
        onClick={() => void alternarFavorito(producto)}
        className={favorito ? 'border-marca/40 bg-marca-suave text-marca-oscura' : ''}
      >
        <Heart size={16} className={favorito ? 'fill-marca text-marca' : ''} aria-hidden />
      </BotonIcono>

      <BotonIcono
        etiqueta={comparando ? `Quitar "${producto.nombre}" del comparador` : `Comparar "${producto.nombre}"`}
        presionado={comparando}
        onClick={() => alternarComparador(producto)}
        className={comparando ? 'border-tinta-claro/40 bg-tinta-suave text-tinta-claro' : ''}
      >
        <ArrowLeftRight size={16} aria-hidden />
      </BotonIcono>

      {!sinAgregar && (
        <button
          type="button"
          onClick={() => void alAgregar()}
          disabled={enviando || !producto.hayStock}
          aria-label={producto.hayStock ? `Anadir "${producto.nombre}" al carrito` : 'Sin stock'}
          className={clases(
            'inline-flex h-9 flex-1 items-center justify-center gap-1.5 rounded-marca text-[13px] font-semibold transition-colors',
            producto.hayStock
              ? 'bg-marca text-white hover:bg-marca-oscura'
              : 'cursor-not-allowed border border-borde bg-superficie-alt text-texto-suave',
          )}
        >
          {enviando ? (
            <Loader2 size={15} className="animate-spin" aria-hidden />
          ) : producto.hayStock ? (
            <ShoppingCart size={15} aria-hidden />
          ) : (
            <Check size={15} aria-hidden />
          )}
          <span>{producto.hayStock ? (enviando ? 'Anadiendo' : 'Anadir') : 'Sin stock'}</span>
        </button>
      )}
    </div>
  )
}

function BotonIcono({
  etiqueta,
  presionado,
  onClick,
  className,
  children,
}: {
  etiqueta: string
  presionado: boolean
  onClick: () => void
  className?: string
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={etiqueta}
      aria-pressed={presionado}
      title={etiqueta}
      className={clases(
        'inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-marca border border-borde bg-white text-texto-medio transition-colors hover:border-borde-fuerte hover:text-texto',
        className,
      )}
    >
      {children}
    </button>
  )
}
