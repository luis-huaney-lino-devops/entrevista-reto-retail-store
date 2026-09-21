import { useState } from 'react'
import type { FormEvent } from 'react'
import { AlertCircle, Eye, EyeOff, Loader2 } from 'lucide-react'
import { ErrorApi } from '../api/cliente'
import Campo from '../componentes/Campo'
import { useSesion } from '../sesion/SesionContexto'

/**
 * Entrar al panel. Nada más.
 *
 * <p>Sin panel lateral, sin lista de características, sin ilustración: esta
 * pantalla la ve gente que ya trabaja aquí y que solo quiere pasar de largo.
 * Contarle lo que hace el producto en el momento en que teclea su contraseña
 * es decorar el camino más corto del panel.
 */
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
      <form onSubmit={(e) => void enviar(e)} noValidate>
        <img className="logo" src="/logo.webp" alt="Retail Store" width={480} height={343} />

        <h1>Entrar al panel</h1>

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
            <input
              id={id}
              className={className}
              type="text"
              autoComplete="username"
              autoFocus
              spellCheck={false}
              value={usuario}
              onChange={(e) => setUsuario(e.target.value)}
            />
          )}
        </Campo>

        <Campo nombre="contrasena" etiqueta="Contraseña" error={error?.deCampo('contrasena')}>
          {({ id, className }) => (
            <div className="campo-contrasena">
              <input
                id={id}
                className={className}
                type={verContrasena ? 'text' : 'password'}
                autoComplete="current-password"
                value={contrasena}
                onChange={(e) => setContrasena(e.target.value)}
              />
              {/* Ver lo que se escribe evita el tercer intento fallido por una
                  tecla de mayúsculas activada. */}
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
            'Entrar'
          )}
        </button>

        <p className="pista">
          Demostración: <strong>admin</strong> · <strong>AdminRetail2026!</strong>
        </p>
      </form>
    </div>
  )
}
