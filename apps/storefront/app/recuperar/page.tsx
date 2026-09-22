'use client'

import { useState } from 'react'
import Link from 'next/link'
import { MailCheck, ShieldQuestion } from 'lucide-react'
import { Loader2 } from 'lucide-react'

import { Contenedor } from '@/componentes/disposicion/Seccion'
import { Boton, EnlaceBoton } from '@/componentes/ui/Boton'
import { CampoTexto } from '@/componentes/ui/Campo'
import { peticion } from '@/lib/api.cliente'
import { ErrorApi } from '@/lib/errores'

/**
 * Pedir el enlace para cambiar la contrasena.
 *
 * **La pantalla de exito se ensena exista o no el correo** (RN-066). No es un
 * descuido: si dijera «esa direccion no esta registrada», cualquiera podria
 * usar este formulario para averiguar quien tiene cuenta en la tienda, y eso
 * revela habitos de compra. La API responde igual en los dos casos y aqui se
 * respeta esa decision en vez de deshacerla con un mensaje distinto.
 */
export default function PaginaRecuperar() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [enviado, setEnviado] = useState(false)

  async function enviar(e: React.FormEvent) {
    e.preventDefault()
    if (enviando) return

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      setError('Escribe un correo valido.')
      return
    }

    setError(null)
    setEnviando(true)
    try {
      await peticion('/cuenta/recuperar', { metodo: 'POST', cuerpo: { email: email.trim() } })
      setEnviado(true)
    } catch (err) {
      setEnviando(false)
      if (err instanceof ErrorApi && err.codigo === 'TOO_MANY_REQUESTS') {
        setError('Ya pediste varios enlaces. Espera unos minutos antes de intentarlo otra vez.')
      } else {
        setError('No pudimos procesar la solicitud. Intentalo en un momento.')
      }
    }
  }

  if (enviado) {
    return (
      <Contenedor className="py-16">
        <div className="mx-auto flex max-w-md flex-col items-center gap-4 text-center">
          <span className="flex h-14 w-14 items-center justify-center rounded-full bg-exito-suave text-exito">
            <MailCheck size={30} strokeWidth={1.75} aria-hidden />
          </span>
          <h1 className="font-marca text-2xl font-bold text-tinta">Revisa tu correo</h1>
          <p className="text-texto-medio">
            Si <strong className="break-all text-tinta">{email.trim()}</strong> tiene una cuenta, le hemos
            enviado un enlace para cambiar la contrasena.
          </p>
          <p className="text-sm text-texto-suave">
            El enlace vale 30 minutos y sirve una sola vez. Si no aparece, mira en la carpeta de correo no
            deseado.
          </p>
          <EnlaceBoton href="/acceso" variante="secundario">
            Volver a entrar
          </EnlaceBoton>
        </div>
      </Contenedor>
    )
  }

  return (
    <Contenedor className="py-12">
      <div className="mx-auto max-w-md">
        <header className="mb-6 text-center">
          <span className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-marca-suave text-marca-oscura">
            <ShieldQuestion size={22} aria-hidden />
          </span>
          <h1 className="font-marca text-2xl font-bold text-tinta">Olvidaste tu contrasena?</h1>
          <p className="mt-1 text-sm text-texto-medio">
            Escribe tu correo y te mandamos un enlace para elegir una nueva.
          </p>
        </header>

        <form onSubmit={enviar} noValidate className="space-y-4 rounded-marca border border-borde bg-white p-5">
          <CampoTexto
            etiqueta="Correo electronico"
            type="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />

          {error && (
            <p role="alert" className="rounded-marca bg-peligro-suave px-3 py-2.5 text-sm font-medium text-peligro">
              {error}
            </p>
          )}

          <Boton type="submit" variante="primario" className="w-full" disabled={enviando}>
            {enviando && <Loader2 size={16} className="animate-spin" aria-hidden />}
            Enviarme el enlace
          </Boton>
        </form>

        <p className="mt-4 text-center text-sm text-texto-suave">
          <Link href="/acceso" className="underline">
            Volver a entrar
          </Link>
        </p>
      </div>
    </Contenedor>
  )
}
