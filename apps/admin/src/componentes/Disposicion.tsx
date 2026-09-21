import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import {
  FolderTree,
  Layers,
  LayoutDashboard,
  LogOut,
  MessagesSquare,
  Package,
  Receipt,
  ShieldCheck,
  Tag,
  Ticket,
  Users,
} from 'lucide-react'
import { esSuperadministrador, useSesion } from '../sesion/SesionContexto'
import { ProveedorTiempoReal } from '../tiemporeal/TiempoReal'
import Campana from './Campana'

const SECCIONES = [
  {
    grupo: 'Catálogo',
    enlaces: [
      { ruta: '/productos', etiqueta: 'Productos', icono: Package },
      { ruta: '/categorias', etiqueta: 'Categorías', icono: FolderTree },
      { ruta: '/subcategorias', etiqueta: 'Subcategorías', icono: Layers },
      { ruta: '/marcas', etiqueta: 'Marcas', icono: Tag },
    ],
  },
  {
    grupo: 'Ventas',
    enlaces: [
      { ruta: '/ordenes', etiqueta: 'Órdenes', icono: Receipt },
      { ruta: '/clientes', etiqueta: 'Clientes', icono: Users },
      { ruta: '/conversaciones', etiqueta: 'Conversaciones', icono: MessagesSquare },
      { ruta: '/cupones', etiqueta: 'Cupones', icono: Ticket },
    ],
  },
  {
    grupo: 'General',
    enlaces: [{ ruta: '/tablero', etiqueta: 'Tablero', icono: LayoutDashboard }],
  },
]

export default function Disposicion({ children }: { children: ReactNode }) {
  const { administrador, salir } = useSesion()

  return (
    // El canal en vivo se monta aquí y no en main.tsx: fuera de la sesión no
    // hay con qué pedir el ticket, y pediría uno en cada carga del login.
    <ProveedorTiempoReal>
      <div className="disposicion">
        <aside className="barra-lateral">
          {/* El logo ya lleva el nombre dentro: acompañarlo de «Retail Store ·
              Panel» sería decir dos veces lo mismo en el sitio donde menos
              espacio sobra. */}
          <div className="marca">
            <NavLink to="/productos" aria-label="Retail Store · ir al inicio">
              <img src="/logo.webp" alt="Retail Store" width={240} height={172} />
            </NavLink>
          </div>

          <nav>
            {SECCIONES.map((seccion) => (
              <div key={seccion.grupo}>
                <div className="grupo-nav">{seccion.grupo}</div>
                {seccion.enlaces.map((enlace) => (
                  <NavLink
                    key={enlace.ruta}
                    to={enlace.ruta}
                    className={({ isActive }) => (isActive ? 'activo' : '')}
                  >
                    <enlace.icono size={16} />
                    {enlace.etiqueta}
                  </NavLink>
                ))}
              </div>
            ))}

            {/* Ocultar lo que el rol no puede usar no es seguridad —el backend lo
                rechaza igual— pero evita ofrecer un botón que solo da un 403. */}
            {esSuperadministrador(administrador) && (
              <div>
                <div className="grupo-nav">Sistema</div>
                <NavLink to="/administradores" className={({ isActive }) => (isActive ? 'activo' : '')}>
                  <ShieldCheck size={16} />
                  Administradores
                </NavLink>
              </div>
            )}
          </nav>

          <div className="pie-lateral">
            <NavLink to="/mi-cuenta" className="tarjeta-usuario">
              <span className="avatar">{iniciales(administrador?.nombre ?? '?')}</span>
              <span className="datos">
                <span className="nombre">{administrador?.nombre}</span>
                <span className="rol">
                  {administrador?.rol === 'SUPERADMINISTRADOR' ? 'Superadministrador' : 'Administrador'}
                </span>
              </span>
            </NavLink>
            <button type="button" className="boton-salir" onClick={() => void salir()}>
              <LogOut size={14} />
              Cerrar sesión
            </button>
          </div>
        </aside>

        <div className="columna-principal">
          {/* La campana vive en la barra superior y no dentro de cada página:
              un aviso de stock tiene que verse estés donde estés. */}
          <header className="barra-superior">
            <Campana />
          </header>
          <main className="contenido">{children}</main>
        </div>
      </div>
    </ProveedorTiempoReal>
  )
}

/** «Administrador General» → «AG». Dos letras como mucho. */
function iniciales(nombre: string): string {
  return nombre
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((palabra) => palabra[0]?.toUpperCase() ?? '')
    .join('')
}
