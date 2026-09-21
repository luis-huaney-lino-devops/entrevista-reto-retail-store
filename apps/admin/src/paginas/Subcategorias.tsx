import { useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Eye, EyeOff, ImageOff, Layers, Pencil, Plus, Trash2 } from 'lucide-react'
import { ErrorApi } from '../api/cliente'
import { categorias as apiCategorias, subcategorias as api } from '../api/recursos'
import type { Archivo, Subcategoria } from '../api/tipos'
import { AvisoError } from '../componentes/Aviso'
import BotonIcono from '../componentes/BotonIcono'
import Campo from '../componentes/Campo'
import CampoImagen from '../componentes/CampoImagen'
import Combobox from '../componentes/Combobox'
import { confirmacionDeBorrado, useConfirmar } from '../componentes/Confirmar'
import Dialogo from '../componentes/Dialogo'
import { Disponibilidad } from '../componentes/Insignia'
import SinDatos from '../componentes/SinDatos'
import { useToast } from '../componentes/Toast'

/** Opciones de categoría para los comboboxes de esta pantalla. */
function useOpcionesCategoria() {
  const consulta = useQuery({ queryKey: ['categorias'], queryFn: apiCategorias.listar })
  return useMemo(
    () =>
      (consulta.data?.items ?? []).map((categoria) => ({
        valor: String(categoria.id),
        etiqueta: categoria.nombre,
        grupo: categoria.activa ? undefined : 'inactiva',
      })),
    [consulta.data],
  )
}

