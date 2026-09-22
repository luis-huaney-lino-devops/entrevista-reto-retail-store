'use client'

import { Suspense, useEffect, useRef, useState } from 'react'
import { CheckCircle2, Loader2, XCircle } from 'lucide-react'

import { Contenedor } from '@/componentes/disposicion/Seccion'
import { EnlaceBoton } from '@/componentes/ui/Boton'
import { peticion } from '@/lib/api.cliente'
import { useSesion } from '@/funcionalidades/sesion/ProveedorSesion'
import { useSearchParams } from 'next/navigation'

/**
 * Confirma el correo con el token del enlace.
 *
 * No exige sesion: el enlace se abre a menudo en otro navegador -el del movil
 * donde se lee el correo- donde no hay ninguna. El token es lo que identifica
 * la cuenta.
 */
export default function PaginaVerificar() {
  return (
    <Suspense fallback={<Estado icono="cargando" titulo="Comprobando el enlace..." />}>
      <Verificacion />
    </Suspense>
  )
}

function Verificacion() {
  const token = useSearchParams().get('token') ?? ''
  const { estado: estadoSesion, refrescarCliente } = useSesion()
  const [resultado, setResultado] = useState<'cargando' | 'ok' | 'error'>('cargando')
  // En desarrollo, React monta dos veces: sin esto el token se consumiria en
  // la primera llamada y la segunda mostraria "enlace invalido" sobre una
  // verificacion que SI funciono.
  const lanzado = useRef(false)

  useEffect(() => {
    if (!token) {
      setResultado('error')
      return
    }
    if (lanzado.current) return
    lanzado.current = true

    peticion('/cuenta/verificacion', { metodo: 'POST', cuerpo: { token } })
      .then(async () => {
        setResultado('ok')
        // Si hay sesion abierta, el aviso de "sin verificar" tiene que
        // desaparecer sin obligar a recargar.
        if (estadoSesion === 'autenticado') await refrescarCliente()
      })
      .catch(() => setResultado('error'))
  }, [token, estadoSesion, refrescarCliente])

  if (resultado === 'cargando') {
    return <Estado icono="cargando" titulo="Comprobando el enlace..." />
  }

  if (resultado === 'ok') {
    return (
      <Estado
        icono="ok"
        titulo="Correo verificado"
        descripcion="Ya puedes ver tu historial de pedidos y cambiar la contrasena."
        accion={{ href: '/mi-cuenta', texto: 'Ir a mi cuenta' }}
      />
    )
  }

  return (
    <Estado
      icono="error"
      titulo="Este enlace ya no vale"
      descripcion="Caduco o se uso. Entra a tu cuenta y pide uno nuevo desde el aviso de verificacion."
      accion={{ href: '/mi-cuenta', texto: 'Ir a mi cuenta' }}
    />
  )
}

function Estado({
  icono,
  titulo,
  descripcion,
  accion,
}: {
  icono: 'cargando' | 'ok' | 'error'
  titulo: string
  descripcion?: string
  accion?: { href: string; texto: string }
}) {
  const decoracion = {
    cargando: { fondo: 'bg-superficie-fondo text-texto-suave', nodo: <Loader2 size={30} className="animate-spin" /> },
    ok: { fondo: 'bg-exito-suave text-exito', nodo: <CheckCircle2 size={30} strokeWidth={1.75} /> },
    error: { fondo: 'bg-peligro-suave text-peligro', nodo: <XCircle size={30} strokeWidth={1.75} /> },
  }[icono]

  return (
    <Contenedor className="py-16">
      <div className="mx-auto flex max-w-md flex-col items-center gap-4 text-center">
        <span
          aria-hidden
          className={`flex h-14 w-14 items-center justify-center rounded-full ${decoracion.fondo}`}
        >
          {decoracion.nodo}
        </span>
        <h1 className="font-marca text-2xl font-bold text-tinta">{titulo}</h1>
        {descripcion && <p className="text-texto-medio">{descripcion}</p>}
        {accion && (
          <EnlaceBoton href={accion.href} variante="primario">
            {accion.texto}
          </EnlaceBoton>
        )}
      </div>
    </Contenedor>
  )
}
