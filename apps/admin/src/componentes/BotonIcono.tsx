import type { LucideIcon } from 'lucide-react'

type Props = {
  icono: LucideIcon
  /** Lo que hace. Es el nombre accesible y también el tooltip. */
  etiqueta: string
  alPulsar: () => void
  /** Rojo, para lo que quita cosas. */
  peligro?: boolean
  /**
   * El botón refleja un estado encendido —destacado, fijado— y no solo una
   * acción. Se pinta relleno y en ámbar, y no se atenúa con el resto: aquí el
   * icono es un dato de la fila, no una acción que se ofrece al pasar.
   */
  activo?: boolean
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
export default function BotonIcono({
  icono: Icono,
  etiqueta,
  alPulsar,
  peligro,
  activo,
  desactivado,
  motivo,
}: Props) {
  return (
    <button
      type="button"
      className={`accion-icono ${peligro ? 'peligro' : ''} ${activo ? 'activo' : ''}`}
      aria-label={etiqueta}
      aria-pressed={activo}
      title={desactivado && motivo ? motivo : etiqueta}
      disabled={desactivado}
      onClick={alPulsar}
    >
      <Icono size={16} fill={activo ? 'currentColor' : 'none'} />
    </button>
  )
}
