import type { ButtonHTMLAttributes, ReactNode } from 'react'
import Link from 'next/link'

import { clases } from '@/lib/formato'

/**
 * El boton de la tienda.
 *
 * Es un componente de servidor: no tiene estado, solo forma. Lo que sea
 * interactivo lo envuelve una isla cliente y le pasa el `onClick`.
 */

export type VarianteBoton = 'primario' | 'secundario' | 'sutil' | 'peligro' | 'fantasma'
export type TamanoBoton = 'sm' | 'md' | 'lg'

const VARIANTES: Record<VarianteBoton, string> = {
  primario: 'bg-marca text-white hover:bg-marca-oscura shadow-s',
  secundario: 'bg-tinta text-white hover:bg-tinta-claro shadow-s',
  sutil: 'bg-white text-texto border border-borde hover:border-borde-fuerte hover:bg-superficie-alt',
  peligro: 'bg-white text-peligro border border-peligro/30 hover:bg-peligro-suave',
  fantasma: 'text-texto-medio hover:bg-superficie-alt hover:text-texto',
}

const TAMANOS: Record<TamanoBoton, string> = {
  sm: 'h-8 px-3 text-[13px] gap-1.5',
  md: 'h-10 px-4 text-sm gap-2',
  lg: 'h-12 px-6 text-[15px] gap-2',
}

export const BASE_BOTON =
  'inline-flex items-center justify-center rounded-marca font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-55'

export function estiloBoton(variante: VarianteBoton = 'primario', tamano: TamanoBoton = 'md'): string {
  return clases(BASE_BOTON, VARIANTES[variante], TAMANOS[tamano])
}

type PropiedadesBoton = ButtonHTMLAttributes<HTMLButtonElement> & {
  variante?: VarianteBoton
  tamano?: TamanoBoton
}

export function Boton({ variante = 'primario', tamano = 'md', className, ...resto }: PropiedadesBoton) {
  return <button {...resto} className={clases(estiloBoton(variante, tamano), className)} />
}

type PropiedadesEnlace = {
  href: string
  children: ReactNode
  variante?: VarianteBoton
  tamano?: TamanoBoton
  className?: string
  'aria-label'?: string
  prefetch?: boolean
}

export function EnlaceBoton({
  href,
  children,
  variante = 'primario',
  tamano = 'md',
  className,
  ...resto
}: PropiedadesEnlace) {
  return (
    <Link href={href} {...resto} className={clases(estiloBoton(variante, tamano), className)}>
      {children}
    </Link>
  )
}
