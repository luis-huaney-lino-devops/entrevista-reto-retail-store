import Image from 'next/image'
import Link from 'next/link'
import { ShieldCheck, Tag, Truck, Wrench } from 'lucide-react'

import { Contenedor, Seccion } from '@/componentes/disposicion/Seccion'
import { RejillaProductos } from '@/componentes/producto/RejillaProductos'
import { EnlaceBoton } from '@/componentes/ui/Boton'
import { esCategoriaDeTienda, listarCategorias, listarProductos, tolerante } from '@/lib/api.servidor'
import { deArchivo, TAMANOS } from '@/lib/imagenes'
import type { Categoria, ProductoResumen } from '@/lib/tipos'

/**
 * La portada.
 *
 * **ISR de 60 segundos.** Destacados y ofertas cambian poco y la ve todo el
 * mundo: es la pagina que mas se beneficia de la cache. Se genera una vez y se
 * sirve a los siguientes visitantes sin tocar la API ni la base.
 *
 * Las cuatro lecturas van en paralelo con `Promise.all` y cada una es
 * *tolerante*: que la seccion de ofertas no responda no justifica un
 * `error.tsx` a pantalla completa; esa seccion sencillamente no se pinta.
 *
 * **Las ofertas se filtran aqui y no en la API** porque el endpoint no tiene un
 * parametro para ello: `GET /productos` acepta `texto`, `categoria`,
 * `subcategoria`, `marca`, `precioMinimo`, `precioMaximo`, `conStock`,
 * `destacado`, `orden` y paginacion, y nada mas. Se pide la pagina maxima (48)
 * y se quedan los que traen `porcentajeDescuento`. No es un calculo de negocio
 * —el descuento lo calcula el backend— sino una seleccion de lo que ya vino.
 */

export const revalidate = 60

export default async function Portada() {
  const [categorias, destacados, ofertasCrudas, novedades] = await Promise.all([
    tolerante(listarCategorias(), [] as Categoria[]),
    tolerante(listarProductos({ destacado: true, tamanoPagina: 8, orden: 'calificacion' }, { revalidar: 60 }), null),
    tolerante(listarProductos({ tamanoPagina: 48, orden: 'precio_asc' }, { revalidar: 60 }), null),
    tolerante(listarProductos({ tamanoPagina: 8, orden: 'recientes' }, { revalidar: 60 }), null),
  ])

  const categoriasTienda = categorias.filter(esCategoriaDeTienda)
  const ofertas: ProductoResumen[] = (ofertasCrudas?.items ?? [])
    .filter((p) => p.porcentajeDescuento !== null && p.porcentajeDescuento > 0)
    .slice(0, 8)

  return (
    <>
      <Heroe />
      <Ventajas />

      {categoriasTienda.length > 0 && (
        <Seccion
          titulo="Compra por categoria"
          descripcion="Todo lo que necesitas, ordenado por donde lo vas a usar."
        >
          <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 sm:gap-4 lg:grid-cols-5">
            {categoriasTienda.slice(0, 10).map((c) => (
              <li key={c.id}>
                <TarjetaCategoria categoria={c} />
              </li>
            ))}
          </ul>
        </Seccion>
      )}

      {destacados && destacados.items.length > 0 && (
        <Seccion
          titulo="Destacados"
          descripcion="Lo que mas se lleva quien viene a construir."
          verTodo={{ href: '/productos?orden=calificacion', texto: 'Ver todos' }}
          className="bg-white"
        >
          <RejillaProductos productos={destacados.items} conPrioridad />
        </Seccion>
      )}

      {ofertas.length > 0 && (
        <Seccion titulo="Ofertas" descripcion="Productos con precio rebajado ahora mismo.">
          <RejillaProductos productos={ofertas} />
        </Seccion>
      )}

      {novedades && novedades.items.length > 0 && (
        <Seccion
          titulo="Ultimas novedades"
          verTodo={{ href: '/productos?orden=recientes', texto: 'Ver el catalogo' }}
          className="bg-white"
        >
          <RejillaProductos productos={novedades.items} />
        </Seccion>
      )}
    </>
  )
}

