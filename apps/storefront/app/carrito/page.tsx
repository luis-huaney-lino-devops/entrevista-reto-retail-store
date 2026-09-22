'use client'

import { useState } from 'react'
import Link from 'next/link'
import { Loader2, ShoppingCart, Ticket, X } from 'lucide-react'

import { LineaCarrito } from '@/componentes/carrito/LineaCarrito'
import { Contenedor, Migas } from '@/componentes/disposicion/Seccion'
import { Boton, EnlaceBoton } from '@/componentes/ui/Boton'
import { SinResultados } from '@/componentes/ui/Estados'
import { clases, dinero } from '@/lib/formato'
import { useAccionesCarrito, useCarrito } from '@/funcionalidades/carrito/ProveedorCarrito'

/**
 * La pagina del carrito.
 *
 * **Es cliente entera, y a proposito.** El carrito es estado de cliente puro:
 * renderizarlo en servidor no aporta nada, impediria cachear la ruta y ademas
 * seria imposible, porque el `id` del carrito vive en `localStorage` y el
 * servidor no lo tiene.
 *
 * Mientras hay una mutacion en vuelo, el resumen se atenua: el descuento del
 * cupon lo calcula el servidor (ADR-0002) y ensenarlo como definitivo antes de
 * que responda seria mentir por 200 ms.
 */
export default function PaginaCarrito() {
  const { carrito, montado, estado, pendientes } = useCarrito()
  const items = carrito?.items ?? []
  const provisional = pendientes > 0

  return (
    <Contenedor className="pb-12">
      <Migas
        items={[
          { nombre: 'Inicio', href: '/' },
          { nombre: 'Mi carrito', href: '/carrito' },
        ]}
      />

      <h1 className="mb-6 font-marca text-2xl font-bold text-tinta sm:text-3xl">Mi carrito</h1>

      {!montado || estado === 'hidratando' ? (
        <EsqueletoCarrito />
      ) : items.length === 0 ? (
        <SinResultados
          icono={<ShoppingCart size={38} strokeWidth={1.5} />}
          titulo="Tu carrito esta vacio"
          descripcion="Anade productos del catalogo y apareceran aqui con su total actualizado."
          accion={
            <EnlaceBoton href="/productos" variante="primario">
              Ver el catalogo
            </EnlaceBoton>
          }
        />
      ) : (
        <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_340px]">
          <section aria-label="Productos en el carrito" className="rounded-marca border border-borde bg-white px-5">
            <ul className="divide-y divide-borde">
              {items.map((item) => (
                <LineaCarrito
                  key={item.id ?? `nuevo-${item.productoId}`}
                  item={item}
                  provisional={provisional}
                />
              ))}
            </ul>
          </section>

          <aside className="lg:sticky lg:top-32 lg:self-start">
            <Resumen provisional={provisional} />
          </aside>
        </div>
      )}
    </Contenedor>
  )
}

function Resumen({ provisional }: { provisional: boolean }) {
  const { carrito } = useCarrito()
  if (!carrito) return null

  return (
    <div className="space-y-4 rounded-marca border border-borde bg-white p-5">
      <h2 className="font-marca text-base font-semibold text-tinta">Resumen</h2>

      <FormularioCupon />

      <dl className={clases('space-y-2 text-sm', provisional && 'opacity-60 transition-opacity')}>
        <div className="flex justify-between">
          <dt className="text-texto-medio">
            Subtotal
            <span className="ml-1 text-texto-suave">
              ({carrito.totalUnidades} {carrito.totalUnidades === 1 ? 'articulo' : 'articulos'})
            </span>
          </dt>
          <dd className="cifra font-semibold text-texto">{dinero(carrito.subtotal)}</dd>
        </div>

        {carrito.descuento > 0 && (
          <div className="flex justify-between text-exito">
            <dt>Descuento{carrito.cuponAplicado ? ` (${carrito.cuponAplicado})` : ''}</dt>
            <dd className="cifra font-semibold">-{dinero(carrito.descuento)}</dd>
          </div>
        )}

        <div className="flex justify-between border-t border-borde pt-2 text-lg">
          <dt className="font-semibold text-tinta">Total</dt>
          <dd className="cifra font-bold text-tinta">{dinero(carrito.total)}</dd>
        </div>
      </dl>

      <p className="text-xs text-texto-suave">Precios con IGV incluido. El envio se calcula al confirmar el pedido.</p>

      <Boton variante="primario" tamano="lg" className="w-full" disabled title="El checkout todavia no esta disponible">
        Continuar la compra
      </Boton>
      <p className="text-center text-xs text-texto-suave">
        El pago aun no esta habilitado en esta demostracion.
      </p>

      <Link
        href="/productos"
        className="block text-center text-[13px] font-medium text-tinta-claro underline-offset-2 hover:underline"
      >
        Seguir comprando
      </Link>
    </div>
  )
}

