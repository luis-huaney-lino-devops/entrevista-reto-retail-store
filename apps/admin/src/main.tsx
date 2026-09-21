import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import { ProveedorConfirmacion } from './componentes/Confirmar'
import { ProveedorToast } from './componentes/Toast'
import { ProveedorSesion } from './sesion/SesionContexto'
import './estilos.css'

const clienteConsultas = new QueryClient({
  defaultOptions: {
    queries: {
      // El panel lo usan pocas personas a la vez y los datos cambian poco:
      // refrescar al volver a la pestaña solo produce parpadeos.
      refetchOnWindowFocus: false,
      // Un 401, un 403 o un 409 no se arreglan repitiendo. El único reintento
      // con sentido es el del refresco, y ese vive en el cliente HTTP.
      retry: false,
      staleTime: 30_000,
    },
  },
})

const raiz = document.getElementById('raiz')
if (!raiz) {
  throw new Error('Falta el elemento #raiz en index.html')
}

// El proveedor de toasts va por fuera de la sesión: un fallo al recuperar la
// sesión también tiene que poder avisarse.
createRoot(raiz).render(
  <StrictMode>
    <QueryClientProvider client={clienteConsultas}>
      <ProveedorToast>
        <BrowserRouter>
          <ProveedorSesion>
            {/* La confirmación va dentro del router: su diálogo enlaza a otras
                secciones, y fuera no tendría a dónde navegar. */}
            <ProveedorConfirmacion>
              <App />
            </ProveedorConfirmacion>
          </ProveedorSesion>
        </BrowserRouter>
      </ProveedorToast>
    </QueryClientProvider>
  </StrictMode>,
)
