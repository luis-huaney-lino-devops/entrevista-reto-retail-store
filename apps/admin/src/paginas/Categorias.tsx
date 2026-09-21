import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Eye, EyeOff, FolderTree, ImageOff, Pencil, Plus, Trash2 } from 'lucide-react'
import { ErrorApi } from '../api/cliente'
import { categorias as api } from '../api/recursos'
import type { Archivo, Categoria } from '../api/tipos'
import { AvisoError } from '../componentes/Aviso'
import BotonIcono from '../componentes/BotonIcono'
import Campo from '../componentes/Campo'
import CampoImagen from '../componentes/CampoImagen'
import { confirmacionDeBorrado, useConfirmar } from '../componentes/Confirmar'
import Dialogo from '../componentes/Dialogo'
import { Disponibilidad } from '../componentes/Insignia'
import SinDatos from '../componentes/SinDatos'
import { useToast } from '../componentes/Toast'

export default function Categorias() {
  const clienteConsultas = useQueryClient()
  const toast = useToast()
  const confirmar = useConfirmar()
  const [editando, setEditando] = useState<Categoria | null>(null)
  const [creando, setCreando] = useState(false)

  const consulta = useQuery({ queryKey: ['categorias'], queryFn: api.listar })

  const cambiarEstado = useMutation({
    mutationFn: ({ id, activo }: { id: number; activo: boolean }) => api.cambiarEstado(id, activo),
    onSuccess: (categoria) => {
      void clienteConsultas.invalidateQueries({ queryKey: ['categorias'] })
      void clienteConsultas.invalidateQueries({ queryKey: ['subcategorias'] })
      toast.exito(categoria.activa ? 'Categoría activada' : 'Categoría desactivada', categoria.nombre)
    },
    onError: (fallo) =>
      toast.error(fallo, 'No se puede desactivar mientras tenga subcategorías activas'),
  })

  const eliminar = useMutation({
    mutationFn: (id: number) => api.eliminar(id),
    onSuccess: () => {
      void clienteConsultas.invalidateQueries({ queryKey: ['categorias'] })
      void clienteConsultas.invalidateQueries({ queryKey: ['papelera'] })
      toast.exito('Categoría eliminada', 'Queda en la papelera por si hay que recuperarla.')
    },
    // El servidor se niega si todavía cuelga algo de ella y dice qué: ese
    // mensaje es más útil que cualquiera que se invente el panel.
    onError: (fallo) => toast.error(fallo, 'No se pudo eliminar'),
  })

  async function pedirBorrado(categoria: Categoria) {
    if (await confirmar(confirmacionDeBorrado('la categoría', categoria.nombre))) {
      eliminar.mutate(categoria.id)
    }
  }

  return (
    <>
      <div className="encabezado">
        <div>
          <h2>Categorías</h2>
          <p>
            Primer nivel del catálogo. Una categoría <strong>no contiene productos</strong>: solo
            subcategorías.
          </p>
        </div>
        <button type="button" className="primario" onClick={() => setCreando(true)}>
          <Plus size={15} />
          Nueva categoría
        </button>
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
                  <th style={{ width: 62 }}>Imagen</th>
                  <th className="numero">Orden</th>
                  <th>Nombre</th>
                  <th>Slug</th>
                  <th>Estado</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {consulta.data.items.map((categoria) => (
                  <tr key={categoria.id}>
                    <td>
                      {categoria.imagen ? (
                        <img
                          className="miniatura"
                          src={categoria.imagen.urls.MINIATURA}
                          alt={categoria.imagen.textoAlt}
                        />
                      ) : (
                        <div className="miniatura vacia">
                          <ImageOff size={15} />
                        </div>
                      )}
                    </td>
                    <td className="numero secundario">{categoria.orden}</td>
                    <td className="principal">{categoria.nombre}</td>
                    <td>
                      <code>{categoria.slug}</code>
                    </td>
                    <td>
                      <Disponibilidad activa={categoria.activa} />
                    </td>
                    <td className="acciones">
                      <BotonIcono icono={Pencil} etiqueta="Editar" alPulsar={() => setEditando(categoria)} />
                      <BotonIcono
                        icono={categoria.activa ? EyeOff : Eye}
                        etiqueta={categoria.activa ? 'Desactivar' : 'Activar'}
                        desactivado={cambiarEstado.isPending}
                        alPulsar={() => cambiarEstado.mutate({ id: categoria.id, activo: !categoria.activa })}
                      />
                      <BotonIcono
                        icono={Trash2}
                        etiqueta="Eliminar"
                        peligro
                        desactivado={eliminar.isPending}
                        alPulsar={() => void pedirBorrado(categoria)}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <SinDatos
            icono={FolderTree}
            titulo="Todavía no hay categorías"
            detalle="Son el primer nivel: crea una y luego sus subcategorías."
          />
        )}
      </div>

      {(creando || editando) && (
        <FormularioCategoria
          categoria={editando}
          alCerrar={() => {
            setCreando(false)
            setEditando(null)
          }}
        />
      )}
    </>
  )
}

function FormularioCategoria({
  categoria,
  alCerrar,
}: {
  categoria: Categoria | null
  alCerrar: () => void
}) {
  const clienteConsultas = useQueryClient()
  const toast = useToast()
  const [nombre, setNombre] = useState(categoria?.nombre ?? '')
  const [descripcion, setDescripcion] = useState(categoria?.descripcion ?? '')
  const [orden, setOrden] = useState(String(categoria?.orden ?? 0))
  const [imagen, setImagen] = useState<Archivo | null>(categoria?.imagen ?? null)
  const [error, setError] = useState<unknown>(null)

  const guardar = useMutation({
    mutationFn: () => {
      const cuerpo = {
        nombre: nombre.trim(),
        descripcion: descripcion.trim() || null,
        orden: Number(orden) || 0,
        imagenId: imagen?.id ?? null,
      }
      return categoria
        ? api.actualizar(categoria.id, { ...cuerpo, activa: categoria.activa })
        : api.crear(cuerpo)
    },
    onSuccess: (guardada) => {
      void clienteConsultas.invalidateQueries({ queryKey: ['categorias'] })
      toast.exito(categoria ? 'Categoría actualizada' : 'Categoría creada', guardada.nombre)
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
      titulo={categoria ? `Editar ${categoria.nombre}` : 'Nueva categoría'}
      alCerrar={alCerrar}
      pie={
        <>
          <button type="button" onClick={alCerrar}>
            Cancelar
          </button>
          <button type="submit" form="formulario-categoria" className="primario" disabled={guardar.isPending}>
            {guardar.isPending ? 'Guardando…' : 'Guardar'}
          </button>
        </>
      }
    >
      <form id="formulario-categoria" onSubmit={enviar}>
        <AvisoError error={error} />

        <Campo nombre="nombre" etiqueta="Nombre" error={errorApi?.deCampo('nombre')}>
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

        <Campo
          nombre="orden"
          etiqueta="Orden en el menú"
          ayuda="El criterio no es alfabético: «Ofertas» va primero aunque empiece por O."
          error={errorApi?.deCampo('orden')}
        >
          {({ id, className }) => (
            <input
              id={id}
              className={className}
              type="number"
              min={0}
              value={orden}
              onChange={(e) => setOrden(e.target.value)}
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
          etiqueta="Imagen"
          archivo={imagen}
          alCambiar={setImagen}
          textoAltBase={nombre ? `Categoría ${nombre}` : 'Imagen de la categoría'}
        />
      </form>
    </Dialogo>
  )
}
