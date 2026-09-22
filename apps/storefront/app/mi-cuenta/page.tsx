'use client'

import { useEffect, useState } from 'react'
import { CheckCircle2, Loader2 } from 'lucide-react'

import { AvisoVerificacion } from '@/componentes/cuenta/AvisoVerificacion'
import { ZonaPrivada } from '@/componentes/cuenta/ZonaPrivada'
import { Boton } from '@/componentes/ui/Boton'
import { CampoTexto } from '@/componentes/ui/Campo'
import { peticion } from '@/lib/api.cliente'
import { ErrorApi, mensajeDeError } from '@/lib/errores'
import { useAvisos } from '@/funcionalidades/avisos/ProveedorAvisos'
import { useSesion } from '@/funcionalidades/sesion/ProveedorSesion'

/**
 * Mis datos.
 *
 * Dos formularios: el perfil y la contrasena.
 *
 * **La pantalla de contrasena distingue dos casos.** Quien entro con Google
 * tiene `tieneContrasena: false`: pedirle "tu contrasena actual" para cambiarla
 * no tiene sentido, asi que se le ofrece **establecer** en lugar de **cambiar**.
 * Mostrar un formulario que el usuario no puede completar es un callejon sin
 * salida.
 */
export default function PaginaMiCuenta() {
  return (
    <ZonaPrivada titulo="Mis datos">
      {/* Acotado: un campo de contrasena de 900 px de ancho no se rellena
          mejor, y el ojo pierde la relacion entre etiqueta y control. En movil
          ocupa todo, que es lo que toca. */}
      <div className="max-w-2xl space-y-6">
        <AvisoVerificacion />
        <FormularioPerfil />
        <FormularioContrasena />
      </div>
    </ZonaPrivada>
  )
}

function FormularioPerfil() {
  const { cliente, refrescarCliente } = useSesion()
  const { avisar } = useAvisos()
  const [nombre, setNombre] = useState('')
  const [telefono, setTelefono] = useState('')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [enviando, setEnviando] = useState(false)

  useEffect(() => {
    setNombre(cliente?.nombre ?? '')
    setTelefono(cliente?.telefono ?? '')
  }, [cliente])

  async function enviar(e: React.FormEvent) {
    e.preventDefault()
    if (enviando) return
    if (nombre.trim().length < 2) {
      setErrores({ nombre: 'Escribe tu nombre.' })
      return
    }
    setErrores({})
    setEnviando(true)
    try {
      await peticion('/cuenta/yo', {
        metodo: 'PUT',
        cuerpo: { nombre: nombre.trim(), ...(telefono.trim() ? { telefono: telefono.trim() } : {}) },
      })
      await refrescarCliente()
      avisar('Guardamos tus datos.', 'exito')
    } catch (err: unknown) {
      if (err instanceof ErrorApi && err.codigo === 'VALIDATION_ERROR') {
        const porCampo: Record<string, string> = {}
        for (const c of err.errores) porCampo[c.field] = c.message
        setErrores(porCampo)
      } else {
        avisar(mensajeDeError(err), 'error')
      }
    } finally {
      setEnviando(false)
    }
  }

  return (
    <section className="rounded-marca border border-borde bg-white p-5" aria-labelledby="titulo-perfil">
      <h2 id="titulo-perfil" className="mb-4 font-marca text-base font-semibold text-tinta">
        Datos personales
      </h2>

      <form onSubmit={enviar} className="space-y-4" noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
          <CampoTexto
            etiqueta="Nombre completo"
            value={nombre}
            required
            autoComplete="name"
            error={errores.nombre}
            onChange={(e) => setNombre(e.target.value)}
          />
          <CampoTexto
            etiqueta="Telefono"
            type="tel"
            value={telefono}
            autoComplete="tel"
            error={errores.telefono}
            onChange={(e) => setTelefono(e.target.value)}
          />
        </div>

        <div className="sm:max-w-md">
          <CampoTexto etiqueta="Correo electronico" value={cliente?.email ?? ''} disabled readOnly />
          <p className="mt-1 flex items-center gap-1.5 text-xs text-texto-suave">
            {cliente?.emailVerificado ? (
              <>
                <CheckCircle2 size={13} className="text-exito" aria-hidden />
                Verificado
              </>
            ) : (
              'Sin verificar'
            )}
          </p>
        </div>

        <Boton type="submit" variante="primario" disabled={enviando}>
          {enviando && <Loader2 size={16} className="animate-spin" aria-hidden />}
          Guardar cambios
        </Boton>
      </form>
    </section>
  )
}

function FormularioContrasena() {
  const { cliente } = useSesion()
  const { avisar } = useAvisos()
  const [actual, setActual] = useState('')
  const [nueva, setNueva] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  if (!cliente) return null

  // Quien entro con Google no tiene contrasena: se le ofrece establecerla, no
  // cambiarla, y no se le pide una "actual" que no existe.
  const tiene = cliente.tieneContrasena

  async function enviar(e: React.FormEvent) {
    e.preventDefault()
    if (enviando) return
    if (nueva.length < 10) {
      setError('La contrasena nueva necesita al menos 10 caracteres.')
      return
    }
    setError(null)
    setEnviando(true)
    try {
      await peticion('/cuenta/yo/contrasena', {
        metodo: 'POST',
        cuerpo: { ...(tiene ? { contrasenaActual: actual } : {}), contrasenaNueva: nueva },
      })
      setActual('')
      setNueva('')
      avisar(tiene ? 'Cambiamos tu contrasena.' : 'Establecimos tu contrasena.', 'exito')
    } catch (err: unknown) {
      setError(mensajeDeError(err))
    } finally {
      setEnviando(false)
    }
  }

  return (
    <section className="rounded-marca border border-borde bg-white p-5" aria-labelledby="titulo-contrasena">
      <h2 id="titulo-contrasena" className="mb-1 font-marca text-base font-semibold text-tinta">
        {tiene ? 'Cambiar contrasena' : 'Establecer contrasena'}
      </h2>
      <p className="mb-4 text-sm text-texto-suave">
        {tiene
          ? 'Al cambiarla, las sesiones abiertas en otros dispositivos se cierran.'
          : 'Entraste con Google y todavia no tienes contrasena. Puedes anadir una para entrar tambien por correo.'}
      </p>

      <form onSubmit={enviar} className="space-y-4" noValidate>
        <div className="grid gap-4 sm:grid-cols-2">
        {tiene && (
          <CampoTexto
            etiqueta="Contrasena actual"
            type="password"
            autoComplete="current-password"
            required
            value={actual}
            onChange={(e) => setActual(e.target.value)}
          />
        )}

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
        </div>

        {error && (
          <p role="alert" className="rounded-marca bg-peligro-suave px-3 py-2.5 text-sm font-medium text-peligro">
            {error}
          </p>
        )}

        <Boton type="submit" variante="secundario" disabled={enviando}>
          {enviando && <Loader2 size={16} className="animate-spin" aria-hidden />}
          {tiene ? 'Cambiar contrasena' : 'Establecer contrasena'}
        </Boton>
      </form>
    </section>
  )
}
