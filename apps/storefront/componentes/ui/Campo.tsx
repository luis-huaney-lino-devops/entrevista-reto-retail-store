'use client'

import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react'
import { useId } from 'react'

import { clases } from '@/lib/formato'

/**
 * Campos de formulario.
 *
 * Cada control lleva su `<label>` unido por `id`, y el error se anuncia con
 * `aria-describedby` + `role="alert"`. Un mensaje de error en rojo que el lector
 * de pantalla no lee es un mensaje que la mitad de la gente no recibe.
 */

const BASE_CONTROL =
  'w-full rounded-marca border border-borde bg-white px-3 text-sm text-texto transition placeholder:text-texto-suave hover:border-borde-fuerte focus:border-tinta-claro disabled:cursor-not-allowed disabled:bg-superficie-alt disabled:text-texto-suave'

type Comunes = {
  etiqueta: string
  error?: string | undefined
  ayuda?: string | undefined
  /** La etiqueta se oculta visualmente pero sigue existiendo para el lector. */
  etiquetaOculta?: boolean
  className?: string
}

function Envoltura({
  etiqueta,
  error,
  ayuda,
  etiquetaOculta,
  idControl,
  idDescripcion,
  className,
  children,
}: Comunes & { idControl: string; idDescripcion: string; children: ReactNode }) {
  return (
    <div className={clases('flex flex-col gap-1.5', className)}>
      <label
        htmlFor={idControl}
        className={clases('text-[13px] font-semibold text-texto-medio', etiquetaOculta && 'sr-only')}
      >
        {etiqueta}
      </label>
      {children}
      {ayuda && !error && (
        <p id={`${idDescripcion}-ayuda`} className="text-xs text-texto-suave">
          {ayuda}
        </p>
      )}
      {error && (
        <p id={`${idDescripcion}-error`} role="alert" className="text-xs font-medium text-peligro">
          {error}
        </p>
      )}
    </div>
  )
}

type PropiedadesTexto = Comunes & Omit<InputHTMLAttributes<HTMLInputElement>, 'className'>

export function CampoTexto({ etiqueta, error, ayuda, etiquetaOculta, className, ...resto }: PropiedadesTexto) {
  const generado = useId()
  const id = resto.id ?? generado
  return (
    <Envoltura
      etiqueta={etiqueta}
      error={error}
      ayuda={ayuda}
      etiquetaOculta={etiquetaOculta}
      idControl={id}
      idDescripcion={id}
      className={className}
    >
      <input
        {...resto}
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : ayuda ? `${id}-ayuda` : undefined}
        className={clases(BASE_CONTROL, 'h-10', error && 'border-peligro')}
      />
    </Envoltura>
  )
}

type PropiedadesSelect = Comunes & Omit<SelectHTMLAttributes<HTMLSelectElement>, 'className'>

export function CampoSelect({ etiqueta, error, ayuda, etiquetaOculta, className, children, ...resto }: PropiedadesSelect) {
  const generado = useId()
  const id = resto.id ?? generado
  return (
    <Envoltura
      etiqueta={etiqueta}
      error={error}
      ayuda={ayuda}
      etiquetaOculta={etiquetaOculta}
      idControl={id}
      idDescripcion={id}
      className={className}
    >
      <select
        {...resto}
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : ayuda ? `${id}-ayuda` : undefined}
        className={clases(BASE_CONTROL, 'h-10 cursor-pointer', error && 'border-peligro')}
      >
        {children}
      </select>
    </Envoltura>
  )
}

type PropiedadesArea = Comunes & Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'className'>

export function CampoArea({ etiqueta, error, ayuda, etiquetaOculta, className, ...resto }: PropiedadesArea) {
  const generado = useId()
  const id = resto.id ?? generado
  return (
    <Envoltura
      etiqueta={etiqueta}
      error={error}
      ayuda={ayuda}
      etiquetaOculta={etiquetaOculta}
      idControl={id}
      idDescripcion={id}
      className={className}
    >
      <textarea
        {...resto}
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : ayuda ? `${id}-ayuda` : undefined}
        className={clases(BASE_CONTROL, 'min-h-[84px] py-2', error && 'border-peligro')}
      />
    </Envoltura>
  )
}
