import { useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft,
  CheckCircle2,
  FileText,
  Loader2,
  Lock,
  MessagesSquare,
  Paperclip,
  Receipt,
  Send,
  Unlock,
  X,
} from 'lucide-react'
import { ErrorApi } from '../api/cliente'
import { conversaciones as api } from '../api/recursos'
import type { Adjunto, ConversacionResumen, Mensaje } from '../api/tipos'
import { AvisoError } from '../componentes/Aviso'
import { useConfirmar } from '../componentes/Confirmar'
import { desdeAhora, fecha, hora } from '../componentes/Insignia'
import SinDatos from '../componentes/SinDatos'
import { useToast } from '../componentes/Toast'
import { useEvento, useTiempoReal } from '../tiemporeal/TiempoReal'

const FILTROS = [
  { valor: '', etiqueta: 'Todas' },
  { valor: 'ABIERTA', etiqueta: 'Abiertas' },
  { valor: 'CERRADA', etiqueta: 'Cerradas' },
]

/** Lo que admite el servidor. Filtrar aquí evita subir 20 MB para un 422. */
const TIPOS_ADMITIDOS = 'image/png,image/jpeg,image/webp,image/gif,application/pdf'
const TOPE_BYTES = 10 * 1024 * 1024

export default function Conversaciones() {
  const [parametros, setParametros] = useSearchParams()
  const [filtro, setFiltro] = useState('')
  const abierta = parametros.get('abrir')
  const seleccionada = abierta ? Number(abierta) : null

  const clienteConsultas = useQueryClient()
  const consulta = useQuery({
    queryKey: ['conversaciones', filtro],
    queryFn: () => api.listar(filtro || undefined),
  })

  // La bandeja se reordena sola cuando entra un mensaje: si hubiera que
  // recargar para ver un hilo nuevo, el chat en vivo lo sería a medias.
  const refrescarBandeja = () =>
    void clienteConsultas.invalidateQueries({ queryKey: ['conversaciones'] })
  useEvento('CONVERSACION_ACTUALIZADA', refrescarBandeja)
  useEvento('CONVERSACION_LEIDA', refrescarBandeja)

  function seleccionar(id: number | null) {
    // En la URL y no en un useState: así un enlace de notificación abre el hilo
    // directamente y el botón de atrás del navegador vuelve a la bandeja.
    if (id === null) {
      parametros.delete('abrir')
    } else {
      parametros.set('abrir', String(id))
    }
    setParametros(parametros, { replace: true })
  }

  const items = consulta.data?.items ?? []

  return (
    <>
      <div className="encabezado">
        <div>
          <h2>Conversaciones</h2>
          <p>Atención al cliente en vivo. Se puede adjuntar una imagen o un PDF.</p>
        </div>
      </div>

      <AvisoError error={consulta.error} />

      <div className={`chat ${seleccionada ? 'con-hilo' : ''}`}>
        <aside className="bandeja">
          <div className="pestanas">
            {FILTROS.map((opcion) => (
              <button
                key={opcion.valor}
                type="button"
                className={filtro === opcion.valor ? 'activa' : ''}
                onClick={() => setFiltro(opcion.valor)}
              >
                {opcion.etiqueta}
              </button>
            ))}
          </div>

          <div className="lista-bandeja">
            {consulta.isLoading ? (
              <p className="cargando">Cargando…</p>
            ) : items.length === 0 ? (
              <SinDatos
                icono={MessagesSquare}
                titulo="No hay conversaciones"
                detalle="Aparecerán aquí en cuanto un cliente escriba."
              />
            ) : (
              items.map((hilo) => (
                <FilaBandeja
                  key={hilo.id}
                  hilo={hilo}
                  activa={hilo.id === seleccionada}
                  alAbrir={() => seleccionar(hilo.id)}
                />
              ))
            )}
          </div>
        </aside>

        <section className="hilo">
          {seleccionada ? (
            <Hilo id={seleccionada} alVolver={() => seleccionar(null)} />
          ) : (
            <SinDatos
              icono={MessagesSquare}
              titulo="Elige una conversación"
              detalle="Los mensajes nuevos llegan solos, sin recargar."
            />
          )}
        </section>
      </div>
    </>
  )
}