function FormularioCupon() {
  const { carrito, pendientes } = useCarrito()
  const { aplicarCupon, quitarCupon } = useAccionesCarrito()
  const [codigo, setCodigo] = useState('')
  const [enviando, setEnviando] = useState(false)

  if (!carrito) return null

  if (carrito.cuponAplicado) {
    return (
      <div className="flex items-center justify-between gap-2 rounded-marca border border-exito/30 bg-exito-suave px-3 py-2.5">
        <p className="flex min-w-0 items-center gap-2 text-sm">
          <Ticket size={15} className="shrink-0 text-exito" aria-hidden />
          <span className="truncate font-semibold text-exito">{carrito.cuponAplicado}</span>
          {!carrito.cuponActivo && (
            <span className="truncate text-xs text-aviso">{carrito.motivoCuponInactivo ?? 'no aplicable'}</span>
          )}
        </p>
        <button
          type="button"
          onClick={() => void quitarCupon()}
          disabled={pendientes > 0}
          aria-label={`Quitar el cupon ${carrito.cuponAplicado}`}
          className="shrink-0 rounded p-1 text-texto-suave transition hover:text-peligro disabled:opacity-40"
        >
          <X size={15} aria-hidden />
        </button>
      </div>
    )
  }

  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault()
        if (!codigo.trim() || enviando) return
        setEnviando(true)
        try {
          const ok = await aplicarCupon(codigo)
          if (ok) setCodigo('')
        } finally {
          setEnviando(false)
        }
      }}
      className="flex gap-2"
    >
      <label htmlFor="codigo-cupon" className="sr-only">
        Codigo de cupon
      </label>
      <input
        id="codigo-cupon"
        value={codigo}
        onChange={(e) => setCodigo(e.target.value.toUpperCase())}
        placeholder="Codigo de cupon"
        autoComplete="off"
        className="h-10 min-w-0 flex-1 rounded-marca border border-borde px-3 text-sm uppercase tracking-wide hover:border-borde-fuerte"
      />
      <Boton type="submit" variante="sutil" disabled={!codigo.trim() || enviando}>
        {enviando ? <Loader2 size={15} className="animate-spin" aria-hidden /> : 'Aplicar'}
      </Boton>
    </form>
  )
}

function EsqueletoCarrito() {
  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_340px]">
      <div className="space-y-4 rounded-marca border border-borde bg-white p-5">
        {[0, 1, 2].map((i) => (
          <div key={i} className="flex gap-3">
            <div className="h-20 w-20 shrink-0 animate-pulse rounded-marca bg-superficie-alt" />
            <div className="flex-1 space-y-2">
              <div className="h-4 w-3/4 animate-pulse rounded bg-superficie-alt" />
              <div className="h-3 w-1/3 animate-pulse rounded bg-superficie-alt" />
              <div className="h-8 w-28 animate-pulse rounded bg-superficie-alt" />
            </div>
          </div>
        ))}
      </div>
      <div className="h-72 animate-pulse rounded-marca bg-white" />
    </div>
  )
}
