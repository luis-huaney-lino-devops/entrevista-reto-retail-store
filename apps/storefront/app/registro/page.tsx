'use client'

import { useState } from 'react'
import Link from 'next/link'
import { CheckCircle2, Loader2 } from 'lucide-react'

import { Contenedor } from '@/componentes/disposicion/Seccion'
import { BotonGoogle } from '@/componentes/cuenta/BotonGoogle'
import { Boton, EnlaceBoton } from '@/componentes/ui/Boton'
import { CampoTexto } from '@/componentes/ui/Campo'
import { peticion } from '@/lib/api.cliente'
import { ErrorApi, mensajeDeError } from '@/lib/errores'

/**
 * Registro.
 *
 * **La interfaz no revela si un correo existe, y eso condiciona la pantalla
 * entera.** El backend responde `202` exista o no la direccion —respuesta
 * identica, tiempo constante, hash senuelo— y basta con que aqui se diga "ese
 * correo ya esta registrado" para tirar todo ese trabajo.
 *
 * Por eso el exito es siempre el mismo mensaje: "Si esa direccion estaba libre,
 * te enviamos un correo". No "cuenta creada", que tambien seria una
 * confirmacion de que no existia.
 *
 * **Contrasena: minimo 10 caracteres, sin exigir mayusculas, digitos ni
 * simbolos.** La interfaz no debe mostrar requisitos que el servidor no aplica:
 * una lista de "debe contener un simbolo" seria sencillamente falsa aqui, y
 * empuja a la gente hacia contrasenas peores.
 */

const MINIMO_CONTRASENA = 10

