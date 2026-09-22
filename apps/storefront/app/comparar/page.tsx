'use client'

import Image from 'next/image'
import Link from 'next/link'
import { Scale, Trash2, X } from 'lucide-react'

import { Contenedor, Migas } from '@/componentes/disposicion/Seccion'
import { Boton, EnlaceBoton } from '@/componentes/ui/Boton'
import { SinResultados } from '@/componentes/ui/Estados'
import { clases, dinero, recortar } from '@/lib/formato'
import { TAMANOS, variante } from '@/lib/imagenes'
import {
  TOPE_COMPARADOR,
  useComparador,
  type ProductoComparado,
} from '@/funcionalidades/comparador/ProveedorComparador'

/**
 * El comparador.
 *
 * **No tiene backend y no lo necesita**: comparar es una decision del visitante
 * sobre su propia pantalla. La seleccion vive en `localStorage`, con el
 * producto ya guardado, asi que esta pagina se pinta sin pedir nada a la API.
 *
 * La tabla enfrenta hasta cuatro productos por fila de atributo. Dos decisiones
 * de maquetacion:
 *
 * - **Es una `<table>` de verdad**, con `<th scope="row">` en la columna de
 *   atributos. Una rejilla de `div` obliga a quien usa lector de pantalla a
 *   recordar de que columna era cada dato.
 * - **Se desplaza dentro de su propio contenedor** (`overflow-x-auto`), no en
 *   el `body`. Cuatro columnas no caben en un movil, y el remedio no puede ser
 *   que toda la pagina se mueva en horizontal.
 */
export default function PaginaComparador() {
  const { productos, montado, quitar, vaciar } = useComparador()

  if (!montado) {
    return (
      <Contenedor className="pb-12">
        <div className="h-96 animate-pulse rounded-marca bg-white" />
      </Contenedor>
    )
  }

  return (
    <Contenedor className="pb-12">
      <Migas
        items={[
          { nombre: 'Inicio', href: '/' },
          { nombre: 'Comparar', href: '/comparar' },
        ]}
      />

      <header className="mb-6 flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="font-marca text-2xl font-bold text-tinta sm:text-3xl">Comparar productos</h1>
          <p className="mt-1 text-sm text-texto-suave">
            Hasta {TOPE_COMPARADOR} productos, uno al lado del otro. La seleccion se guarda en este navegador.
          </p>
        </div>
        {productos.length > 0 && (
          <Boton variante="peligro" onClick={vaciar}>
            <Trash2 size={15} aria-hidden />
            Vaciar comparacion
          </Boton>
        )}
      </header>

      {productos.length === 0 ? (
        <SinResultados
          icono={<Scale size={38} strokeWidth={1.5} />}
          titulo="No hay nada que comparar todavia"
          descripcion={`Pulsa el icono de balanza en cualquier tarjeta de producto y anade hasta ${TOPE_COMPARADOR}.`}
          accion={
            <EnlaceBoton href="/productos" variante="primario">
              Ver el catalogo
            </EnlaceBoton>
          }
        />
      ) : (
        <div className="overflow-x-auto rounded-marca border border-borde bg-white">
          <table className="w-full min-w-[640px] border-collapse text-sm">
            <caption className="sr-only">
              Comparacion de {productos.length} {productos.length === 1 ? 'producto' : 'productos'}
            </caption>

            <thead>
              <tr>
                <th scope="col" className="w-40 border-b border-borde bg-superficie-alt p-3 text-left align-bottom">
                  <span className="text-[11px] font-semibold uppercase tracking-wide text-texto-suave">Producto</span>
                </th>
                {productos.map((p) => (
                  <th key={p.id} scope="col" className="border-b border-l border-borde p-3 align-top">
                    <ColumnaProducto producto={p} alQuitar={() => quitar(p.id)} />
                  </th>
                ))}
              </tr>
            </thead>

            <tbody>
              <Fila titulo="Precio" productos={productos}>
                {(p) => (
                  <div>
                    <p className="cifra text-base font-bold text-tinta">{dinero(p.precio)}</p>
                    {p.precioAnterior !== null && p.precioAnterior > p.precio && (
                      <p className="cifra text-xs text-texto-suave">
                        <span className="sr-only">Antes </span>
                        <del>{dinero(p.precioAnterior)}</del>
                        {p.porcentajeDescuento ? (
                          <span className="ml-1.5 font-semibold text-marca-oscura">-{p.porcentajeDescuento}%</span>
                        ) : null}
                      </p>
                    )}
                  </div>
                )}
              </Fila>

              <Fila titulo="Marca" productos={productos}>
                {(p) => p.marca ?? <SinDato />}
              </Fila>

              <Fila titulo="Subcategoria" productos={productos}>
                {(p) => p.subcategoria ?? <SinDato />}
              </Fila>

              <Fila titulo="Disponibilidad" productos={productos}>
                {(p) => (
                  <span className={clases('font-semibold', p.hayStock ? 'text-exito' : 'text-peligro')}>
                    {p.hayStock ? 'Con stock' : 'Agotado'}
                  </span>
                )}
              </Fila>

              <Fila titulo="Calificacion" productos={productos}>
                {(p) =>
                  p.calificacionPromedio === null ? (
                    <SinDato />
                  ) : (
                    <span className="cifra">{p.calificacionPromedio.toFixed(1)} / 5</span>
                  )
                }
              </Fila>

              <Fila titulo="Descripcion" productos={productos}>
                {(p) =>
                  p.descripcionCorta ? (
                    <span className="text-[13px] leading-relaxed text-texto-medio">
                      {recortar(p.descripcionCorta, 140)}
                    </span>
                  ) : (
                    <SinDato />
                  )
                }
              </Fila>

              <tr>
                <th scope="row" className="border-t border-borde bg-superficie-alt p-3 text-left align-middle">
                  <span className="text-[13px] font-semibold text-texto">Ver ficha</span>
                </th>
                {productos.map((p) => (
                  <td key={p.id} className="border-l border-t border-borde p-3 align-middle">
                    <EnlaceBoton href={`/productos/${p.slug}`} variante="sutil" tamano="sm">
                      Ver producto
                    </EnlaceBoton>
                  </td>
                ))}
              </tr>
            </tbody>
          </table>
        </div>
      )}

      {productos.length > 0 && productos.length < TOPE_COMPARADOR && (
        <p className="mt-4 text-sm text-texto-suave">
          Puedes anadir {TOPE_COMPARADOR - productos.length} mas desde{' '}
          <Link href="/productos" className="font-medium text-tinta-claro hover:underline">
            el catalogo
          </Link>
          .
        </p>
      )}
    </Contenedor>
  )
}

