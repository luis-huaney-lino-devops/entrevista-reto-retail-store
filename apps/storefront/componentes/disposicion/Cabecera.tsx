import Image from 'next/image'
import Link from 'next/link'
import { Suspense } from 'react'

import { esCategoriaDeTienda, listarCategorias, tolerante } from '@/lib/api.servidor'

import { Buscador } from './Buscador'
import { ContadoresCabecera } from './InsigniaCarrito'
import { MenuCategorias } from './MenuCategorias'
import { MenuCuenta } from './MenuCuenta'

/**
 * La cabecera.
 *
 * Es un **componente de servidor**: pide las categorias una vez, con ISR de
 * diez minutos, y las pasa ya resueltas al desplegable. Las islas de cliente
 * son solo las cuatro que tienen estado —buscador, menu de categorias,
 * contadores y cuenta— y cada una es diminuta.
 *
 * El error tipico seria marcar la cabecera entera con `'use client'` porque el
 * carrito necesita estado: eso convertiria todo su subarbol en cliente y se
 * perderia la razon por la que se eligio Next.
 */

export async function Cabecera() {
  const categorias = (await tolerante(listarCategorias(), [])).filter(esCategoriaDeTienda)

  return (
    <header className="sticky top-0 z-50 border-b border-borde bg-white/95 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-2 px-3 sm:gap-4 sm:px-6">
        <Link href="/" aria-label="Retail Store, ir a la portada" className="shrink-0">
          <Image
            src="/logo.webp"
            alt="Retail Store"
            width={150}
            height={44}
            priority
            className="hidden h-9 w-auto object-contain sm:block"
          />
          <Image
            src="/isotipo.webp"
            alt="Retail Store"
            width={40}
            height={40}
            priority
            className="h-9 w-9 object-contain sm:hidden"
          />
        </Link>

        <div className="hidden lg:block">
          <MenuCategorias categorias={categorias} />
        </div>

        {/* `useSearchParams` obliga a envolver en Suspense: sin esto, toda la
            pagina se volveria dinamica y se perderia el ISR del catalogo. */}
        <div className="min-w-0 flex-1">
          <Suspense fallback={<div className="h-10 rounded-marca border border-borde bg-white" />}>
            <Buscador />
          </Suspense>
        </div>

        <div className="flex shrink-0 items-center gap-1">
          <ContadoresCabecera />
          <MenuCuenta />
        </div>
      </div>

      {/* Fila de categorias para pantallas medianas y grandes. En movil se
          llega por el catalogo, que ya tiene el filtro completo. */}
      <nav aria-label="Categorias destacadas" className="hidden border-t border-borde bg-superficie-alt lg:block">
        <ul className="mx-auto flex max-w-7xl items-center gap-1 overflow-x-auto px-6 py-1.5">
          <li>
            <Link
              href="/productos"
              className="inline-block whitespace-nowrap rounded-marca px-2.5 py-1.5 text-[13px] font-semibold text-texto-medio transition hover:bg-white hover:text-marca-oscura"
            >
              Todo el catalogo
            </Link>
          </li>
          {categorias.slice(0, 8).map((c) => (
            <li key={c.id}>
              <Link
                href={`/c/${c.slug}`}
                className="inline-block whitespace-nowrap rounded-marca px-2.5 py-1.5 text-[13px] text-texto-medio transition hover:bg-white hover:text-marca-oscura"
              >
                {c.nombre}
              </Link>
            </li>
          ))}
        </ul>
      </nav>
    </header>
  )
}
