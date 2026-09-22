import { Contenedor } from '@/componentes/disposicion/Seccion'

/** Esqueleto de la ficha: la proporcion de la imagen esta fijada para que la
 *  galeria no empuje el contenido al cargar. */
export default function CargandoProducto() {
  return (
    <Contenedor className="py-8">
      <div className="mb-4 h-4 w-64 animate-pulse rounded bg-borde" />
      <div className="grid gap-8 lg:grid-cols-2 lg:gap-12">
        <div className="aspect-square animate-pulse rounded-marca bg-white" />
        <div className="space-y-4">
          <div className="h-3 w-24 animate-pulse rounded bg-borde" />
          <div className="h-8 w-4/5 animate-pulse rounded bg-borde" />
          <div className="h-4 w-full animate-pulse rounded bg-borde" />
          <div className="h-48 animate-pulse rounded-marca bg-white" />
        </div>
      </div>
    </Contenedor>
  )
}
