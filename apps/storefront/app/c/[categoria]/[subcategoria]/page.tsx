import type { Metadata } from 'next'
import { notFound, redirect } from 'next/navigation'

import { Contenedor, Migas } from '@/componentes/disposicion/Seccion'
import { RejillaProductos } from '@/componentes/producto/RejillaProductos'
import { EnlaceBoton } from '@/componentes/ui/Boton'
import { SinResultados } from '@/componentes/ui/Estados'
import {
  esCategoriaDeTienda,
  listarCategorias,
  listarProductos,
  subcategoriaPorSlug,
  tolerante,
} from '@/lib/api.servidor'
import { ErrorApi } from '@/lib/errores'
import { MigasJsonLd, type Miga } from '@/lib/jsonld'
import type { Categoria, Subcategoria } from '@/lib/tipos'

/**
 * Pagina de aterrizaje de una subcategoria.
 *
 * **ISR de 600 s, primera pagina, orden por defecto, sin filtros.** Es la ruta
 * indexable del listado; la version filtrable vive en `/productos` y lleva
 * `noindex, follow` para que las dos no compitan como contenido duplicado.
 *
 * El slug de subcategoria es unico globalmente
 * (`cementos-y-agregados-construccion-y-acabados`), asi que tecnicamente
 * serviria una URL plana. **Se elige la anidada como canonica** porque la
 * jerarquia en la ruta produce migas naturales, hace obvio el `BreadcrumbList`
 * y evita que una misma pagina exista en dos direcciones. Si alguien llega con
 * la categoria equivocada en la URL, se redirige a la buena en lugar de servir
 * la misma pagina en dos sitios.
 */

export const revalidate = 600

/** Se pregeneran todas: son pocas (una treintena) y son paginas de aterrizaje
 *  indexables, justo las que conviene tener ya en cache. */
export async function generateStaticParams() {
  const categorias = (await tolerante(listarCategorias(), [] as Categoria[])).filter(esCategoriaDeTienda)
  return categorias.flatMap((c) => c.subcategorias.map((s) => ({ categoria: c.slug, subcategoria: s.slug })))
}

type Props = { params: Promise<{ categoria: string; subcategoria: string }> }

async function cargar(slug: string): Promise<Subcategoria | null> {
  try {
    return await subcategoriaPorSlug(slug)
  } catch (e) {
    if (e instanceof ErrorApi && e.estado === 404) return null
    throw e
  }
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { subcategoria: slug } = await params
  const sub = await cargar(slug)
  if (!sub) return { title: 'Subcategoria no encontrada', robots: { index: false, follow: false } }

  return {
    title: sub.nombre,
    description: sub.descripcion ?? `Productos de ${sub.nombre} en Retail Store.`,
    alternates: { canonical: `/c/${sub.categoria?.slug ?? ''}/${sub.slug}` },
  }
}

export default async function PaginaSubcategoria({ params }: Props) {
  const { categoria: slugCategoria, subcategoria: slugSub } = await params
  const sub = await cargar(slugSub)
  if (!sub) notFound()

  // Una misma pagina no puede vivir en dos direcciones: si la categoria de la
  // URL no es la suya, se redirige a la canonica.
  if (sub.categoria && sub.categoria.slug !== slugCategoria) {
    redirect(`/c/${sub.categoria.slug}/${sub.slug}`)
  }

  const resultado = await tolerante(
    listarProductos({ subcategoria: sub.slug, tamanoPagina: 24 }, { revalidar: 600 }),
    null,
  )

  const migas: Miga[] = [{ nombre: 'Inicio', href: '/' }]
  if (sub.categoria) migas.push({ nombre: sub.categoria.nombre, href: `/c/${sub.categoria.slug}` })
  migas.push({ nombre: sub.nombre, href: `/c/${sub.categoria?.slug ?? slugCategoria}/${sub.slug}` })

  const productos = resultado?.items ?? []
  const total = resultado?.totalItems ?? 0

  return (
    <>
      <Contenedor className="pb-12">
        <Migas items={migas} />

        <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="font-marca text-2xl font-bold text-tinta sm:text-3xl">{sub.nombre}</h1>
            {sub.descripcion && <p className="mt-1.5 max-w-2xl text-sm text-texto-suave">{sub.descripcion}</p>}
            <p className="mt-2 text-sm text-texto-suave">
              <span className="cifra font-semibold text-texto">{total}</span> {total === 1 ? 'producto' : 'productos'}
            </p>
          </div>
          <EnlaceBoton href={`/productos?subcategoria=${sub.slug}`} variante="sutil">
            Filtrar y ordenar
          </EnlaceBoton>
        </header>

        {productos.length === 0 ? (
          <SinResultados
            titulo="Todavia no hay productos aqui"
            descripcion="Estamos ampliando esta subcategoria. Mientras tanto, echa un vistazo al catalogo completo."
            accion={
              <EnlaceBoton href="/productos" variante="sutil">
                Ver el catalogo
              </EnlaceBoton>
            }
          />
        ) : (
          <>
            <RejillaProductos productos={productos} conPrioridad columnas="ancha" />
            {total > productos.length && (
              <div className="mt-8 text-center">
                <EnlaceBoton href={`/productos?subcategoria=${sub.slug}`} variante="secundario">
                  Ver los {total} productos
                </EnlaceBoton>
              </div>
            )}
          </>
        )}
      </Contenedor>

      <MigasJsonLd migas={migas} />
    </>
  )
}
