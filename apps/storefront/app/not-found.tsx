import Link from 'next/link'

import { Contenedor } from '@/componentes/disposicion/Seccion'
import { EnlaceBoton } from '@/componentes/ui/Boton'

/**
 * Pagina 404.
 *
 * Un producto inexistente llega aqui con `notFound()`, que da un `404` de
 * verdad. Devolver `200` con un mensaje de "no encontrado" deja al buscador
 * indexando paginas vacias.
 */
export default function NoEncontrada() {
  return (
    <Contenedor className="flex flex-col items-center justify-center gap-4 py-24 text-center">
      <p className="font-marca text-6xl font-bold text-marca">404</p>
      <h1 className="font-marca text-2xl font-bold text-tinta">No encontramos esta pagina</h1>
      <p className="max-w-md text-sm text-texto-suave">
        Puede que el producto ya no este en el catalogo o que la direccion tenga una errata.
      </p>
      <div className="mt-2 flex flex-wrap justify-center gap-3">
        <EnlaceBoton href="/productos" variante="primario">
          Ver el catalogo
        </EnlaceBoton>
        <Link href="/" className="inline-flex h-10 items-center px-4 text-sm font-semibold text-tinta-claro hover:underline">
          Volver a la portada
        </Link>
      </div>
    </Contenedor>
  )
}
