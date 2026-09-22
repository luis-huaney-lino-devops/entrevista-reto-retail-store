'use client'

import { Heart, Scale, ShoppingCart } from 'lucide-react'
import Link from 'next/link'

import { useAccionesCarrito, useCarrito } from '@/funcionalidades/carrito/ProveedorCarrito'
import { useComparador } from '@/funcionalidades/comparador/ProveedorComparador'
import { useFavoritos } from '@/funcionalidades/favoritos/ProveedorFavoritos'

/**
 * Los tres contadores de la cabecera: favoritos, comparador y carrito.
 *
 * **Ninguno se renderiza en servidor con datos.** El numero depende de
 * `localStorage`, y el servidor no lo tiene: escribir `<span>{cuenta}</span>`
 * daria `0` en el servidor y `3` en el cliente, un desajuste de hidratacion en
 * el componente que aparece en **todas** las paginas.
 *
 * La solucion es el `montado` del provider: el primer render del cliente es
 * identico al del servidor —icono sin numero— y la cifra aparece en el
 * siguiente pintado. Lo que **no** es solucion es `suppressHydrationWarning`:
 * no arregla el desajuste, solo silencia el aviso y se pierde la senal.
 *
 * El hueco se reserva con un ancho minimo para que la aparicion del numero no
 * mueva la cabecera, y la region va con `aria-live="polite"`: quien no ve la
 * pantalla tiene que enterarse de que el carrito paso a tres articulos.
 */

export function ContadoresCabecera() {
  const { totalUnidades, montado: carritoMontado } = useUnidades()
  const { ids, montado: favMontado } = useFavoritos()
  const { productos, montado: cmpMontado } = useComparador()
  const { abrirPanel } = useAccionesCarrito()

  return (
    <div className="flex items-center gap-0.5">
      <Link
        href="/favoritos"
        aria-label="Mis favoritos"
        className="relative inline-flex h-10 w-10 items-center justify-center rounded-marca text-tinta transition hover:bg-tinta-suave"
      >
        <Heart size={19} aria-hidden />
        <Contador valor={favMontado ? ids.size : 0} />
      </Link>

      <Link
        href="/comparar"
        aria-label="Comparador de productos"
        className="relative inline-flex h-10 w-10 items-center justify-center rounded-marca text-tinta transition hover:bg-tinta-suave"
      >
        <Scale size={19} aria-hidden />
        <Contador valor={cmpMontado ? productos.length : 0} />
      </Link>

      <button
        type="button"
        onClick={abrirPanel}
        aria-label="Abrir el carrito"
        className="relative inline-flex h-10 w-10 items-center justify-center rounded-marca text-tinta transition hover:bg-tinta-suave"
      >
        <ShoppingCart size={19} aria-hidden />
        <Contador valor={carritoMontado ? totalUnidades : 0} destacado />
      </button>

      <span aria-live="polite" className="sr-only">
        {carritoMontado && totalUnidades > 0
          ? `${totalUnidades} ${totalUnidades === 1 ? 'articulo' : 'articulos'} en el carrito`
          : ''}
      </span>
    </div>
  )
}

/** Solo el numero de unidades, para no repintar por cualquier cambio de estado. */
function useUnidades() {
  const { carrito, montado } = useCarrito()
  return { totalUnidades: carrito?.totalUnidades ?? 0, montado }
}

function Contador({ valor, destacado = false }: { valor: number; destacado?: boolean }) {
  if (valor <= 0) return null
  return (
    <span
      aria-hidden
      className={`absolute -right-0.5 -top-0.5 inline-flex h-[18px] min-w-[18px] items-center justify-center rounded-full px-1 text-[10px] font-bold leading-none text-white ${
        destacado ? 'bg-marca' : 'bg-tinta'
      }`}
    >
      {valor > 99 ? '99+' : valor}
    </span>
  )
}
