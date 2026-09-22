'use client'

import { Suspense, useEffect, useRef, useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { ArrowLeft, Loader2, MessageSquare, Send } from 'lucide-react'

import { ZonaPrivada } from '@/componentes/cuenta/ZonaPrivada'
import { Boton, EnlaceBoton } from '@/componentes/ui/Boton'
import { SinResultados } from '@/componentes/ui/Estados'
import { peticion } from '@/lib/api.cliente'
import { clases, fecha } from '@/lib/formato'
import type { ConversacionMia } from '@/lib/tipos'
import { useChat } from '@/funcionalidades/chat/useChat'

/**
 * Mis mensajes con la tienda.
 *
 * Una sola ruta con dos vistas: la bandeja y el hilo, segun haya `?hilo=` en la
 * URL. Asi el boton «atras» del navegador funciona como se espera y un hilo se
 * puede compartir o recargar sin perderse.
 */
export default function PaginaMensajes() {
  return (
    <ZonaPrivada titulo="Mis mensajes">
      <Suspense fallback={<Cargando />}>
        <Contenido />
      </Suspense>
    </ZonaPrivada>
  )
}

function Cargando() {
  return (
    <div className="flex justify-center py-12 text-texto-suave">
      <Loader2 className="animate-spin" size={26} aria-hidden />
    </div>
  )
}

function Contenido() {
  const token = useSearchParams().get('hilo')
  return token ? <Hilo token={token} /> : <Bandeja />
}

/* ------------------------------------------------------------------ bandeja */

function Bandeja() {
  const [hilos, setHilos] = useState<ConversacionMia[] | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    let vivo = true
    peticion<ConversacionMia[]>('/cuenta/conversaciones')
      .then((d) => vivo && setHilos(d))
      .catch(() => vivo && setError(true))
    return () => {
      vivo = false
    }
  }, [])

  if (error) {
    return (
      <p role="alert" className="rounded-marca bg-peligro-suave px-4 py-3 text-sm font-medium text-peligro">
        No pudimos cargar tus mensajes. Recarga la pagina en un momento.
      </p>
    )
  }

  if (hilos === null) return <Cargando />

  if (hilos.length === 0) {
    return (
      <SinResultados
        icono={<MessageSquare size={38} strokeWidth={1.5} />}
        titulo="Todavia no tienes mensajes"
        descripcion="Si tienes una duda sobre un pedido, escribenos desde la pagina del pedido y te respondemos por aqui."
        accion={
          <EnlaceBoton href="/mi-cuenta/pedidos" variante="primario">
            Ver mis pedidos
          </EnlaceBoton>
        }
      />
    )
  }

  return (
    <ul className="space-y-3">
      {hilos.map((hilo) => (
        <li key={hilo.token}>
          <a
            href={`/mi-cuenta/mensajes?hilo=${hilo.token}`}
            className="flex items-start justify-between gap-4 rounded-marca border border-borde bg-white p-4 transition hover:border-borde-fuerte"
          >
            <div className="min-w-0">
              <p className="truncate font-semibold text-tinta">{hilo.asunto}</p>
              <p className="mt-0.5 text-xs text-texto-suave">
                {hilo.ordenNumero && <span className="font-mono">{hilo.ordenNumero} · </span>}
                {hilo.ultimoMensajeEn ? fecha(hilo.ultimoMensajeEn) : fecha(hilo.creadoEn)}
                {hilo.estado === 'CERRADA' && ' · cerrada'}
              </p>
            </div>
            {hilo.noLeidos > 0 && (
              <span className="shrink-0 rounded-full bg-marca px-2 py-0.5 text-xs font-bold text-white">
                {hilo.noLeidos}
              </span>
            )}
          </a>
        </li>
      ))}
    </ul>
  )
}

/* --------------------------------------------------------------------- hilo */

