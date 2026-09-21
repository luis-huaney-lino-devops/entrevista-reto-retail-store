import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react'
import { ErrorApi } from '../api/cliente'

type Tono = 'exito' | 'error' | 'info'

type Aviso = {
  id: number
  tono: Tono
  titulo: string
  detalle?: string
  saliendo?: boolean
}

type Api = {
  exito: (titulo: string, detalle?: string) => void
  info: (titulo: string, detalle?: string) => void
  /** Acepta un ErrorApi y saca de él lo que el usuario necesita saber. */
  error: (fallo: unknown, titulo?: string) => void
}

const Contexto = createContext<Api | null>(null)

/** Un error se queda hasta que lo cierren; un éxito se va solo. */
const DURACION: Record<Tono, number> = {
  exito: 3200,
  info: 4200,
  error: 0,
}

const ICONOS: Record<Tono, typeof CheckCircle2> = {
  exito: CheckCircle2,
  error: AlertCircle,
  info: Info,
}

export function ProveedorToast({ children }: { children: ReactNode }) {
  const [avisos, setAvisos] = useState<Aviso[]>([])
  const siguienteId = useRef(1)

  const cerrar = useCallback((id: number) => {
    // Se marca como saliendo para que la animación termine antes de quitarlo
    // del DOM: si se borra de golpe, desaparece a saltos.
    setAvisos((previos) => previos.map((a) => (a.id === id ? { ...a, saliendo: true } : a)))
    setTimeout(() => setAvisos((previos) => previos.filter((a) => a.id !== id)), 180)
  }, [])

  const agregar = useCallback(
    (tono: Tono, titulo: string, detalle?: string) => {
      const id = siguienteId.current++
      setAvisos((previos) => [...previos.slice(-3), { id, tono, titulo, detalle }])
      const duracion = DURACION[tono]
      if (duracion > 0) {
        setTimeout(() => cerrar(id), duracion)
      }
    },
    [cerrar],
  )

  const api = useMemo<Api>(
    () => ({
      exito: (titulo, detalle) => agregar('exito', titulo, detalle),
      info: (titulo, detalle) => agregar('info', titulo, detalle),
      error: (fallo, titulo) => {
        if (fallo instanceof ErrorApi) {
          // Los fallos por campo ya se pintan junto a su input; repetirlos en
          // el toast solo añade ruido. Lo que aporta aquí es el mensaje y, en
          // un 500, la referencia que lleva al log.
          const detalle =
            fallo.estado >= 500 && typeof fallo.extra.correlationId === 'string'
              ? `Referencia: ${fallo.extra.correlationId}`
              : fallo.errores.length > 0
                ? `${fallo.errores.length} ${fallo.errores.length === 1 ? 'campo' : 'campos'} por corregir`
                : undefined
          agregar('error', titulo ?? fallo.message, detalle)
          return
        }
        agregar('error', titulo ?? 'No se pudo completar la operación.')
      },
    }),
    [agregar],
  )

  return (
    <Contexto.Provider value={api}>
      {children}
      {/* aria-live para que un lector de pantalla anuncie el mensaje: un toast
          que solo se ve deja fuera a quien no mira la esquina de la pantalla. */}
      <div className="pila-toast" role="status" aria-live="polite">
        {avisos.map((aviso) => {
          const Icono = ICONOS[aviso.tono]
          return (
            <div key={aviso.id} className={`toast ${aviso.tono} ${aviso.saliendo ? 'saliendo' : ''}`}>
              <Icono size={18} className="icono-toast" />
              <div className="cuerpo">
                <div className="titulo">{aviso.titulo}</div>
                {aviso.detalle && <div className="detalle">{aviso.detalle}</div>}
              </div>
              <button type="button" className="cerrar" aria-label="Cerrar" onClick={() => cerrar(aviso.id)}>
                <X size={15} />
              </button>
            </div>
          )
        })}
      </div>
    </Contexto.Provider>
  )
}

export function useToast(): Api {
  const contexto = useContext(Contexto)
  if (!contexto) {
    throw new Error('useToast se usó fuera de ProveedorToast')
  }
  return contexto
}
