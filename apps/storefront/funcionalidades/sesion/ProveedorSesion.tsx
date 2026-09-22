'use client'

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'

import { alExpirarSesion, fijarToken, intentarRefrescar, peticion } from '@/lib/api.cliente'
import type { Cliente, Sesion } from '@/lib/tipos'

/**
 * La sesion del cliente.
 *
 * **El servidor de Next no sabe quien eres, y esta bien.** La cookie de
 * refresco la emite la API y es de ese host; el servidor de Next no puede
 * leerla ni aunque quisiera. De ahi una conclusion que ahorra discusiones:
 * *toda* la interfaz que depende de la sesion es cliente. No hay paginas
 * privadas renderizadas en servidor en esta tienda, y no hace falta que las
 * haya: el catalogo es publico y la cuenta no necesita SEO.
 *
 * Tres estados y no dos. Mientras el refresco esta en vuelo la sesion es
 * **desconocida** y la cabecera pinta un hueco neutro: ni "Entrar" ni "Mi
 * cuenta". Si pintara "Entrar", todo cliente con sesion veria ese texto un
 * instante antes de que lo sustituyera "Mi cuenta", y ademas seria un
 * desajuste de hidratacion.
 *
 * **El 401 del arranque no dispara nada.** Ni aviso, ni redireccion, ni
 * registro de error: la inmensa mayoria de quien visita una tienda no tiene
 * sesion. Tratar eso como fallo manda a `/acceso` a todo el mundo.
 */

export type EstadoSesion = 'desconocida' | 'anonima' | 'autenticado'

type Contexto = {
  estado: EstadoSesion
  cliente: Cliente | null
  acceder: (email: string, contrasena: string) => Promise<void>
  accederConGoogle: (credencial: string) => Promise<void>
  salir: () => Promise<void>
  /** Tras editar el perfil, para que la cabecera se entere. */
  refrescarCliente: () => Promise<void>
}

const ContextoSesion = createContext<Contexto | null>(null)

export function ProveedorSesion({ children }: { children: ReactNode }) {
  const [estado, setEstado] = useState<EstadoSesion>('desconocida')
  const [cliente, setCliente] = useState<Cliente | null>(null)

  const adoptar = useCallback((sesion: Sesion) => {
    fijarToken(sesion.tokenAcceso)
    setCliente(sesion.cliente)
    setEstado('autenticado')
  }, [])

  const olvidar = useCallback(() => {
    fijarToken(null)
    setCliente(null)
    setEstado('anonima')
  }, [])

  // Rehidratacion al arrancar: un solo refresco, silencioso.
  useEffect(() => {
    let vivo = true
    void (async () => {
      const renovado = await intentarRefrescar()
      if (!vivo) return
      if (!renovado) {
        setEstado('anonima')
        return
      }
      try {
        const yo = await peticion<Cliente>('/cuenta/yo', { silencioso: true, sinReintento: true })
        if (!vivo) return
        setCliente(yo)
        setEstado('autenticado')
      } catch {
        if (vivo) olvidar()
      }
    })()
    return () => {
      vivo = false
    }
  }, [olvidar])

  // Lo que hacer cuando la sesion se pierde de verdad, a mitad de una accion.
  useEffect(() => {
    alExpirarSesion(() => {
      fijarToken(null)
      setCliente(null)
      setEstado('anonima')
    })
    return () => alExpirarSesion(null)
  }, [])

  const acceder = useCallback(
    async (email: string, contrasena: string) => {
      const sesion = await peticion<Sesion>('/cuenta/acceso', {
        metodo: 'POST',
        cuerpo: { email, contrasena },
        sinReintento: true,
      })
      adoptar(sesion)
    },
    [adoptar],
  )

  const accederConGoogle = useCallback(
    async (credencial: string) => {
      const sesion = await peticion<Sesion>('/cuenta/google', {
        metodo: 'POST',
        cuerpo: { credencial },
        sinReintento: true,
      })
      adoptar(sesion)
    },
    [adoptar],
  )

  const salir = useCallback(async () => {
    try {
      await peticion('/cuenta/salir', { metodo: 'POST', sinReintento: true })
    } catch {
      // Aunque el servidor no conteste, en este navegador la sesion se acaba.
    }
    olvidar()
  }, [olvidar])

  const refrescarCliente = useCallback(async () => {
    try {
      setCliente(await peticion<Cliente>('/cuenta/yo'))
    } catch {
      /* se deja el que hay */
    }
  }, [])

  const valor = useMemo<Contexto>(
    () => ({ estado, cliente, acceder, accederConGoogle, salir, refrescarCliente }),
    [estado, cliente, acceder, accederConGoogle, salir, refrescarCliente],
  )

  return <ContextoSesion.Provider value={valor}>{children}</ContextoSesion.Provider>
}

export function useSesion(): Contexto {
  const v = useContext(ContextoSesion)
  if (!v) throw new Error('useSesion fuera de ProveedorSesion')
  return v
}
