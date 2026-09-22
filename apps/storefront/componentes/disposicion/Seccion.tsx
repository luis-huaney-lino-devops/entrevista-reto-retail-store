import type { ReactNode } from 'react'
import Link from 'next/link'
import { ArrowRight } from 'lucide-react'

import { clases } from '@/lib/formato'

/** Envoltura de ancho maximo. Una sola definicion para toda la tienda. */
export function Contenedor({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={clases('mx-auto w-full max-w-7xl px-4 sm:px-6', className)}>{children}</div>
}

type PropiedadesSeccion = {
  titulo: string
  descripcion?: string
  verTodo?: { href: string; texto: string }
  children: ReactNode
  className?: string
}

/** Un bloque de portada: titulo, descripcion opcional y enlace a "ver todo". */
export function Seccion({ titulo, descripcion, verTodo, children, className }: PropiedadesSeccion) {
  return (
    <section className={clases('py-10 sm:py-12', className)}>
      <Contenedor>
        <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h2 className="font-marca text-xl font-bold text-tinta sm:text-2xl">{titulo}</h2>
            {descripcion && <p className="mt-1 text-sm text-texto-suave">{descripcion}</p>}
          </div>
          {verTodo && (
            <Link
              href={verTodo.href}
              className="inline-flex items-center gap-1 text-sm font-semibold text-tinta-claro hover:text-marca-oscura"
            >
              {verTodo.texto}
              <ArrowRight size={15} aria-hidden />
            </Link>
          )}
        </div>
        {children}
      </Contenedor>
    </section>
  )
}

/** Migas de pan. Las visibles; el `BreadcrumbList` de datos estructurados va
 *  aparte, en `lib/jsonld.tsx`. */
export function Migas({ items }: { items: { nombre: string; href: string }[] }) {
  return (
    <nav aria-label="Ruta de navegacion" className="py-3">
      <ol className="flex flex-wrap items-center gap-1 text-[13px] text-texto-suave">
        {items.map((m, i) => {
          const ultimo = i === items.length - 1
          return (
            <li key={m.href} className="flex items-center gap-1">
              {i > 0 && <span aria-hidden>/</span>}
              {ultimo ? (
                <span aria-current="page" className="font-medium text-texto-medio">
                  {m.nombre}
                </span>
              ) : (
                <Link href={m.href} className="hover:text-marca-oscura">
                  {m.nombre}
                </Link>
              )}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
