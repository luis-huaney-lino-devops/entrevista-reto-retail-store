import type { Metadata } from 'next'
import { notFound } from 'next/navigation'
import Link from 'next/link'
import { CheckCircle2, Package, Truck } from 'lucide-react'

import { Contenedor } from '@/componentes/disposicion/Seccion'
import { EnlaceBoton } from '@/componentes/ui/Boton'
import { pedidoPorNumero } from '@/lib/api.servidor'
import { dinero, fecha } from '@/lib/formato'

/**
 * La confirmacion del pedido.
 *
 * **Se renderiza en servidor y no se cachea.** Un pedido cambia de estado y
 * cachear esta pagina ensenaria un estado viejo a quien vuelve a mirarla.
 *
 * **Que esta URL sea segura depende del backend, no de aqui.** No pide sesion a
 * proposito: quien compro como invitado no tiene ninguna. Lo unico que impide
 * leer pedidos ajenos es que el numero sea `ORD-AAAAMM-XXXXXX` con sufijo
 * aleatorio (RN-057); con un numero correlativo esta pagina seria un catalogo
 * de pedidos de otros abierto a cualquiera que comprara una vez y restara.
 *
 * Por eso mismo va con `noindex` y la tienda no enlaza el numero desde ningun
 * sitio publico.
 */

export const dynamic = 'force-dynamic'

type Props = { params: Promise<{ numero: string }> }

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { numero } = await params
  return {
    title: `Pedido ${numero}`,
    // Que un buscador indexe esto seria publicar el pedido de alguien.
    robots: { index: false, follow: false },
  }
}

export default async function PaginaPedido({ params }: Props) {
  const { numero } = await params
  const pedido = await pedidoPorNumero(numero)
  if (!pedido) notFound()

  return (
    <Contenedor className="pb-12">
      <div className="mx-auto max-w-2xl">
        <header className="mb-8 flex flex-col items-center gap-3 text-center">
          <span className="flex h-14 w-14 items-center justify-center rounded-full bg-exito-suave text-exito">
            <CheckCircle2 size={30} strokeWidth={1.75} />
          </span>
          <h1 className="font-marca text-2xl font-bold text-tinta sm:text-3xl">
            Pedido confirmado
          </h1>
          <p className="text-texto-medio">
            Gracias, {pedido.nombreContacto.split(' ')[0]}. Te escribiremos a{' '}
            <strong className="text-tinta">{pedido.email}</strong> con los siguientes pasos.
          </p>
        </header>

        <section className="mb-6 rounded-xl border border-borde bg-superficie-alt p-5">
          <dl className="grid gap-4 sm:grid-cols-3">
            <div>
              <dt className="text-[13px] font-semibold text-texto-medio">Numero de pedido</dt>
              {/* Tabular para que no baile al leerlo o dictarlo por telefono. */}
              <dd className="font-mono text-sm font-bold tracking-tight text-tinta">
                {pedido.numero}
              </dd>
            </div>
            <div>
              <dt className="text-[13px] font-semibold text-texto-medio">Fecha</dt>
              <dd className="text-sm text-tinta">{fecha(pedido.creadoEn)}</dd>
            </div>
            <div>
              <dt className="text-[13px] font-semibold text-texto-medio">Estado</dt>
              <dd className="inline-flex items-center gap-1.5 text-sm font-semibold text-tinta">
                <Package size={14} />
                {pedido.estadoEtiqueta}
              </dd>
            </div>
          </dl>
        </section>

        <section className="mb-6 rounded-xl border border-borde bg-white p-5">
          <h2 className="mb-4 font-semibold text-tinta">Lo que compraste</h2>
          <ul className="flex flex-col divide-y divide-borde">
            {pedido.items.map((linea) => (
              <li key={linea.productoId} className="flex justify-between gap-4 py-3 text-sm">
                <div>
                  {/* El nombre sale de la linea, no del producto: la orden copia
                      y no referencia (RN-052). Si el producto se renombra
                      manana, este pedido sigue diciendo que se compro. */}
                  <p className="font-medium text-tinta">{linea.nombreProducto}</p>
                  <p className="text-texto-suave">
                    {linea.cantidad} x {dinero(linea.precioUnitario)} · SKU {linea.sku}
                  </p>
                </div>
                <span className="shrink-0 font-semibold text-tinta">{dinero(linea.totalLinea)}</span>
              </li>
            ))}
          </ul>

          <dl className="mt-4 flex flex-col gap-2 border-t border-borde pt-4 text-sm">
            <div className="flex justify-between">
              <dt className="text-texto-medio">Subtotal</dt>
              <dd className="text-tinta">{dinero(pedido.subtotal)}</dd>
            </div>
            {pedido.descuento > 0 && (
              <div className="flex justify-between text-exito">
                <dt>Descuento {pedido.codigoCupon && `(${pedido.codigoCupon})`}</dt>
                <dd>-{dinero(pedido.descuento)}</dd>
              </div>
            )}
            <div className="flex justify-between border-t border-borde pt-2 text-base font-bold text-tinta">
              <dt>Total</dt>
              <dd>{dinero(pedido.total)}</dd>
            </div>
          </dl>
        </section>

        <section className="mb-8 rounded-xl border border-borde bg-white p-5">
          <h2 className="mb-3 flex items-center gap-2 font-semibold text-tinta">
            <Truck size={16} /> Entrega
          </h2>
          <p className="text-sm text-texto-medio">{pedido.direccion}</p>
          {pedido.telefono && (
            <p className="mt-1 text-sm text-texto-suave">Telefono: {pedido.telefono}</p>
          )}
        </section>

        <div className="flex flex-wrap justify-center gap-3">
          <EnlaceBoton href="/productos" variante="primario">
            Seguir comprando
          </EnlaceBoton>
          <EnlaceBoton href="/mi-cuenta/pedidos" variante="secundario">
            Ver mis pedidos
          </EnlaceBoton>
        </div>

        <p className="mt-6 text-center text-xs text-texto-suave">
          Guarda este numero: es lo que necesitas para consultar el pedido.{' '}
          <Link href="/" className="underline">
            Volver al inicio
          </Link>
        </p>
      </div>
    </Contenedor>
  )
}
