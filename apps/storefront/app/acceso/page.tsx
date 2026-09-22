'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { Suspense } from 'react'
import { Loader2 } from 'lucide-react'

import { Contenedor } from '@/componentes/disposicion/Seccion'
import { BotonGoogle } from '@/componentes/cuenta/BotonGoogle'
import { Boton } from '@/componentes/ui/Boton'
import { CampoTexto } from '@/componentes/ui/Campo'
import { ErrorApi, mensajeDeError } from '@/lib/errores'
import { useSesion } from '@/funcionalidades/sesion/ProveedorSesion'

/**
 * Acceso.
 *
 * **Un solo mensaje de error.** El backend devuelve `401 INVALID_CREDENTIALS`
 * para correo inexistente, contrasena equivocada y cuenta desactivada, y se
 * esfuerza en que las tres respuestas sean indistinguibles. Mapear ese codigo a
 * tres textos distintos aqui reabriria exactamente el agujero que el backend
 * cerro: bastaria probar correos hasta ver cambiar el mensaje para saber cuales
 * estan registrados.
 *
 * `autocomplete` correcto en los dos campos para que los gestores de
 * contrasenas funcionen. Suena menor y no lo es: un formulario que el gestor no
 * reconoce empuja a la gente a contrasenas que pueda recordar.
 */
export default function PaginaAcceso() {
  return (
    <Suspense fallback={<Contenedor className="py-16">{null}</Contenedor>}>
      <FormularioAcceso />
    </Suspense>
  )
}

function FormularioAcceso() {
  const { acceder, estado } = useSesion()
  const router = useRouter()
  const parametros = useSearchParams()
  const destino = parametros.get('volverA') ?? '/mi-cuenta'

  const [email, setEmail] = useState('')
  const [contrasena, setContrasena] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  // Quien ya tiene sesion no tiene nada que hacer aqui.
  useEffect(() => {
    if (estado === 'autenticado') router.replace(destino)
  }, [estado, router, destino])

  async function enviar(e: React.FormEvent) {
    e.preventDefault()
    if (enviando) return
    setError(null)
    setEnviando(true)
    try {
      await acceder(email.trim(), contrasena)
      router.replace(destino)
    } catch (err: unknown) {
      if (err instanceof ErrorApi && err.estado === 404) {
        setError('El acceso de clientes todavia no esta disponible en este entorno.')
      } else {
        setError(mensajeDeError(err))
      }
    } finally {
      setEnviando(false)
    }
  }

  return (
    <Contenedor className="flex justify-center py-12 sm:py-16">
      <div className="w-full max-w-sm">
        <h1 className="mb-1.5 font-marca text-2xl font-bold text-tinta">Entrar</h1>
        <p className="mb-7 text-sm text-texto-suave">
          Accede para ver tus pedidos, tus direcciones y tus favoritos desde cualquier dispositivo.
        </p>

        <form onSubmit={enviar} className="space-y-4" noValidate>
          <CampoTexto
            etiqueta="Correo electronico"
            type="email"
            name="email"
            autoComplete="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />

          <CampoTexto
            etiqueta="Contrasena"
            type="password"
            name="contrasena"
            autoComplete="current-password"
            required
            value={contrasena}
            onChange={(e) => setContrasena(e.target.value)}
          />

          {error && (
            <p role="alert" className="rounded-marca bg-peligro-suave px-3 py-2.5 text-sm font-medium text-peligro">
              {error}
            </p>
          )}

          <Boton type="submit" variante="primario" tamano="lg" className="w-full" disabled={enviando}>
            {enviando && <Loader2 size={17} className="animate-spin" aria-hidden />}
            {enviando ? 'Entrando...' : 'Entrar'}
          </Boton>
        </form>

        <p className="mt-3 text-center text-sm">
          <Link href="/recuperar" className="text-texto-medio underline-offset-2 hover:underline">
            Olvidaste tu contrasena?
          </Link>
        </p>

        <div className="mt-6">
          <BotonGoogle alFallar={setError} />
        </div>

        <p className="mt-7 text-center text-sm text-texto-medio">
          No tienes cuenta?{' '}
          <Link href="/registro" className="font-semibold text-tinta-claro hover:underline">
            Crear una
          </Link>
        </p>
        <p className="mt-2 text-center text-xs text-texto-suave">
          Comprar no exige cuenta: el carrito funciona sin iniciar sesion.
        </p>
      </div>
    </Contenedor>
  )
}
