'use client'

import { Suspense, useState } from 'react'
import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import { CheckCircle2, KeyRound, Loader2 } from 'lucide-react'

import { Contenedor } from '@/componentes/disposicion/Seccion'
import { Boton, EnlaceBoton } from '@/componentes/ui/Boton'
import { CampoTexto } from '@/componentes/ui/Campo'
import { peticion } from '@/lib/api.cliente'
import { ErrorApi } from '@/lib/errores'

/**
 * Poner una contrasena nueva con el token del correo.
 *
 * El token llega en la URL porque tiene que viajar en un enlace pulsable. Lo
 * que protege la cuenta no es esconderlo, sino que dure 30 minutos, sirva una
 * sola vez y en la base solo este su hash (RN-066).
 *
 * `useSearchParams` obliga a envolver en `Suspense`: sin el, Next no puede
 * prerenderizar la ruta y avisa al construir.
 */
export default function PaginaRestablecer() {
  return (
    <Suspense fallback={<Cargando />}>
      <Formulario />
    </Suspense>
  )
}

function Cargando() {
  return (
    <Contenedor className="py-16">
      <div className="flex justify-center text-texto-suave">
        <Loader2 className="animate-spin" size={26} aria-hidden />
      </div>
    </Contenedor>
  )
}

function Formulario() {
  const token = useSearchParams().get('token') ?? ''

  const [nueva, setNueva] = useState('')
  const [repetida, setRepetida] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [listo, setListo] = useState(false)

  async function enviar(e: React.FormEvent) {
    e.preventDefault()
    if (enviando) return

    if (nueva.length < 10) {
      setError('La contrasena necesita al menos 10 caracteres.')
      return
    }
    // Se comprueba aqui y no en el servidor a proposito: la repeticion es una
    // ayuda de la interfaz contra las erratas, no una regla de negocio. La API
    // no tiene por que saber que existe un segundo campo.
    if (nueva !== repetida) {
      setError('Las dos contrasenas no coinciden.')
      return
    }

    setError(null)
    setEnviando(true)
    try {
      await peticion('/cuenta/restablecer', {
        metodo: 'POST',
        cuerpo: { token, contrasenaNueva: nueva },
      })
      setListo(true)
    } catch (err) {
      setEnviando(false)
      if (err instanceof ErrorApi && err.codigo === 'INVALID_TOKEN') {
        setError('Este enlace ya no vale: caduco o se uso. Pide uno nuevo desde la pantalla de acceso.')
      } else if (err instanceof ErrorApi && err.codigo === 'VALIDATION_ERROR') {
        setError(err.errores[0]?.message ?? 'Revisa la contrasena.')
      } else {
        setError('No pudimos cambiarla. Intentalo en un momento.')
      }
    }
  }

  if (listo) {
    return (
      <Contenedor className="py-16">
        <div className="mx-auto flex max-w-md flex-col items-center gap-4 text-center">
          <span className="flex h-14 w-14 items-center justify-center rounded-full bg-exito-suave text-exito">
            <CheckCircle2 size={30} strokeWidth={1.75} aria-hidden />
          </span>
          <h1 className="font-marca text-2xl font-bold text-tinta">Contrasena cambiada</h1>
          <p className="text-texto-medio">
            Cerramos las sesiones abiertas en otros dispositivos. Entra con la contrasena nueva.
          </p>
          <EnlaceBoton href="/acceso" variante="primario">
            Ir a entrar
          </EnlaceBoton>
        </div>
      </Contenedor>
    )
  }

  if (!token) {
    return (
      <Contenedor className="py-16">
        <div className="mx-auto max-w-md text-center">
          <h1 className="mb-2 font-marca text-2xl font-bold text-tinta">Enlace incompleto</h1>
          <p className="mb-5 text-texto-medio">
            Abre el enlace tal como viene en el correo, sin recortarlo.
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
            <KeyRound size={22} aria-hidden />
          </span>
          <h1 className="font-marca text-2xl font-bold text-tinta">Elige una contrasena nueva</h1>
          <p className="mt-1 text-sm text-texto-medio">
            Al guardarla se cierran las sesiones abiertas en otros dispositivos.
          </p>
        </header>

        <form onSubmit={enviar} noValidate className="space-y-4 rounded-marca border border-borde bg-white p-5">
          <CampoTexto
            etiqueta="Contrasena nueva"
            type="password"
            autoComplete="new-password"
            required
            minLength={10}
            value={nueva}
            ayuda="Al menos 10 caracteres."
            onChange={(e) => setNueva(e.target.value)}
          />

          <CampoTexto
            etiqueta="Repite la contrasena"
            type="password"
            autoComplete="new-password"
            required
            value={repetida}
            onChange={(e) => setRepetida(e.target.value)}
          />

          {error && (
            <p role="alert" className="rounded-marca bg-peligro-suave px-3 py-2.5 text-sm font-medium text-peligro">
              {error}
            </p>
          )}

          <Boton type="submit" variante="primario" className="w-full" disabled={enviando}>
            {enviando && <Loader2 size={16} className="animate-spin" aria-hidden />}
            Guardar contrasena
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
