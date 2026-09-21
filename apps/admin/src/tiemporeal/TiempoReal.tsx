import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { conversaciones } from '../api/recursos'

/**
 * El canal en vivo del panel.
 *
 * <p>Una sola conexión para todo —chat y notificaciones— y no una por página.
 * Con una por página, entrar y salir de Conversaciones abre y cierra sockets
 * continuamente, y la campana dejaría de enterarse de nada en cuanto el
 * administrador estuviera mirando otra sección.
 *
 * <p>La credencial va en un <strong>ticket de un solo uso</strong>: el
 * navegador no deja poner cabeceras en el apretón de manos de un WebSocket,
 * así que mandar allí el token de acceso lo dejaría escrito en los registros
 * del servidor y del proxy.
 */

export type EventoTiempoReal = {
  tipo: string
  [clave: string]: unknown
}

type Api = {
  /**
   * Escucha los eventos de un tipo. Devuelve la función para dejar de
   * escuchar; llamarla al desmontar es obligatorio o el manejador seguiría
   * vivo con el estado de una pantalla que ya no existe.
   */
  suscribir: (tipo: string, manejador: (evento: EventoTiempoReal) => void) => () => void
  enviar: (mensaje: Record<string, unknown>) => void
  conectado: boolean
}

const Contexto = createContext<Api | null>(null)

/** Reconexión con espera creciente, con tope: reintentar cada 100 ms no ayuda. */
const ESPERAS_MS = [500, 1_000, 2_000, 5_000, 10_000]

function urlDelSocket(ticket: string): string {
  const api: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1'
  const base = new URL(api, window.location.origin)
  base.protocol = base.protocol === 'https:' ? 'wss:' : 'ws:'
  // El WebSocket cuelga de la raíz, no de /api/v1: no es una ruta REST.
  base.pathname = '/ws/chat'
  base.search = `?ticket=${encodeURIComponent(ticket)}`
  return base.toString()
}

export function ProveedorTiempoReal({ children }: { children: ReactNode }) {
  const [conectado, setConectado] = useState(false)
  const socket = useRef<WebSocket | null>(null)
  const manejadores = useRef(new Map<string, Set<(evento: EventoTiempoReal) => void>>())
  const intentos = useRef(0)
  const temporizador = useRef<number | null>(null)
  const vivo = useRef(true)

  const conectar = useCallback(async () => {
    if (!vivo.current || socket.current) {
      return
    }
    try {
      const { ticket } = await conversaciones.ticketWs()
      if (!vivo.current) {
        return
      }
      const ws = new WebSocket(urlDelSocket(ticket))
      socket.current = ws

      ws.onopen = () => {
        intentos.current = 0
        setConectado(true)
      }

      ws.onmessage = (evento) => {
        let dato: EventoTiempoReal
        try {
          dato = JSON.parse(evento.data as string) as EventoTiempoReal
        } catch {
          // Un frame que no es JSON no debe tumbar el canal.
          return
        }
        manejadores.current.get(dato.tipo)?.forEach((manejador) => manejador(dato))
      }

      ws.onclose = () => {
        socket.current = null
        setConectado(false)
        reintentar()
      }

      // onerror siempre viene seguido de onclose: reconectar en los dos
      // programaría dos reintentos por cada caída.
      ws.onerror = () => ws.close()
    } catch {
      // El ticket no se pudo pedir —sesión caducada o API caída—. Se reintenta
      // igual: si la sesión murió de verdad, el cliente HTTP ya habrá echado
      // al usuario al formulario de acceso.
      reintentar()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const reintentar = useCallback(() => {
    if (!vivo.current || temporizador.current !== null) {
      return
    }
    const espera = ESPERAS_MS[Math.min(intentos.current, ESPERAS_MS.length - 1)]
    intentos.current += 1
    temporizador.current = window.setTimeout(() => {
      temporizador.current = null
      void conectar()
    }, espera)
  }, [conectar])

  useEffect(() => {
    vivo.current = true
    void conectar()
    return () => {
      vivo.current = false
      if (temporizador.current !== null) {
        clearTimeout(temporizador.current)
        temporizador.current = null
      }
      // onclose dispararía una reconexión de algo que se está desmontando.
      const abierto = socket.current
      socket.current = null
      if (abierto) {
        abierto.onclose = null
        abierto.close()
      }
    }
  }, [conectar])

  const api = useMemo<Api>(
    () => ({
      suscribir: (tipo, manejador) => {
        const grupo = manejadores.current.get(tipo) ?? new Set()
        grupo.add(manejador)
        manejadores.current.set(tipo, grupo)
        return () => {
          grupo.delete(manejador)
          if (grupo.size === 0) {
            manejadores.current.delete(tipo)
          }
        }
      },
      enviar: (mensaje) => {
        if (socket.current?.readyState === WebSocket.OPEN) {
          socket.current.send(JSON.stringify(mensaje))
        }
      },
      conectado,
    }),
    [conectado],
  )

  return <Contexto.Provider value={api}>{children}</Contexto.Provider>
}

export function useTiempoReal(): Api {
  const contexto = useContext(Contexto)
  if (!contexto) {
    throw new Error('useTiempoReal se usó fuera de ProveedorTiempoReal')
  }
  return contexto
}

/** Azúcar para el caso normal: escuchar un tipo mientras el componente viva. */
export function useEvento(tipo: string, manejador: (evento: EventoTiempoReal) => void): void {
  const { suscribir } = useTiempoReal()
  const ultimo = useRef(manejador)
  ultimo.current = manejador

  useEffect(
    () => suscribir(tipo, (evento) => ultimo.current(evento)),
    [suscribir, tipo],
  )
}