function FilaBandeja({
  hilo,
  activa,
  alAbrir,
}: {
  hilo: ConversacionResumen
  activa: boolean
  alAbrir: () => void
}) {
  return (
    <button type="button" className={`fila-bandeja ${activa ? 'activa' : ''}`} onClick={alAbrir}>
      <span className="cabecera">
        <span className="nombre">{hilo.clienteNombre}</span>
        <span className="cuando">{desdeAhora(hilo.ultimoMensajeEn ?? hilo.creadoEn)}</span>
      </span>
      <span className="asunto">{hilo.asunto}</span>
      <span className="pie">
        {hilo.ordenNumero && (
          <span className="insignia gris sin-punto">
            <Receipt size={10} />
            {hilo.ordenNumero}
          </span>
        )}
        {hilo.estado === 'CERRADA' && <span className="insignia gris">Cerrada</span>}
        {hilo.noLeidos > 0 && <span className="globo">{hilo.noLeidos}</span>}
      </span>
    </button>
  )
}

function Hilo({ id, alVolver }: { id: number; alVolver: () => void }) {
  const clienteConsultas = useQueryClient()
  const toast = useToast()
  const confirmar = useConfirmar()
  const { conectado, enviar } = useTiempoReal()

  const [borrador, setBorrador] = useState('')
  const [adjuntos, setAdjuntos] = useState<Adjunto[]>([])
  const [subiendo, setSubiendo] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [enVivo, setEnVivo] = useState<Mensaje[]>([])
  const fondo = useRef<HTMLDivElement>(null)
  const selector = useRef<HTMLInputElement>(null)

  const consulta = useQuery({ queryKey: ['conversacion', id], queryFn: () => api.abrir(id) })

  // Suscribirse al hilo: sin esto el servidor no sabe a quién mandarle sus
  // mensajes y el panel solo vería lo que él mismo escribe.
  useEffect(() => {
    if (conectado) {
      enviar({ tipo: 'SUSCRIBIR', conversacionId: id })
    }
  }, [conectado, enviar, id])

  // Al cambiar de hilo hay que tirar los mensajes en vivo del anterior, o
  // aparecerían colgando debajo de una conversación que no es la suya.
  useEffect(() => {
    setEnVivo([])
    setBorrador('')
    setAdjuntos([])
    setError(null)
  }, [id])

  useEvento('MENSAJE', (evento) => {
    if (evento.conversacionId !== id) {
      return
    }
    const mensaje = evento.mensaje as Mensaje
    setEnVivo((previos) => (previos.some((m) => m.id === mensaje.id) ? previos : [...previos, mensaje]))
  })

  const mensajes = useMemo(() => {
    const cargados = consulta.data?.mensajes ?? []
    const conocidos = new Set(cargados.map((m) => m.id))
    return [...cargados, ...enVivo.filter((m) => !conocidos.has(m.id))]
  }, [consulta.data, enVivo])

  // Bajar del todo al llegar algo nuevo. Sin esto hay que arrastrar la barra
  // cada vez que el cliente escribe, que es justo cuando menos apetece.
  useEffect(() => {
    fondo.current?.scrollTo({ top: fondo.current.scrollHeight, behavior: 'smooth' })
  }, [mensajes.length])

  const responder = useMutation({
    mutationFn: () =>
      api.responder(id, {
        cuerpo: borrador.trim() || null,
        adjuntoIds: adjuntos.map((a) => a.id),
      }),
    onSuccess: () => {
      setBorrador('')
      setAdjuntos([])
      setError(null)
      // El mensaje vuelve por el WebSocket con su id ya asignado; invalidar la
      // bandeja es para que el hilo suba al principio de la lista.
      void clienteConsultas.invalidateQueries({ queryKey: ['conversaciones'] })
    },
    onError: (fallo) => {
      setError(fallo)
      toast.error(fallo, 'No se pudo enviar')
    },
  })

  const cambiarEstado = useMutation({
    mutationFn: (abrir: boolean) => api.cambiarEstado(id, abrir),
    onSuccess: (detalle) => {
      void clienteConsultas.invalidateQueries({ queryKey: ['conversacion', id] })
      void clienteConsultas.invalidateQueries({ queryKey: ['conversaciones'] })
      toast.exito(detalle.conversacion.estado === 'ABIERTA' ? 'Conversación reabierta' : 'Conversación cerrada')
    },
    onError: (fallo) => toast.error(fallo),
  })

  async function adjuntar(archivos: FileList | null) {
    if (!archivos || archivos.length === 0) {
      return
    }
    setSubiendo(true)
    setError(null)
    try {
      for (const archivo of Array.from(archivos)) {
        if (archivo.size > TOPE_BYTES) {
          toast.error(`«${archivo.name}» pasa de 10 MB.`, 'Archivo demasiado grande')
          continue
        }
        const subido = await api.subirAdjunto(archivo)
        setAdjuntos((previos) => [...previos, subido])
      }
    } catch (fallo) {
      setError(fallo)
      // El servidor dice qué construcción del PDF lo delató: repetirlo aquí es
      // lo que convierte un rechazo en algo que se puede corregir.
      toast.error(fallo, fallo instanceof ErrorApi ? undefined : 'No se pudo subir el archivo')
    } finally {
      setSubiendo(false)
      if (selector.current) selector.current.value = ''
    }
  }

  async function alternarEstado() {
    const cerrada = consulta.data?.conversacion.estado === 'CERRADA'
    if (!cerrada) {
      const aceptado = await confirmar({
        titulo: '¿Cerrar la conversación?',
        etiquetaAccion: 'Cerrar',
        mensaje: (
          <>
            <p>El cliente ya no podrá escribir en este hilo.</p>
            <p className="secundario">Se puede reabrir en cualquier momento; nada se borra.</p>
          </>
        ),
      })
      if (!aceptado) return
    }
    cambiarEstado.mutate(cerrada)
  }

  if (consulta.isLoading) {
    return <p className="cargando">Cargando…</p>
  }
  if (consulta.error || !consulta.data) {
    return <AvisoError error={consulta.error} />
  }

  const hilo = consulta.data.conversacion
  const cerrada = hilo.estado === 'CERRADA'
  const puedeEnviar = (borrador.trim().length > 0 || adjuntos.length > 0) && !responder.isPending

  return (
    <>
      <header className="cabecera-hilo">
        <button type="button" className="icono solo-movil" aria-label="Volver" onClick={alVolver}>
          <ArrowLeft size={17} />
        </button>
        <span className="avatar">{iniciales(hilo.clienteNombre)}</span>
        <div className="quien">
          <h3>{hilo.clienteNombre}</h3>
          <p>
            {hilo.asunto}
            {hilo.ordenNumero && <> · orden {hilo.ordenNumero}</>}
          </p>
        </div>
        <div className="acciones">
          <span className={`punto-conexion ${conectado ? 'vivo' : ''}`} title={conectado ? 'En vivo' : 'Reconectando…'} />
          <button type="button" disabled={cambiarEstado.isPending} onClick={() => void alternarEstado()}>
            {cerrada ? <Unlock size={15} /> : <Lock size={15} />}
            {cerrada ? 'Reabrir' : 'Cerrar'}
          </button>
        </div>
      </header>

      <div className="mensajes" ref={fondo}>
        {mensajes.length === 0 ? (
          <p className="secundario centrado">Todavía no hay mensajes. Escribe el primero.</p>
        ) : (
          mensajes.map((mensaje, indice) => (
            <Burbuja
              key={mensaje.id}
              mensaje={mensaje}
              abreTanda={mensajes[indice - 1]?.autor !== mensaje.autor}
              // El día solo se repite cuando cambia: una fecha sobre cada
              // mensaje convierte el hilo en una lista de fechas.
              dia={diaDistinto(mensajes[indice - 1], mensaje) ? fecha(mensaje.enviadoEn) : null}
            />
          ))
        )}
      </div>

      <footer className="redactor">
        <AvisoError error={error} />

        {adjuntos.length > 0 && (
          <div className="adjuntos-pendientes">
            {adjuntos.map((adjunto) => (
              <span key={adjunto.id} className="chip-adjunto">
                {adjunto.esImagen ? (
                  <img src={adjunto.url} alt={adjunto.nombre} />
                ) : (
                  <FileText size={14} />
                )}
                <span className="nombre">{adjunto.nombre}</span>
                <button
                  type="button"
                  aria-label={`Quitar ${adjunto.nombre}`}
                  onClick={() => setAdjuntos((previos) => previos.filter((a) => a.id !== adjunto.id))}
                >
                  <X size={13} />
                </button>
              </span>
            ))}
          </div>
        )}

        {cerrada ? (
          <p className="secundario centrado">
            Esta conversación está cerrada. Reábrela para poder responder.
          </p>
        ) : (
          <div className="caja-redactor">
            <input
              ref={selector}
              type="file"
              accept={TIPOS_ADMITIDOS}
              multiple
              hidden
              onChange={(e) => void adjuntar(e.target.files)}
            />
            <button
              type="button"
              className="icono"
              aria-label="Adjuntar imagen o PDF"
              title="Solo imagen o PDF · máximo 10 MB"
              disabled={subiendo}
              onClick={() => selector.current?.click()}
            >
              {subiendo ? <Loader2 size={17} className="girando" /> : <Paperclip size={17} />}
            </button>
            <textarea
              rows={1}
              placeholder="Escribe tu respuesta…"
              value={borrador}
              onChange={(e) => setBorrador(e.target.value)}
              onKeyDown={(e) => {
                // Intro envía, Mayús+Intro hace párrafo: lo que ya hace
                // cualquier chat, y lo que la gente intenta sin pensarlo.
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault()
                  if (puedeEnviar) responder.mutate()
                }
              }}
            />
            <button
              type="button"
              className="primario"
              disabled={!puedeEnviar}
              onClick={() => responder.mutate()}
            >
              <Send size={15} />
              {responder.isPending ? 'Enviando…' : 'Enviar'}
            </button>
          </div>
        )}
      </footer>
    </>
  )
}

