import { Contenedor } from '@/componentes/disposicion/Seccion'
import { EsqueletoRejilla } from '@/componentes/ui/Estados'

/** Esqueleto del catalogo: imita la columna de filtros y la rejilla, con el
 *  mismo numero de tarjetas, para que no haya salto de maquetacion al llegar
 *  los datos. */
export default function CargandoCatalogo() {
  return (
    <Contenedor className="py-8">
      <div className="mb-6 h-8 w-48 animate-pulse rounded bg-borde" />
      <div className="grid gap-6 lg:grid-cols-[250px_minmax(0,1fr)] lg:gap-8">
        <div className="hidden h-96 animate-pulse rounded-marca bg-white lg:block" />
        <EsqueletoRejilla cuantas={12} />
      </div>
    </Contenedor>
  )
}
