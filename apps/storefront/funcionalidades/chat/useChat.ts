'use client'

import { useCallback, useEffect, useRef, useState } from 'react'

import { peticion } from '@/lib/api.cliente'
import type { ConversacionDetalle, MensajeChat } from '@/lib/tipos'

/**
 * Un hilo de chat, con sus mensajes en vivo.
 *
 * **Sin librería: la API WebSocket del navegador.** Socket.IO es un protocolo
 * propio encima de WebSocket y necesita su servidor al otro lado; el backend es
 * Spring con WebSocket estándar.
 *
 * **La conexión va atada al token del hilo**, no a la sesión: el apretón de
 * manos de un WebSocket no admite cabeceras, así que la credencial tiene que ir
 * en la URL. Aquí lo que viaja es el token de la conversación —que ya
 * identifica ese hilo y solo ese—, nunca el JWT.
 *
 * **Los mensajes propios también llegan por la difusión.** Al enviar no se
 * añade nada localmente: el servidor guarda y difunde, y quien escribió lo ve
 * exactamente igual que el resto. Así no hay dos caminos por los que un mensaje
 * pueda aparecer, ni el riesgo de que difieran.
 */

const ESPERAS_MS = [500, 1_000, 2_000, 5_000, 10_000]

type Estado = {
  cargando: boolean
  error: string | null
  detalle: ConversacionDetalle | null
  mensajes: MensajeChat[]
  conectado: boolean
  escribiendoElOtro: boolean
}

export function useChat(token: string | null): Estado & {
  enviar: (cuerpo: string) => void
  avisarQueEscribo: () => void
} {
  const [detalle, setDetalle] = useState<ConversacionDetalle | null>(null)
  const [mensajes, setMensajes] = useState<MensajeChat[]>([])
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [conectado, setConectado] = useState(false)
  const [escribiendoElOtro, setEscribiendoElOtro] = useState(false)

  const socket = useRef<WebSocket | null>(null)
  const intentos = useRef(0)
  const temporizador = useRef<number | null>(null)
  const vivo = useRef(true)
  const finEscritura = useRef<number | null>(null)

  // 1. El historial por HTTP. El WebSocket solo trae lo que llega DESPUÉS de
  //    conectar: pedirle el historial sería duplicar un endpoint que ya existe.
  useEffect(() => {
    if (!token) return
    let activo = true
    setCargando(true)
    peticion<ConversacionDetalle>(`/cuenta/conversaciones/${token}`)
      .then((d) => {
        if (!activo) return
        setDetalle(d)
        setMensajes(d.mensajes)
        setError(null)
      })
      .catch(() => activo && setError('No pudimos abrir esta conversacion.'))
      .finally(() => activo && setCargando(false))
    return () => {
      activo = false
    }
  }, [token])

  // 2. El WebSocket, para lo que llegue a partir de ahora.
  useEffect(() => {
    if (!token) return
    vivo.current = true

    function conectar() {
      if (!vivo.current || socket.current) return

      const base = new URL(
        process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1',
        window.location.origin,
      )
      base.protocol = base.protocol === 'https:' ? 'wss:' : 'ws:'
      // Cuelga de la raiz y no de /api/v1: no es un recurso REST.
      base.pathname = '/ws/chat'
      base.search = `?conversacion=${encodeURIComponent(token!)}`

      const ws = new WebSocket(base.toString())
      socket.current = ws

      ws.onopen = () => {
        intentos.current = 0
        setConectado(true)
      }

      ws.onmessage = (evento) => {
        try {
          const dato = JSON.parse(evento.data as string) as {
            tipo: string
            mensaje?: MensajeChat
            quien?: string
          }
          if (dato.tipo === 'MENSAJE' && dato.mensaje) {
            const llegado = dato.mensaje
            // Por id y no por posicion: una reconexion puede reenviar algo que
            // ya se pinto, y duplicarlo se ve peor que perderlo.
            setMensajes((previos) =>
              previos.some((m) => m.id === llegado.id) ? previos : [...previos, llegado],
            )
            if (llegado.autor !== 'CLIENTE') setEscribiendoElOtro(false)
          } else if (dato.tipo === 'ESCRIBIENDO' && dato.quien === 'ADMINISTRADOR') {
            setEscribiendoElOtro(true)
            if (finEscritura.current) window.clearTimeout(finEscritura.current)
            // Nadie manda un "dejo de escribir": se apaga solo.
            finEscritura.current = window.setTimeout(() => setEscribiendoElOtro(false), 3_000)
          }
        } catch {
          /* un mensaje ilegible no puede tumbar el hilo */
        }
      }

      ws.onclose = () => {
        socket.current = null
        setConectado(false)
        if (!vivo.current) return
        // Espera creciente: reintentar cada 100 ms no ayuda cuando el servidor
        // esta caido, y con tope de 10 s la reconexion sigue siendo imperceptible.
        const espera = ESPERAS_MS[Math.min(intentos.current, ESPERAS_MS.length - 1)]!
        intentos.current += 1
        temporizador.current = window.setTimeout(conectar, espera)
      }

      // onerror siempre viene seguido de onclose: un solo camino de reconexion.
      ws.onerror = () => ws.close()
    }

    conectar()

    return () => {
      vivo.current = false
      if (temporizador.current) window.clearTimeout(temporizador.current)
      if (finEscritura.current) window.clearTimeout(finEscritura.current)
      const abierto = socket.current
      socket.current = null
      if (abierto) {
        // Sin esto, cerrar a proposito dispararia una reconexion de algo que se
        // esta desmontando.
        abierto.onclose = null
        abierto.close()
      }
    }
  }, [token])

  const enviar = useCallback((cuerpo: string) => {
    const texto = cuerpo.trim()
    if (!texto || socket.current?.readyState !== WebSocket.OPEN) return
    socket.current.send(JSON.stringify({ tipo: 'MENSAJE', cuerpo: texto }))
  }, [])

  const avisarQueEscribo = useCallback(() => {
    if (socket.current?.readyState !== WebSocket.OPEN) return
    socket.current.send(JSON.stringify({ tipo: 'ESCRIBIENDO' }))
  }, [])

  return { cargando, error, detalle, mensajes, conectado, escribiendoElOtro, enviar, avisarQueEscribo }
}
