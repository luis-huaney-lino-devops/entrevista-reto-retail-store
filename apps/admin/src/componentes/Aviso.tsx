import { AlertCircle, CheckCircle2, Info } from 'lucide-react'
import { ErrorApi } from '../api/cliente'

/**
 * Un error de la API convertido en algo accionable.
 *
 * <p>El mensaje del servidor ya viene en español y es específico, así que se
 * muestra tal cual. Lo que se añade es el desglose por campo, la lista de lo
 * que bloquea una desactivación y, en un 500, la referencia que lleva al log.
 *
 * <p>Convive con los toasts: el toast avisa de que algo pasó, y este aviso se
 * queda en el formulario mientras haya que corregir algo.
 */
export function AvisoError({ error }: { error: unknown }) {
  if (!error) return null

  if (error instanceof ErrorApi) {
    const bloqueantes = Array.isArray(error.extra.bloqueantes)
      ? (error.extra.bloqueantes as string[])
      : []

    return (
      <div className="aviso error" role="alert">
        <AlertCircle size={17} />
        <div style={{ minWidth: 0 }}>
          <div>{error.message}</div>

          {error.errores.length > 0 && (
            <ul>
              {error.errores.map((fallo) => (
                <li key={fallo.field}>
                  <strong>{fallo.field}</strong>: {fallo.message}
                </li>
              ))}
            </ul>
          )}

          {bloqueantes.length > 0 && (
            <ul>
              {bloqueantes.map((nombre) => (
                <li key={nombre}>{nombre}</li>
              ))}
              {typeof error.extra.totalBloqueantes === 'number' &&
                error.extra.totalBloqueantes > bloqueantes.length && (
                  <li className="secundario">
                    y {error.extra.totalBloqueantes - bloqueantes.length} más
                  </li>
                )}
            </ul>
          )}

          {typeof error.extra.correlationId === 'string' && error.estado >= 500 && (
            <div className="detalle">Referencia: {error.extra.correlationId}</div>
          )}
        </div>
      </div>
    )
  }

  return (
    <div className="aviso error" role="alert">
      <AlertCircle size={17} />
      <div>No se pudo completar la operación.</div>
    </div>
  )
}

export function AvisoExito({ mensaje }: { mensaje: string | null }) {
  if (!mensaje) return null
  return (
    <div className="aviso exito" role="status">
      <CheckCircle2 size={17} />
      <div>{mensaje}</div>
    </div>
  )
}

export function AvisoInfo({ children }: { children: React.ReactNode }) {
  return (
    <div className="aviso info">
      <Info size={17} />
      <div>{children}</div>
    </div>
  )
}
