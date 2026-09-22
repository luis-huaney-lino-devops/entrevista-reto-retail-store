'use client'

import { useCallback, useEffect, useId, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { Check, ChevronsUpDown, Search } from 'lucide-react'

import { CampoEnvoltura, type Comunes } from '@/componentes/ui/Campo'
import { clases } from '@/lib/formato'

/**
 * Select con buscador.
 *
 * Es el mismo componente que el panel de administracion resolvio en
 * `apps/admin/src/componentes/Combobox.tsx`, portado a Tailwind y a las
 * convenciones de la tienda. **No se instalo ninguna libreria**: lo que hay que
 * hacer bien —teclado, foco, cierre al pulsar fuera, ARIA— cabe en este archivo,
 * y una dependencia de 40 kB para esto sale cara en una pagina que ya carga un
 * mapa.
 *
 * Por que existe: un `<select>` nativo deja de servir pasadas unas quince
 * opciones. El formulario de direcciones encadena departamento, provincia y
 * distrito sobre un ubigeo de **1 828 distritos**; recorrer eso con la vista, o
 * con la busqueda por primera letra del navegador, es inviable. Escribir tres
 * letras no lo es.
 *
 * Lo que se conserva del panel, porque ya estaba bien resuelto:
 *
 * - **Flechas** para moverse, **Enter** para elegir, **Escape** para cerrar.
 * - **El foco va al buscador al abrir.** Si no, hace falta un clic mas para
 *   empezar a escribir, que es justo lo que el componente venia a evitar.
 * - **Cierra al pulsar fuera**, o quedan dos paneles abiertos en cuanto hay dos
 *   comboboxes en el mismo formulario.
 * - **Se busca tambien en el grupo**: escribir "Lima" encuentra sus distritos
 *   aunque ninguno se llame asi.
 *
 * Lo que se anade:
 *
 * - **El filtro ignora acentos y mayusculas.** Los nombres del ubigeo llegan en
 *   mayusculas y sin tildes (`HUANUCO`, `JUNIN`); quien escribe teclea "Huánuco"
 *   o "junin". Sin normalizar, la busqueda correcta no encuentra nada, que es
 *   peor que no tener busqueda.
 * - **ARIA de combobox de verdad.** El `<input>` es el `role="combobox"` con
 *   `aria-expanded`, `aria-controls`, `aria-autocomplete="list"` y
 *   `aria-activedescendant` apuntando a la opcion resaltada: asi el lector de
 *   pantalla canta la opcion mientras se recorre la lista con las flechas, sin
 *   mover el foco real fuera del campo de texto.
 * - **La misma envoltura que los demas campos** (`CampoEnvoltura`): etiqueta,
 *   ayuda y error se pintan y se anuncian igual que en un `<CampoTexto/>`.
 */

export type OpcionCombobox = {
  valor: string
  etiqueta: string
  /** Se muestra a la derecha y tambien se busca: provincia, marca, lo que ubique. */
  grupo?: string
}

/**
 * Minusculas y sin diacriticos, para comparar lo que se escribe con lo que hay.
 *
 * `NFD` separa la letra de su tilde y el rango `0300-036f` son justo esas
 * marcas: "Ñ" queda en "n" y "Á" en "a". Se exporta porque el formulario de
 * direcciones necesita exactamente esta regla para cruzar los nombres que
 * devuelve Nominatim con los del ubigeo.
 */
export function normalizarTexto(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim()
}

type Propiedades = Comunes & {
  opciones: OpcionCombobox[]
  valor: string
  alCambiar: (valor: string) => void
  /** Lo que se lee en el boton cuando no hay nada elegido. */
  marcador?: string
  deshabilitado?: boolean
  id?: string
}

export function Combobox({
  etiqueta,
  error,
  ayuda,
  etiquetaOculta,
  className,
  opciones,
  valor,
  alCambiar,
  marcador = 'Elige...',
  deshabilitado = false,
  id,
}: Propiedades) {
  const generado = useId()
  const idControl = id ?? generado
  const idLista = `${idControl}-lista`

  const [abierto, setAbierto] = useState(false)
  const [busqueda, setBusqueda] = useState('')
  const [resaltada, setResaltada] = useState(0)

  const contenedor = useRef<HTMLDivElement>(null)
  const boton = useRef<HTMLButtonElement>(null)
  const entrada = useRef<HTMLInputElement>(null)
  const lista = useRef<HTMLUListElement>(null)

  const visibles = useMemo(() => {
    const termino = normalizarTexto(busqueda)
    if (!termino) return opciones
    return opciones.filter((o) => normalizarTexto(`${o.etiqueta} ${o.grupo ?? ''}`).includes(termino))
  }, [opciones, busqueda])

  const seleccionada = opciones.find((o) => o.valor === valor)

  const cerrar = useCallback((devolverFoco: boolean) => {
    setAbierto(false)
    if (devolverFoco) boton.current?.focus()
  }, [])

  // Cerrar al pulsar fuera.
  useEffect(() => {
    if (!abierto) return
    const alPulsar = (evento: MouseEvent) => {
      if (!contenedor.current?.contains(evento.target as Node)) setAbierto(false)
    }
    document.addEventListener('mousedown', alPulsar)
    return () => document.removeEventListener('mousedown', alPulsar)
  }, [abierto])

  // El foco entra en el buscador al abrir, y la lista arranca sobre lo elegido.
  useLayoutEffect(() => {
    if (!abierto) return
    entrada.current?.focus()
    setBusqueda('')
    const indice = opciones.findIndex((o) => o.valor === valor)
    setResaltada(indice >= 0 ? indice : 0)
    // `opciones` y `valor` solo interesan en el momento de abrir.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [abierto])

  useEffect(() => {
    lista.current?.children[resaltada]?.scrollIntoView({ block: 'nearest' })
  }, [resaltada, visibles])

  function elegir(opcion: OpcionCombobox) {
    alCambiar(opcion.valor)
    cerrar(true)
  }

  function alTeclear(evento: React.KeyboardEvent) {
    if (evento.key === 'Escape') {
      evento.preventDefault()
      cerrar(true)
      return
    }
    if (evento.key === 'ArrowDown') {
      evento.preventDefault()
      setResaltada((i) => Math.min(i + 1, visibles.length - 1))
      return
    }
    if (evento.key === 'ArrowUp') {
      evento.preventDefault()
      setResaltada((i) => Math.max(i - 1, 0))
      return
    }
    if (evento.key === 'Home') {
      evento.preventDefault()
      setResaltada(0)
      return
    }
    if (evento.key === 'End') {
      evento.preventDefault()
      setResaltada(Math.max(visibles.length - 1, 0))
      return
    }
    if (evento.key === 'Enter') {
      evento.preventDefault()
      const opcion = visibles[resaltada]
      if (opcion) elegir(opcion)
      return
    }
    if (evento.key === 'Tab') {
      // Tabular fuera del buscador cierra: dejar el panel abierto sobre el
      // siguiente campo tapa justo lo que la persona acaba de enfocar.
      setAbierto(false)
    }
  }

  return (
    <CampoEnvoltura
      etiqueta={etiqueta}
      error={error}
      ayuda={ayuda}
      etiquetaOculta={etiquetaOculta}
      idControl={idControl}
      idDescripcion={idControl}
      className={className}
    >
      <div ref={contenedor} className="relative">
        <button
          ref={boton}
          id={idControl}
          type="button"
          disabled={deshabilitado}
          aria-haspopup="listbox"
          aria-expanded={abierto}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${idControl}-error` : ayuda ? `${idControl}-ayuda` : undefined}
          onClick={() => setAbierto((a) => !a)}
          onKeyDown={(evento) => {
            if ((evento.key === 'ArrowDown' || evento.key === 'ArrowUp') && !abierto) {
              evento.preventDefault()
              setAbierto(true)
            }
          }}
          className={clases(
            'flex h-10 w-full items-center justify-between gap-2 rounded-marca border border-borde bg-white px-3 text-left text-sm text-texto transition',
            'hover:border-borde-fuerte focus:border-tinta-claro',
            'disabled:cursor-not-allowed disabled:bg-superficie-alt disabled:text-texto-suave',
            error && 'border-peligro',
          )}
        >
          <span className={clases('truncate', !seleccionada && 'text-texto-suave')}>
            {seleccionada ? seleccionada.etiqueta : marcador}
          </span>
          <ChevronsUpDown size={15} aria-hidden className="shrink-0 text-texto-suave" />
        </button>

        {abierto && (
          /* `z-40` y no `z-10`: este panel se abre justo encima del mapa, y los
             paneles de Leaflet van de 400 para arriba. El contenedor del mapa
             se aisla (`isolate`) para que esa numeracion no salga de ahi, y
             este se queda por debajo de la cabecera pegajosa, que es `z-50`. */
          <div className="absolute left-0 top-full z-40 mt-1 w-full overflow-hidden rounded-marca border border-borde bg-white shadow-l animate-entrada">
            <div className="flex items-center gap-2 border-b border-borde px-3">
              <Search size={15} aria-hidden className="shrink-0 text-texto-suave" />
              <input
                ref={entrada}
                type="text"
                role="combobox"
                aria-expanded
                aria-controls={idLista}
                aria-autocomplete="list"
                aria-activedescendant={visibles[resaltada] ? `${idLista}-${resaltada}` : undefined}
                aria-label={`Buscar en ${etiqueta.toLowerCase()}`}
                value={busqueda}
                placeholder="Escribe para buscar..."
                autoComplete="off"
                onChange={(e) => {
                  setBusqueda(e.target.value)
                  setResaltada(0)
                }}
                onKeyDown={alTeclear}
                className="h-10 w-full bg-transparent text-sm text-texto outline-none placeholder:text-texto-suave"
              />
            </div>

            {visibles.length === 0 ? (
              <p className="px-3 py-3 text-[13px] text-texto-suave">Nada coincide con &laquo;{busqueda}&raquo;.</p>
            ) : (
              <ul ref={lista} id={idLista} role="listbox" aria-label={etiqueta} className="max-h-64 overflow-y-auto py-1">
                {visibles.map((opcion, indice) => (
                  <li
                    key={opcion.valor}
                    id={`${idLista}-${indice}`}
                    role="option"
                    aria-selected={opcion.valor === valor}
                    onMouseEnter={() => setResaltada(indice)}
                    onClick={() => elegir(opcion)}
                    className={clases(
                      'flex cursor-pointer items-center justify-between gap-2 px-3 py-1.5 text-[13px]',
                      indice === resaltada ? 'bg-tinta-suave text-tinta' : 'text-texto-medio',
                    )}
                  >
                    <span className="truncate">{opcion.etiqueta}</span>
                    <span className="flex shrink-0 items-center gap-2">
                      {opcion.grupo && <span className="text-[11px] text-texto-suave">{opcion.grupo}</span>}
                      {opcion.valor === valor && <Check size={14} aria-hidden className="text-marca-oscura" />}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
    </CampoEnvoltura>
  )
}
