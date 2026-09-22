'use client'

import { useEffect, useRef, useState } from 'react'

import { useSesion } from '@/funcionalidades/sesion/ProveedorSesion'

/**
 * "Continuar con Google", con Google Identity Services.
 *
 * **Si no hay `NEXT_PUBLIC_GOOGLE_CLIENT_ID`, este componente no pinta nada.**
 * Ensenar un boton que se sabe que va a fallar es peor que no ofrecerlo: el
 * usuario lo pulsa, no pasa nada y se lleva la impresion de que la tienda esta
 * rota.
 *
 * El flujo es el de GIS con ID token: Google devuelve una `credencial` (un JWT
 * firmado) y la tienda la manda a `POST /cuenta/google`. La verificacion de esa
 * firma ocurre en el backend, que es donde tiene que ocurrir: aqui no se
 * inspecciona ni se confia en su contenido.
 *
 * El script se carga una sola vez y se limpia el callback al desmontar, porque
 * GIS registra su estado en `window`.
 */

type RespuestaGoogle = { credential?: string }

declare global {
  interface Window {
    google?: {
      accounts: {
        id: {
          initialize: (config: {
            client_id: string
            callback: (respuesta: RespuestaGoogle) => void
            ux_mode?: 'popup' | 'redirect'
          }) => void
          renderButton: (elemento: HTMLElement, opciones: Record<string, unknown>) => void
        }
      }
    }
  }
}

const URL_SCRIPT = 'https://accounts.google.com/gsi/client'

export function BotonGoogle({ alFallar }: { alFallar?: (mensaje: string) => void }) {
  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? ''
  const { accederConGoogle } = useSesion()
  const contenedor = useRef<HTMLDivElement>(null)
  const [listo, setListo] = useState(false)

  useEffect(() => {
    if (!clientId) return

    let cancelado = false

    function montar() {
      if (cancelado || !window.google || !contenedor.current) return
      window.google.accounts.id.initialize({
        client_id: clientId,
        callback: (respuesta) => {
          if (!respuesta.credential) {
            alFallar?.('Google no devolvio una credencial valida.')
            return
          }
          void accederConGoogle(respuesta.credential).catch((e: unknown) => {
            alFallar?.(e instanceof Error ? e.message : 'No pudimos entrar con Google.')
          })
        },
      })
      window.google.accounts.id.renderButton(contenedor.current, {
        theme: 'outline',
        size: 'large',
        width: 320,
        text: 'continue_with',
        locale: 'es',
      })
      setListo(true)
    }

    const existente = document.querySelector<HTMLScriptElement>(`script[src="${URL_SCRIPT}"]`)
    if (existente) {
      if (window.google) montar()
      else existente.addEventListener('load', montar, { once: true })
      return () => {
        cancelado = true
      }
    }

    const script = document.createElement('script')
    script.src = URL_SCRIPT
    script.async = true
    script.defer = true
    script.addEventListener('load', montar, { once: true })
    script.addEventListener('error', () => alFallar?.('No pudimos cargar el acceso con Google.'), { once: true })
    document.head.appendChild(script)

    return () => {
      cancelado = true
    }
  }, [clientId, accederConGoogle, alFallar])

  // Sin client id no hay boton. Es la decision, no un descuido.
  if (!clientId) return null

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-3">
        <span className="h-px flex-1 bg-borde" />
        <span className="text-xs uppercase tracking-wide text-texto-suave">o</span>
        <span className="h-px flex-1 bg-borde" />
      </div>
      <div ref={contenedor} className="flex justify-center" />
      {!listo && <p className="text-center text-xs text-texto-suave">Cargando el acceso con Google...</p>}
    </div>
  )
}
