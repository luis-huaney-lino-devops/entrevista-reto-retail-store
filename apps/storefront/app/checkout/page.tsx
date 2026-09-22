'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { Loader2, ShieldCheck, ShoppingCart } from 'lucide-react'

import { Contenedor, Migas } from '@/componentes/disposicion/Seccion'
import { Boton, EnlaceBoton } from '@/componentes/ui/Boton'
import { CampoTexto } from '@/componentes/ui/Campo'
import { SinResultados } from '@/componentes/ui/Estados'
import { peticion } from '@/lib/api.cliente'
import { ErrorApi } from '@/lib/errores'
import { dinero } from '@/lib/formato'
import type { CrearPedido, Pedido } from '@/lib/tipos'
import { useAccionesCarrito, useCarrito } from '@/funcionalidades/carrito/ProveedorCarrito'
import { useSesion } from '@/funcionalidades/sesion/ProveedorSesion'

/**
 * Confirmar la compra.
 *
 * **Es cliente entera**, como el carrito: el `id` del carrito vive en
 * `localStorage` y el servidor no lo tiene, asi que no hay nada que renderizar
 * en servidor.
 *
 * Lo que se envia son datos de contacto y el id del carrito. **Ningun importe**
 * (RN-051): los recalcula el servidor desde los precios vigentes. El resumen de
 * la derecha es informativo; si el precio cambio desde que se agrego el
 * producto, manda lo que devuelva la API.
 *
 * **Comprar no exige cuenta** (RN-063). Si hay sesion, los campos llegan
 * rellenos; si no, se compra como invitado, que es el caso por defecto.
 */
export default function PaginaCheckout() {
  const router = useRouter()
  const { carrito, montado, estado } = useCarrito()
  const { vaciarTrasComprar } = useAccionesCarrito()
  const { cliente } = useSesion()

  const [nombreContacto, setNombreContacto] = useState('')
  const [email, setEmail] = useState('')
  const [telefono, setTelefono] = useState('')
  const [direccion, setDireccion] = useState('')

  const [errores, setErrores] = useState<Record<string, string>>({})
  const [errorGeneral, setErrorGeneral] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  // Si hay sesion, no hacer que reescriba lo que la cuenta ya sabe. No se
  // sobrescribe lo que ya haya tecleado: solo rellena lo que este vacio.
  useEffect(() => {
    if (!cliente) return
    setNombreContacto((actual) => actual || cliente.nombre || '')
    setEmail((actual) => actual || cliente.email || '')
  }, [cliente])

  const items = carrito?.items ?? []
  const vacio = montado && estado !== 'hidratando' && items.length === 0

  function validar(): boolean {
    const nuevos: Record<string, string> = {}
    if (!nombreContacto.trim()) nuevos.nombreContacto = 'Dinos a nombre de quien va el pedido.'
    if (!email.trim()) {
      // RN-056: el correo es obligatorio aunque se compre sin cuenta, porque
      // es la unica via para confirmar el pedido.
      nuevos.email = 'Necesitamos un correo para confirmarte el pedido.'
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      nuevos.email = 'Ese correo no parece valido.'
    }
    if (!direccion.trim()) nuevos.direccion = 'Dinos a donde lo enviamos.'
    setErrores(nuevos)
    return Object.keys(nuevos).length === 0
  }

  async function confirmar(evento: React.FormEvent) {
    evento.preventDefault()
    setErrorGeneral(null)
    if (!carrito || enviando) return
    if (!validar()) return

    setEnviando(true)
    try {
      const cuerpo: CrearPedido = {
        carritoId: carrito.id,
        nombreContacto: nombreContacto.trim(),
        email: email.trim(),
        telefono: telefono.trim() || undefined,
        direccion: direccion.trim(),
      }
      const pedido = await peticion<Pedido>('/ordenes', { metodo: 'POST', cuerpo })

      // El carrito del servidor quedo CONVERTIDO: hay que soltar el id local o
      // la siguiente visita intentaria seguir comprando sobre un carrito muerto.
      vaciarTrasComprar()

      // `replace` y no `push`: si pulsa "atras" desde la confirmacion no debe
      // volver a un checkout cuyo carrito ya se convirtio.
      router.replace(`/pedido/${pedido.numero}`)
    } catch (e) {
      setEnviando(false)
      setErrorGeneral(mensajeDeError(e))
    }
  }

  if (!montado || estado === 'hidratando') {
    return (
      <Contenedor className="pb-12">
        <div className="flex min-h-[40vh] items-center justify-center text-texto-medio">
          <Loader2 className="animate-spin" size={28} />
        </div>
      </Contenedor>
    )
  }

  if (vacio) {
    return (
      <Contenedor className="pb-12">
        <SinResultados
          icono={<ShoppingCart size={38} strokeWidth={1.5} />}
          titulo="No hay nada que confirmar"
          descripcion="Tu carrito esta vacio. Anade productos y vuelve a intentarlo."
          accion={
            <EnlaceBoton href="/productos" variante="primario">
              Ver el catalogo
            </EnlaceBoton>
          }
        />
      </Contenedor>
    )
  }

  return (
    <Contenedor className="pb-12">
      <Migas
        items={[
          { nombre: 'Inicio', href: '/' },
          { nombre: 'Mi carrito', href: '/carrito' },
          { nombre: 'Confirmar compra', href: '/checkout' },
        ]}
      />

      <h1 className="mb-6 font-marca text-2xl font-bold text-tinta sm:text-3xl">Confirmar compra</h1>

      <div className="grid gap-8 lg:grid-cols-[1fr_20rem]">
        <form onSubmit={confirmar} noValidate className="flex flex-col gap-5">
          {errorGeneral && (
            <p
              role="alert"
              className="rounded-lg border border-peligro bg-peligro-suave px-4 py-3 text-sm text-peligro"
            >
              {errorGeneral}
            </p>
          )}

          <fieldset className="flex flex-col gap-4" disabled={enviando}>
            <legend className="mb-1 text-sm font-semibold text-tinta">Datos de contacto</legend>

            <CampoTexto
              etiqueta="Nombre completo"
              value={nombreContacto}
              onChange={(e) => setNombreContacto(e.target.value)}
              error={errores.nombreContacto}
              autoComplete="name"
              maxLength={120}
              required
            />

            <CampoTexto
              etiqueta="Correo electronico"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              error={errores.email}
              ayuda="Aqui te enviamos la confirmacion del pedido."
              autoComplete="email"
              maxLength={160}
              required
            />

            <CampoTexto
              etiqueta="Telefono (opcional)"
              type="tel"
              value={telefono}
              onChange={(e) => setTelefono(e.target.value)}
              error={errores.telefono}
              autoComplete="tel"
              maxLength={20}
            />

            <CampoTexto
              etiqueta="Direccion de entrega"
              value={direccion}
              onChange={(e) => setDireccion(e.target.value)}
              error={errores.direccion}
              ayuda="Calle, numero, distrito y una referencia si ayuda."
              autoComplete="street-address"
              maxLength={300}
              required
            />
          </fieldset>

          {!cliente && (
            <p className="text-sm text-texto-medio">
              Estas comprando como invitado.{' '}
              <Link href="/acceso" className="font-semibold text-tinta underline">
                Entra a tu cuenta
              </Link>{' '}
              si quieres que el pedido quede en tu historial.
            </p>
          )}

          <div className="flex flex-wrap items-center gap-3">
            {/* Deshabilitado desde el primer clic. No basta ante un fallo de
                red -la peticion pudo llegar- y por eso un POST de pedido no se
                reintenta solo; la solucion robusta es una clave de
                idempotencia, anotada como pendiente en el contrato. */}
            <Boton type="submit" variante="primario" disabled={enviando}>
              {enviando ? (
                <>
                  <Loader2 className="animate-spin" size={16} /> Confirmando...
                </>
              ) : (
                <>Confirmar compra por {dinero(carrito?.total ?? 0)}</>
              )}
            </Boton>
            <EnlaceBoton href="/carrito" variante="secundario">
              Volver al carrito
            </EnlaceBoton>
          </div>

          <p className="flex items-center gap-2 text-xs text-texto-medio">
            <ShieldCheck size={14} />
            No pedimos datos de pago: este pedido se confirma y se coordina despues.
          </p>
        </form>

        <Resumen />
      </div>
    </Contenedor>
  )
}

