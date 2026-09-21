import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Check, Inbox, MessageSquare, Package, Receipt, Truck, X } from 'lucide-react'
import { conversaciones as chat, ordenes as api } from '../api/recursos'
import type { EstadoOrden, OrdenDetalle } from '../api/tipos'
import { AvisoError } from '../componentes/Aviso'
import Dialogo from '../componentes/Dialogo'
import { fecha, soles } from '../componentes/Insignia'
import Paginacion from '../componentes/Paginacion'
import { useToast } from '../componentes/Toast'

const TONO: Record<EstadoOrden, string> = {
  PENDIENTE: 'ambar',
  PAGADA: 'azul',
  ENVIADA: 'cian',
  ENTREGADA: 'verde',
  CANCELADA: 'roja',
}

/** El camino normal de una orden. Cancelada queda fuera: es una salida, no un paso. */
const RECORRIDO: EstadoOrden[] = ['PENDIENTE', 'PAGADA', 'ENVIADA', 'ENTREGADA']

const ICONO_ESTADO: Record<EstadoOrden, typeof Check> = {
  PENDIENTE: Receipt,
  PAGADA: Check,
  ENVIADA: Truck,
  ENTREGADA: Package,
  CANCELADA: X,
}

export default function Ordenes() {
  const [parametros, setParametros] = useSearchParams()
  const estado = parametros.get('estado') ?? ''
  const [pagina, setPagina] = useState(1)
  const [abierta, setAbierta] = useState<number | null>(null)

  const consulta = useQuery({
    queryKey: ['ordenes', estado, pagina],
    queryFn: () => api.listar(estado, pagina),
  })

  function filtrar(nuevo: string) {
    setPagina(1)
    setParametros(nuevo ? { estado: nuevo } : {})
  }

  return (
    <>
      <div className="encabezado">
        <div>
          <h2>Órdenes</h2>
          <p>
            Las órdenes las crea el checkout de la tienda. Desde aquí se consultan y se mueven de
            estado; cancelar devuelve el stock.
          </p>
        </div>
      </div>

      <div className="barra-filtros">
        <button type="button" className={estado === '' ? 'primario' : ''} onClick={() => filtrar('')}>
          Todas
        </button>
        {RECORRIDO.concat('CANCELADA').map((valor) => (
          <button
            key={valor}
            type="button"
            className={estado === valor ? 'primario' : ''}
            onClick={() => filtrar(valor)}
          >
            {etiqueta(valor)}
          </button>
        ))}
      </div>

      <AvisoError error={consulta.error} />

      <div className="tarjeta plana">
        {consulta.isLoading ? (
          <p className="cargando">Cargando…</p>
        ) : consulta.data && consulta.data.items.length > 0 ? (
          <div className="envoltura-tabla">
            <table>
              <thead>
                <tr>
                  <th>Número</th>
                  <th>Cliente</th>
                  <th>Fecha</th>
                  <th className="numero">Uds.</th>
                  <th className="numero">Total</th>
                  <th>Estado</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {consulta.data.items.map((orden) => (
                  <tr key={orden.id}>
                    <td>
                      <code>{orden.numero}</code>
                    </td>
                    <td>
                      <div className="principal">{orden.nombreContacto}</div>
                      <div className="secundario">{orden.email}</div>
                    </td>
                    <td className="secundario">{fecha(orden.creadoEn)}</td>
                    <td className="numero">{orden.totalUnidades}</td>
                    <td className="numero principal">{soles(orden.total)}</td>
                    <td>
                      <span className={`insignia ${TONO[orden.estado]}`}>{orden.estadoEtiqueta}</span>
                    </td>
                    <td>
                      <button type="button" className="enlace" onClick={() => setAbierta(orden.id)}>
                        Ver
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="sin-datos">
            <Inbox size={30} />
            <p>No hay órdenes {estado ? `en estado ${etiqueta(estado as EstadoOrden).toLowerCase()}` : ''}</p>
            <small>Aparecerán aquí en cuanto la tienda registre una compra.</small>
          </div>
        )}
      </div>

      {consulta.data && (
        <Paginacion
          pagina={consulta.data.pagina}
          totalPaginas={consulta.data.totalPaginas}
          totalItems={consulta.data.totalItems}
          alCambiar={setPagina}
        />
      )}

      {abierta !== null && <DetalleOrden id={abierta} alCerrar={() => setAbierta(null)} />}
    </>
  )
}

function DetalleOrden({ id, alCerrar }: { id: number; alCerrar: () => void }) {
  const clienteConsultas = useQueryClient()
  const toast = useToast()
  const navegar = useNavigate()

  const consulta = useQuery({ queryKey: ['orden', id], queryFn: () => api.porId(id) })

  /**
   * Abre el chat con quien hizo la orden.
   *
   * <p>Si ya hay un hilo sobre esta orden se va a ese en lugar de crear otro:
   * dos conversaciones sobre la misma compra acaban con el cliente contando lo
   * mismo dos veces.
   */
  const abrirChat = useMutation({
    mutationFn: async () => {
      const orden = consulta.data
      if (!orden || orden.clienteId === null) {
        throw new Error('La orden no tiene cliente al que escribir.')
      }
      if (orden.conversacionId !== null) {
        return orden.conversacionId
      }
      const nueva = await chat.crear({
        clienteId: orden.clienteId,
        ordenId: orden.id,
        asunto: `Sobre la orden ${orden.numero}`,
        mensaje: null,
      })
      return nueva.conversacion.id
    },
    onSuccess: (idConversacion) => {
      void clienteConsultas.invalidateQueries({ queryKey: ['conversaciones'] })
      alCerrar()
      navegar(`/conversaciones?abrir=${idConversacion}`)
    },
    onError: (fallo) => toast.error(fallo, 'No se pudo abrir la conversación'),
  })

  const mover = useMutation({
    mutationFn: (estado: EstadoOrden) => api.cambiarEstado(id, estado),
    onSuccess: (orden) => {
      clienteConsultas.setQueryData(['orden', id], orden)
      void clienteConsultas.invalidateQueries({ queryKey: ['ordenes'] })
      void clienteConsultas.invalidateQueries({ queryKey: ['metricas'] })
      // Cancelar repone unidades, así que el catálogo que se vea después ya no
      // es el mismo que estaba en caché.
      if (orden.estado === 'CANCELADA') {
        void clienteConsultas.invalidateQueries({ queryKey: ['productos'] })
        toast.exito('Orden cancelada', 'El stock volvió al inventario')
      } else {
        toast.exito(`Orden ${orden.estadoEtiqueta.toLowerCase()}`, orden.numero)
      }
    },
    onError: (fallo) => toast.error(fallo),
  })

  const orden = consulta.data

  return (
    <Dialogo titulo={orden ? `Orden ${orden.numero}` : 'Orden'} alCerrar={alCerrar} ancho>
      {consulta.isLoading || !orden ? (
        <p className="cargando">Cargando…</p>
      ) : (
        <>
          <Recorrido orden={orden} />

          <div className="rejilla" style={{ margin: '18px 0' }}>
            <div>
              <label>Quién la hizo</label>
              <div className="principal">{orden.nombreContacto}</div>
              <div className="secundario">{orden.email}</div>
              {orden.telefono && <div className="secundario">{orden.telefono}</div>}
              {/* Sin cuenta no hay a quién escribir: el chat necesita un
                  cliente al que atar el hilo y su token de acceso. */}
              {orden.clienteId === null ? (
                <div className="secundario">Compró como invitado, sin cuenta.</div>
              ) : (
                <button
                  type="button"
                  className="enlace"
                  style={{ padding: '4px 0', marginTop: 4 }}
                  disabled={abrirChat.isPending}
                  onClick={() => abrirChat.mutate()}
                >
                  <MessageSquare size={14} />
                  {orden.conversacionId
                    ? 'Ver la conversación'
                    : abrirChat.isPending
                      ? 'Abriendo…'
                      : 'Escribir al cliente'}
                </button>
              )}
            </div>
            <div>
              <label>Entrega</label>
              <div>{orden.direccion}</div>
            </div>
          </div>

          <div className="envoltura-tabla" style={{ border: '1px solid var(--borde)', borderRadius: 'var(--radio-s)' }}>
            <table>
              <thead>
                <tr>
                  <th>Producto</th>
                  <th className="numero">Precio</th>
                  <th className="numero">Cant.</th>
                  <th className="numero">Total</th>
                </tr>
              </thead>
              <tbody>
                {orden.items.map((linea) => (
                  <tr key={linea.productoId}>
                    <td>
                      <div className="principal">{linea.nombreProducto}</div>
                      <div className="secundario">
                        <code>{linea.sku}</code>
                      </div>
                    </td>
                    <td className="numero">{soles(linea.precioUnitario)}</td>
                    <td className="numero">{linea.cantidad}</td>
                    <td className="numero">{soles(linea.totalLinea)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="totales" style={{ marginTop: 16, marginLeft: 'auto', maxWidth: 280 }}>
            <div>
              <span className="secundario">Subtotal</span>
              <span>{soles(orden.subtotal)}</span>
            </div>
            {orden.descuento > 0 && (
              <div>
                <span className="secundario">
                  Descuento {orden.codigoCupon && <code>{orden.codigoCupon}</code>}
                </span>
                <span style={{ color: 'var(--exito)' }}>−{soles(orden.descuento)}</span>
              </div>
            )}
            <div className="final">
              <span>Total</span>
              <span>{soles(orden.total)}</span>
            </div>
          </div>

          <p className="secundario" style={{ marginTop: 18, marginBottom: 0 }}>
            Creada el {fecha(orden.creadoEn)} · última actualización de {orden.actualizadoPor} el{' '}
            {fecha(orden.actualizadoEn)}
          </p>

          <div className="acciones" style={{ marginTop: 18, justifyContent: 'flex-end' }}>
            {orden.transicionesPermitidas.length === 0 ? (
              <span className="secundario">
                Una orden {orden.estadoEtiqueta.toLowerCase()} ya no cambia de estado.
              </span>
            ) : (
              orden.transicionesPermitidas.map((destino) => {
                const Icono = ICONO_ESTADO[destino]
                return (
                  <button
                    key={destino}
                    type="button"
                    className={destino === 'CANCELADA' ? 'peligro' : 'primario'}
                    disabled={mover.isPending}
                    onClick={() => mover.mutate(destino)}
                  >
                    <Icono size={15} />
                    {destino === 'CANCELADA' ? 'Cancelar' : `Marcar como ${etiqueta(destino).toLowerCase()}`}
                  </button>
                )
              })
            )}
          </div>
        </>
      )}
    </Dialogo>
  )
}

/** Dónde está la orden dentro del recorrido normal. */
function Recorrido({ orden }: { orden: OrdenDetalle }) {
  if (orden.estado === 'CANCELADA') {
    return (
      <div className="aviso error" style={{ marginBottom: 0 }}>
        <X size={17} />
        <div>
          Esta orden se canceló. El stock de sus líneas volvió al inventario.
        </div>
      </div>
    )
  }

  const actual = RECORRIDO.indexOf(orden.estado)
  return (
    <div className="linea-tiempo">
      {RECORRIDO.map((paso, indice) => {
        const Icono = ICONO_ESTADO[paso]
        const clase = indice < actual ? 'hecho' : indice === actual ? 'actual' : ''
        return (
          <span key={paso} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            {indice > 0 && <span style={{ color: 'var(--borde-fuerte)' }}>→</span>}
            <span className={`paso ${clase}`}>
              <Icono size={14} />
              {etiqueta(paso)}
            </span>
          </span>
        )
      })}
    </div>
  )
}

function etiqueta(estado: EstadoOrden): string {
  return {
    PENDIENTE: 'Pendiente',
    PAGADA: 'Pagada',
    ENVIADA: 'Enviada',
    ENTREGADA: 'Entregada',
    CANCELADA: 'Cancelada',
  }[estado]
}
