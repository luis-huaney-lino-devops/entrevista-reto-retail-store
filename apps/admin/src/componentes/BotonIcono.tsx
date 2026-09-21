import type { LucideIcon } from 'lucide-react'

type Props = {
  icono: LucideIcon
  /** Lo que hace. Es el nombre accesible y también el tooltip. */
  etiqueta: string
  alPulsar: () => void
  /** Rojo, para lo que quita cosas. */
  peligro?: boolean
  desactivado?: boolean
  /** Por qué está desactivado. Sin esto, un botón gris no explica nada. */
  motivo?: string
}

/**
 * Una acción de fila.
 *
 * <p>Solo icono: una tabla con «Editar · Desactivar · Eliminar» escrito en
 * cada fila dedica más ancho a repetir las mismas tres palabras que a los
 * datos. El nombre va en `aria-label` y en `title`, así que sigue estando
 * para quien navega con lector de pantalla y para quien pasa el ratón.
 */
export default function BotonIcono({ icono: Icono, etiqueta, alPulsar, peligro, desactivado, motivo }: Props) {
  return (
    <button
      type="button"
      className={`accion-icono ${peligro ? 'peligro' : ''}`}
      aria-label={etiqueta}
      title={desactivado && motivo ? motivo : etiqueta}
      disabled={desactivado}
      onClick={alPulsar}
    >
      <Icono size={16} />
    </button>
  )
}
