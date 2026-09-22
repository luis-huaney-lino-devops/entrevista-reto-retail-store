'use client'

import { useState } from 'react'
import Image from 'next/image'

import { clases } from '@/lib/formato'
import { TAMANOS } from '@/lib/imagenes'
import type { ImagenProducto } from '@/lib/tipos'

/**
 * Galeria del producto.
 *
 * Es cliente porque cambia de imagen al pulsar, y es lo unico de la ficha que
 * lo es. El resto de la pagina —precio, descripcion, relacionados, JSON-LD— se
 * queda en el servidor.
 *
 * La imagen grande lleva `priority`: es el LCP de esta pagina. Las miniaturas
 * no, porque precargarlas todas las pondria a competir por el mismo ancho de
 * banda y retrasaria justo la que se queria acelerar.
 *
 * El `alt` viene de la API —es obligatorio al subir la imagen— y se usa tal
 * cual. No se sustituye por el nombre del producto "porque queda mejor": ese
 * texto lo escribio alguien mirando la foto.
 */

type Propiedades = {
  imagenes: ImagenProducto[]
  nombreProducto: string
}

export function Galeria({ imagenes, nombreProducto }: Propiedades) {
  const [activa, setActiva] = useState(0)

  if (imagenes.length === 0) {
    return (
      <div className="flex aspect-square items-center justify-center rounded-marca border border-borde bg-superficie-alt text-sm text-texto-suave">
        Sin imagen
      </div>
    )
  }

  const actual = imagenes[activa] ?? imagenes[0]
  if (!actual) return null

  const grande = actual.detalle ?? actual.tarjeta ?? actual.original
  const alt = actual.textoAlt ?? nombreProducto

  return (
    <div className="flex flex-col gap-3">
      <div className="relative aspect-square overflow-hidden rounded-marca border border-borde bg-white">
        {grande ? (
          <Image
            key={actual.archivoId}
            src={grande}
            alt={alt}
            fill
            sizes={TAMANOS.detalle}
            priority
            className="object-contain p-6"
          />
        ) : null}
      </div>

      {imagenes.length > 1 && (
        <ul className="flex flex-wrap gap-2" aria-label={`Imagenes de ${nombreProducto}`}>
          {imagenes.map((img, i) => {
            const mini = img.miniatura ?? img.tarjeta ?? img.original
            const activaEsta = i === activa
            return (
              <li key={img.archivoId}>
                <button
                  type="button"
                  onClick={() => setActiva(i)}
                  aria-label={`Ver imagen ${i + 1} de ${imagenes.length}`}
                  aria-current={activaEsta ? 'true' : undefined}
                  className={clases(
                    'relative h-16 w-16 overflow-hidden rounded-marca border-2 bg-white transition',
                    activaEsta ? 'border-marca' : 'border-borde hover:border-borde-fuerte',
                  )}
                >
                  {mini ? (
                    <Image src={mini} alt="" fill sizes={TAMANOS.miniatura} className="object-contain p-1" />
                  ) : null}
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
