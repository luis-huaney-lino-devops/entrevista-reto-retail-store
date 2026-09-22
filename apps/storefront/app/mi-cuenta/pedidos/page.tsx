'use client'

import { useEffect, useState } from 'react'
import { Package } from 'lucide-react'

import { ZonaPrivada } from '@/componentes/cuenta/ZonaPrivada'
import { EnlaceBoton } from '@/componentes/ui/Boton'
import { SinResultados } from '@/componentes/ui/Estados'
import { peticion } from '@/lib/api.cliente'
import { dinero, fecha } from '@/lib/formato'
import { ErrorApi } from '@/lib/errores'
import { useSesion } from '@/funcionalidades/sesion/ProveedorSesion'

/**
 * Mis pedidos.
 *
 * El contrato de pedidos del cliente todavia no existe —ni en
 * `contracts/openapi.yaml` ni en la API viva— y **esta pagina no se lo
 * inventa**. Intenta leer `GET /cuenta/pedidos`; si no esta, lo dice con
 * claridad en vez de ensenar una tabla con datos falsos.
 *
 * Es deliberado: una pantalla de demostracion con pedidos ficticios parece
 * funcionar hasta que alguien pregunta de donde salen.
 */

type ResumenPedido = {
  numero: string
  creadoEn: string | null
  estado: string | null
  total: number
  totalUnidades: number | null
}

export default function PaginaPedidos() {
  return (
    <ZonaPrivada titulo="Mis pedidos">
      <Contenido />
    </ZonaPrivada>
  )
}

function Contenido() {
  const { estado: estadoSesion, cliente } = useSesion()
  const [pedidos, setPedidos] = useState<ResumenPedido[] | null>(null)
  const [noDisponible, setNoDisponible] = useState(false)
  const [necesitaVerificar, setNecesitaVerificar] = useState(false)

  useEffect(() => {
    if (estadoSesion !== 'autenticado') return
    let vivo = true
    void (async () => {
      try {
        const lista = await peticion<ResumenPedido[]>('/cuenta/pedidos')
        if (vivo) setPedidos(lista)
      } catch (e) {
        if (!vivo) return
        if (e instanceof ErrorApi && e.codigo === 'EMAIL_NOT_VERIFIED') {
          setNecesitaVerificar(true)
        } else {
          setNoDisponible(true)
        }
        setPedidos([])
      }
    })()
    return () => {
      vivo = false
    }
  }, [estadoSesion])

  // Ver el historial exige correo verificado. No es un error generico: se dice
  // que falta y como resolverlo.
  if (necesitaVerificar || (cliente && !cliente.emailVerificado)) {
    return (
      <SinResultados
        icono={<Package size={38} strokeWidth={1.5} />}
        titulo="Verifica tu correo para ver tus pedidos"
        descripcion="Comprar no lo necesita, pero el historial si. Busca el correo que te enviamos al registrarte; el enlace caduca, y si ya caduco puedes pedir otro desde el correo de bienvenida."
      />
    )
  }

  if (pedidos === null) {
    return <div className="h-48 animate-pulse rounded-marca bg-white" />
  }

  if (noDisponible) {
    return (
      <SinResultados
        icono={<Package size={38} strokeWidth={1.5} />}
        titulo="El historial de pedidos todavia no esta disponible"
        descripcion="El contrato de pedidos del cliente aun no esta publicado en la API. Cuando lo este, esta pagina lo mostrara sin mas cambios."
        accion={
          <EnlaceBoton href="/productos" variante="sutil">
            Ver el catalogo
          </EnlaceBoton>
        }
      />
    )
  }

  if (pedidos.length === 0) {
    return (
      <SinResultados
        icono={<Package size={38} strokeWidth={1.5} />}
        titulo="Todavia no hiciste ningun pedido"
        descripcion="Cuando compres, aqui veras el numero, la fecha y el estado de cada pedido."
        accion={
          <EnlaceBoton href="/productos" variante="primario">
            Ver el catalogo
          </EnlaceBoton>
        }
      />
    )
  }

  return (
    <div className="overflow-x-auto rounded-marca border border-borde bg-white">
      <table className="w-full min-w-[520px] border-collapse text-sm">
        <caption className="sr-only">Historial de pedidos</caption>
        <thead>
          <tr className="border-b border-borde bg-superficie-alt">
            <th scope="col" className="px-4 py-2.5 text-left text-[11px] font-semibold uppercase tracking-wide text-texto-suave">
              Numero
            </th>
            <th scope="col" className="px-4 py-2.5 text-left text-[11px] font-semibold uppercase tracking-wide text-texto-suave">
              Fecha
            </th>
            <th scope="col" className="px-4 py-2.5 text-left text-[11px] font-semibold uppercase tracking-wide text-texto-suave">
              Estado
            </th>
            <th scope="col" className="px-4 py-2.5 text-right text-[11px] font-semibold uppercase tracking-wide text-texto-suave">
              Total
            </th>
          </tr>
        </thead>
        <tbody>
          {pedidos.map((p) => (
            <tr key={p.numero} className="border-b border-borde last:border-0">
              <td className="cifra px-4 py-3 font-semibold text-texto">{p.numero}</td>
              <td className="px-4 py-3 text-texto-medio">{fecha(p.creadoEn)}</td>
              <td className="px-4 py-3 text-texto-medio">{p.estado ?? '-'}</td>
              <td className="cifra px-4 py-3 text-right font-semibold text-tinta">{dinero(p.total)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
