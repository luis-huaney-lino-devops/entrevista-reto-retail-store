'use client'

import { useRouter, useSearchParams } from 'next/navigation'
import { useEffect, useState, useTransition } from 'react'
import { SlidersHorizontal, X } from 'lucide-react'

import { clases } from '@/lib/formato'
import type { Categoria, Marca } from '@/lib/tipos'
import { Boton } from '@/componentes/ui/Boton'

/**
 * El panel de filtros del catalogo.
 *
 * **Escribe en la URL, no en `useState`.** De ahi salen cinco propiedades que
 * se notan: la vista es compartible, el boton atras funciona como se espera,
 * recargar no pierde los filtros, el servidor recibe todo en la peticion
 * inicial y devuelve el HTML ya filtrado, y la pagina puede indexarse o no
 * segun convenga.
 *
 * Tres matices del comportamiento:
 *
 * - `router.push` y no `replace`: cambiar un filtro es una navegacion
 *   deliberada y debe poder deshacerse con "atras".
 * - `scroll: false`: saltar al principio de la pagina cada vez que se mueve el
 *   precio maximo es desorientador.
 * - `useTransition` alrededor de la navegacion: mientras el servidor
 *   re-renderiza, `isPending` atenua la rejilla en vez de dejarla congelada.
 *   Es lo que evita que cambiar un filtro parezca que no hizo nada.
 *
 * Y una regla que se aplica siempre: **cualquier cambio de filtro vuelve a la
 * pagina 1**. Quedarse en la pagina 7 de un resultado que ahora tiene 2 es la
 * forma mas rapida de ensenar una rejilla vacia sin motivo.
 */

type Propiedades = {
  categorias: Categoria[]
  marcas: Marca[]
  /** Lo emite el padre para atenuar la rejilla durante la transicion. */
  alCambiarPendiente?: (pendiente: boolean) => void
}

export function PanelFiltros({ categorias, marcas, alCambiarPendiente }: Propiedades) {
  const [abiertoMovil, setAbiertoMovil] = useState(false)

  return (
    <>
      <div className="lg:hidden">
        <Boton variante="sutil" onClick={() => setAbiertoMovil(true)} className="w-full">
          <SlidersHorizontal size={16} aria-hidden />
          Filtrar
        </Boton>
      </div>

      {/* En pantalla grande, columna fija. En movil, panel deslizante. */}
      <div className="hidden lg:block">
        <Formulario categorias={categorias} marcas={marcas} alCambiarPendiente={alCambiarPendiente} />
      </div>

      {abiertoMovil && (
        <div className="fixed inset-0 z-[60] flex lg:hidden" role="dialog" aria-modal="true" aria-label="Filtros">
          <button
            type="button"
            aria-label="Cerrar filtros"
            tabIndex={-1}
            onClick={() => setAbiertoMovil(false)}
            className="absolute inset-0 cursor-default bg-tinta/40"
          />
          <div className="relative flex h-full w-full max-w-sm flex-col bg-white shadow-l">
            <header className="flex items-center justify-between border-b border-borde px-4 py-3.5">
              <h2 className="font-marca text-base font-semibold text-tinta">Filtros</h2>
              <button
                type="button"
                onClick={() => setAbiertoMovil(false)}
                aria-label="Cerrar filtros"
                className="rounded-marca p-1.5 text-texto-suave hover:bg-superficie-alt hover:text-texto"
              >
                <X size={18} aria-hidden />
              </button>
            </header>
            <div className="flex-1 overflow-y-auto p-4">
              <Formulario
                categorias={categorias}
                marcas={marcas}
                alAplicar={() => setAbiertoMovil(false)}
                alCambiarPendiente={alCambiarPendiente}
              />
            </div>
          </div>
        </div>
      )}
    </>
  )
}

