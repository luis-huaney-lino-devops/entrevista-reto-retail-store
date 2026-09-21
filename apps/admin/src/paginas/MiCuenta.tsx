import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation } from '@tanstack/react-query'
import { ErrorApi } from '../api/cliente'
import { sesion } from '../api/recursos'
import { AvisoError, AvisoInfo } from '../componentes/Aviso'
import Campo from '../componentes/Campo'
import { fecha } from '../componentes/Insignia'
import { useToast } from '../componentes/Toast'
import { useSesion } from '../sesion/SesionContexto'

export default function MiCuenta() {
  const { administrador, salir } = useSesion()
  const toast = useToast()
  const [actual, setActual] = useState('')
  const [nueva, setNueva] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [hecho, setHecho] = useState(false)

  const cambiar = useMutation({
    mutationFn: () => sesion.cambiarContrasena(actual, nueva),
    onSuccess: () => {
      setHecho(true)
      toast.exito('Contraseña cambiada', 'Vas a tener que entrar de nuevo')
      // Cambiar la contraseña revoca las sesiones en el servidor, incluida
      // esta: seguir en el panel solo llevaría a un 401 en el siguiente clic.
      setTimeout(() => void salir(), 2000)
    },
    onError: (fallo) => {
      setError(fallo)
      toast.error(fallo)
    },
  })

  function enviar(evento: FormEvent) {
    evento.preventDefault()
    setError(null)
    cambiar.mutate()
  }

  const errorApi = error instanceof ErrorApi ? error : null

  return (
    <>
      <div className="encabezado">
        <div>
          <h2>Mi cuenta</h2>
          <p>Datos de acceso al panel.</p>
        </div>
      </div>

      <div className="rejilla-2">
        <div className="tarjeta">
          <h3>Cambiar contraseña</h3>

          {hecho ? (
            <AvisoInfo>Contraseña cambiada. Cerrando la sesión…</AvisoInfo>
          ) : (
            <form onSubmit={enviar}>
              <AvisoError error={error} />

              <Campo
                nombre="contrasenaActual"
                etiqueta="Contraseña actual"
                ayuda="Se pide aunque ya estés dentro: evita que quien pase por un panel abierto se apropie de la cuenta."
                error={errorApi?.deCampo('contrasenaActual')}
              >
                {({ id, className }) => (
                  <input
                    id={id}
                    className={className}
                    type="password"
                    autoComplete="current-password"
                    value={actual}
                    onChange={(e) => setActual(e.target.value)}
                  />
                )}
              </Campo>

              <Campo
                nombre="contrasenaNueva"
                etiqueta="Contraseña nueva"
                ayuda="Mínimo 10 caracteres. Una frase larga es más segura que una palabra con símbolos."
                error={errorApi?.deCampo('contrasenaNueva')}
              >
                {({ id, className }) => (
                  <input
                    id={id}
                    className={className}
                    type="password"
                    autoComplete="new-password"
                    value={nueva}
                    onChange={(e) => setNueva(e.target.value)}
                  />
                )}
              </Campo>

              <button type="submit" className="primario" disabled={cambiar.isPending}>
                {cambiar.isPending ? 'Guardando…' : 'Cambiar contraseña'}
              </button>
            </form>
          )}
        </div>

        <div className="tarjeta">
          <h3>Datos</h3>
          <dl className="lista-definiciones">
            <dt>Usuario</dt>
            <dd>
              <code>{administrador?.usuario}</code>
            </dd>
            <dt>Nombre</dt>
            <dd>{administrador?.nombre}</dd>
            <dt>Rol</dt>
            <dd>
              {administrador?.rol === 'SUPERADMINISTRADOR' ? 'Superadministrador' : 'Administrador'}
            </dd>
            <dt>Último acceso</dt>
            <dd>{fecha(administrador?.ultimoAccesoEn ?? null)}</dd>
            <dt>Cuenta creada</dt>
            <dd>{fecha(administrador?.creadoEn ?? null)}</dd>
          </dl>
        </div>
      </div>
    </>
  )
}
