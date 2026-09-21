import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { Check, ChevronsUpDown, Search } from 'lucide-react'

export type Opcion = {
  valor: string
  etiqueta: string
  /** Se muestra a la derecha y también se busca: categoría, SKU, lo que ubique. */
  grupo?: string
}

type Props = {
  id?: string
  className?: string
  opciones: Opcion[]
  valor: string
  alCambiar: (valor: string) => void
  marcador?: string
  /** Texto de la opción que representa «ninguno». Si falta, el campo es obligatorio. */
  etiquetaVacia?: string
  deshabilitado?: boolean
}

/**
 * Select con buscador.
 *
 * <p>Un `<select>` nativo deja de servir a partir de unas quince opciones:
 * para elegir una subcategoría entre dieciocho hay que recorrerlas con la
 * vista. Escribir tres letras es más rápido que leer una lista.
 *
 * <p>Se implementa a mano y no con una librería porque el componente es
 * conocido y lo que hay que hacer bien —teclado, foco, cierre al pulsar
 * fuera— cabe en este archivo.
 */
export default function Combobox({
  id,
  className,
  opciones,
  valor,
  alCambiar,
  marcador = 'Selecciona…',
  etiquetaVacia,
  deshabilitado,
}: Props) {
  const [abierto, setAbierto] = useState(false)
  const [busqueda, setBusqueda] = useState('')
  const [resaltada, setResaltada] = useState(0)
  const contenedor = useRef<HTMLDivElement>(null)
  const entrada = useRef<HTMLInputElement>(null)
  const lista = useRef<HTMLUListElement>(null)

  const visibles = useMemo(() => {
    const base: Opcion[] = etiquetaVacia
      ? [{ valor: '', etiqueta: etiquetaVacia }, ...opciones]
      : opciones
    const termino = busqueda.trim().toLowerCase()
    if (!termino) return base
    // Se busca también en el grupo: escribir «Deportes» encuentra todas sus
    // subcategorías aunque ninguna se llame así.
    return base.filter((o) =>
      `${o.etiqueta} ${o.grupo ?? ''}`.toLowerCase().includes(termino),
    )
  }, [opciones, busqueda, etiquetaVacia])

  const seleccionada = opciones.find((o) => o.valor === valor)

  // Cerrar al pulsar fuera. Sin esto quedan dos paneles abiertos en cuanto hay
  // dos comboboxes en el mismo formulario.
  useEffect(() => {
    if (!abierto) return
    const alPulsar = (evento: MouseEvent) => {
      if (!contenedor.current?.contains(evento.target as Node)) {
        setAbierto(false)
      }
    }
    document.addEventListener('mousedown', alPulsar)
    return () => document.removeEventListener('mousedown', alPulsar)
  }, [abierto])

  // El foco va al buscador al abrir: si no, hay que hacer un clic más para
  // empezar a escribir, que es justo lo que el componente venía a evitar.
  useLayoutEffect(() => {
    if (abierto) {
      entrada.current?.focus()
      setBusqueda('')
      const indice = visibles.findIndex((o) => o.valor === valor)
      setResaltada(indice >= 0 ? indice : 0)
    }
    // visibles cambia con la búsqueda; aquí solo interesa el momento de abrir.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [abierto])

  useEffect(() => {
    lista.current?.children[resaltada]?.scrollIntoView({ block: 'nearest' })
  }, [resaltada])

  function elegir(opcion: Opcion) {
    alCambiar(opcion.valor)
    setAbierto(false)
  }

  function alTeclear(evento: React.KeyboardEvent) {
    if (evento.key === 'Escape') {
      setAbierto(false)
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
    if (evento.key === 'Enter') {
      evento.preventDefault()
      const opcion = visibles[resaltada]
      if (opcion) elegir(opcion)
    }
  }

  return (
    <div className="combobox" ref={contenedor}>
      <button
        id={id}
        type="button"
        className={`disparador ${className ?? ''}`}
        disabled={deshabilitado}
        aria-haspopup="listbox"
        aria-expanded={abierto}
        onClick={() => setAbierto((a) => !a)}
        onKeyDown={(evento) => {
          if (evento.key === 'ArrowDown' && !abierto) {
            evento.preventDefault()
            setAbierto(true)
          }
        }}
      >
        <span className={`valor ${seleccionada ? '' : 'vacio'}`}>
          {seleccionada ? seleccionada.etiqueta : (etiquetaVacia && !valor ? etiquetaVacia : marcador)}
        </span>
        <ChevronsUpDown size={15} style={{ color: 'var(--texto-suave)', flexShrink: 0 }} />
      </button>

      {abierto && (
        <div className="panel">
          <div className="buscador">
            <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
              <Search size={15} style={{ color: 'var(--texto-suave)', flexShrink: 0 }} />
              <input
                ref={entrada}
                type="text"
                value={busqueda}
                placeholder="Buscar…"
                onChange={(e) => {
                  setBusqueda(e.target.value)
                  setResaltada(0)
                }}
                onKeyDown={alTeclear}
              />
            </div>
          </div>

          {visibles.length === 0 ? (
            <div className="vacio-opciones">Nada coincide con «{busqueda}».</div>
          ) : (
            <ul className="opciones" role="listbox" ref={lista}>
              {visibles.map((opcion, indice) => (
                <li
                  key={opcion.valor || '__vacio'}
                  role="option"
                  aria-selected={opcion.valor === valor}
                  className={indice === resaltada ? 'resaltada' : ''}
                  onMouseEnter={() => setResaltada(indice)}
                  onClick={() => elegir(opcion)}
                >
                  <span>{opcion.etiqueta}</span>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    {opcion.grupo && <span className="grupo">{opcion.grupo}</span>}
                    {opcion.valor === valor && <Check size={14} />}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