function Formulario({
  categorias,
  marcas,
  alAplicar,
  alCambiarPendiente,
}: Propiedades & { alAplicar?: () => void }) {
  const router = useRouter()
  const parametros = useSearchParams()
  const [pendiente, iniciar] = useTransition()

  const categoriaActual = parametros.get('categoria') ?? ''
  const subcategoriaActual = parametros.get('subcategoria') ?? ''
  const marcaActual = parametros.get('marca') ?? ''
  const conStock = parametros.get('conStock') === 'true'

  const [minimo, setMinimo] = useState(parametros.get('precioMinimo') ?? '')
  const [maximo, setMaximo] = useState(parametros.get('precioMaximo') ?? '')

  useEffect(() => {
    alCambiarPendiente?.(pendiente)
  }, [pendiente, alCambiarPendiente])

  useEffect(() => {
    setMinimo(parametros.get('precioMinimo') ?? '')
    setMaximo(parametros.get('precioMaximo') ?? '')
  }, [parametros])

  function navegar(cambios: Record<string, string | null>) {
    const p = new URLSearchParams(parametros.toString())
    for (const [clave, valor] of Object.entries(cambios)) {
      if (valor === null || valor === '') p.delete(clave)
      else p.set(clave, valor)
    }
    // Cualquier cambio de filtro vuelve a la pagina 1.
    p.delete('pagina')
    iniciar(() => {
      router.push(`/productos${p.toString() ? `?${p.toString()}` : ''}`, { scroll: false })
      alAplicar?.()
    })
  }

  const categoriaElegida = categorias.find((c) => c.slug === categoriaActual)
  const hayFiltros =
    categoriaActual || subcategoriaActual || marcaActual || conStock || minimo || maximo || parametros.get('texto')

  function aplicarPrecio() {
    const min = minimo.trim() === '' ? null : String(Math.max(0, Number(minimo)))
    const max = maximo.trim() === '' ? null : String(Math.max(0, Number(maximo)))
    // El control impide invertir el rango antes de que llegue al servidor. Si
    // aun asi llegara (una URL pegada a mano), la API responde 422 y el listado
    // lo trata como cualquier otro error por codigo.
    if (min !== null && max !== null && Number(min) > Number(max)) {
      navegar({ precioMinimo: max, precioMaximo: min })
      return
    }
    navegar({ precioMinimo: min, precioMaximo: max })
  }

  return (
    <div className={clases('space-y-6', pendiente && 'opacity-70 transition-opacity')}>
      {hayFiltros ? (
        <button
          type="button"
          onClick={() =>
            navegar({
              categoria: null,
              subcategoria: null,
              marca: null,
              conStock: null,
              precioMinimo: null,
              precioMaximo: null,
              texto: null,
            })
          }
          className="text-[13px] font-semibold text-tinta-claro underline-offset-2 hover:underline"
        >
          Limpiar todos los filtros
        </button>
      ) : null}

      <Grupo titulo="Categoria">
        <ul className="space-y-1">
          <li>
            <Opcion
              activa={!categoriaActual && !subcategoriaActual}
              onClick={() => navegar({ categoria: null, subcategoria: null })}
            >
              Todas
            </Opcion>
          </li>
          {categorias.map((c) => (
            <li key={c.id}>
              <Opcion
                activa={categoriaActual === c.slug && !subcategoriaActual}
                onClick={() => navegar({ categoria: c.slug, subcategoria: null })}
              >
                {c.nombre}
              </Opcion>
              {(categoriaActual === c.slug || c.subcategorias.some((s) => s.slug === subcategoriaActual)) && (
                <ul className="ml-3 mt-1 space-y-1 border-l border-borde pl-2.5">
                  {c.subcategorias.map((s) => (
                    <li key={s.id}>
                      <Opcion
                        activa={subcategoriaActual === s.slug}
                        pequena
                        onClick={() =>
                          navegar({
                            categoria: c.slug,
                            subcategoria: subcategoriaActual === s.slug ? null : s.slug,
                          })
                        }
                      >
                        {s.nombre}
                      </Opcion>
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>
        {categoriaElegida && (
          <p className="mt-2 text-xs text-texto-suave">
            Tambien puedes ver la{' '}
            <a href={`/c/${categoriaElegida.slug}`} className="font-medium text-tinta-claro hover:underline">
              pagina de {categoriaElegida.nombre}
            </a>
            .
          </p>
        )}
      </Grupo>

      <Grupo titulo="Precio">
        <div className="flex items-end gap-2">
          <label className="flex-1">
            <span className="mb-1 block text-xs text-texto-suave">Desde</span>
            <input
              type="number"
              min={0}
              step="0.1"
              value={minimo}
              onChange={(e) => setMinimo(e.target.value)}
              onBlur={aplicarPrecio}
              onKeyDown={(e) => e.key === 'Enter' && aplicarPrecio()}
              placeholder="0"
              className="cifra h-9 w-full rounded-marca border border-borde px-2.5 text-sm hover:border-borde-fuerte"
            />
          </label>
          <label className="flex-1">
            <span className="mb-1 block text-xs text-texto-suave">Hasta</span>
            <input
              type="number"
              min={0}
              step="0.1"
              value={maximo}
              onChange={(e) => setMaximo(e.target.value)}
              onBlur={aplicarPrecio}
              onKeyDown={(e) => e.key === 'Enter' && aplicarPrecio()}
              placeholder="Sin tope"
              className="cifra h-9 w-full rounded-marca border border-borde px-2.5 text-sm hover:border-borde-fuerte"
            />
          </label>
        </div>
      </Grupo>

      <Grupo titulo="Disponibilidad">
        <label className="flex cursor-pointer items-center gap-2.5 text-sm text-texto-medio">
          <input
            type="checkbox"
            checked={conStock}
            onChange={(e) => navegar({ conStock: e.target.checked ? 'true' : null })}
            className="h-4 w-4 rounded border-borde-fuerte text-marca accent-marca"
          />
          Solo productos con stock
        </label>
      </Grupo>

      <Grupo titulo="Marca">
        <label className="sr-only" htmlFor="filtro-marca">
          Filtrar por marca
        </label>
        <select
          id="filtro-marca"
          value={marcaActual}
          onChange={(e) => navegar({ marca: e.target.value || null })}
          className="h-9 w-full cursor-pointer rounded-marca border border-borde bg-white px-2.5 text-sm hover:border-borde-fuerte"
        >
          <option value="">Todas las marcas</option>
          {marcas.map((m) => (
            <option key={m.id} value={m.slug}>
              {m.nombre}
            </option>
          ))}
        </select>
      </Grupo>
    </div>
  )
}

function Grupo({ titulo, children }: { titulo: string; children: React.ReactNode }) {
  return (
    <fieldset>
      <legend className="mb-2 text-[13px] font-semibold uppercase tracking-wide text-texto">{titulo}</legend>
      {children}
    </fieldset>
  )
}

function Opcion({
  activa,
  pequena = false,
  onClick,
  children,
}: {
  activa: boolean
  pequena?: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={activa}
      className={clases(
        'block w-full rounded-marca px-2 py-1 text-left transition',
        pequena ? 'text-[13px]' : 'text-sm',
        activa ? 'bg-marca-suave font-semibold text-marca-oscura' : 'text-texto-medio hover:bg-superficie-alt',
      )}
    >
      {children}
    </button>
  )
}