function Burbuja({
  mensaje,
  dia,
  abreTanda,
}: {
  mensaje: Mensaje
  dia: string | null
  abreTanda: boolean
}) {
  const mio = mensaje.autor === 'ADMINISTRADOR'
  const conCola = abreTanda || dia !== null
  return (
    <>
      {dia && <div className="separador-dia">{dia}</div>}
      <div className={`burbuja ${mio ? 'mia' : 'suya'} ${conCola ? 'con-cola' : ''}`}>
        {!mio && conCola && <span className="autor">{mensaje.autorNombre}</span>}
        {mensaje.cuerpo && <p>{mensaje.cuerpo}</p>}

        {mensaje.adjuntos.length > 0 && (
          <div className="adjuntos">
            {mensaje.adjuntos.map((adjunto) =>
              adjunto.esImagen ? (
                <a key={adjunto.id} href={adjunto.url} target="_blank" rel="noreferrer">
                  <img src={adjunto.url} alt={adjunto.nombre} />
                </a>
              ) : (
                // target y rel explícitos: el servidor lo sirve con
                // Content-Disposition: attachment, así que esto descarga el PDF
                // en vez de abrirlo dentro del panel.
                <a
                  key={adjunto.id}
                  className="adjunto-pdf"
                  href={adjunto.url}
                  target="_blank"
                  rel="noreferrer"
                >
                  <FileText size={16} />
                  <span>
                    <span className="nombre">{adjunto.nombre}</span>
                    <span className="peso">{pesoLegible(adjunto.bytes)}</span>
                  </span>
                </a>
              ),
            )}
          </div>
        )}

        <span className="momento">
          {hora(mensaje.enviadoEn)}
          {mio && mensaje.leidoEn && <CheckCircle2 size={12} aria-label="Leído" />}
        </span>
      </div>
    </>
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

function diaDistinto(anterior: Mensaje | undefined, actual: Mensaje): boolean {
  if (!anterior) return true
  return new Date(anterior.enviadoEn).toDateString() !== new Date(actual.enviadoEn).toDateString()
}

function pesoLegible(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} kB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
