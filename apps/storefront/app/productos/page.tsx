import type { Metadata } from 'next'
import { Suspense } from 'react'
import { SearchX } from 'lucide-react'

import { Contenedor, Migas } from '@/componentes/disposicion/Seccion'
import { BarraOrden } from '@/componentes/filtros/BarraOrden'
import { DisposicionCatalogo } from '@/componentes/filtros/DisposicionCatalogo'
import { Paginacion } from '@/componentes/filtros/Paginacion'
import { RejillaProductos } from '@/componentes/producto/RejillaProductos'
import { EnlaceBoton } from '@/componentes/ui/Boton'
import { SinResultados } from '@/componentes/ui/Estados'
import {
  consultaProductos,
  esCategoriaDeTienda,
  listarCategorias,
  listarMarcas,
  listarProductos,
  tolerante,
} from '@/lib/api.servidor'
import { ErrorApi } from '@/lib/errores'
import { esOrden, type FiltrosProductos, type Pagina, type ProductoResumen } from '@/lib/tipos'

/**
 * El catalogo filtrable.
 *
 * **SSR dinamico, sin ISR.** Lee `searchParams`, y una pagina que depende de la
 * consulta no se puede precalcular. Intentar cachearla seria cachear una de las
 * decenas de miles de combinaciones posibles.
 *
 * **SEO: `noindex, follow`** en cuanto hay un filtro activo. Un catalogo de 113
 * productos genera decenas de miles de URL equivalentes si se dejan indexar
 * todas, y el buscador reparte su presupuesto de rastreo entre basura. El
 * `follow` importa: los enlaces a los productos siguen valiendo para el rastreo
 * aunque la pagina filtrada no se indexe.
 *
 * El `canonical` apunta a `/productos` sin parametros.
 */

const TAMANO = 24

type Parametros = Record<string, string | string[] | undefined>

export async function generateMetadata({
  searchParams,
}: {
  searchParams: Promise<Parametros>
}): Promise<Metadata> {
  const parametros = await searchParams
  const filtros = aFiltros(parametros)
  const filtrado = hayFiltros(filtros)

  return {
    title: filtros.texto ? `Buscar: ${filtros.texto}` : 'Catalogo',
    description:
      'Todo el catalogo de Retail Store: materiales de construccion, herramientas, ferreteria, electricidad y acabados.',
    // El canonical no lleva los parametros volatiles: sin esa regla, cada
    // combinacion de filtros seria una pagina distinta a ojos del buscador.
    alternates: { canonical: '/productos' },
    robots: filtrado ? { index: false, follow: true } : undefined,
  }
}

function texto(p: Parametros, clave: string): string | undefined {
  const v = p[clave]
  const s = Array.isArray(v) ? v[0] : v
  return s && s.trim() !== '' ? s.trim() : undefined
}

function numero(p: Parametros, clave: string): number | undefined {
  const s = texto(p, clave)
  if (s === undefined) return undefined
  const n = Number(s)
  return Number.isFinite(n) && n >= 0 ? n : undefined
}

function aFiltros(p: Parametros): FiltrosProductos {
  const orden = texto(p, 'orden')
  const pagina = numero(p, 'pagina')
  return {
    texto: texto(p, 'texto'),
    categoria: texto(p, 'categoria'),
    subcategoria: texto(p, 'subcategoria'),
    marca: texto(p, 'marca'),
    precioMinimo: numero(p, 'precioMinimo'),
    precioMaximo: numero(p, 'precioMaximo'),
    conStock: texto(p, 'conStock') === 'true',
    // Un `orden` desconocido no se manda a la API: no se inventa un orden por
    // defecto, se ignora el parametro invalido y se deja que decida el backend.
    orden: esOrden(orden) ? orden : undefined,
    pagina: pagina && pagina >= 1 ? Math.floor(pagina) : 1,
    tamanoPagina: TAMANO,
  }
}

function hayFiltros(f: FiltrosProductos): boolean {
  return Boolean(
    f.texto || f.categoria || f.subcategoria || f.marca || f.conStock || f.precioMinimo || f.precioMaximo,
  )
}

