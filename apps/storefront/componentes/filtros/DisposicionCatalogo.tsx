'use client'

import { useCallback, useState, type ReactNode } from 'react'

import { clases } from '@/lib/formato'
import type { Categoria, Marca } from '@/lib/tipos'

import { PanelFiltros } from './PanelFiltros'

/**
 * La disposicion de dos columnas del catalogo: filtros a la izquierda, rejilla
 * a la derecha.
 *
 * Es cliente solo por una razon: sostener el `pendiente` de la transicion para
 * **atenuar la rejilla mientras el servidor re-renderiza**. Sin eso, cambiar un
 * filtro parece que no hizo nada durante los 300 ms que tarda la respuesta.
 *
 * `children` llega como prop, asi que la rejilla **sigue renderizandose en el
 * servidor**. Este componente solo la envuelve en un `div` con una clase.
 */

type Propiedades = {
  categorias: Categoria[]
  marcas: Marca[]
  children: ReactNode
}

export function DisposicionCatalogo({ categorias, marcas, children }: Propiedades) {
  const [pendiente, setPendiente] = useState(false)
  const alCambiarPendiente = useCallback((v: boolean) => setPendiente(v), [])

  return (
    <div className="grid gap-6 lg:grid-cols-[250px_minmax(0,1fr)] lg:gap-8">
      <aside className="lg:sticky lg:top-32 lg:self-start" aria-label="Filtros del catalogo">
        <PanelFiltros categorias={categorias} marcas={marcas} alCambiarPendiente={alCambiarPendiente} />
      </aside>

      <div
        aria-busy={pendiente}
        className={clases('min-w-0', pendiente && 'pointer-events-none opacity-55 transition-opacity')}
      >
        {children}
      </div>
    </div>
  )
}
