import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Eye, EyeOff, ImageOff, Pencil, Plus, Search, Tag, Trash2 } from 'lucide-react'
import { ErrorApi } from '../api/cliente'
import { marcas as api } from '../api/recursos'
import type { Archivo, Marca } from '../api/tipos'
import { AvisoError } from '../componentes/Aviso'
import BotonIcono from '../componentes/BotonIcono'
import Campo from '../componentes/Campo'
import CampoImagen from '../componentes/CampoImagen'
import { confirmacionDeBorrado, useConfirmar } from '../componentes/Confirmar'
import Dialogo from '../componentes/Dialogo'
import { Disponibilidad } from '../componentes/Insignia'
import SinDatos from '../componentes/SinDatos'
import { useToast } from '../componentes/Toast'

export default function Marcas() {
  const clienteConsultas = useQueryClient()
  const toast = useToast()
  const confirmar = useConfirmar()
  const [busqueda, setBusqueda] = useState('')
  const [editando, setEditando] = useState<Marca | null>(null)
  const [creando, setCreando] = useState(false)

  const consulta = useQuery({
    queryKey: ['marcas', busqueda],
    queryFn: () => api.listar(busqueda || undefined),
  })

  const cambiarEstado = useMutation({
    mutationFn: ({ id, activo }: { id: number; activo: boolean }) => api.cambiarEstado(id, activo),
    onSuccess: (marca) => {
      void clienteConsultas.invalidateQueries({ queryKey: ['marcas'] })
      toast.exito(marca.activa ? 'Marca activada' : 'Marca desactivada', marca.nombre)
    },
    onError: (fallo) => toast.error(fallo),
  })

  const eliminar = useMutation({
    mutationFn: (id: number) => api.eliminar(id),
    onSuccess: () => {
      void clienteConsultas.invalidateQueries({ queryKey: ['marcas'] })
      void clienteConsultas.invalidateQueries({ queryKey: ['papelera'] })
      toast.exito('Marca eliminada', 'Queda en la papelera por si hay que recuperarla.')
    },
    onError: (fallo) => toast.error(fallo, 'No se pudo eliminar'),
  })

  async function pedirBorrado(marca: Marca) {
    if (await confirmar(confirmacionDeBorrado('la marca', marca.nombre))) {
      eliminar.mutate(marca.id)
    }
  }

  return (
    <>
      <div className="encabezado">
        <div>
          <h2>Marcas</h2>
          <p>Un producto puede no tener marca: muchos artículos de tienda no la tienen.</p>
        </div>
        <button type="button" className="primario" onClick={() => setCreando(true)}>
          <Plus size={15} />
          Nueva marca
        </button>
      </div>

      <div className="barra-filtros">
        <div className="busqueda">
          <Search size={15} />
          <input
            type="search"
            placeholder="Buscar por nombre…"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
          />
        </div>
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
                  <th style={{ width: 62 }}>Logo</th>
                  <th>Nombre</th>
                  <th>Slug</th>
                  <th>Descripción</th>
                  <th>Estado</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {consulta.data.items.map((marca) => (
                  <tr key={marca.id}>
                    <td>
                      {marca.logo ? (
                        <img className="miniatura" src={marca.logo.urls.MINIATURA} alt={marca.logo.textoAlt} />
                      ) : (
                        <div className="miniatura vacia">
                          <ImageOff size={15} />
                        </div>
                      )}
                    </td>
                    <td className="principal">{marca.nombre}</td>
                    <td>
                      <code>{marca.slug}</code>
                    </td>
                    <td className="secundario">{marca.descripcion ?? '—'}</td>
                    <td>
                      <Disponibilidad activa={marca.activa} />
                    </td>
                    <td className="acciones">
                      <BotonIcono icono={Pencil} etiqueta="Editar" alPulsar={() => setEditando(marca)} />
                      <BotonIcono
                        icono={marca.activa ? EyeOff : Eye}
                        etiqueta={marca.activa ? 'Desactivar' : 'Activar'}
                        desactivado={cambiarEstado.isPending}
                        alPulsar={() => cambiarEstado.mutate({ id: marca.id, activo: !marca.activa })}
                      />
                      <BotonIcono
                        icono={Trash2}
                        etiqueta="Eliminar"
                        peligro
                        desactivado={eliminar.isPending}
                        alPulsar={() => void pedirBorrado(marca)}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <SinDatos
            icono={Tag}
            titulo={busqueda ? 'Ninguna marca coincide' : 'Todavía no hay marcas'}
            detalle={busqueda ? 'Prueba con otro término.' : 'Crea la primera para poder asignarla a los productos.'}
          />
        )}
      </div>

      {(creando || editando) && (
        <FormularioMarca
          marca={editando}
          alCerrar={() => {
            setCreando(false)
            setEditando(null)
          }}
        />
      )}
    </>
  )
}

function FormularioMarca({ marca, alCerrar }: { marca: Marca | null; alCerrar: () => void }) {
  const clienteConsultas = useQueryClient()
  const toast = useToast()
  const [nombre, setNombre] = useState(marca?.nombre ?? '')
  const [descripcion, setDescripcion] = useState(marca?.descripcion ?? '')
  const [logo, setLogo] = useState<Archivo | null>(marca?.logo ?? null)
  const [error, setError] = useState<unknown>(null)

  const guardar = useMutation({
    mutationFn: () => {
      const cuerpo = {
        nombre: nombre.trim(),
        descripcion: descripcion.trim() || null,
        logoId: logo?.id ?? null,
      }
      return marca ? api.actualizar(marca.id, { ...cuerpo, activa: marca.activa }) : api.crear(cuerpo)
    },
    onSuccess: (guardada) => {
      void clienteConsultas.invalidateQueries({ queryKey: ['marcas'] })
      toast.exito(marca ? 'Marca actualizada' : 'Marca creada', guardada.nombre)
      alCerrar()
    },
    onError: (fallo) => {
      setError(fallo)
      toast.error(fallo, 'No se pudo guardar')
    },
  })

  function enviar(evento: FormEvent) {
    evento.preventDefault()
    setError(null)
    guardar.mutate()
  }

  const errorApi = error instanceof ErrorApi ? error : null

  return (
    <Dialogo
      titulo={marca ? `Editar ${marca.nombre}` : 'Nueva marca'}
      alCerrar={alCerrar}
      pie={
        <>
          <button type="button" onClick={alCerrar}>
            Cancelar
          </button>
          <button type="submit" form="formulario-marca" className="primario" disabled={guardar.isPending}>
            {guardar.isPending ? 'Guardando…' : 'Guardar'}
          </button>
        </>
      }
    >
      <form id="formulario-marca" onSubmit={enviar}>
        <AvisoError error={error} />

        <Campo
          nombre="nombre"
          etiqueta="Nombre"
          error={errorApi?.deCampo('nombre')}
          ayuda={marca ? 'El slug no cambia aunque cambie el nombre: los enlaces siguen funcionando.' : undefined}
        >
          {({ id, className }) => (
            <input
              id={id}
              className={className}
              type="text"
              autoFocus
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
            />
          )}
        </Campo>

        <Campo nombre="descripcion" etiqueta="Descripción" error={errorApi?.deCampo('descripcion')}>
          {({ id, className }) => (
            <textarea
              id={id}
              className={className}
              rows={3}
              maxLength={500}
              value={descripcion}
              onChange={(e) => setDescripcion(e.target.value)}
            />
          )}
        </Campo>

        <CampoImagen
          etiqueta="Logo"
          archivo={logo}
          alCambiar={setLogo}
          textoAltBase={nombre ? `Logo de ${nombre}` : 'Logo de la marca'}
        />
      </form>
    </Dialogo>
  )
}
