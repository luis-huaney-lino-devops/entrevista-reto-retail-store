import { useEffect, useRef } from 'react'
import type { ReactNode } from 'react'
import { X } from 'lucide-react'

type Props = {
  titulo: string
  children: ReactNode
  alCerrar: () => void
  ancho?: boolean
  /** Botones del pie. Si falta, el diálogo solo tiene cuerpo. */
  pie?: ReactNode
}

export default function Dialogo({ titulo, children, alCerrar, ancho, pie }: Props) {
  const caja = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const alPulsar = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') alCerrar()
    }
    document.addEventListener('keydown', alPulsar)

    // Sin esto, la página de atrás sigue moviéndose bajo el diálogo y al
    // cerrarlo la lista ha cambiado de sitio.
    const desbordeAnterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    // El foco entra en el diálogo: si se queda en el botón que lo abrió, la
    // primera tecla actúa sobre la página de detrás.
    caja.current?.focus()

    return () => {
      document.removeEventListener('keydown', alPulsar)
      document.body.style.overflow = desbordeAnterior
    }
  }, [alCerrar])

  return (
    <div className="fondo-dialogo" onMouseDown={alCerrar} role="presentation">
      <div
        ref={caja}
        tabIndex={-1}
        className={`dialogo ${ancho ? 'ancho' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-label={titulo}
        style={{ outline: 'none' }}
        onMouseDown={(evento) => evento.stopPropagation()}
      >
        <header>
          <h3>{titulo}</h3>
          <button type="button" className="icono" aria-label="Cerrar" onClick={alCerrar}>
            <X size={16} />
          </button>
        </header>
        <div className="cuerpo">{children}</div>
        {pie && <footer>{pie}</footer>}
      </div>
    </div>
  )
}
