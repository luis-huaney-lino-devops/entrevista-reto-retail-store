import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Ban,
  CheckCircle2,
  Eye,
  Mail,
  MessageSquarePlus,
  Phone,
  Search,
  ShieldCheck,
  Users,
} from 'lucide-react'
import { clientes as api, conversaciones as chat } from '../api/recursos'
import type { Cliente } from '../api/tipos'
import { AvisoError } from '../componentes/Aviso'
import BotonIcono from '../componentes/BotonIcono'
import { useConfirmar } from '../componentes/Confirmar'
import Dialogo from '../componentes/Dialogo'
import { Disponibilidad, fecha, fechaCorta, soles } from '../componentes/Insignia'
import Paginacion from '../componentes/Paginacion'
import SinDatos from '../componentes/SinDatos'
import { useToast } from '../componentes/Toast'

export default function Clientes() {
  const clienteConsultas = useQueryClient()
  const toast = useToast()
  const confirmar = useConfirmar()
  const [busqueda, setBusqueda] = useState('')
  const [pagina, setPagina] = useState(1)
  const [viendo, setViendo] = useState<Cliente | null>(null)

  const consulta = useQuery({
    queryKey: ['clientes', busqueda, pagina],
    queryFn: () => api.listar(busqueda || undefined, pagina),
  })

  const cambiarEstado = useMutation({
    mutationFn: ({ id, activo }: { id: number; activo: boolean }) => api.cambiarEstado(id, activo),
    onSuccess: (cliente) => {
      void clienteConsultas.invalidateQueries({ queryKey: ['clientes'] })
      toast.exito(cliente.activo ? 'Cliente reactivado' : 'Cliente bloqueado', cliente.nombre)
    },
    onError: (fallo) => toast.error(fallo),
  })

  /**
   * Bloquear una cuenta impide entrar, pero no borra nada: las órdenes que ya
   * hizo siguen ahí. Por eso se confirma —no es reversible sin que el cliente
   * se entere— pero no se pinta como una eliminación.
   */
  async function alternar(cliente: Cliente) {
    if (cliente.activo) {
      const aceptado = await confirmar({
        titulo: '¿Bloquear el acceso?',
        etiquetaAccion: 'Bloquear',
        peligro: true,
        mensaje: (
          <>
            <p>
              <strong>{cliente.nombre}</strong> no podrá volver a entrar con su cuenta.
            </p>
            <p className="secundario">
              Sus {cliente.ordenes} {cliente.ordenes === 1 ? 'orden' : 'órdenes'} y sus conversaciones
              se conservan. Se puede reactivar cuando quieras.
            </p>
          </>
        ),
      })
      if (!aceptado) return
    }
    cambiarEstado.mutate({ id: cliente.id, activo: !cliente.activo })
  }

  function buscar(texto: string) {
    setBusqueda(texto)
    // Cambiar el filtro sin volver a la primera página deja al usuario mirando
    // una página 4 que ya no existe, y una tabla vacía parece un error.
    setPagina(1)
  }

  return (
    <>
      <div className="encabezado">
        <div>
          <h2>Clientes</h2>
          <p>Quién compra, cuánto lleva gastado y cómo contactarle.</p>
        </div>
      </div>

      <div className="barra-filtros">
        <div className="busqueda">
          <Search size={15} />
          <input
            type="search"
            placeholder="Buscar por nombre, correo o teléfono…"
            value={busqueda}
            onChange={(e) => buscar(e.target.value)}
          />
        </div>
      </div>

      <AvisoError error={consulta.error} />

      <div className="tarjeta plana">
        {consulta.isLoading ? (
          <p className="cargando">Cargando…</p>
        ) : consulta.data && consulta.data.items.length > 0 ? (
          <>
            <div className="envoltura-tabla">
              <table>
                <thead>
                  <tr>
                    <th>Cliente</th>
                    <th>Contacto</th>
                    <th className="numero">Órdenes</th>
                    <th className="numero">Total comprado</th>
                    <th>Último acceso</th>
                    <th>Estado</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {consulta.data.items.map((cliente) => (
                    <tr key={cliente.id}>
                      <td>
                        <div className="celda-cliente">
                          <span className="avatar pequeno">{iniciales(cliente.nombre)}</span>
                          <div>
                            <div className="principal">{cliente.nombre}</div>
                            <div className="secundario">Desde {fechaCorta(cliente.creadoEn)}</div>
                          </div>
                        </div>
                      </td>
                      <td>
                        <div className="contacto">
                          <span>
                            <Mail size={13} />
                            {cliente.email}
                            {cliente.emailVerificado && (
                              <ShieldCheck size={13} className="verificado" aria-label="Correo verificado" />
                            )}
                          </span>
                          {cliente.telefono && (
                            <span className="secundario">
                              <Phone size={13} />
                              {cliente.telefono}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="numero">{cliente.ordenes}</td>
                      <td className="numero principal">{soles(cliente.totalComprado)}</td>
                      <td className="secundario">
                        {/* Sin contraseña compró como invitado: no tiene sesión que
                            registrar, y un «—» a secas parecería un dato perdido. */}
                        {cliente.tieneContrasena ? fecha(cliente.ultimoAccesoEn) : 'Compró como invitado'}
                      </td>
                      <td>
                        <Disponibilidad activa={cliente.activo} />
                      </td>
                      <td className="acciones">
                        <BotonIcono icono={Eye} etiqueta="Ver ficha" alPulsar={() => setViendo(cliente)} />
                        <BotonIcono
                          icono={cliente.activo ? Ban : CheckCircle2}
                          etiqueta={cliente.activo ? 'Bloquear acceso' : 'Reactivar'}
                          peligro={cliente.activo}
                          desactivado={cambiarEstado.isPending}
                          alPulsar={() => void alternar(cliente)}
                        />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Paginacion
              pagina={consulta.data.pagina}
              totalPaginas={consulta.data.totalPaginas}
              totalItems={consulta.data.totalItems}
              alCambiar={setPagina}
            />
          </>
        ) : (
          <SinDatos
            icono={Users}
            titulo={busqueda ? 'Ningún cliente coincide' : 'Todavía no hay clientes'}
            detalle={
              busqueda
                ? 'Prueba con otro término.'
                : 'Aparecerán aquí en cuanto alguien haga su primera compra.'
            }
          />
        )}
      </div>

      {viendo && <FichaCliente cliente={viendo} alCerrar={() => setViendo(null)} />}
    </>
  )
}

function FichaCliente({ cliente, alCerrar }: { cliente: Cliente; alCerrar: () => void }) {
  const navegar = useNavigate()
  const toast = useToast()
  const clienteConsultas = useQueryClient()

  const consulta = useQuery({
    queryKey: ['cliente', cliente.id],
    queryFn: () => api.porId(cliente.id),
  })

  const abrirHilo = useMutation({
    mutationFn: () =>
      chat.crear({
        clienteId: cliente.id,
        ordenId: null,
        asunto: `Contacto con ${cliente.nombre}`,
        mensaje: null,
      }),
    onSuccess: (detalle) => {
      void clienteConsultas.invalidateQueries({ queryKey: ['conversaciones'] })
      toast.exito('Conversación abierta', cliente.nombre)
      navegar(`/conversaciones?abrir=${detalle.conversacion.id}`)
    },
    onError: (fallo) => toast.error(fallo, 'No se pudo abrir la conversación'),
  })

  return (
    <Dialogo
      titulo={cliente.nombre}
      alCerrar={alCerrar}
      ancho
      pie={
        <>
          <button type="button" onClick={alCerrar}>
            Cerrar
          </button>
          <button
            type="button"
            className="primario"
            disabled={abrirHilo.isPending}
            onClick={() => abrirHilo.mutate()}
          >
            <MessageSquarePlus size={15} />
            {abrirHilo.isPending ? 'Abriendo…' : 'Escribirle'}
          </button>
        </>
      }
    >
      <AvisoError error={consulta.error} />

      <div className="resumen-cliente">
        <div>
          <span className="etiqueta">Correo</span>
          <span className="valor">{cliente.email}</span>
        </div>
        <div>
          <span className="etiqueta">Teléfono</span>
          <span className="valor">{cliente.telefono ?? '—'}</span>
        </div>
        <div>
          <span className="etiqueta">Órdenes</span>
          <span className="valor">{cliente.ordenes}</span>
        </div>
        <div>
          <span className="etiqueta">Total comprado</span>
          <span className="valor">{soles(cliente.totalComprado)}</span>
        </div>
      </div>

      <h4 className="subtitulo">Órdenes</h4>
      {consulta.isLoading ? (
        <p className="cargando">Cargando…</p>
      ) : consulta.data && consulta.data.ordenes.length > 0 ? (
        <div className="envoltura-tabla">
          <table className="compacta">
            <thead>
              <tr>
                <th>Número</th>
                <th>Fecha</th>
                <th>Estado</th>
                <th className="numero">Total</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {consulta.data.ordenes.map((orden) => (
                <tr key={orden.id}>
                  <td className="principal">
                    <code>{orden.numero}</code>
                  </td>
                  <td className="secundario">{fechaCorta(orden.creadoEn)}</td>
                  <td>
                    <span className="insignia gris">{orden.estadoEtiqueta}</span>
                  </td>
                  <td className="numero">{soles(orden.total)}</td>
                  <td className="acciones">
                    <BotonIcono
                      icono={Eye}
                      etiqueta="Ver la orden"
                      alPulsar={() => {
                        alCerrar()
                        navegar(`/ordenes?abrir=${orden.id}`)
                      }}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="secundario">Todavía no ha comprado nada.</p>
      )}

      {consulta.data && consulta.data.conversaciones.length > 0 && (
        <>
          <h4 className="subtitulo">Conversaciones</h4>
          <ul className="lista-hilos">
            {consulta.data.conversaciones.map((hilo) => (
              <li key={hilo.id}>
                <button
                  type="button"
                  className="enlace"
                  onClick={() => {
                    alCerrar()
                    navegar(`/conversaciones?abrir=${hilo.id}`)
                  }}
                >
                  {hilo.asunto}
                </button>
                <span className={`insignia ${hilo.estado === 'ABIERTA' ? 'verde' : 'gris'}`}>
                  {hilo.estado === 'ABIERTA' ? 'Abierta' : 'Cerrada'}
                </span>
                {hilo.noLeidos > 0 && <span className="insignia ambar">{hilo.noLeidos} sin leer</span>}
              </li>
            ))}
          </ul>
        </>
      )}
    </Dialogo>
  )
}

function iniciales(nombre: string): string {
  return nombre
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((palabra) => palabra[0]?.toUpperCase() ?? '')
    .join('')
}
