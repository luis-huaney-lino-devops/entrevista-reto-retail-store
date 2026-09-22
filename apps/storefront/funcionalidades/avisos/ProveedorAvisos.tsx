'use client'

import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react'
import { CheckCircle2, Info, TriangleAlert, X, XCircle } from 'lucide-react'

import { clases } from '@/lib/formato'

/**
 * Avisos efimeros (toasts).
 *
 * Escritos a mano y no traidos de una libreria: son cuarenta lineas, y una
 * dependencia mas en el paquete del cliente cuesta mas de lo que ahorra.
 *
 * La region lleva `role="status"` y `aria-live="polite"`: un cambio de color no
 * es una confirmacion para quien no ve la pantalla.
 */

export type TipoAviso = 'exito' | 'error' | 'aviso' | 'info'

type Aviso = {
  id: number
  texto: string
  tipo: TipoAviso
}

type Contexto = {
  avisar: (texto: string, tipo?: TipoAviso) => void
}

const ContextoAvisos = createContext<Contexto | null>(null)

const ICONOS = {
  exito: CheckCircle2,
  error: XCircle,
  aviso: TriangleAlert,
  info: Info,
} as const

const ESTILOS: Record<TipoAviso, string> = {
  exito: 'border-exito/30 bg-exito-suave text-exito',
  error: 'border-peligro/30 bg-peligro-suave text-peligro',
  aviso: 'border-aviso/30 bg-aviso-suave text-aviso',
  info: 'border-tinta-claro/30 bg-tinta-suave text-tinta-claro',
}

export function ProveedorAvisos({ children }: { children: ReactNode }) {
  const [avisos, setAvisos] = useState<Aviso[]>([])
  const siguiente = useRef(0)

  const cerrar = useCallback((id: number) => {
    setAvisos((v) => v.filter((a) => a.id !== id))
  }, [])

  const avisar = useCallback(
    (texto: string, tipo: TipoAviso = 'info') => {
      siguiente.current += 1
      const id = siguiente.current
      setAvisos((v) => [...v.slice(-2), { id, texto, tipo }])
      setTimeout(() => cerrar(id), tipo === 'error' ? 7000 : 4000)
    },
    [cerrar],
  )

  const valor = useMemo<Contexto>(() => ({ avisar }), [avisar])

  return (
    <ContextoAvisos.Provider value={valor}>
      {children}
      <div
        role="status"
        aria-live="polite"
        className="pointer-events-none fixed inset-x-3 bottom-3 z-[70] flex flex-col gap-2 sm:inset-x-auto sm:right-5 sm:bottom-5 sm:w-96"
      >
        {avisos.map((a) => {
          const Icono = ICONOS[a.tipo]
          return (
            <div
              key={a.id}
              className={clases(
                'pointer-events-auto flex items-start gap-2.5 rounded-marca border px-3.5 py-3 text-sm shadow-l animate-entrada',
                ESTILOS[a.tipo],
              )}
            >
              <Icono size={17} className="mt-0.5 shrink-0" aria-hidden />
              <p className="flex-1 leading-snug text-texto">{a.texto}</p>
              <button
                type="button"
                onClick={() => cerrar(a.id)}
                aria-label="Cerrar aviso"
                className="shrink-0 rounded p-0.5 text-texto-suave transition hover:text-texto focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-tinta-claro"
              >
                <X size={15} aria-hidden />
              </button>
            </div>
          )
        })}
      </div>
    </ContextoAvisos.Provider>
  )
}

export function useAvisos(): Contexto {
  const v = useContext(ContextoAvisos)
  if (!v) throw new Error('useAvisos fuera de ProveedorAvisos')
  return v
}
