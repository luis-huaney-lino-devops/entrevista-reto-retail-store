'use client'

import { useEffect } from 'react'
import { AlertTriangle } from 'lucide-react'

import { Contenedor } from '@/componentes/disposicion/Seccion'
import { Boton, EnlaceBoton } from '@/componentes/ui/Boton'

/**
 * Frontera de error de la tienda.
 *
 * **Tiene que ser un componente cliente**: recibe `reset()` y lo invoca al
 * pulsar "Reintentar". Es la unica forma de recuperarse sin recargar la pagina
 * entera.
 *
 * El `detail` crudo del error nunca se ensena. Lo que se ofrece es el `digest`,
 * que es lo que permite encontrar la traza en el registro del servidor.
 */
export default function ErrorTienda({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error('[storefront]', error)
  }, [error])

  return (
    <Contenedor className="flex flex-col items-center justify-center gap-4 py-24 text-center">
      <span className="inline-flex h-14 w-14 items-center justify-center rounded-full bg-peligro-suave text-peligro">
        <AlertTriangle size={26} aria-hidden />
      </span>
      <h1 className="font-marca text-2xl font-bold text-tinta">Algo no cargo bien</h1>
      <p className="max-w-md text-sm text-texto-suave">
        No pudimos traer esta parte de la tienda. Puede ser un corte momentaneo: vuelve a intentarlo.
      </p>
      {error.digest && <p className="cifra text-xs text-texto-suave">Referencia: {error.digest}</p>}
      <div className="mt-2 flex flex-wrap justify-center gap-3">
        <Boton onClick={reset} variante="primario">
          Reintentar
        </Boton>
        <EnlaceBoton href="/" variante="sutil">
          Ir a la portada
        </EnlaceBoton>
      </div>
    </Contenedor>
  )
}
