'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { Loader2, MessageSquare } from 'lucide-react'

import { Boton } from '@/componentes/ui/Boton'
import { peticion } from '@/lib/api.cliente'
import { ErrorApi } from '@/lib/errores'
import type { ConversacionDetalle } from '@/lib/tipos'
import { useSesion } from '@/funcionalidades/sesion/ProveedorSesion'

/**
 * Abre —o retoma— la conversacion sobre un pedido.
 *
 * **Solo se pinta si hay sesion y el pedido tiene cuenta asociada.** Una compra
 * de invitado no tiene a quien atar el hilo ni sesion desde la que leerlo
 * despues; ofrecer un boton que va a fallar es peor que no ofrecerlo.
 *
 * Si ya existe un hilo sobre este pedido, el servidor devuelve ese en vez de
 * crear otro: dos hilos sobre la misma compra acaban con la persona contando su
 * problema dos veces.
 */
export function ContactarSobrePedido({
  ordenId,
  ordenNumero,
  clienteId,
}: {
  ordenId: number
  ordenNumero: string
  clienteId: number | null
}) {
  const router = useRouter()
  const { cliente } = useSesion()
  const [abriendo, setAbriendo] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Ni invitado, ni el pedido de otra persona.
  if (!cliente || clienteId === null || cliente.id !== clienteId) return null

  async function contactar() {
    if (abriendo) return
    setError(null)
    setAbriendo(true)
    try {
      const hilo = await peticion<ConversacionDetalle>('/cuenta/conversaciones', {
        metodo: 'POST',
        cuerpo: { ordenId, asunto: `Consulta sobre el pedido ${ordenNumero}` },
      })
      router.push(`/mi-cuenta/mensajes?hilo=${hilo.tokenAcceso}`)
    } catch (e) {
      setAbriendo(false)
      setError(
        e instanceof ErrorApi && e.codigo === 'VALIDATION_ERROR'
          ? 'No pudimos abrir la conversacion con esos datos.'
          : 'No pudimos abrir la conversacion. Intentalo en un momento.',
      )
    }
  }

  return (
    <div>
      <Boton variante="secundario" onClick={contactar} disabled={abriendo}>
        {abriendo ? (
          <Loader2 size={15} className="animate-spin" aria-hidden />
        ) : (
          <MessageSquare size={15} aria-hidden />
        )}
        Escribir sobre este pedido
      </Boton>
      {error && (
        <p role="alert" className="mt-2 text-xs font-medium text-peligro">
          {error}
        </p>
      )}
    </div>
  )
}