export default function PaginaRegistro() {
  const [nombre, setNombre] = useState('')
  const [email, setEmail] = useState('')
  const [telefono, setTelefono] = useState('')
  const [contrasena, setContrasena] = useState('')
  const [errores, setErrores] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)
  const [enviado, setEnviado] = useState(false)

  function validar(): boolean {
    const nuevos: Record<string, string> = {}
    if (nombre.trim().length < 2) nuevos.nombre = 'Escribe tu nombre.'
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim())) nuevos.email = 'Revisa el formato del correo.'
    if (contrasena.length < MINIMO_CONTRASENA) {
      nuevos.contrasena = `Usa al menos ${MINIMO_CONTRASENA} caracteres.`
    }
    setErrores(nuevos)
    return Object.keys(nuevos).length === 0
  }

  async function enviar(e: React.FormEvent) {
    e.preventDefault()
    if (enviando || !validar()) return
    setError(null)
    setEnviando(true)
    try {
      await peticion('/cuenta/registro', {
        metodo: 'POST',
        sinReintento: true,
        cuerpo: {
          email: email.trim(),
          nombre: nombre.trim(),
          contrasena,
          ...(telefono.trim() ? { telefono: telefono.trim() } : {}),
        },
      })
      setEnviado(true)
    } catch (err: unknown) {
      if (err instanceof ErrorApi && err.estado === 404) {
        setError('El registro de clientes todavia no esta disponible en este entorno.')
      } else if (err instanceof ErrorApi && err.codigo === 'VALIDATION_ERROR') {
        const porCampo: Record<string, string> = {}
        for (const campo of err.errores) porCampo[campo.field] = campo.message
        setErrores(porCampo)
      } else {
        setError(mensajeDeError(err))
      }
    } finally {
      setEnviando(false)
    }
  }

  if (enviado) {
    return (
      <Contenedor className="flex justify-center py-16">
        <div className="w-full max-w-sm text-center">
          <span className="mx-auto mb-4 inline-flex h-14 w-14 items-center justify-center rounded-full bg-exito-suave text-exito">
            <CheckCircle2 size={28} aria-hidden />
          </span>
          <h1 className="mb-2 font-marca text-xl font-bold text-tinta">Revisa tu correo</h1>
          {/* Un solo mensaje, exista o no la direccion. */}
          <p className="text-sm text-texto-medio">
            Si esa direccion estaba libre, te enviamos un correo con el enlace para verificar la cuenta. Puede tardar
            un par de minutos en llegar.
          </p>
          <div className="mt-6 flex justify-center gap-3">
            <EnlaceBoton href="/acceso" variante="sutil">
              Ir a entrar
            </EnlaceBoton>
            <EnlaceBoton href="/productos" variante="primario">
              Seguir comprando
            </EnlaceBoton>
          </div>
        </div>
      </Contenedor>
    )
  }

  return (
    <Contenedor className="flex justify-center py-12 sm:py-16">
      <div className="w-full max-w-sm">
        <h1 className="mb-1.5 font-marca text-2xl font-bold text-tinta">Crear cuenta</h1>
        <p className="mb-7 text-sm text-texto-suave">
          Guarda tus direcciones, sigue tus pedidos y conserva tus favoritos.
        </p>

        <form onSubmit={enviar} className="space-y-4" noValidate>
          <CampoTexto
            etiqueta="Nombre completo"
            name="nombre"
            autoComplete="name"
            required
            value={nombre}
            error={errores.nombre}
            onChange={(e) => setNombre(e.target.value)}
          />

          <CampoTexto
            etiqueta="Correo electronico"
            type="email"
            name="email"
            autoComplete="email"
            required
            value={email}
            error={errores.email}
            onChange={(e) => setEmail(e.target.value)}
          />

          <CampoTexto
            etiqueta="Telefono"
            type="tel"
            name="telefono"
            autoComplete="tel"
            value={telefono}
            error={errores.telefono}
            ayuda="Opcional. Sirve para coordinar la entrega."
            onChange={(e) => setTelefono(e.target.value)}
          />

          <div>
            <CampoTexto
              etiqueta="Contrasena"
              type="password"
              name="contrasena"
              autoComplete="new-password"
              required
              minLength={MINIMO_CONTRASENA}
              value={contrasena}
              error={errores.contrasena}
              ayuda={`Al menos ${MINIMO_CONTRASENA} caracteres. Una frase larga es mejor que un simbolo raro.`}
              onChange={(e) => setContrasena(e.target.value)}
            />
            <MedidorFuerza valor={contrasena} />
          </div>

          {error && (
            <p role="alert" className="rounded-marca bg-peligro-suave px-3 py-2.5 text-sm font-medium text-peligro">
              {error}
            </p>
          )}

          <Boton type="submit" variante="primario" tamano="lg" className="w-full" disabled={enviando}>
            {enviando && <Loader2 size={17} className="animate-spin" aria-hidden />}
            {enviando ? 'Creando...' : 'Crear cuenta'}
          </Boton>
        </form>

        <div className="mt-6">
          <BotonGoogle alFallar={setError} />
        </div>

        <p className="mt-7 text-center text-sm text-texto-medio">
          Ya tienes cuenta?{' '}
          <Link href="/acceso" className="font-semibold text-tinta-claro hover:underline">
            Entrar
          </Link>
        </p>
      </div>
    </Contenedor>
  )
}

/**
 * Medidor de fuerza.
 *
 * Es una **pista**, no una puerta: no impide enviar nada que el servidor
 * aceptaria. Ensenar una barra roja junto a una contrasena valida solo consigue
 * que la gente anada un "1!" al final y crea que ha hecho algo.
 */
function MedidorFuerza({ valor }: { valor: string }) {
  if (valor.length === 0) return null

  const puntos =
    (valor.length >= MINIMO_CONTRASENA ? 1 : 0) +
    (valor.length >= 14 ? 1 : 0) +
    (/[^a-zA-Z0-9]/.test(valor) || /\s/.test(valor) ? 1 : 0)

  const etiquetas = ['Corta', 'Aceptable', 'Buena', 'Muy buena'] as const
  const colores = ['bg-peligro', 'bg-aviso', 'bg-exito', 'bg-exito'] as const

  return (
    <div className="mt-2">
      <div className="flex gap-1" aria-hidden>
        {[0, 1, 2].map((i) => (
          <span
            key={i}
            className={`h-1 flex-1 rounded-full ${i < puntos ? colores[puntos] : 'bg-borde'}`}
          />
        ))}
      </div>
      <p className="mt-1 text-xs text-texto-suave">Fuerza: {etiquetas[puntos]}</p>
    </div>
  )
}
