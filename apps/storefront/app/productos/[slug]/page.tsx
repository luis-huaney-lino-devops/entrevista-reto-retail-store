import type { Metadata } from 'next'
import Link from 'next/link'
import { notFound } from 'next/navigation'
import { Check, PackageX, Truck } from 'lucide-react'

import { Contenedor, Migas } from '@/componentes/disposicion/Seccion'
import { ComprarProducto } from '@/componentes/producto/ComprarProducto'
import { Estrellas } from '@/componentes/producto/Estrellas'
import { Galeria } from '@/componentes/producto/Galeria'
import { Markdown } from '@/componentes/producto/Markdown'
import { Precio } from '@/componentes/producto/Precio'
import { RejillaProductos } from '@/componentes/producto/RejillaProductos'
import { listarProductos, productoPorSlug, relacionados, tolerante } from '@/lib/api.servidor'
import { ErrorApi } from '@/lib/errores'
import { MigasJsonLd, ProductoJsonLd, type Miga } from '@/lib/jsonld'
import { recortar } from '@/lib/formato'
import type { ProductoDetalle, ProductoResumen } from '@/lib/tipos'

/**
 * La ficha de producto.
 *
 * **ISR con `revalidate` de 300 s y `generateStaticParams`.** Es la pagina que
 * se comparte y se indexa: se pregeneran los destacados y el resto se genera en
 * la primera visita y queda cacheado. Los siguientes miles de visitantes la
 * reciben sin tocar la API ni la base — que en un VPS de 2 GB no es un lujo.
 *
 * La frontera servidor/cliente de esta pagina:
 *
 *   page.tsx              <- SERVIDOR
 *   |- Migas              <- servidor (datos, sin interaccion)
 *   |- Galeria            <- CLIENTE (cambia de imagen al pulsar)
 *   |- Precio             <- servidor (solo formatea)
 *   |- ComprarProducto    <- CLIENTE (isla: cantidad, anadir, favorito)
 *   |- Markdown           <- servidor (el parser no viaja al navegador)
 *   |- Relacionados       <- servidor (misma subcategoria, en la misma pagina
 *   |                        cacheada: no es una peticion extra del navegador)
 *   \- JSON-LD            <- servidor (un `<script>`, cero JS al cliente)
 */

export const revalidate = 300

/** Se pregeneran los destacados, que son los que mas se visitan. El resto se
 *  genera bajo demanda: `dynamicParams` sigue en su valor por defecto. */
export async function generateStaticParams() {
  const destacados = await tolerante(listarProductos({ destacado: true, tamanoPagina: 24 }), null)
  return (destacados?.items ?? []).map((p) => ({ slug: p.slug }))
}

type Props = { params: Promise<{ slug: string }> }

async function cargar(slug: string): Promise<ProductoDetalle | null> {
  try {
    return await productoPorSlug(slug)
  } catch (e) {
    if (e instanceof ErrorApi && (e.estado === 404 || e.codigo === 'PRODUCT_NOT_FOUND')) return null
    throw e
  }
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params
  const producto = await cargar(slug)

  // Si el producto no existe, los metadatos tampoco se inventan: la pagina va a
  // dar 404 y anunciar un titulo seria describir algo que no esta.
  if (!producto) return { title: 'Producto no encontrado', robots: { index: false, follow: false } }

  const titulo = producto.marca ? `${producto.nombre} - ${producto.marca.nombre}` : producto.nombre
  const primera = producto.imagenes[0]
  const imagen = primera?.detalle ?? primera?.tarjeta ?? undefined

  return {
    title: titulo,
    description: recortar(producto.descripcionCorta ?? producto.nombre, 155),
    alternates: { canonical: `/productos/${producto.slug}` },
    openGraph: {
      type: 'website',
      title: titulo,
      description: recortar(producto.descripcionCorta ?? producto.nombre, 155),
      url: `/productos/${producto.slug}`,
      // La variante `detalle` son 1200 px de ancho, que es justo lo que piden
      // las previsualizaciones de WhatsApp y las redes.
      images: imagen ? [{ url: imagen, alt: primera?.textoAlt ?? producto.nombre }] : undefined,
    },
  }
}

