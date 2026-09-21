import type { ReactNode } from 'react'

type Props = {
  /** Debe coincidir con el nombre del campo JSON: así casa con errors[].field. */
  nombre: string
  etiqueta: string
  error?: string
  ayuda?: string
  children: (props: { id: string; className: string }) => ReactNode
}

/**
 * Un campo de formulario con su etiqueta, su ayuda y su error.
 *
 * El `nombre` es el del contrato, no uno inventado para la interfaz. Esa
 * coincidencia es lo que permite pintar el error donde toca sin una tabla de
 * traducción que alguien tendría que mantener (ADR-0011).
 */
export default function Campo({ nombre, etiqueta, error, ayuda, children }: Props) {
  const id = `campo-${nombre}`
  return (
    <div className="campo">
      <label htmlFor={id}>{etiqueta}</label>
      {children({ id, className: error ? 'con-error' : '' })}
      {ayuda && !error && <div className="ayuda">{ayuda}</div>}
      {error && (
        <div className="error" id={`${id}-error`}>
          {error}
        </div>
      )}
    </div>
  )
}