export default async function PaginaCatalogo({ searchParams }: { searchParams: Promise<Parametros> }) {
  const parametros = await searchParams
  const filtros = aFiltros(parametros)

  const [categorias, marcas] = await Promise.all([
    tolerante(listarCategorias(), []),
    tolerante(listarMarcas(), []),
  ])

  let resultado: Pagina<ProductoResumen> | null = null
  let errorBusqueda: string | null = null
  try {
    resultado = await listarProductos(filtros, { revalidar: 0 })
  } catch (e) {
    // El unico error esperable aqui es un `400`/`422` por un parametro llegado
    // desde una URL pegada a mano. Se trata por codigo, se ensena el mensaje y
    // se ofrece limpiar, en vez de tumbar la pagina con `error.tsx`.
    errorBusqueda = e instanceof ErrorApi ? e.mensajeUsuario : 'No pudimos cargar el catalogo.'
  }

  const productos = resultado?.items ?? []
  const total = resultado?.totalItems ?? 0
  const filtrado = hayFiltros(filtros)

  function hrefDePagina(pagina: number): string {
    return `/productos${consultaProductos({ ...filtros, pagina, tamanoPagina: undefined })}`
  }

  return (
    <Contenedor className="pb-12">
      <Migas
        items={[
          { nombre: 'Inicio', href: '/' },
          { nombre: 'Catalogo', href: '/productos' },
        ]}
      />

      <header className="mb-6">
        <h1 className="font-marca text-2xl font-bold text-tinta sm:text-3xl">
          {filtros.texto ? `Resultados para "${filtros.texto}"` : 'Catalogo'}
        </h1>
        <p className="mt-1 text-sm text-texto-suave">
          Filtra por categoria, marca, precio y disponibilidad. El enlace conserva lo que elijas.
        </p>
      </header>

      <Suspense fallback={<div className="h-64 animate-pulse rounded-marca bg-white" />}>
        <DisposicionCatalogo categorias={categorias.filter(esCategoriaDeTienda)} marcas={marcas}>
          <div className="space-y-5">
            <BarraOrden total={total} />

            {errorBusqueda ? (
              <SinResultados
                titulo="No pudimos aplicar esos filtros"
                descripcion={errorBusqueda}
                accion={
                  <EnlaceBoton href="/productos" variante="sutil">
                    Empezar de nuevo
                  </EnlaceBoton>
                }
              />
            ) : productos.length === 0 ? (
              // Vacio con filtros y vacio sin filtros son mensajes distintos.
              // El mismo texto generico en los dos casos no le dice al
              // comprador que puede hacer.
              <SinResultados
                icono={<SearchX size={38} strokeWidth={1.5} />}
                titulo={filtrado ? 'Ningun producto encaja con esos filtros' : 'El catalogo esta vacio'}
                descripcion={
                  filtrado
                    ? descripcionVacio(filtros)
                    : 'Aun no hay productos publicados. Vuelve a intentarlo en un rato.'
                }
                accion={
                  filtrado ? (
                    <EnlaceBoton href="/productos" variante="sutil">
                      Limpiar los filtros
                    </EnlaceBoton>
                  ) : undefined
                }
              />
            ) : (
              <>
                <RejillaProductos productos={productos} conPrioridad columnas="ancha" />
                <Paginacion
                  pagina={resultado?.pagina ?? 1}
                  totalPaginas={resultado?.totalPaginas ?? 1}
                  hrefDePagina={hrefDePagina}
                />
              </>
            )}
          </div>
        </DisposicionCatalogo>
      </Suspense>
    </Contenedor>
  )
}

/** "No hay productos entre S/ 50 y S/ 80" dice mucho mas que "sin resultados". */
function descripcionVacio(f: FiltrosProductos): string {
  const partes: string[] = []
  if (f.texto) partes.push(`que contengan "${f.texto}"`)
  if (f.subcategoria) partes.push(`en ${f.subcategoria.replace(/-/g, ' ')}`)
  else if (f.categoria) partes.push(`en ${f.categoria.replace(/-/g, ' ')}`)
  if (f.marca) partes.push(`de la marca ${f.marca.replace(/-/g, ' ')}`)
  if (f.precioMinimo !== undefined && f.precioMaximo !== undefined) {
    partes.push(`entre S/ ${f.precioMinimo.toFixed(2)} y S/ ${f.precioMaximo.toFixed(2)}`)
  } else if (f.precioMinimo !== undefined) {
    partes.push(`desde S/ ${f.precioMinimo.toFixed(2)}`)
  } else if (f.precioMaximo !== undefined) {
    partes.push(`hasta S/ ${f.precioMaximo.toFixed(2)}`)
  }
  if (f.conStock) partes.push('con stock disponible')

  return partes.length === 0
    ? 'Prueba con otros filtros.'
    : `No encontramos productos ${partes.join(', ')}. Prueba a quitar alguno de los filtros.`
}
