import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  Area,
  AreaChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import {
  AlertTriangle,
  Boxes,
  Clock,
  Eye,
  ImageOff,
  Package,
  TrendingUp,
} from 'lucide-react'
import { metricas as api } from '../api/recursos'
import type { EstadoOrden } from '../api/tipos'
import { AvisoError } from '../componentes/Aviso'
import { soles } from '../componentes/Insignia'

/** Un color por estado, el mismo en el gráfico y en la tabla. */
const COLOR_ESTADO: Record<EstadoOrden, string> = {
  PENDIENTE: '#d9a406',
  PAGADA: '#2f54eb',
  ENVIADA: '#0f6f8f',
  ENTREGADA: '#0f7b4f',
  CANCELADA: '#c02717',
}

export default function Tablero() {
  const consulta = useQuery({ queryKey: ['metricas'], queryFn: api.obtener })

  if (consulta.isLoading) {
    return (
      <>
        <div className="encabezado">
          <div>
            <h2>Tablero</h2>
            <p>Cargando las cifras…</p>
          </div>
        </div>
        <div className="tarjetas-kpi">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="kpi">
              <div className="esqueleto" style={{ width: '55%', marginBottom: 14 }} />
              <div className="esqueleto" style={{ height: 26, width: '70%' }} />
            </div>
          ))}
        </div>
        <div className="tarjeta">
          <div className="esqueleto" style={{ height: 268 }} />
        </div>
      </>
    )
  }

  if (consulta.error || !consulta.data) {
    return (
      <>
        <div className="encabezado">
          <h2>Tablero</h2>
        </div>
        <AvisoError error={consulta.error} />
      </>
    )
  }

  const { resumen, ventasPorDia, ordenesPorEstado, masVistos, masVendidos, stockBajo } = consulta.data
  const conOrdenes = ordenesPorEstado.filter((e) => e.cantidad > 0)
  const totalOrdenes = ordenesPorEstado.reduce((suma, e) => suma + e.cantidad, 0)

  return (
    <>
      <div className="encabezado">
        <div>
          <h2>Tablero</h2>
          <p>Ventas de los últimos 30 días, estado del catálogo y qué se está mirando.</p>
        </div>
      </div>

      {/* ------------------------------------------------------------- KPI */}
      <div className="tarjetas-kpi">
        <Kpi
          etiqueta="Ventas · 30 días"
          valor={soles(resumen.ventas30Dias)}
          nota={
            resumen.ticketPromedio === null
              ? 'Sin órdenes en el periodo'
              : `${resumen.ordenes30Dias} órdenes · ticket medio ${soles(resumen.ticketPromedio)}`
          }
          icono={<TrendingUp size={16} />}
          color="var(--exito)"
          fondo="var(--exito-suave)"
        />
        <Kpi
          etiqueta="Pendientes"
          valor={String(resumen.ordenesPendientes)}
          nota={resumen.ordenesPendientes > 0 ? 'Esperando confirmación de pago' : 'Nada por atender'}
          icono={<Clock size={16} />}
          color="var(--aviso)"
          fondo="var(--aviso-suave)"
          enlace="/ordenes?estado=PENDIENTE"
        />
        <Kpi
          etiqueta="Catálogo"
          valor={String(resumen.productosActivos)}
          nota={`${resumen.productosBorrador} en borrador · ${resumen.productosSinStock} sin stock`}
          icono={<Package size={16} />}
          color="var(--acento)"
          fondo="var(--acento-suave)"
          enlace="/productos"
        />
        <Kpi
          etiqueta="Vistas de producto"
          valor={resumen.vistasTotales.toLocaleString('es-PE')}
          nota={`Inventario valorado en ${soles(resumen.valorInventario)}`}
          icono={<Eye size={16} />}
          color="var(--info)"
          fondo="var(--info-suave)"
        />
      </div>

      {/* --------------------------------------------- lo que se está mirando */}
      {masVistos.length > 0 && (
        <section className="tira-productos">
          <header>
            <h3>Lo que más se está mirando</h3>
            <Link to="/productos" className="enlace">
              Ver el catálogo
            </Link>
          </header>
          <div className="carril">
            {masVistos.slice(0, 6).map((producto, posicion) => (
              <Link
                key={producto.id}
                to={`/productos/${producto.id}`}
                className="tarjeta-producto"
                title={producto.nombre}
              >
                <span className="puesto">{posicion + 1}</span>
                {/* La foto es el dato, no el adorno: un nombre de producto en
                    una lista no se reconoce de un vistazo y una foto sí. */}
                {producto.imagen ? (
                  <img src={producto.imagen} alt="" loading="lazy" />
                ) : (
                  <span className="sin-foto">
                    <ImageOff size={18} />
                  </span>
                )}
                <span className="nombre">{producto.nombre}</span>
                <span className="vistas">
                  <Eye size={12} />
                  {producto.vistas.toLocaleString('es-PE')}
                </span>
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* -------------------------------------------------- ventas y estado */}
      <div className="rejilla-2">
        <div className="tarjeta">
          <h3>Ventas por día</h3>
          <div className="grafico">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={ventasPorDia} margin={{ top: 4, right: 8, left: -14, bottom: 0 }}>
                <defs>
                  <linearGradient id="degradadoVentas" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#2f54eb" stopOpacity={0.28} />
                    <stop offset="100%" stopColor="#2f54eb" stopOpacity={0.02} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke="#eef0f3" vertical={false} />
                <XAxis
                  dataKey="fecha"
                  tickFormatter={diaCorto}
                  tick={{ fontSize: 11, fill: '#7b8494' }}
                  axisLine={false}
                  tickLine={false}
                  minTickGap={22}
                />
                <YAxis
                  tickFormatter={(valor: number) => (valor >= 1000 ? `${Math.round(valor / 1000)}k` : String(valor))}
                  tick={{ fontSize: 11, fill: '#7b8494' }}
                  axisLine={false}
                  tickLine={false}
                  width={52}
                />
                <Tooltip content={<TooltipVentas />} cursor={{ stroke: '#cbd1da' }} />
                <Area
                  type="monotone"
                  dataKey="total"
                  stroke="#2f54eb"
                  strokeWidth={2}
                  fill="url(#degradadoVentas)"
                  // Sin puntos: con 31 días el gráfico se llena de círculos y
                  // deja de leerse la tendencia, que es para lo que está.
                  dot={false}
                  activeDot={{ r: 4, strokeWidth: 2, stroke: '#fff' }}
                />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="tarjeta">
          <h3>Órdenes por estado</h3>
          {totalOrdenes === 0 ? (
            <p className="secundario">Todavía no hay órdenes.</p>
          ) : (
            <>
              <div className="grafico grafico-bajo">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={conOrdenes}
                      dataKey="cantidad"
                      nameKey="etiqueta"
                      innerRadius={58}
                      outerRadius={88}
                      paddingAngle={2}
                      strokeWidth={0}
                    >
                      {conOrdenes.map((entrada) => (
                        <Cell key={entrada.estado} fill={COLOR_ESTADO[entrada.estado]} />
                      ))}
                    </Pie>
                    <Tooltip content={<TooltipEstado total={totalOrdenes} />} />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <div className="leyenda">
                {conOrdenes.map((entrada) => (
                  <span key={entrada.estado}>
                    <i style={{ background: COLOR_ESTADO[entrada.estado] }} />
                    {entrada.etiqueta}
                    <strong style={{ fontVariantNumeric: 'tabular-nums' }}>{entrada.cantidad}</strong>
                  </span>
                ))}
              </div>
            </>
          )}
        </div>
      </div>

      {/* ----------------------------------------------- vistas y vendidos */}
      {/* «Más vistos» ya está arriba con las fotos: repetirlo aquí como barras
          era decir lo mismo dos veces en la misma pantalla. */}
      <div style={{ marginTop: 16 }}>
        <div className="tarjeta plana">
          <h3 style={{ padding: '18px 18px 0', margin: 0 }}>Más vendidos</h3>
          {masVendidos.length === 0 ? (
            <p className="secundario" style={{ padding: '10px 18px 18px' }}>
              Todavía no hay ventas.
            </p>
          ) : (
            <table style={{ marginTop: 12 }}>
              <thead>
                <tr>
                  <th>Producto</th>
                  <th className="numero">Uds.</th>
                  <th className="numero">Total</th>
                </tr>
              </thead>
              <tbody>
                {masVendidos.map((producto) => (
                  <tr key={producto.nombre}>
                    <td className="principal">{producto.nombre}</td>
                    <td className="numero">{producto.unidades}</td>
                    <td className="numero">{soles(producto.total)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      {/* ------------------------------------------------------ stock bajo */}
      {stockBajo.length > 0 && (
        <div className="tarjeta" style={{ marginTop: 16 }}>
          <h3>
            <AlertTriangle size={15} style={{ verticalAlign: -2, marginRight: 7, color: 'var(--aviso)' }} />
            Stock bajo
          </h3>
          <div className="rejilla">
            {stockBajo.map((producto) => (
              <Link
                key={producto.id}
                to={`/productos/${producto.id}`}
                style={{ color: 'inherit', textDecoration: 'none' }}
              >
                <div
                  style={{
                    border: '1px solid var(--borde)',
                    borderRadius: 'var(--radio-s)',
                    padding: '11px 13px',
                  }}
                >
                  <div className="principal" style={{ fontSize: 13 }}>
                    {producto.nombre}
                  </div>
                  <div className="secundario" style={{ marginBottom: 2 }}>
                    <code>{producto.sku}</code>
                  </div>
                  <span className={`insignia ${producto.stock === 0 ? 'roja' : 'ambar'}`}>
                    {producto.stock === 0 ? 'Agotado' : `${producto.stock} unidades`}
                  </span>
                  <div className="barra-progreso">
                    <i
                      style={{
                        width: `${Math.min(100, (producto.stock / 10) * 100)}%`,
                        background: producto.stock === 0 ? 'var(--peligro)' : 'var(--aviso)',
                      }}
                    />
                  </div>
                </div>
              </Link>
            ))}
          </div>
          <p className="secundario" style={{ marginTop: 12, marginBottom: 0 }}>
            <Boxes size={13} style={{ verticalAlign: -2, marginRight: 5 }} />
            Se consideran bajos los productos publicados con menos de 10 unidades.
          </p>
        </div>
      )}
    </>
  )
}

/* ------------------------------------------------------------------ piezas */

function Kpi({
  etiqueta,
  valor,
  nota,
  icono,
  color,
  fondo,
  enlace,
}: {
  etiqueta: string
  valor: string
  nota: string
  icono: React.ReactNode
  color: string
  fondo: string
  enlace?: string
}) {
  const contenido = (
    <div className="kpi">
      <div className="cabecera">
        <span className="etiqueta">{etiqueta}</span>
        <span className="icono-kpi" style={{ background: fondo, color }}>
          {icono}
        </span>
      </div>
      <div className="valor">{valor}</div>
      <div className="nota">{nota}</div>
    </div>
  )

  return enlace ? (
    <Link to={enlace} style={{ color: 'inherit', textDecoration: 'none' }}>
      {contenido}
    </Link>
  ) : (
    contenido
  )
}

type PuntoVenta = { fecha: string; total: number; ordenes: number }

function TooltipVentas({ active, payload }: { active?: boolean; payload?: { payload: PuntoVenta }[] }) {
  const punto = payload?.[0]?.payload
  if (!active || !punto) return null
  return (
    <div className="tooltip-grafico">
      <div className="fecha">{diaLargo(punto.fecha)}</div>
      <div className="dato">{soles(punto.total)}</div>
      <div style={{ color: '#98a2b3', fontSize: 11.5 }}>
        {punto.ordenes} {punto.ordenes === 1 ? 'orden' : 'órdenes'}
      </div>
    </div>
  )
}

function TooltipEstado({
  active,
  payload,
  total,
}: {
  active?: boolean
  payload?: { payload: { etiqueta: string; cantidad: number; total: number } }[]
  total: number
}) {
  const dato = payload?.[0]?.payload
  if (!active || !dato) return null
  return (
    <div className="tooltip-grafico">
      <div className="fecha">{dato.etiqueta}</div>
      <div className="dato">
        {dato.cantidad} {dato.cantidad === 1 ? 'orden' : 'órdenes'} ·{' '}
        {Math.round((dato.cantidad / total) * 100)}%
      </div>
      <div style={{ color: '#98a2b3', fontSize: 11.5 }}>{soles(dato.total)}</div>
    </div>
  )
}

function partes(iso: string): [number, number, number] {
  const [anio, mes, dia] = iso.split('-').map(Number)
  return [anio ?? 1970, mes ?? 1, dia ?? 1]
}

function diaCorto(iso: string): string {
  const [, mes, dia] = partes(iso)
  return `${dia}/${mes}`
}

function diaLargo(iso: string): string {
  const [anio, mes, dia] = partes(iso)
  return new Intl.DateTimeFormat('es-PE', { dateStyle: 'full' }).format(new Date(anio, mes - 1, dia))
}
