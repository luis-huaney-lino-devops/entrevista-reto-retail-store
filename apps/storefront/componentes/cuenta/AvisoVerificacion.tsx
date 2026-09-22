'use client'

import { useState } from 'react'
import { AlertTriangle, Loader2, MailCheck } from 'lucide-react'

import { peticion } from '@/lib/api.cliente'
import { ErrorApi } from '@/lib/errores'
import { useSesion } from '@/funcionalidades/sesion/ProveedorSesion'

/**
 * El aviso de «tu correo no esta verificado», con el boton para reenviarlo.
 *
 * Vive en un componente propio porque aparece en mas de una pantalla: en «Mis
 * datos» y en «Mis pedidos», que es justo donde la falta de verificacion
 * estorba. Duplicarlo habria garantizado que los dos textos se separaran a la
 * primera correccion.
 *
 * **El estado de exito no se borra.** Tras reenviar se sustituye el aviso por
 * la confirmacion y ahi se queda: volver a ensenar el boton invita a pulsarlo
 * otra vez, y el limite son tres por cuarto de hora (RN-068).
 */
export function AvisoVerificacion() {
  const { cliente } = useSesion()
  const [estado, setEstado] = useState<'inicial' | 'enviando' | 'enviado'>('inicial')
  const [error, setError] = useState<string | null>(null)

  if (!cliente || cliente.emailVerificado) return null

  async function reenviar() {
    if (estado === 'enviando') return
    setError(null)
    setEstado('enviando')
    try {
      await peticion('/cuenta/verificacion/reenviar', { metodo: 'POST' })
      setEstado('enviado')
    } catch (e) {
      setEstado('inicial')
      if (e instanceof ErrorApi && e.codigo === 'TOO_MANY_REQUESTS') {
        setError('Ya enviamos varios correos. Espera unos minutos antes de pedir otro.')
      } else if (e instanceof ErrorApi && e.codigo === 'EMAIL_ALREADY_VERIFIED') {
        setError('Tu correo ya estaba verificado. Recarga la pagina.')
      } else {
        setError('No pudimos reenviarlo. Intentalo en un momento.')
      }
    }
  }

  if (estado === 'enviado') {
    return (
      <div className="flex items-start gap-2.5 rounded-marca border border-exito/30 bg-exito-suave px-4 py-3">
        <MailCheck size={17} className="mt-0.5 shrink-0 text-exito" aria-hidden />
        <div className="min-w-0 text-sm">
          <p className="font-semibold text-exito">Te enviamos el correo</p>
          <p className="mt-0.5 break-words text-texto-medio">
            Revisa <strong className="text-texto">{cliente.email}</strong>. El enlace vale 24 horas; si no
            aparece, mira en la carpeta de correo no deseado.
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="rounded-marca border border-aviso/30 bg-aviso-suave px-4 py-3">
      <div className="flex items-start gap-2.5">
        <AlertTriangle size={17} className="mt-0.5 shrink-0 text-aviso" aria-hidden />
        <div className="min-w-0 text-sm">
          <p className="font-semibold text-aviso">Tu correo todavia no esta verificado</p>
          <p className="mt-0.5 text-texto-medio">
            Comprar no lo necesita, pero ver el historial de pedidos y cambiar la contrasena si.
          </p>

          {error && (
            <p role="alert" className="mt-2 font-medium text-peligro">
              {error}
            </p>
          )}

          {/* `flex-wrap`: en un movil estrecho el boton baja de linea en vez de
              empujar el texto fuera del aviso. */}
          <div className="mt-2.5 flex flex-wrap items-center gap-x-3 gap-y-1">
            <button
              type="button"
              onClick={reenviar}
              disabled={estado === 'enviando'}
              className="inline-flex items-center gap-1.5 rounded-marca bg-aviso px-3 py-1.5 text-[13px] font-semibold text-white transition hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {estado === 'enviando' && <Loader2 size={13} className="animate-spin" aria-hidden />}
              Reenviar el correo
            </button>
            <span className="text-xs text-texto-suave">Lo mandamos a {cliente.email}</span>
          </div>
        </div>
      </div>
    </div>
  )
}