function Heroe() {
  return (
    <section className="border-b border-borde bg-tinta">
      <Contenedor className="grid items-center gap-8 py-14 sm:py-20 md:grid-cols-[1.15fr_1fr]">
        <div>
          <p className="mb-3 inline-flex items-center gap-1.5 rounded-full bg-marca/15 px-3 py-1 text-xs font-semibold text-marca">
            <Tag size={13} aria-hidden />
            Precios de distribuidor, para todos
          </p>
          <h1 className="font-marca text-3xl font-bold leading-tight text-white sm:text-5xl">
            Todo para tu obra,
            <br />
            <span className="text-marca">en un solo lugar</span>
          </h1>
          <p className="mt-4 max-w-lg text-[15px] leading-relaxed text-white/75">
            Cemento, fierro, herramientas, pintura, electricidad y seguridad industrial. Mas de cien productos con
            stock real y entrega en todo el Peru.
          </p>
          <div className="mt-7 flex flex-wrap gap-3">
            <EnlaceBoton href="/productos" variante="primario" tamano="lg">
              Ver el catalogo
            </EnlaceBoton>
            <EnlaceBoton
              href="/productos?orden=precio_asc"
              variante="sutil"
              tamano="lg"
              className="border-white/25 bg-white/10 text-white hover:border-white/40 hover:bg-white/15"
            >
              Buscar ofertas
            </EnlaceBoton>
          </div>
        </div>

        <div className="hidden justify-center md:flex">
          <Image
            src="/isotipo.webp"
            alt=""
            width={280}
            height={280}
            priority
            className="h-56 w-auto object-contain opacity-95 drop-shadow-2xl lg:h-72"
          />
        </div>
      </Contenedor>
    </section>
  )
}

const VENTAJAS = [
  { icono: Truck, titulo: 'Entrega a todo el Peru', texto: 'Despacho coordinado a obra o domicilio.' },
  { icono: ShieldCheck, titulo: 'Garantia de fabrica', texto: 'Productos nuevos, en su empaque original.' },
  { icono: Wrench, titulo: 'Asesoria tecnica', texto: 'Te ayudamos a elegir lo que la obra pide.' },
  { icono: Tag, titulo: 'Cupones de descuento', texto: 'Aplicalos directamente en tu carrito.' },
] as const

function Ventajas() {
  return (
    <section className="border-b border-borde bg-white">
      <Contenedor>
        <ul className="grid gap-x-6 gap-y-5 py-7 sm:grid-cols-2 lg:grid-cols-4">
          {VENTAJAS.map(({ icono: Icono, titulo, texto }) => (
            <li key={titulo} className="flex items-start gap-3">
              <span className="mt-0.5 inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-marca bg-marca-suave text-marca-oscura">
                <Icono size={18} aria-hidden />
              </span>
              <div>
                <p className="text-sm font-semibold text-texto">{titulo}</p>
                <p className="text-xs text-texto-suave">{texto}</p>
              </div>
            </li>
          ))}
        </ul>
      </Contenedor>
    </section>
  )
}

function TarjetaCategoria({ categoria }: { categoria: Categoria }) {
  const imagen = deArchivo(categoria.imagen, 'tarjeta')

  return (
    <Link
      href={`/c/${categoria.slug}`}
      className="group flex h-full flex-col overflow-hidden rounded-marca border border-borde bg-white transition-all duration-200 hover:-translate-y-0.5 hover:border-marca/40 hover:shadow-m"
    >
      <div className="relative aspect-[4/3] overflow-hidden bg-superficie-alt">
        {imagen ? (
          <Image
            src={imagen}
            alt={categoria.imagen?.textoAlt ?? categoria.nombre}
            fill
            sizes={TAMANOS.categoria}
            className="object-contain p-4 transition-transform duration-300 group-hover:scale-105"
          />
        ) : null}
      </div>
      <div className="p-3">
        <p className="text-sm font-semibold leading-snug text-texto group-hover:text-marca-oscura">
          {categoria.nombre}
        </p>
        <p className="mt-0.5 text-xs text-texto-suave">
          {categoria.subcategorias.length}{' '}
          {categoria.subcategorias.length === 1 ? 'subcategoria' : 'subcategorias'}
        </p>
      </div>
    </Link>
  )
}
