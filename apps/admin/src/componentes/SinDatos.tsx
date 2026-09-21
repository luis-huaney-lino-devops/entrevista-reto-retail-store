import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'

/**
 * El estado vacío de una tabla.
 *
 * <p>Una tabla sin filas y sin explicación deja la duda de si no hay datos o
 * si algo falló. Aquí se dice cuál de las dos cosas es y qué hacer.
 */
export default function SinDatos({
  icono: Icono,
  titulo,
  detalle,
  accion,
}: {
  icono: LucideIcon
  titulo: string
  detalle?: string
  accion?: ReactNode
}) {
  return (
    <div className="sin-datos">
      <Icono size={30} />
      <p>{titulo}</p>
      {detalle && <small>{detalle}</small>}
      {accion && <div style={{ marginTop: 14 }}>{accion}</div>}
    </div>
  )
}
