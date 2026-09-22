'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { Loader2, Package } from 'lucide-react'

import { AvisoVerificacion } from '@/componentes/cuenta/AvisoVerificacion'
import { ZonaPrivada } from '@/componentes/cuenta/ZonaPrivada'
import { EnlaceBoton } from '@/componentes/ui/Boton'
import { SinResultados } from '@/componentes/ui/Estados'
import { peticion } from '@/lib/api.cliente'
import { dinero, fecha } from '@/lib/formato'
import { useSesion } from '@/funcionalidades/sesion/ProveedorSesion'

/**
 * Mis pedidos.
 *
 * Lee `GET /ordenes/mios`, que solo devuelve los que tienen cuenta asociada.
 * Una compra hecha como invitado con el mismo correo **no** sale aqui: si
 * saliera, bastaria registrarse con el correo de otra persona para ver lo que
 * compro sin cuenta. Por eso la nota del final apunta a la confirmacion, que se
 * consulta por numero.
 */

type ResumenPedido = {
  numero: string
  creadoEn: string
  estado: string
  estadoEtiqueta: string
  total: number
  totalUnidades: number
}

export default function PaginaPedidos() {
  return (
    <ZonaPrivada titulo="Mis pedidos">
      <div className="max-w-2xl space-y-6">
        <AvisoVerificacion />
        <Contenido />
      </div>
    </ZonaPrivada>
  )
}

function Contenido() {
  const { estado: estadoSesion } = useSesion()
  const [pedidos, setPedidos] = useState<ResumenPedido[] | null>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    if (estadoSesion !== 'autenticado') return
    let vivo = true
    peticion<ResumenPedido[]>('/ordenes/mios')
      .then((datos) => {
        if (vivo) setPedidos(datos)
      })
      .catch(() => {
        if (vivo) setError(true)
      })
    return () => {
      vivo = false
    }
  }, [estadoSesion])

  if (error) {
    return (
      <p role="alert" className="rounded-marca bg-peligro-suave px-4 py-3 text-sm font-medium text-peligro">
        No pudimos cargar tus pedidos. Recarga la pagina en un momento.
      </p>
    )
  }

  if (pedidos === null) {
    return (
      <div className="flex justify-center py-12 text-texto-suave">
        <Loader2 className="animate-spin" size={26} aria-hidden />
        <span className="sr-only">Cargando tus pedidos...</span>
      </div>
    )
  }

  if (pedidos.length === 0) {
    return (
      <SinResultados
        icono={<Package size={38} strokeWidth={1.5} />}
        titulo="Todavia no tienes pedidos"
        descripcion="Cuando compres algo, aparecera aqui con su estado."
        accion={
          <EnlaceBoton href="/productos" variante="primario">
            Ver el catalogo
          </EnlaceBoton>
        }
      />
    )
  }

  return (
    <>
      <ul className="space-y-3">
        {pedidos.map((pedido) => (
          <li key={pedido.numero}>
            {/* Tarjeta y no fila de tabla: en un movil una tabla de cinco
                columnas obliga a desplazar en horizontal para leer el total,
                que es justo el dato que se viene a mirar. */}
            <Link
              href={`/pedido/${pedido.numero}`}
              className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 rounded-marca border border-borde bg-white p-4 transition hover:border-borde-fuerte"
            >
              <div className="min-w-0">
                <p className="font-mono text-sm font-bold tracking-tight text-tinta">{pedido.numero}</p>
                <p className="mt-0.5 text-xs text-texto-suave">
                  {fecha(pedido.creadoEn)} · {pedido.totalUnidades}{' '}
                  {pedido.totalUnidades === 1 ? 'articulo' : 'articulos'}
                </p>
              </div>
              <div className="flex items-center gap-3">
                <span className="rounded-full bg-superficie-fondo px-2.5 py-1 text-xs font-semibold text-texto-medio">
                  {pedido.estadoEtiqueta}
                </span>
                <span className="font-bold text-tinta">{dinero(pedido.total)}</span>
              </div>
            </Link>
          </li>
        ))}
      </ul>

      <p className="text-xs text-texto-suave">
        Si compraste sin iniciar sesion, ese pedido no aparece aqui. Puedes consultarlo con el numero que
        te dimos al confirmar.
      </p>
    </>
  )
}
