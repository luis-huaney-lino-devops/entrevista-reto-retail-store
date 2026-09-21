import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  AlertTriangle,
  Bell,
  CheckCheck,
  MessageSquare,
  PackageX,
  Receipt,
  XCircle,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { notificaciones as api } from '../api/recursos'
import type { Notificacion, TipoNotificacion } from '../api/tipos'
import { useEvento, useTiempoReal } from '../tiemporeal/TiempoReal'
import { desdeAhora } from './Insignia'

const ICONOS: Record<TipoNotificacion, LucideIcon> = {
  STOCK_AGOTADO: PackageX,
  STOCK_BAJO: AlertTriangle,
  ORDEN_NUEVA: Receipt,
  ORDEN_CANCELADA: XCircle,
  MENSAJE_CLIENTE: MessageSquare,
}

/**
 * La campana del panel.
 *
 * <p>El contador llega empujado por el WebSocket, no preguntando cada pocos
 * segundos: un sondeo con diez administradores conectados son diez consultas
 * por minuto para decir casi siempre que no hay nada. La consulta inicial sí
 * se hace, porque al abrir el panel ya puede haber avisos de antes.
 */
export default function Campana() {
  const clienteConsultas = useQueryClient()
  const navegar = useNavigate()
  const { conectado } = useTiempoReal()
  const [abierta, setAbierta] = useState(false)
  const caja = useRef<HTMLDivElement>(null)

  const consulta = useQuery({ queryKey: ['notificaciones'], queryFn: api.bandeja })

  // Cualquier evento de notificación invalida la bandeja y la vuelve a pedir.
  // Insertar el evento en la caché a mano sería más rápido, pero duplicaría en
  // el cliente las reglas de deduplicación que ya tiene el servidor.
  const refrescar = () => void clienteConsultas.invalidateQueries({ queryKey: ['notificaciones'] })
  useEvento('NOTIFICACION', refrescar)
  useEvento('NOTIFICACIONES_ACTUALIZADAS', refrescar)

  const marcarLeida = useMutation({
    mutationFn: (id: number) => api.marcarLeida(id),
    onSuccess: refrescar,
  })

  const marcarTodas = useMutation({
    mutationFn: () => api.marcarTodasLeidas(),
    onSuccess: refrescar,
  })

  useEffect(() => {
    if (!abierta) {
      return
    }
    const fuera = (evento: MouseEvent) => {
      if (!caja.current?.contains(evento.target as Node)) {
        setAbierta(false)
      }
    }
    const escape = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') setAbierta(false)
    }
    document.addEventListener('mousedown', fuera)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('mousedown', fuera)
      document.removeEventListener('keydown', escape)
    }
  }, [abierta])

  const pendientes = consulta.data?.pendientes ?? 0
  const items = consulta.data?.items ?? []

  function abrirNotificacion(notificacion: Notificacion) {
    marcarLeida.mutate(notificacion.id)
    setAbierta(false)
    if (notificacion.enlace) {
      navegar(notificacion.enlace)
    }
  }

  return (
    <div className="campana" ref={caja}>
      <button
        type="button"
        className="boton-campana"
        aria-label={pendientes > 0 ? `Notificaciones: ${pendientes} sin leer` : 'Notificaciones'}
        aria-expanded={abierta}
        onClick={() => setAbierta((previo) => !previo)}
      >
        <Bell size={18} />
        {pendientes > 0 && <span className="contador">{pendientes > 99 ? '99+' : pendientes}</span>}
      </button>

      {abierta && (
        <div className="panel-campana" role="dialog" aria-label="Notificaciones">
          <header>
            <h4>Notificaciones</h4>
            {pendientes > 0 && (
              <button
                type="button"
                className="enlace"
                disabled={marcarTodas.isPending}
                onClick={() => marcarTodas.mutate()}
              >
                <CheckCheck size={14} />
                Marcar todas
              </button>
            )}
          </header>

          <div className="lista-campana">
            {items.length === 0 ? (
              <p className="vacio-campana">
                {consulta.isLoading ? 'Cargando…' : 'Nada pendiente. Todo al día.'}
              </p>
            ) : (
              items.map((notificacion) => {
                const Icono = ICONOS[notificacion.tipo] ?? Bell
                return (
                  <button
                    key={notificacion.id}
                    type="button"
                    className={`fila-campana ${notificacion.severidad.toLowerCase()}`}
                    onClick={() => abrirNotificacion(notificacion)}
                  >
                    <span className="icono">
                      <Icono size={15} />
                    </span>
                    <span className="texto">
                      <span className="titulo">{notificacion.titulo}</span>
                      {notificacion.detalle && <span className="detalle">{notificacion.detalle}</span>}
                      <span className="cuando">{desdeAhora(notificacion.creadoEn)}</span>
                    </span>
                  </button>
                )
              })
            )}
          </div>

          {/* Si el canal se cae, el contador deja de moverse. Decirlo evita que
              alguien confíe en una campana en cero que en realidad está muda. */}
          {!conectado && (
            <footer className="pie-campana desconectado">Sin conexión en vivo: reintentando…</footer>
          )}
        </div>
      )}
    </div>
  )
}
