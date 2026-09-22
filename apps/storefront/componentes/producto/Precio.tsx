import { dinero } from '@/lib/formato'
import { clases } from '@/lib/formato'

/**
 * Precio, con descuento si lo hay.
 *
 * Es un componente de servidor: solo formatea, no interactua.
 *
 * El detalle que casi siempre se olvida: **el precio tachado no se anuncia
 * solo**. Dos numeros seguidos, oidos en voz alta, no significan nada. Por eso
 * hay un texto para lector de pantalla que los relaciona ("antes X, ahora Y") y
 * los numeros visibles quedan ocultos para la voz.
 */

type Propiedades = {
  precio: number
  precioAnterior?: number | null
  porcentajeDescuento?: number | null
  tamano?: 'sm' | 'md' | 'lg'
  className?: string
}

const TAMANOS = {
  sm: { actual: 'text-[15px]', anterior: 'text-xs' },
  md: { actual: 'text-lg', anterior: 'text-[13px]' },
  lg: { actual: 'text-3xl', anterior: 'text-sm' },
} as const

export function Precio({ precio, precioAnterior, porcentajeDescuento, tamano = 'md', className }: Propiedades) {
  const hayDescuento = precioAnterior !== null && precioAnterior !== undefined && precioAnterior > precio
  const t = TAMANOS[tamano]

  return (
    <p className={clases('flex flex-wrap items-baseline gap-x-2 gap-y-0.5', className)}>
      <span className="sr-only">
        {hayDescuento ? `Antes ${dinero(precioAnterior)}, ahora ${dinero(precio)}` : `Precio ${dinero(precio)}`}
      </span>

      <span aria-hidden className={clases('cifra font-marca font-bold text-tinta', t.actual)}>
        {dinero(precio)}
      </span>

      {hayDescuento && (
        <>
          <del aria-hidden className={clases('cifra text-texto-suave', t.anterior)}>
            {dinero(precioAnterior)}
          </del>
          {porcentajeDescuento ? (
            <span
              aria-hidden
              className={clases(
                'rounded-full bg-marca-suave px-2 py-0.5 font-semibold text-marca-oscura',
                tamano === 'lg' ? 'text-xs' : 'text-[11px]',
              )}
            >
              -{porcentajeDescuento}%
            </span>
          ) : null}
        </>
      )}
    </p>
  )
}
