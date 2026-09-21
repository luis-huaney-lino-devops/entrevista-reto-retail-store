import { Suspense, lazy } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import Disposicion from './componentes/Disposicion'
import Acceso from './paginas/Acceso'
import Administradores from './paginas/Administradores'
import Categorias from './paginas/Categorias'
import Clientes from './paginas/Clientes'
import Cupones from './paginas/Cupones'
import Marcas from './paginas/Marcas'
import MiCuenta from './paginas/MiCuenta'
import Ordenes from './paginas/Ordenes'
import Papelera from './paginas/Papelera'
import ProductoEditor from './paginas/ProductoEditor'
import Productos from './paginas/Productos'
import Subcategorias from './paginas/Subcategorias'
import { esSuperadministrador, useSesion } from './sesion/SesionContexto'

/**
 * El tablero se carga aparte porque arrastra la librería de gráficos: sin
 * esto, quien solo entra a editar un precio descarga 400 kB que no va a usar,
 * y el formulario de acceso los descarga antes incluso de saber quién es.
 */
const Tablero = lazy(() => import('./paginas/Tablero'))

/**
 * Conversaciones se carga aparte por el motivo contrario al del tablero: no
 * pesa, pero abre el WebSocket y monta un hilo entero. Quien nunca entra a
 * atención al cliente no tiene por qué descargarlo.
 */
const Conversaciones = lazy(() => import('./paginas/Conversaciones'))

/**
 * A dónde ir después de entrar.
 *
 * <p>Se comprueba que sea una ruta de este panel y no `/login`: el estado de
 * la navegación lo puede fabricar cualquiera, y una cadena que empiece por
 * `//` o por `http:` convertiría el acceso en un salto a otro sitio.
 */
function destinoTrasEntrar(estado: unknown): string {
  const desde = (estado as { desde?: unknown } | null)?.desde
  if (typeof desde !== 'string' || !desde.startsWith('/') || desde.startsWith('//')) {
    return '/productos'
  }
  return desde.startsWith('/login') ? '/productos' : desde
}

export default function App() {
  const { administrador, comprobando } = useSesion()
  const ubicacion = useLocation()

  // Mientras se comprueba la cookie de refresco no se decide nada: pintar el
  // formulario de acceso aquí haría parpadear el login en cada recarga de
  // alguien que sí tiene sesión.
  if (comprobando) {
    return <p className="cargando">Comprobando la sesión…</p>
  }

  // Sin sesión todo lleva a /login, y el acceso tiene su propia URL en vez de
  // pintarse encima de la que hubiera: así «cerrar sesión» lleva a un sitio
  // concreto, el navegador puede guardarla y el formulario no aparece bajo una
  // dirección que dice /productos.
  if (!administrador) {
    return (
      <Routes>
        <Route path="/login" element={<Acceso />} />
        <Route
          path="*"
          element={
            // De dónde venía, para devolverle ahí tras entrar en lugar de
            // soltarle siempre en la misma página.
            <Navigate to="/login" replace state={{ desde: ubicacion.pathname + ubicacion.search }} />
          }
        />
      </Routes>
    )
  }

  const volverA = destinoTrasEntrar(ubicacion.state)

  return (
    <Disposicion>
      <Suspense fallback={<p className="cargando">Cargando…</p>}>
        <Routes>
          {/* La raíz lleva a productos y no al tablero: el tablero se mira
              una vez al día, el catálogo se trabaja todo el día. */}
          {/* Ya dentro, /login no tiene nada que enseñar: devuelve a donde
              se intentaba ir antes de que le pidieran la contraseña. */}
          <Route path="/login" element={<Navigate to={volverA} replace />} />
          <Route path="/" element={<Navigate to="/productos" replace />} />
          <Route path="/tablero" element={<Tablero />} />
          <Route path="/productos" element={<Productos />} />
          <Route path="/productos/nuevo" element={<ProductoEditor />} />
          <Route path="/productos/:id" element={<ProductoEditor />} />
          <Route path="/categorias" element={<Categorias />} />
          <Route path="/subcategorias" element={<Subcategorias />} />
          <Route path="/marcas" element={<Marcas />} />
          <Route path="/ordenes" element={<Ordenes />} />
          <Route path="/clientes" element={<Clientes />} />
          <Route path="/conversaciones" element={<Conversaciones />} />
          <Route path="/cupones" element={<Cupones />} />
          {/* Sin entrada en el menú a propósito: la papelera es una red de
              seguridad, no una sección de trabajo diario. Se llega por la URL
              o desde el aviso que sale al eliminar algo. */}
          <Route path="/papelera" element={<Papelera />} />
          <Route path="/mi-cuenta" element={<MiCuenta />} />
          {esSuperadministrador(administrador) && (
            <Route path="/administradores" element={<Administradores />} />
          )}
          <Route path="*" element={<Navigate to="/productos" replace />} />
        </Routes>
      </Suspense>
    </Disposicion>
  )
}
