import { useState } from 'react'
import type { FormEvent } from 'react'
import {
  AlertCircle,
  BarChart3,
  Check,
  Eye,
  EyeOff,
  Loader2,
  Lock,
  ShieldCheck,
  User,
} from 'lucide-react'
import { ErrorApi } from '../api/cliente'
import Campo from '../componentes/Campo'
import { useSesion } from '../sesion/SesionContexto'

const VENTAJAS = [
  'Catálogo de dos niveles con marcas y variantes de imagen',
  'Imágenes convertidas a WebP en cuatro tamaños al subirlas',
  'Ventas, vistas y stock en un solo tablero',
]

export default function Acceso() {
  const { acceder } = useSesion()
  const [usuario, setUsuario] = useState('')
  const [contrasena, setContrasena] = useState('')
  const [verContrasena, setVerContrasena] = useState(false)
  const [error, setError] = useState<ErrorApi | null>(null)
  const [enviando, setEnviando] = useState(false)

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      await acceder(usuario.trim(), contrasena)
    } catch (fallo) {
      setError(
        fallo instanceof ErrorApi
          ? fallo
          : new ErrorApi(0, 'SIN_RED', 'No se pudo contactar con el servidor. ¿Está levantada la API?'),
      )
    } finally {
      setEnviando(false)
    }
  }

  // El límite de intentos deja la cuenta en espera unos minutos: insistir en
  // ese momento solo gasta otro intento, así que el botón se bloquea.
  const enEspera = error?.codigo === 'TOO_MANY_REQUESTS'

  return (
    <div className="acceso">
      <aside className="panel-marca">
        <div className="logo-grande">
          <img src="/logo.webp" alt="Retail Store" width={480} height={343} />
        </div>

        <div>
          <h2>El catálogo, bajo control</h2>
          <p>
            Productos, marcas, categorías, cupones y órdenes desde un solo panel, con las reglas de
            negocio aplicadas en el servidor.
          </p>
          <ul>
            {VENTAJAS.map((ventaja) => (
              <li key={ventaja}>
                <Check size={16} />
                {ventaja}
              </li>
            ))}
          </ul>
        </div>

        <footer>
          <BarChart3 size={13} style={{ verticalAlign: -2, marginRight: 6 }} />
          Panel de administración · acceso restringido
        </footer>
      </aside>

      <main className="panel-formulario">
        <form onSubmit={(e) => void enviar(e)} noValidate>
          {/* Cuando el panel lateral no cabe, la marca se repone aquí. */}
          <div className="logo-movil">
            <img src="/logo.webp" alt="Retail Store" width={480} height={343} />
          </div>

          <h3>Entrar al panel</h3>
          <p className="intro">Usa las credenciales que te dieron. No hay registro público.</p>

          {error && (
            <div className="aviso error" role="alert">
              <AlertCircle size={17} />
              <div>
                {error.message}
                {enEspera && (
                  <div className="detalle" style={{ fontFamily: 'inherit' }}>
                    Espera a que pase el tiempo indicado antes de volver a intentarlo.
                  </div>
                )}
              </div>
            </div>
          )}

          <Campo nombre="usuario" etiqueta="Usuario" error={error?.deCampo('usuario')}>
            {({ id, className }) => (
              <div className="entrada-icono">
                <User size={16} />
                <input
                  id={id}
                  className={className}
                  type="text"
                  autoComplete="username"
                  autoFocus
                  spellCheck={false}
                  placeholder="admin"
                  value={usuario}
                  onChange={(e) => setUsuario(e.target.value)}
                />
              </div>
            )}
          </Campo>

          <Campo nombre="contrasena" etiqueta="Contraseña" error={error?.deCampo('contrasena')}>
            {({ id, className }) => (
              <div className="entrada-icono">
                <Lock size={16} />
                <input
                  id={id}
                  className={`${className} con-ojo`}
                  type={verContrasena ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="••••••••••"
                  value={contrasena}
                  onChange={(e) => setContrasena(e.target.value)}
                />
                {/* Ver lo que se escribe evita el tercer intento fallido por una
                    tecla mayúsculas activada. */}
                <button
                  type="button"
                  className="ojo"
                  onClick={() => setVerContrasena((v) => !v)}
                  title={verContrasena ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                  aria-label={verContrasena ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                >
                  {verContrasena ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
            )}
          </Campo>

          <button
            type="submit"
            className="primario"
            disabled={enviando || enEspera || !usuario.trim() || !contrasena}
          >
            {enviando ? (
              <>
                <Loader2 size={16} className="girando" />
                Entrando…
              </>
            ) : (
              <>
                <ShieldCheck size={16} />
                Entrar
              </>
            )}
          </button>

          <div className="pista">
            Acceso de demostración: <strong>admin</strong> / <strong>AdminRetail2026!</strong>
          </div>
        </form>
      </main>
    </div>
  )
}
