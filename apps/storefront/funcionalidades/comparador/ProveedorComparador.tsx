'use client'

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

import type { ProductoResumen } from '@/lib/tipos'
import { useAvisos } from '@/funcionalidades/avisos/ProveedorAvisos'

/**
 * Comparador de productos.
 *
 * **No tiene backend y no lo necesita.** Comparar es una decision del visitante
 * sobre su propia pantalla: no hay nada que persistir en el servidor ni que
 * compartir entre dispositivos. Se guarda el producto entero en `localStorage`
 * —no solo el id— para que `/comparar` pinte la tabla sin pedir nada a la API.
 *
 * El tope es de cuatro. No es arbitrario: una tabla de comparacion con mas
 * columnas deja de caber en un movil, que es donde se mira.
 */

const CLAVE = 'retailstore.comparador'
export const TOPE_COMPARADOR = 4

/** Solo los campos que la tabla enfrenta. Guardar el producto entero haria que
 *  el arcon creciera sin motivo y que un cambio del contrato lo invalidara. */
export type ProductoComparado = Pick<
  ProductoResumen,
  | 'id'
  | 'nombre'
  | 'slug'
  | 'precio'
  | 'precioAnterior'
  | 'porcentajeDescuento'
  | 'imagen'
  | 'textoAltImagen'
  | 'hayStock'
  | 'descripcionCorta'
  | 'calificacionPromedio'
> & {
  marca: string | null
  subcategoria: string | null
}

type Contexto = {
  productos: ProductoComparado[]
  montado: boolean
  estaComparando: (id: number) => boolean
  alternar: (producto: ProductoResumen) => void
  quitar: (id: number) => void
  vaciar: () => void
}

const ContextoComparador = createContext<Contexto | null>(null)

export function ProveedorComparador({ children }: { children: ReactNode }) {
  const [productos, setProductos] = useState<ProductoComparado[]>([])
  const [montado, setMontado] = useState(false)
  const { avisar } = useAvisos()

  useEffect(() => {
    setMontado(true)
    setProductos(leer())
  }, [])

  const persistir = useCallback((lista: ProductoComparado[]) => {
    setProductos(lista)
    try {
      window.localStorage.setItem(CLAVE, JSON.stringify(lista))
    } catch {
      /* modo privado */
    }
  }, [])

  const alternar = useCallback(
    (producto: ProductoResumen) => {
      const yaEsta = productos.some((p) => p.id === producto.id)
      if (yaEsta) {
        persistir(productos.filter((p) => p.id !== producto.id))
        return
      }
      if (productos.length >= TOPE_COMPARADOR) {
        avisar(`Puedes comparar hasta ${TOPE_COMPARADOR} productos. Quita uno para anadir otro.`, 'aviso')
        return
      }
      persistir([...productos, comoComparado(producto)])
      avisar(`"${producto.nombre}" entra en la comparacion.`, 'exito')
    },
    [productos, persistir, avisar],
  )

  const quitar = useCallback(
    (id: number) => persistir(productos.filter((p) => p.id !== id)),
    [productos, persistir],
  )

  const vaciar = useCallback(() => persistir([]), [persistir])

  const estaComparando = useCallback((id: number) => productos.some((p) => p.id === id), [productos])

  const valor = useMemo<Contexto>(
    () => ({ productos, montado, estaComparando, alternar, quitar, vaciar }),
    [productos, montado, estaComparando, alternar, quitar, vaciar],
  )

  return <ContextoComparador.Provider value={valor}>{children}</ContextoComparador.Provider>
}

export function useComparador(): Contexto {
  const v = useContext(ContextoComparador)
  if (!v) throw new Error('useComparador fuera de ProveedorComparador')
  return v
}

function comoComparado(p: ProductoResumen): ProductoComparado {
  return {
    id: p.id,
    nombre: p.nombre,
    slug: p.slug,
    precio: p.precio,
    precioAnterior: p.precioAnterior,
    porcentajeDescuento: p.porcentajeDescuento,
    imagen: p.imagen,
    textoAltImagen: p.textoAltImagen,
    hayStock: p.hayStock,
    descripcionCorta: p.descripcionCorta,
    calificacionPromedio: p.calificacionPromedio,
    marca: p.marca?.nombre ?? null,
    subcategoria: p.subcategoria?.nombre ?? null,
  }
}

function leer(): ProductoComparado[] {
  try {
    const crudo = window.localStorage.getItem(CLAVE)
    if (!crudo) return []
    const v: unknown = JSON.parse(crudo)
    if (!Array.isArray(v)) return []
    return v
      .filter((x): x is ProductoComparado => !!x && typeof x === 'object' && typeof (x as ProductoComparado).id === 'number')
      .slice(0, TOPE_COMPARADOR)
  } catch {
    return []
  }
}
