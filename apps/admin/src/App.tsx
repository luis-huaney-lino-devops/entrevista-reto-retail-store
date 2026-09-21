import { Suspense, lazy } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
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

export default function App() {
  const { administrador, comprobando } = useSesion()

  // Mientras se comprueba la cookie de refresco no se decide nada: pintar el
  // formulario de acceso aquí haría parpadear el login en cada recarga de
  // alguien que sí tiene sesión.
  if (comprobando) {
    return <p className="cargando">Comprobando la sesión…</p>
  }

  if (!administrador) {
    return <Acceso />
  }

  return (
    <Disposicion>
      <Suspense fallback={<p className="cargando">Cargando…</p>}>
        <Routes>
          {/* La raíz lleva a productos y no al tablero: el tablero se mira
              una vez al día, el catálogo se trabaja todo el día. */}
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