/** El resumen es informativo: quien decide los importes es el servidor. */
function Resumen() {
  const { carrito } = useCarrito()
  if (!carrito) return null

  return (
    <aside className="h-fit rounded-xl border border-borde bg-white p-5">
      <h2 className="mb-4 font-semibold text-tinta">Tu pedido</h2>

      <ul className="mb-4 flex flex-col gap-3">
        {carrito.items.map((item) => (
          <li key={item.productoId} className="flex justify-between gap-3 text-sm">
            <span className="text-texto-medio">
              {item.nombre}
              <span className="text-texto-suave"> x{item.cantidad}</span>
            </span>
            <span className="shrink-0 font-medium text-tinta">{dinero(item.totalLinea)}</span>
          </li>
        ))}
      </ul>

      <dl className="flex flex-col gap-2 border-t border-borde pt-4 text-sm">
        <div className="flex justify-between">
          <dt className="text-texto-medio">Subtotal</dt>
          <dd className="text-tinta">{dinero(carrito.subtotal)}</dd>
        </div>
        {carrito.descuento > 0 && (
          <div className="flex justify-between text-exito">
            <dt>Descuento {carrito.cuponAplicado && `(${carrito.cuponAplicado})`}</dt>
            <dd>-{dinero(carrito.descuento)}</dd>
          </div>
        )}
        <div className="flex justify-between border-t border-borde pt-2 text-base font-bold text-tinta">
          <dt>Total</dt>
          <dd>{dinero(carrito.total)}</dd>
        </div>
      </dl>
    </aside>
  )
}

/**
 * Cada motivo de rechazo del checkout tiene su mensaje.
 *
 * Que pueda fallar por estas causas no es un defecto: es la consecuencia
 * directa de que el servidor revalide todo al confirmar en vez de fiarse de lo
 * que el carrito llevaba. Lo importante es que **ninguno** deja al comprador
 * delante de un formulario vacio.
 */
function mensajeDeError(e: unknown): string {
  if (!(e instanceof ErrorApi)) {
    return 'No pudimos confirmar el pedido. Intentalo de nuevo.'
  }
  switch (e.codigo) {
    case 'INSUFFICIENT_STOCK':
      return `${e.message} Revisa tu carrito y ajusta la cantidad.`
    case 'PRODUCT_INACTIVE':
      return `${e.message} Quitalo del carrito para continuar.`
    case 'CART_EMPTY':
      return 'Tu carrito esta vacio.'
    case 'CART_ALREADY_CONVERTED':
      return 'Este carrito ya se confirmo. Revisa tus pedidos.'
    case 'COUPON_EXHAUSTED':
      return 'El cupon agoto sus usos. Quitalo del carrito y vuelve a intentarlo.'
    case 'VALIDATION_ERROR':
      return 'Revisa los datos marcados.'
    default:
      return e.message || 'No pudimos confirmar el pedido. Intentalo de nuevo.'
  }
}