export default function Subcategorias() {
  const clienteConsultas = useQueryClient()
  const toast = useToast()
  const confirmar = useConfirmar()
  const [editando, setEditando] = useState<Subcategoria | null>(null)
  const [creando, setCreando] = useState(false)
  const [filtro, setFiltro] = useState('')

  const consulta = useQuery({ queryKey: ['subcategorias'], queryFn: api.listar })
  const opcionesCategoria = useOpcionesCategoria()

  const cambiarEstado = useMutation({
    mutationFn: ({ id, activo }: { id: number; activo: boolean }) => api.cambiarEstado(id, activo),
    onSuccess: (subcategoria) => {
      void clienteConsultas.invalidateQueries({ queryKey: ['subcategorias'] })
      toast.exito(
        subcategoria.activa ? 'Subcategoría activada' : 'Subcategoría desactivada',
        subcategoria.nombre,
      )
    },
    onError: (fallo) => toast.error(fallo, 'No se puede desactivar mientras tenga productos activos'),
  })

  const eliminar = useMutation({
    mutationFn: (id: number) => api.eliminar(id),
    onSuccess: () => {
      void clienteConsultas.invalidateQueries({ queryKey: ['subcategorias'] })
      void clienteConsultas.invalidateQueries({ queryKey: ['papelera'] })
      toast.exito('Subcategoría eliminada', 'Queda en la papelera por si hay que recuperarla.')
    },
    onError: (fallo) => toast.error(fallo, 'No se pudo eliminar'),
  })

  async function pedirBorrado(subcategoria: Subcategoria) {
    if (await confirmar(confirmacionDeBorrado('la subcategoría', subcategoria.nombre))) {
      eliminar.mutate(subcategoria.id)
    }
  }

  const visibles = (consulta.data?.items ?? []).filter(
    (s) => !filtro || s.categoria.id === Number(filtro),
  )

  return (
    <>
      <div className="encabezado">
        <div>
          <h2>Subcategorías</h2>
          <p>Segundo nivel. Aquí cuelgan los productos.</p>
        </div>
        <button type="button" className="primario" onClick={() => setCreando(true)}>
          <Plus size={15} />
          Nueva subcategoría
        </button>
      </div>

      <div className="barra-filtros">
        <div style={{ width: 240 }}>
          <Combobox
            opciones={opcionesCategoria}
            valor={filtro}
            etiquetaVacia="Todas las categorías"
            alCambiar={setFiltro}
          />
        </div>
      </div>

      <AvisoError error={consulta.error} />

      <div className="tarjeta plana">
        {consulta.isLoading ? (
          <p className="cargando">Cargando…</p>
        ) : visibles.length > 0 ? (
          <div className="envoltura-tabla">
            <table>
              <thead>
                <tr>
                  <th style={{ width: 62 }}>Imagen</th>
                  <th>Categoría</th>
                  <th>Nombre</th>
                  <th>Slug</th>
                  <th className="numero">Orden</th>
                  <th>Estado</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {visibles.map((subcategoria) => (
                  <tr key={subcategoria.id}>
                    <td>
                      {subcategoria.imagen ? (
                        <img
                          className="miniatura"
                          src={subcategoria.imagen.urls.MINIATURA}
                          alt={subcategoria.imagen.textoAlt}
                        />
                      ) : (
                        <div className="miniatura vacia">
                          <ImageOff size={15} />
                        </div>
                      )}
                    </td>
                    <td className="secundario">{subcategoria.categoria.nombre}</td>
                    <td className="principal">{subcategoria.nombre}</td>
                    <td>
                      <code>{subcategoria.slug}</code>
                    </td>
                    <td className="numero secundario">{subcategoria.orden}</td>
                    <td>
                      <Disponibilidad activa={subcategoria.activa} />
                    </td>
                    <td className="acciones">
                      <BotonIcono
                        icono={Pencil}
                        etiqueta="Editar"
                        alPulsar={() => setEditando(subcategoria)}
                      />
                      <BotonIcono
                        icono={subcategoria.activa ? EyeOff : Eye}
                        etiqueta={subcategoria.activa ? 'Desactivar' : 'Activar'}
                        desactivado={cambiarEstado.isPending}
                        alPulsar={() =>
                          cambiarEstado.mutate({ id: subcategoria.id, activo: !subcategoria.activa })
                        }
                      />
                      <BotonIcono
                        icono={Trash2}
                        etiqueta="Eliminar"
                        peligro
                        desactivado={eliminar.isPending}
                        alPulsar={() => void pedirBorrado(subcategoria)}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <SinDatos
            icono={Layers}
            titulo={filtro ? 'Esa categoría no tiene subcategorías' : 'Todavía no hay subcategorías'}
            detalle="Los productos cuelgan de aquí, así que hace falta al menos una."
          />
        )}
      </div>

      {(creando || editando) && (
        <FormularioSubcategoria
          subcategoria={editando}
          opcionesCategoria={opcionesCategoria}
          alCerrar={() => {
            setCreando(false)
            setEditando(null)
          }}
        />
      )}
    </>
  )
}

function FormularioSubcategoria({
  subcategoria,
  opcionesCategoria,
  alCerrar,
}: {
  subcategoria: Subcategoria | null
  opcionesCategoria: { valor: string; etiqueta: string; grupo?: string }[]
  alCerrar: () => void
}) {
  const clienteConsultas = useQueryClient()
  const toast = useToast()
  const [categoriaId, setCategoriaId] = useState(String(subcategoria?.categoria.id ?? ''))
  const [nombre, setNombre] = useState(subcategoria?.nombre ?? '')
  const [descripcion, setDescripcion] = useState(subcategoria?.descripcion ?? '')
  const [orden, setOrden] = useState(String(subcategoria?.orden ?? 0))
  const [imagen, setImagen] = useState<Archivo | null>(subcategoria?.imagen ?? null)
  const [error, setError] = useState<unknown>(null)

  const guardar = useMutation({
    mutationFn: () => {
      const cuerpo = {
        categoriaId: Number(categoriaId),
        nombre: nombre.trim(),
        descripcion: descripcion.trim() || null,
        orden: Number(orden) || 0,
        imagenId: imagen?.id ?? null,
      }
      return subcategoria
        ? api.actualizar(subcategoria.id, { ...cuerpo, activa: subcategoria.activa })
        : api.crear(cuerpo)
    },
    onSuccess: (guardada) => {
      void clienteConsultas.invalidateQueries({ queryKey: ['subcategorias'] })
      toast.exito(
        subcategoria ? 'Subcategoría actualizada' : 'Subcategoría creada',
        `${guardada.categoria.nombre} › ${guardada.nombre}`,
      )
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
      titulo={subcategoria ? `Editar ${subcategoria.nombre}` : 'Nueva subcategoría'}
      alCerrar={alCerrar}
      pie={
        <>
          <button type="button" onClick={alCerrar}>
            Cancelar
          </button>
          <button
            type="submit"
            form="formulario-subcategoria"
            className="primario"
            disabled={guardar.isPending}
          >
            {guardar.isPending ? 'Guardando…' : 'Guardar'}
          </button>
        </>
      }
    >
      <form id="formulario-subcategoria" onSubmit={enviar}>
        <AvisoError error={error} />

        <Campo
          nombre="categoriaId"
          etiqueta="Categoría"
          error={errorApi?.deCampo('categoriaId')}
          ayuda={subcategoria ? 'Se puede mover a otra categoría sin perder sus productos.' : undefined}
        >
          {({ id, className }) => (
            <Combobox
              id={id}
              className={className}
              opciones={opcionesCategoria}
              valor={categoriaId}
              marcador="Elige una categoría…"
              alCambiar={setCategoriaId}
            />
          )}
        </Campo>

        <Campo
          nombre="nombre"
          etiqueta="Nombre"
          ayuda="Único dentro de su categoría: «Accesorios» puede existir en Tecnología y en Deportes."
          error={errorApi?.deCampo('nombre')}
        >
          {({ id, className }) => (
            <input
              id={id}
              className={className}
              type="text"
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
            />
          )}
        </Campo>

        <Campo nombre="orden" etiqueta="Orden" error={errorApi?.deCampo('orden')}>
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
          textoAltBase={nombre ? `Subcategoría ${nombre}` : 'Imagen de la subcategoría'}
        />
      </form>
    </Dialogo>
  )
}
