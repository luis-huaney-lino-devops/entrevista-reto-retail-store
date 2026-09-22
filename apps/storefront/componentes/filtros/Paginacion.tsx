import Link from 'next/link'
import { ChevronLeft, ChevronRight } from 'lucide-react'

import { clases } from '@/lib/formato'

/**
 * Paginacion.
 *
 * Es un componente de **servidor**: son enlaces, no botones. Eso significa que
 * el rastreador los sigue, que se pueden abrir en otra pestana y que funcionan
 * sin JavaScript.
 */

type Propiedades = {
  pagina: number
  totalPaginas: number
  /** Construye el `href` de una pagina concreta conservando los demas filtros. */
  hrefDePagina: (pagina: number) => string
}

export function Paginacion({ pagina, totalPaginas, hrefDePagina }: Propiedades) {
  if (totalPaginas <= 1) return null

  const numeros = ventana(pagina, totalPaginas)

  return (
    <nav aria-label="Paginacion" className="flex items-center justify-center gap-1.5 pt-8">
      <Flecha
        href={pagina > 1 ? hrefDePagina(pagina - 1) : null}
        etiqueta="Pagina anterior"
        icono={<ChevronLeft size={16} aria-hidden />}
      />

      {numeros.map((n, i) =>
        n === null ? (
          <span key={`hueco-${i}`} aria-hidden className="px-1 text-texto-suave">
            ...
          </span>
        ) : (
          <Link
            key={n}
            href={hrefDePagina(n)}
            scroll={false}
            aria-label={`Pagina ${n}`}
            aria-current={n === pagina ? 'page' : undefined}
            className={clases(
              'cifra inline-flex h-9 min-w-9 items-center justify-center rounded-marca px-2.5 text-sm font-semibold transition',
              n === pagina
                ? 'bg-tinta text-white'
                : 'border border-borde bg-white text-texto-medio hover:border-borde-fuerte hover:text-texto',
            )}
          >
            {n}
          </Link>
        ),
      )}

      <Flecha
        href={pagina < totalPaginas ? hrefDePagina(pagina + 1) : null}
        etiqueta="Pagina siguiente"
        icono={<ChevronRight size={16} aria-hidden />}
      />
    </nav>
  )
}

function Flecha({ href, etiqueta, icono }: { href: string | null; etiqueta: string; icono: React.ReactNode }) {
  const clase =
    'inline-flex h-9 w-9 items-center justify-center rounded-marca border border-borde bg-white text-texto-medio transition'
  if (!href) {
    return (
      <span aria-disabled className={clases(clase, 'cursor-not-allowed opacity-40')}>
        {icono}
      </span>
    )
  }
  return (
    <Link href={href} scroll={false} aria-label={etiqueta} className={clases(clase, 'hover:border-borde-fuerte hover:text-texto')}>
      {icono}
    </Link>
  )
}

/** Primera, ultima, la actual y sus vecinas. Los huecos van con puntos. */
function ventana(pagina: number, total: number): (number | null)[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)

  const paginas = new Set<number>([1, total, pagina, pagina - 1, pagina + 1])
  const ordenadas = [...paginas].filter((n) => n >= 1 && n <= total).sort((a, b) => a - b)

  const salida: (number | null)[] = []
  let previa = 0
  for (const n of ordenadas) {
    if (previa && n - previa > 1) salida.push(null)
    salida.push(n)
    previa = n
  }
  return salida
}