function ColumnaProducto({ producto, alQuitar }: { producto: ProductoComparado; alQuitar: () => void }) {
  const imagen = variante(producto.imagen, 'tarjeta')
  return (
    <div className="relative w-40 space-y-2">
      <button
        type="button"
        onClick={alQuitar}
        aria-label={`Quitar "${producto.nombre}" de la comparacion`}
        className="absolute -right-1 -top-1 z-10 rounded-full border border-borde bg-white p-1 text-texto-suave shadow-s transition hover:text-peligro"
      >
        <X size={13} aria-hidden />
      </button>

      <div className="relative aspect-square overflow-hidden rounded-marca border border-borde bg-white">
        {imagen ? (
          <Image
            src={imagen}
            alt={producto.textoAltImagen ?? producto.nombre}
            fill
            sizes={TAMANOS.categoria}
            className="object-contain p-2"
          />
        ) : null}
      </div>

      <Link
        href={`/productos/${producto.slug}`}
        className="line-clamp-3 block text-[13px] font-semibold leading-snug text-texto hover:text-marca-oscura"
      >
        {producto.nombre}
      </Link>
    </div>
  )
}

function Fila({
  titulo,
  productos,
  children,
}: {
  titulo: string
  productos: ProductoComparado[]
  children: (p: ProductoComparado) => React.ReactNode
}) {
  return (
    <tr>
      <th scope="row" className="border-t border-borde bg-superficie-alt p-3 text-left align-top">
        <span className="text-[13px] font-semibold text-texto">{titulo}</span>
      </th>
      {productos.map((p) => (
        <td key={p.id} className="border-l border-t border-borde p-3 align-top text-texto-medio">
          {children(p)}
        </td>
      ))}
    </tr>
  )
}

function SinDato() {
  return <span className="text-texto-suave">Sin dato</span>
}
