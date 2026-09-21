import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Eye, EyeOff, ImageOff, Package, Pencil, Plus, Search, Trash2 } from 'lucide-react'
import { productos as api } from '../api/recursos'
import type { ProductoAdmin } from '../api/tipos'
import { AvisoError } from '../componentes/Aviso'
import BotonIcono from '../componentes/BotonIcono'
import Combobox from '../componentes/Combobox'
import { confirmacionDeBorrado, useConfirmar } from '../componentes/Confirmar'
import { Destacado, Estado, soles } from '../componentes/Insignia'
import Paginacion from '../componentes/Paginacion'
import SinDatos from '../componentes/SinDatos'
import { useToast } from '../componentes/Toast'

const ORDENES = [
  { valor: 'recientes', etiqueta: 'Más recientes' },
  { valor: 'nombre_asc', etiqueta: 'Nombre (A-Z)' },
  { valor: 'precio_asc', etiqueta: 'Precio (menor primero)' },
  { valor: 'precio_desc', etiqueta: 'Precio (mayor primero)' },
  { valor: 'calificacion', etiqueta: 'Mejor calificados' },
]

const ESTADOS = [
  { valor: '', etiqueta: 'Todos los estados' },
  { valor: 'true', etiqueta: 'Publicados' },
  { valor: 'false', etiqueta: 'Borradores' },
]

