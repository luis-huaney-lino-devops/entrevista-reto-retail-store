import { Suspense, lazy, useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { AlertCircle, ChevronRight, Eye, EyeOff, Loader2, Save } from 'lucide-react'
import { ErrorApi } from '../api/cliente'
import { marcas as apiMarcas, productos as api, subcategorias as apiSubcategorias } from '../api/recursos'
import type { Imagen } from '../api/tipos'
import { AvisoError } from '../componentes/Aviso'
import Campo from '../componentes/Campo'
import Combobox from '../componentes/Combobox'
import GaleriaImagenes from '../componentes/GaleriaImagenes'
import { Estado, fecha } from '../componentes/Insignia'
import { useToast } from '../componentes/Toast'

// TipTap pesa lo suyo y solo hace falta en esta pantalla; el listado de
// productos no tiene por qué descargarlo.
const EditorTexto = lazy(() => import('../componentes/EditorTexto'))

type Borrador = {
  sku: string
  nombre: string
  descripcionCorta: string
  descripcion: string
  subcategoriaId: string
  marcaId: string
  precio: string
  precioAnterior: string
  stock: string
  destacado: boolean
  imagenes: Imagen[]
}

const VACIO: Borrador = {
  sku: '',
  nombre: '',
  descripcionCorta: '',
  descripcion: '',
  subcategoriaId: '',
  marcaId: '',
  precio: '',
  precioAnterior: '',
  stock: '0',
  destacado: false,
  imagenes: [],
}

export default function ProductoEditor() {
  const { id } = useParams()
  const esNuevo = id === undefined
  const navegar = useNavigate()
  const clienteConsultas = useQueryClient()
  const toast = useToast()

  const [borrador, setBorrador] = useState<Borrador>(VACIO)
  const [error, setError] = useState<unknown>(null)

  const producto = useQuery({
    queryKey: ['producto', id],
    queryFn: () => api.porId(Number(id)),
    enabled: !esNuevo,
  })

  const subcategorias = useQuery({ queryKey: ['subcategorias'], queryFn: apiSubcategorias.listar })
  const marcas = useQuery({ queryKey: ['marcas', ''], queryFn: () => apiMarcas.listar() })

  useEffect(() => {
    if (producto.data) {
      setBorrador({
        sku: producto.data.sku,
        nombre: producto.data.nombre,
        descripcionCorta: producto.data.descripcionCorta ?? '',
        descripcion: producto.data.descripcion ?? '',
        subcategoriaId: String(producto.data.subcategoria.id),
        marcaId: producto.data.marca ? String(producto.data.marca.id) : '',
        precio: String(producto.data.precio),
        precioAnterior: producto.data.precioAnterior === null ? '' : String(producto.data.precioAnterior),
        stock: String(producto.data.stock),
        destacado: producto.data.destacado,
        imagenes: producto.data.imagenes,
      })
    }
  }, [producto.data])

  const opcionesSubcategoria = useMemo(
    () =>
      (subcategorias.data?.items ?? [])
        .filter((s) => s.activa)
        .map((s) => ({ valor: String(s.id), etiqueta: s.nombre, grupo: s.categoria.nombre })),
    [subcategorias.data],
  )

  const opcionesMarca = useMemo(
    () => (marcas.data?.items ?? []).map((m) => ({ valor: String(m.id), etiqueta: m.nombre })),
    [marcas.data],
  )

  const guardar = useMutation({
    mutationFn: () => {
      const cuerpo = {
        nombre: borrador.nombre.trim(),
        descripcionCorta: borrador.descripcionCorta.trim() || null,
        descripcion: borrador.descripcion.trim() || null,
        subcategoriaId: Number(borrador.subcategoriaId),
        marcaId: borrador.marcaId ? Number(borrador.marcaId) : null,
        precio: Number(borrador.precio),
        precioAnterior: borrador.precioAnterior ? Number(borrador.precioAnterior) : null,
        stock: Number(borrador.stock) || 0,
        destacado: borrador.destacado,
        imagenIds: borrador.imagenes.map((imagen) => imagen.archivoId),
      }
      // El SKU solo viaja al crear: es inmutable (RN-002), y mandarlo en la
      // edición obligaría al servidor a decidir qué hacer si difiere.
      return esNuevo ? api.crear({ ...cuerpo, sku: borrador.sku.trim() }) : api.actualizar(Number(id), cuerpo)
    },
    onSuccess: (guardado) => {
      void clienteConsultas.invalidateQueries({ queryKey: ['productos'] })
      void clienteConsultas.invalidateQueries({ queryKey: ['metricas'] })
      setError(null)
      if (esNuevo) {
        toast.exito('Producto creado', 'Nace como borrador: publícalo cuando esté listo')
        navegar(`/productos/${guardado.id}`, { replace: true })
      } else {
        void clienteConsultas.invalidateQueries({ queryKey: ['producto', id] })
        toast.exito('Cambios guardados', guardado.nombre)
      }
    },
    onError: (fallo) => {
      setError(fallo)
      toast.error(fallo, 'No se pudo guardar')
    },
  })

  const publicar = useMutation({
    mutationFn: (activo: boolean) => api.cambiarEstado(Number(id), activo),
    onSuccess: (guardado) => {
      void clienteConsultas.invalidateQueries({ queryKey: ['producto', id] })
      void clienteConsultas.invalidateQueries({ queryKey: ['productos'] })
      void clienteConsultas.invalidateQueries({ queryKey: ['metricas'] })
      setError(null)
      toast.exito(
        guardado.activo ? 'Producto publicado' : 'Producto despublicado',
        guardado.activo ? 'Ya se ve en la tienda' : 'Deja de verse en la tienda',
      )
    },
    onError: (fallo) => {
      setError(fallo)
      toast.error(fallo)
    },
  })

  function enviar(evento: FormEvent) {
    evento.preventDefault()
    setError(null)
    guardar.mutate()
  }

  const errorApi = error instanceof ErrorApi ? error : null

  if (!esNuevo && producto.isLoading) {
    return <p className="cargando">Cargando…</p>
  }

  const sinImagenes = borrador.imagenes.length === 0
  const publicado = producto.data?.activo ?? false

  return (
    <form onSubmit={enviar}>
      <div className="migas">
        <Link to="/productos">Productos</Link>
        <ChevronRight size={13} />
        <span>{esNuevo ? 'Nuevo' : borrador.nombre || 'Producto'}</span>
      </div>

      <div className="encabezado">
        <div>
          <h2>{esNuevo ? 'Nuevo producto' : borrador.nombre || 'Producto'}</h2>
          {producto.data && (
            <p>
              {producto.data.vistas.toLocaleString('es-PE')} vistas · creado por{' '}
              {producto.data.creadoPor} · última edición de {producto.data.actualizadoPor} el{' '}
              {fecha(producto.data.actualizadoEn)}
            </p>
          )}
        </div>
        <div className="acciones">
          {producto.data && <Estado activo={publicado} />}
          {!esNuevo && producto.data && (
            <button
              type="button"
              onClick={() => publicar.mutate(!publicado)}
              disabled={publicar.isPending || (!publicado && sinImagenes)}
              title={!publicado && sinImagenes ? 'Un producto necesita al menos una imagen' : undefined}
            >
              {publicado ? <EyeOff size={15} /> : <Eye size={15} />}
              {publicado ? 'Despublicar' : 'Publicar'}
            </button>
          )}
          <button type="submit" className="primario" disabled={guardar.isPending}>
            {guardar.isPending ? <Loader2 size={15} className="girando" /> : <Save size={15} />}
            {guardar.isPending ? 'Guardando…' : 'Guardar'}
          </button>
        </div>
      </div>

      <AvisoError error={error} />

      <div className="rejilla-2">
        <div className="columna">
          <div className="tarjeta">
            <h3>Datos básicos</h3>

            <Campo
              nombre="sku"
              etiqueta="SKU"
              ayuda={
                esNuevo
                  ? 'Código interno único. No se podrá cambiar después.'
                  : 'Inmutable: identifica al producto en inventario y en las órdenes ya emitidas.'
              }
              error={errorApi?.deCampo('sku')}
            >
              {({ id: campoId, className }) => (
                <input
                  id={campoId}
                  className={className}
                  type="text"
                  placeholder="TEC-AUD-001"
                  spellCheck={false}
                  value={borrador.sku}
                  disabled={!esNuevo}
                  onChange={(e) => setBorrador({ ...borrador, sku: e.target.value.toUpperCase() })}
                />
              )}
            </Campo>

            <Campo nombre="nombre" etiqueta="Nombre" error={errorApi?.deCampo('nombre')}>
              {({ id: campoId, className }) => (
                <input
                  id={campoId}
                  className={className}
                  type="text"
                  value={borrador.nombre}
                  onChange={(e) => setBorrador({ ...borrador, nombre: e.target.value })}
                />
              )}
            </Campo>

            <Campo
              nombre="descripcionCorta"
              etiqueta="Descripción corta"
              ayuda={`La que se ve en la tarjeta del listado · ${borrador.descripcionCorta.length}/300`}
              error={errorApi?.deCampo('descripcionCorta')}
            >
              {({ id: campoId, className }) => (
                <textarea
                  id={campoId}
                  className={className}
                  rows={2}
                  maxLength={300}
                  value={borrador.descripcionCorta}
                  onChange={(e) => setBorrador({ ...borrador, descripcionCorta: e.target.value })}
                />
              )}
            </Campo>

            <Campo
              nombre="descripcion"
              etiqueta="Descripción"
              ayuda="Se guarda como Markdown, no como HTML: lo que se publica está acotado a lo que el editor produce."
              error={errorApi?.deCampo('descripcion')}
            >
              {({ id: campoId, className }) => (
                <Suspense fallback={<div className="esqueleto" style={{ height: 230 }} />}>
                  <EditorTexto
                    id={campoId}
                    alto
                    conError={className.includes('con-error')}
                    valor={borrador.descripcion}
                    marcador="Materiales, medidas, qué incluye la caja…"
                    alCambiar={(markdown) => setBorrador((previo) => ({ ...previo, descripcion: markdown }))}
                  />
                </Suspense>
              )}
            </Campo>
          </div>

          <div className="tarjeta">
            <h3>Imágenes</h3>
            {sinImagenes && (
              <div className="aviso info" style={{ marginBottom: 14 }}>
                <AlertCircle size={17} />
                <div>
                  Un producto necesita <strong>al menos una imagen</strong> para poder publicarse. La
                  primera es la que se ve en la rejilla de la tienda.
                </div>
              </div>
            )}
            <GaleriaImagenes
              imagenes={borrador.imagenes}
              textoAltBase={borrador.nombre}
              alCambiar={(imagenes) => setBorrador((previo) => ({ ...previo, imagenes }))}
            />
          </div>
        </div>

        <div className="columna">
          <div className="tarjeta">
            <h3>Ubicación</h3>

            <Campo
              nombre="subcategoriaId"
              etiqueta="Subcategoría"
              ayuda="Los productos cuelgan de la subcategoría, nunca de la categoría."
              error={errorApi?.deCampo('subcategoriaId')}
            >
              {({ id: campoId, className }) => (
                <Combobox
                  id={campoId}
                  className={className}
                  opciones={opcionesSubcategoria}
                  valor={borrador.subcategoriaId}
                  marcador="Elige una subcategoría…"
                  alCambiar={(valor) => setBorrador({ ...borrador, subcategoriaId: valor })}
                />
              )}
            </Campo>

            <Campo
              nombre="marcaId"
              etiqueta="Marca"
              ayuda="Opcional: muchos productos de tienda no tienen marca."
              error={errorApi?.deCampo('marcaId')}
            >
              {({ id: campoId, className }) => (
                <Combobox
                  id={campoId}
                  className={className}
                  opciones={opcionesMarca}
                  valor={borrador.marcaId}
                  etiquetaVacia="Sin marca"
                  alCambiar={(valor) => setBorrador({ ...borrador, marcaId: valor })}
                />
              )}
            </Campo>
          </div>

          <div className="tarjeta">
            <h3>Precio y stock</h3>

            <Campo nombre="precio" etiqueta="Precio" error={errorApi?.deCampo('precio')}>
              {({ id: campoId, className }) => (
                <div className="entrada-prefijo">
                  <span>S/</span>
                  <input
                    id={campoId}
                    className={className}
                    type="number"
                    step="0.01"
                    min="0.01"
                    placeholder="0.00"
                    value={borrador.precio}
                    onChange={(e) => setBorrador({ ...borrador, precio: e.target.value })}
                  />
                </div>
              )}
            </Campo>

            <Campo
              nombre="precioAnterior"
              etiqueta="Precio anterior"
              ayuda="Solo si es mayor que el precio actual. Es lo que produce el precio tachado."
              error={errorApi?.deCampo('precioAnterior')}
            >
              {({ id: campoId, className }) => (
                <div className="entrada-prefijo">
                  <span>S/</span>
                  <input
                    id={campoId}
                    className={className}
                    type="number"
                    step="0.01"
                    min="0"
                    placeholder="Sin promoción"
                    value={borrador.precioAnterior}
                    onChange={(e) => setBorrador({ ...borrador, precioAnterior: e.target.value })}
                  />
                </div>
              )}
            </Campo>

            {descuentoVisible(borrador) !== null && (
              <div className="aviso exito" style={{ marginBottom: 14 }}>
                <span className="insignia roja sin-punto">−{descuentoVisible(borrador)}%</span>
                <div>Así se verá la oferta en la tienda.</div>
              </div>
            )}

            <Campo nombre="stock" etiqueta="Stock" error={errorApi?.deCampo('stock')}>
              {({ id: campoId, className }) => (
                <input
                  id={campoId}
                  className={className}
                  type="number"
                  min="0"
                  value={borrador.stock}
                  onChange={(e) => setBorrador({ ...borrador, stock: e.target.value })}
                />
              )}
            </Campo>

            <label className="casilla">
              <input
                type="checkbox"
                checked={borrador.destacado}
                onChange={(e) => setBorrador({ ...borrador, destacado: e.target.checked })}
              />
              Destacado en la portada
            </label>
          </div>
        </div>
      </div>
    </form>
  )
}

/** El mismo cálculo que hace el servidor, para que la vista previa no mienta. */
function descuentoVisible(borrador: Borrador): number | null {
  const precio = Number(borrador.precio)
  const anterior = Number(borrador.precioAnterior)
  if (!precio || !anterior || anterior <= precio) return null
  return Math.round(((anterior - precio) / anterior) * 100)
}