export default async function PaginaProducto({ params }: Props) {
  const { slug } = await params
  const producto = await cargar(slug)

  // Un producto inexistente es `notFound()`, no un error: da un 404 de verdad,
  // que es lo que el rastreador necesita ver.
  if (!producto) notFound()

  const similares = await tolerante(relacionados(slug), [] as ProductoResumen[])

  const migas: Miga[] = [{ nombre: 'Inicio', href: '/' }]
  if (producto.categoria) migas.push({ nombre: producto.categoria.nombre, href: `/c/${producto.categoria.slug}` })
  if (producto.categoria && producto.subcategoria) {
    migas.push({
      nombre: producto.subcategoria.nombre,
      href: `/c/${producto.categoria.slug}/${producto.subcategoria.slug}`,
    })
  }
  migas.push({ nombre: producto.nombre, href: `/productos/${producto.slug}` })

  // El resumen que necesita la isla de compra. Solo datos planos cruzan.
  const resumen: ProductoResumen = {
    id: producto.id,
    nombre: producto.nombre,
    slug: producto.slug,
    descripcionCorta: producto.descripcionCorta,
    precio: producto.precio,
    precioAnterior: producto.precioAnterior,
    porcentajeDescuento: producto.porcentajeDescuento,
    hayStock: producto.hayStock,
    destacado: producto.destacado,
    calificacionPromedio: producto.calificacionPromedio,
    calificacionConteo: producto.calificacionConteo,
    imagen: producto.imagenes[0]?.tarjeta ?? producto.imagenes[0]?.miniatura ?? null,
    textoAltImagen: producto.imagenes[0]?.textoAlt ?? null,
    marca: producto.marca,
    subcategoria: producto.subcategoria,
  }

  return (
    <>
      <Contenedor className="pb-12">
        <Migas items={migas} />

        <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:gap-12">
          <Galeria imagenes={producto.imagenes} nombreProducto={producto.nombre} />

          <div className="space-y-5">
            <div className="space-y-2">
              {producto.marca && (
                <Link
                  href={`/productos?marca=${producto.marca.slug}`}
                  className="inline-block text-xs font-semibold uppercase tracking-wide text-tinta-claro hover:text-marca-oscura"
                >
                  {producto.marca.nombre}
                </Link>
              )}
              <h1 className="font-marca text-2xl font-bold leading-tight text-tinta sm:text-3xl">{producto.nombre}</h1>
              <Estrellas
                promedio={producto.calificacionPromedio}
                conteo={producto.calificacionConteo}
                tamano={15}
              />
            </div>

            {producto.descripcionCorta && (
              <p className="text-[15px] leading-relaxed text-texto-medio">{producto.descripcionCorta}</p>
            )}

            <div className="rounded-marca border border-borde bg-white p-5">
              <Precio
                precio={producto.precio}
                precioAnterior={producto.precioAnterior}
                porcentajeDescuento={producto.porcentajeDescuento}
                tamano="lg"
              />
              <p className="mt-1 text-xs text-texto-suave">Precio con IGV incluido.</p>

              {/* El estado nunca se comunica solo con color: se dice con
                  palabras, y el numero exacto cuando queda poco. */}
              <p className="mt-4 flex items-center gap-2 text-sm">
                {producto.hayStock ? (
                  <>
                    <Check size={16} className="text-exito" aria-hidden />
                    <span className="font-medium text-exito">
                      {producto.stock <= 10 ? `Quedan ${producto.stock} unidades` : 'Disponible'}
                    </span>
                  </>
                ) : (
                  <>
                    <PackageX size={16} className="text-peligro" aria-hidden />
                    <span className="font-medium text-peligro">Agotado</span>
                  </>
                )}
              </p>

              <div className="mt-5">
                <ComprarProducto producto={resumen} stock={producto.stock} />
              </div>
            </div>

            <p className="flex items-start gap-2 text-sm text-texto-suave">
              <Truck size={16} className="mt-0.5 shrink-0" aria-hidden />
              Entrega coordinada a obra o domicilio en todo el Peru.
            </p>
          </div>
        </div>

        {producto.descripcion && (
          <section className="mt-12 max-w-3xl" aria-labelledby="descripcion-producto">
            <h2 id="descripcion-producto" className="mb-3 font-marca text-lg font-bold text-tinta">
              Descripcion
            </h2>
            <Markdown texto={producto.descripcion} />
          </section>
        )}

        {similares.length > 0 && (
          <section className="mt-14" aria-labelledby="relacionados">
            <h2 id="relacionados" className="mb-5 font-marca text-xl font-bold text-tinta">
              Productos relacionados
            </h2>
            <RejillaProductos productos={similares} />
          </section>
        )}
      </Contenedor>

      <ProductoJsonLd producto={producto} />
      <MigasJsonLd migas={migas} />
    </>
  )
}
