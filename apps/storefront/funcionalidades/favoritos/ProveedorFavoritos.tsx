'use client'

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'

import { peticion } from '@/lib/api.cliente'
import { ErrorApi } from '@/lib/errores'
import type { ProductoResumen } from '@/lib/tipos'

import { useSesion } from '@/funcionalidades/sesion/ProveedorSesion'

/**
 * Favoritos.
 *
 * Tiene dos modos y la transicion entre ellos es lo unico delicado:
 *
 * - **Sin sesion**: los ids viven en `localStorage`. Marcar corazones sin
 *   cuenta es lo que hace casi todo el mundo, y perderlos al registrarse es la
 *   peor manera de recibir a un cliente nuevo.
 * - **Con sesion**: mandan los del servidor (`GET /cuenta/favoritos`).
 *
 * **Al iniciar sesion se suben los de invitado** con `PUT` (idempotente, asi que
 * repetirlo no molesta), se limpia el arcon local y se vuelve a leer la lista
 * del servidor. Si la subida falla, los locales **no** se borran: perder un
 * favorito por un fallo de red no es aceptable.
 *
 * Mientras la API de cuenta no exista (404), el modo con sesion degrada al
 * local y la tienda sigue funcionando.
 */

const CLAVE = 'retailstore.favoritos'

type Contexto = {
  /** Ids de producto. Es un `Set` porque la pregunta que se hace 24 veces por
   *  pagina es "esta este?", no "damelos todos". */
  ids: ReadonlySet<number>
  montado: boolean
  cargando: boolean
  alternar: (producto: ProductoResumen | { id: number; nombre: string }) => Promise<void>
  esFavorito: (id: number) => boolean
}

const ContextoFavoritos = createContext<Contexto | null>(null)

export function ProveedorFavoritos({ children }: { children: ReactNode }) {
  const [ids, setIds] = useState<Set<number>>(new Set())
  const [montado, setMontado] = useState(false)
  const [cargando, setCargando] = useState(false)
  const { estado: estadoSesion } = useSesion()
  const fusionado = useRef(false)

  useEffect(() => {
    setMontado(true)
    setIds(new Set(leerLocales()))
  }, [])

  // Cuando aparece la sesion: subir los locales y adoptar los del servidor.
  useEffect(() => {
    if (estadoSesion !== 'autenticado') {
      fusionado.current = false
      return
    }
    if (fusionado.current) return
    fusionado.current = true

    let vivo = true
    void (async () => {
      setCargando(true)
      const locales = leerLocales()
      try {
        for (const id of locales) {
          await peticion(`/cuenta/favoritos/${id}`, { metodo: 'PUT' })
        }
        // Solo se borran los locales cuando el servidor ya los tiene.
        borrarLocales()
      } catch (e) {
        if (!(e instanceof ErrorApi && e.estado === 404)) {
          console.error('[favoritos] no se pudieron subir los de invitado', e)
        }
      }
      try {
        const remotos = await peticion<ProductoResumen[]>('/cuenta/favoritos')
        if (vivo) setIds(new Set(remotos.map((p) => p.id)))
      } catch {
        // La API de cuenta aun no existe: se sigue con los locales.
        if (vivo) setIds(new Set(locales))
      } finally {
        if (vivo) setCargando(false)
      }
    })()

    return () => {
      vivo = false
    }
  }, [estadoSesion])

  const alternar = useCallback(
    async (producto: { id: number }) => {
      const eraFavorito = ids.has(producto.id)
      const siguiente = new Set(ids)
      if (eraFavorito) siguiente.delete(producto.id)
      else siguiente.add(producto.id)
      setIds(siguiente)

      if (estadoSesion === 'autenticado') {
        try {
          await peticion(`/cuenta/favoritos/${producto.id}`, { metodo: eraFavorito ? 'DELETE' : 'PUT' })
          return
        } catch {
          // Si el servidor no acepta, se guarda en local igualmente: es mejor
          // que perder la marca. Se reconciliara en el siguiente arranque.
        }
      }
      guardarLocales([...siguiente])
    },
    [ids, estadoSesion],
  )

  const esFavorito = useCallback((id: number) => ids.has(id), [ids])

  const valor = useMemo<Contexto>(
    () => ({ ids, montado, cargando, alternar, esFavorito }),
    [ids, montado, cargando, alternar, esFavorito],
  )

  return <ContextoFavoritos.Provider value={valor}>{children}</ContextoFavoritos.Provider>
}

export function useFavoritos(): Contexto {
  const v = useContext(ContextoFavoritos)
  if (!v) throw new Error('useFavoritos fuera de ProveedorFavoritos')
  return v
}

/* ---------------------------------------------------------- localStorage */

function leerLocales(): number[] {
  try {
    const crudo = window.localStorage.getItem(CLAVE)
    if (!crudo) return []
    const v: unknown = JSON.parse(crudo)
    return Array.isArray(v) ? v.filter((x): x is number => typeof x === 'number') : []
  } catch {
    return []
  }
}

function guardarLocales(ids: number[]): void {
  try {
    window.localStorage.setItem(CLAVE, JSON.stringify(ids))
  } catch {
    /* modo privado */
  }
}

function borrarLocales(): void {
  try {
    window.localStorage.removeItem(CLAVE)
  } catch {
    /* modo privado */
  }
}
