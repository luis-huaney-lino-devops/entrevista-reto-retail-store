import type { Metadata } from 'next'
import Image from 'next/image'
import Link from 'next/link'
import { notFound } from 'next/navigation'

import { Contenedor, Migas } from '@/componentes/disposicion/Seccion'
import { RejillaProductos } from '@/componentes/producto/RejillaProductos'
import { EnlaceBoton } from '@/componentes/ui/Boton'
import { SinResultados } from '@/componentes/ui/Estados'
import { categoriaPorSlug, esCategoriaDeTienda, listarCategorias, listarProductos, tolerante } from '@/lib/api.servidor'
import { ErrorApi } from '@/lib/errores'
import { deArchivo, TAMANOS } from '@/lib/imagenes'
import { MigasJsonLd, type Miga } from '@/lib/jsonld'
import type { Categoria } from '@/lib/tipos'

/**
 * Hub de categoria.
 *
 * **ISR de 600 s.** Cambia con menos frecuencia que un producto y es una pagina
 * de aterrizaje: lista las subcategorias y unos destacados.
 *
 * Esta pagina **no lee `searchParams` a proposito**. Si soportara
 * `?orden=&pagina=&precioMinimo=`, seria dinamica siempre y perderia la cache
 * justo en la ruta que mas interesa cachear, que es la que indexa el buscador.
 * Para eso esta el boton "Filtrar y ordenar", que lleva a `/productos` con la
 * categoria ya puesta.
 */

export const revalidate = 600

export async function generateStaticParams() {
  const categorias = await tolerante(listarCategorias(), [] as Categoria[])
  return categorias.filter(esCategoriaDeTienda).map((c) => ({ categoria: c.slug }))
}

type Props = { params: Promise<{ categoria: string }> }

async function cargar(slug: string): Promise<Categoria | null> {
  try {
    return await categoriaPorSlug(slug)
  } catch (e) {
    if (e instanceof ErrorApi && e.estado === 404) return null
    throw e
  }
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { categoria: slug } = await params
  const categoria = await cargar(slug)
  if (!categoria) return { title: 'Categoria no encontrada', robots: { index: false, follow: false } }

  return {
    title: categoria.nombre,
    description:
      categoria.descripcion ??
      `Todo en ${categoria.nombre}: ${categoria.subcategorias.map((s) => s.nombre).join(', ')}.`,
    alternates: { canonical: `/c/${categoria.slug}` },
  }
}

export default async function PaginaCategoria({ params }: Props) {
  const { categoria: slug } = await params
  const categoria = await cargar(slug)
  if (!categoria) notFound()

  const destacados = await tolerante(
    listarProductos({ categoria: categoria.slug, tamanoPagina: 8, orden: 'calificacion' }, { revalidar: 600 }),
    null,
  )

  const migas: Miga[] = [
    { nombre: 'Inicio', href: '/' },
    { nombre: categoria.nombre, href: `/c/${categoria.slug}` },
  ]

  return (
    <>
      <Contenedor className="pb-12">
        <Migas items={migas} />

        <header className="mb-8 flex flex-wrap items-center justify-between gap-4">
          <div>
            <h1 className="font-marca text-2xl font-bold text-tinta sm:text-3xl">{categoria.nombre}</h1>
            {categoria.descripcion && <p className="mt-1.5 max-w-2xl text-sm text-texto-suave">{categoria.descripcion}</p>}
          </div>
          <EnlaceBoton href={`/productos?categoria=${categoria.slug}`} variante="sutil">
            Filtrar y ordenar
          </EnlaceBoton>
        </header>

        {categoria.subcategorias.length > 0 ? (
          <section aria-labelledby="subcategorias" className="mb-12">
            <h2 id="subcategorias" className="mb-4 font-marca text-lg font-bold text-tinta">
              Subcategorias
            </h2>
            <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4 lg:grid-cols-4">
              {categoria.subcategorias.map((s) => {
                const imagen = deArchivo(s.imagen, 'tarjeta')
                return (
                  <li key={s.id}>
                    <Link
                      href={`/c/${categoria.slug}/${s.slug}`}
                      className="group flex h-full flex-col overflow-hidden rounded-marca border border-borde bg-white transition-all hover:-translate-y-0.5 hover:border-marca/40 hover:shadow-m"
                    >
                      <div className="relative aspect-[4/3] bg-superficie-alt">
                        {imagen ? (
                          <Image
                            src={imagen}
                            alt={s.imagen?.textoAlt ?? s.nombre}
                            fill
                            sizes={TAMANOS.categoria}
                            className="object-contain p-4 transition-transform duration-300 group-hover:scale-105"
                          />
                        ) : null}
                      </div>
                      <div className="p-3">
                        <p className="text-sm font-semibold text-texto group-hover:text-marca-oscura">{s.nombre}</p>
                        {s.descripcion && <p className="mt-0.5 line-clamp-2 text-xs text-texto-suave">{s.descripcion}</p>}
                      </div>
                    </Link>
                  </li>
                )
              })}
            </ul>
          </section>
        ) : (
          <SinResultados
            titulo="Esta categoria todavia no tiene subcategorias"
            descripcion="Vuelve pronto: estamos ampliando el catalogo."
            className="mb-12"
          />
        )}

        {destacados && destacados.items.length > 0 && (
          <section aria-labelledby="destacados-categoria">
            <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
              <h2 id="destacados-categoria" className="font-marca text-lg font-bold text-tinta">
                Lo mas valorado en {categoria.nombre}
              </h2>
              <Link
                href={`/productos?categoria=${categoria.slug}`}
                className="text-sm font-semibold text-tinta-claro hover:text-marca-oscura"
              >
                Ver los {destacados.totalItems} productos
              </Link>
            </div>
            <RejillaProductos productos={destacados.items} conPrioridad />
          </section>
        )}
      </Contenedor>

      <MigasJsonLd migas={migas} />
    </>
  )
}