export default function Productos() {
  const clienteConsultas = useQueryClient()
  const toast = useToast()
  const confirmar = useConfirmar()
  const [texto, setTexto] = useState('')
  const [estado, setEstado] = useState('')
  const [orden, setOrden] = useState('recientes')
  const [pagina, setPagina] = useState(1)

  const consulta = useQuery({
    queryKey: ['productos', texto, estado, orden, pagina],
    queryFn: () =>
      api.listar({
        texto: texto || undefined,
        activo: estado === '' ? undefined : estado === 'true',
        orden,
        pagina,
        tamanoPagina: 20,
      }),
  })

  const cambiarEstado = useMutation({
    mutationFn: ({ id, activo }: { id: number; activo: boolean }) => api.cambiarEstado(id, activo),
    onSuccess: (producto) => {
      void clienteConsultas.invalidateQueries({ queryKey: ['productos'] })
      void clienteConsultas.invalidateQueries({ queryKey: ['metricas'] })
      toast.exito(
        producto.activo ? 'Producto publicado' : 'Producto despublicado',
        producto.nombre,
      )
    },
    // El caso habitual es publicar sin imagen: el mensaje del servidor ya lo
    // dice, así que se muestra tal cual en vez de inventar otro.
    onError: (fallo) => toast.error(fallo),
  })

  const eliminar = useMutation({
    mutationFn: (id: number) => api.eliminar(id),
    onSuccess: (_, id) => {
      void clienteConsultas.invalidateQueries({ queryKey: ['productos'] })
      void clienteConsultas.invalidateQueries({ queryKey: ['metricas'] })
      void clienteConsultas.invalidateQueries({ queryKey: ['papelera'] })
      toast.exito('Producto eliminado', `Queda en la papelera (#${id}) por si hay que recuperarlo.`)
    },
    onError: (fallo) => toast.error(fallo, 'No se pudo eliminar'),
  })

  async function pedirBorrado(producto: ProductoAdmin) {
    if (await confirmar(confirmacionDeBorrado('el producto', producto.nombre))) {
      eliminar.mutate(producto.id)
    }
  }

  return (
    <>
      <div className="encabezado">
        <div>
          <h2>Productos</h2>
          <p>Un producto nace como borrador. Publicar exige al menos una imagen.</p>
        </div>
        <Link to="/productos/nuevo" className="boton primario">
          <Plus size={15} />
          Nuevo producto
        </Link>
      </div>

      <div className="barra-filtros">
        <div className="busqueda">
          <Search size={15} />
          <input
            type="search"
            placeholder="Buscar por nombre o SKU…"
            value={texto}
            onChange={(e) => {
              setTexto(e.target.value)
              setPagina(1)
            }}
          />
        </div>
        <div style={{ width: 190 }}>
          <Combobox
            opciones={ESTADOS.slice(1)}
            valor={estado}
            etiquetaVacia="Todos los estados"
            alCambiar={(valor) => {
              setEstado(valor)
              setPagina(1)
            }}
          />
        </div>
        <div style={{ width: 210 }}>
          <Combobox opciones={ORDENES} valor={orden} alCambiar={setOrden} />
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
                  <th style={{ width: 62 }} />
                  <th>Producto</th>
                  <th>Ubicación</th>
                  <th className="numero">Precio</th>
                  <th className="numero">Stock</th>
                  <th className="numero">Vistas</th>
                  <th>Estado</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {consulta.data.items.map((producto) => (
                  <tr key={producto.id}>
                    <td>
                      {producto.imagenes[0] ? (
                        <img
                          className="miniatura"
                          src={producto.imagenes[0].miniatura}
                          alt={producto.imagenes[0].textoAlt}
                          loading="lazy"
                        />
                      ) : (
                        <div className="miniatura vacia" title="Sin imagen: no se puede publicar">
                          <ImageOff size={15} />
                        </div>
                      )}
                    </td>
                    <td>
                      <Link to={`/productos/${producto.id}`} className="principal">
                        {producto.nombre}
                      </Link>
                      <div className="secundario" style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                        <code>{producto.sku}</code>
                        {producto.destacado && <Destacado />}
                      </div>
                    </td>
                    <td className="secundario">
                      {producto.categoria.nombre} › {producto.subcategoria.nombre}
                      {producto.marca && <div>{producto.marca.nombre}</div>}
                    </td>
                    <td className="numero">
                      <div className="principal">{soles(producto.precio)}</div>
                      {producto.porcentajeDescuento !== null && (
                        <span className="insignia roja sin-punto">−{producto.porcentajeDescuento}%</span>
                      )}
                    </td>
                    <td className="numero">
                      {producto.stock === 0 ? (
                        <span className="insignia roja">Agotado</span>
                      ) : producto.stock < 10 ? (
                        <span className="insignia ambar">{producto.stock}</span>
                      ) : (
                        producto.stock
                      )}
                    </td>
                    <td className="numero secundario">
                      <span title={`${producto.vistas} visitas a la ficha`}>
                        <Eye size={12} style={{ verticalAlign: -1, marginRight: 4 }} />
                        {producto.vistas.toLocaleString('es-PE')}
                      </span>
                    </td>
                    <td>
                      <Estado activo={producto.activo} />
                    </td>
                    <td className="acciones">
                      <Link
                        to={`/productos/${producto.id}`}
                        className="accion-icono"
                        aria-label="Editar"
                        title="Editar"
                      >
                        <Pencil size={16} />
                      </Link>
                      <BotonIcono
                        icono={producto.activo ? EyeOff : Eye}
                        etiqueta={producto.activo ? 'Despublicar' : 'Publicar'}
                        desactivado={cambiarEstado.isPending}
                        alPulsar={() => cambiarEstado.mutate({ id: producto.id, activo: !producto.activo })}
                      />
                      <BotonIcono
                        icono={Trash2}
                        etiqueta="Eliminar"
                        peligro
                        desactivado={eliminar.isPending}
                        alPulsar={() => void pedirBorrado(producto)}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <SinDatos
            icono={Package}
            titulo={texto || estado ? 'Ningún producto coincide' : 'Todavía no hay productos'}
            detalle={
              texto || estado
                ? 'Prueba con otro término o quita los filtros.'
                : 'Crea el primero: necesitarás una subcategoría activa.'
            }
            accion={
              !texto && !estado ? (
                <Link to="/productos/nuevo" className="boton primario">
                  <Plus size={15} />
                  Nuevo producto
                </Link>
              ) : undefined
            }
          />
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
    </>
  )
}