function Hilo({ token }: { token: string }) {
  const router = useRouter()
  const { cargando, error, detalle, mensajes, conectado, escribiendoElOtro, enviar, avisarQueEscribo } =
    useChat(token)
  const [borrador, setBorrador] = useState('')
  const finDeLista = useRef<HTMLDivElement>(null)

  // Al fondo en cada mensaje nuevo: en un chat lo ultimo es lo que importa.
  useEffect(() => {
    finDeLista.current?.scrollIntoView({ behavior: 'smooth' })
  }, [mensajes.length])

  if (cargando) return <Cargando />

  if (error || !detalle) {
    return (
      <p role="alert" className="rounded-marca bg-peligro-suave px-4 py-3 text-sm font-medium text-peligro">
        No pudimos abrir esta conversacion.
      </p>
    )
  }

  const cerrada = detalle.conversacion.estado === 'CERRADA'

  function mandar(e: React.FormEvent) {
    e.preventDefault()
    if (!borrador.trim()) return
    enviar(borrador)
    // Se limpia sin esperar: el mensaje vuelve por la difusion y aparece solo.
    setBorrador('')
  }

  return (
    <div className="flex h-[70vh] flex-col rounded-marca border border-borde bg-white">
      <header className="flex items-center gap-3 border-b border-borde px-4 py-3">
        <button
          type="button"
          onClick={() => router.push('/mi-cuenta/mensajes')}
          aria-label="Volver a mis mensajes"
          className="rounded p-1 text-texto-medio transition hover:bg-superficie-fondo"
        >
          <ArrowLeft size={18} aria-hidden />
        </button>
        <div className="min-w-0 flex-1">
          <p className="truncate font-semibold text-tinta">{detalle.conversacion.asunto}</p>
          {detalle.conversacion.ordenNumero && (
            <p className="font-mono text-xs text-texto-suave">{detalle.conversacion.ordenNumero}</p>
          )}
        </div>
        <span
          title={conectado ? 'Conectado' : 'Reconectando...'}
          className={clases('h-2 w-2 shrink-0 rounded-full', conectado ? 'bg-exito' : 'bg-texto-suave')}
        />
      </header>

      <div className="flex-1 space-y-3 overflow-y-auto px-4 py-4">
        {mensajes.map((m) => {
          const mio = m.autor === 'CLIENTE'
          return (
            <div key={m.id} className={clases('flex', mio ? 'justify-end' : 'justify-start')}>
              <div
                className={clases(
                  'max-w-[85%] rounded-marca px-3 py-2 text-sm',
                  mio ? 'bg-tinta text-white' : 'bg-superficie-fondo text-texto',
                )}
              >
                {!mio && (
                  <p className="mb-0.5 text-xs font-semibold text-tinta-claro">{m.autorNombre}</p>
                )}
                {m.cuerpo && <p className="whitespace-pre-wrap break-words">{m.cuerpo}</p>}
                {m.adjuntos.map((a) => (
                  <a
                    key={a.id}
                    href={a.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="mt-1 block text-xs underline"
                  >
                    {a.nombre}
                  </a>
                ))}
                <p className={clases('mt-1 text-[11px]', mio ? 'text-white/60' : 'text-texto-suave')}>
                  {fecha(m.enviadoEn)}
                </p>
              </div>
            </div>
          )
        })}

        {escribiendoElOtro && (
          <p className="text-xs italic text-texto-suave">La tienda esta escribiendo...</p>
        )}
        <div ref={finDeLista} />
      </div>

      {cerrada ? (
        <p className="border-t border-borde px-4 py-3 text-sm text-texto-suave">
          Esta conversacion esta cerrada. Abre una nueva desde tu pedido si necesitas algo mas.
        </p>
      ) : (
        <form onSubmit={mandar} className="flex items-center gap-2 border-t border-borde px-3 py-3">
          <input
            value={borrador}
            onChange={(e) => {
              setBorrador(e.target.value)
              avisarQueEscribo()
            }}
            placeholder={conectado ? 'Escribe tu mensaje...' : 'Reconectando...'}
            disabled={!conectado}
            maxLength={4000}
            aria-label="Mensaje"
            className="h-10 w-full min-w-0 rounded-marca border border-borde px-3 text-sm outline-none transition focus:border-tinta-claro disabled:bg-superficie-alt"
          />
          <Boton type="submit" variante="primario" disabled={!conectado || !borrador.trim()}>
            <Send size={15} aria-hidden />
            <span className="sr-only">Enviar</span>
          </Boton>
        </form>
      )}
    </div>
  )
}
