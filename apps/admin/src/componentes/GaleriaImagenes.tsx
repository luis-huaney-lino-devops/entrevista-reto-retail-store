import { useRef, useState } from 'react'
import { ImagePlus, Loader2, Star, X } from 'lucide-react'
import { archivos as api } from '../api/recursos'
import type { Imagen } from '../api/tipos'
import { motivoDeRechazo, nombreLegible } from './subida'
import { useToast } from './Toast'

type Props = {
  imagenes: Imagen[]
  alCambiar: (imagenes: Imagen[]) => void
  /** Nombre del producto: describe las fotos cuando no hay otro texto (RN-074). */
  textoAltBase: string
}

/**
 * Galería del producto: soltar varias, reordenar arrastrando, quitar.
 *
 * <p><strong>La primera imagen es la principal</strong> y es la que se ve en
 * la rejilla de la tienda. Por eso el orden se cambia arrastrando y no con un
 * campo numérico: la relación entre «lo que veo» y «lo que sale publicado»
 * tiene que ser directa.
 */
export default function GaleriaImagenes({ imagenes, alCambiar, textoAltBase }: Props) {
  const toast = useToast()
  const entrada = useRef<HTMLInputElement>(null)
  const [encima, setEncima] = useState(false)
  const [subiendo, setSubiendo] = useState(0)
  const [arrastrando, setArrastrando] = useState<number | null>(null)
  const [destino, setDestino] = useState<number | null>(null)

  async function subir(seleccionados: FileList | null) {
    if (!seleccionados || seleccionados.length === 0) return

    const validos: File[] = []
    for (const archivo of Array.from(seleccionados)) {
      const motivo = motivoDeRechazo(archivo)
      if (motivo) {
        toast.error(motivo)
      } else {
        validos.push(archivo)
      }
    }
    if (validos.length === 0) return

    setSubiendo(validos.length)
    const nuevas: Imagen[] = []
    for (const [indice, archivo] of validos.entries()) {
      try {
        const posicion = imagenes.length + indice + 1
        const textoAlt = textoAltBase.trim()
          ? `${textoAltBase.trim()} (foto ${posicion})`
          : nombreLegible(archivo.name)
        const subido = await api.subir(archivo, textoAlt)
        nuevas.push({
          archivoId: subido.id,
          miniatura: subido.urls.MINIATURA,
          tarjeta: subido.urls.TARJETA,
          detalle: subido.urls.DETALLE,
          original: subido.urls.ORIGINAL,
          textoAlt: subido.textoAlt,
          ancho: subido.ancho,
          alto: subido.alto,
        })
      } catch (fallo) {
        toast.error(fallo, `No se pudo subir «${archivo.name}»`)
      } finally {
        setSubiendo((quedan) => quedan - 1)
      }
    }

    if (nuevas.length > 0) {
      // Se deduplica por id: el backend devuelve el archivo existente cuando la
      // imagen ya estaba subida, y sin esto la misma foto aparecería dos veces
      // en la galería aunque en la base fuera una sola.
      const yaEstan = new Set(imagenes.map((i) => i.archivoId))
      const anadir = nuevas.filter((i) => !yaEstan.has(i.archivoId))
      const repetidas = nuevas.length - anadir.length

      alCambiar([...imagenes, ...anadir])
      if (anadir.length > 0) {
        toast.exito(
          anadir.length === 1 ? 'Imagen añadida' : `${anadir.length} imágenes añadidas`,
          'Convertidas a WebP en cuatro tamaños',
        )
      }
      if (repetidas > 0) {
        toast.info(
          repetidas === 1 ? 'Una imagen ya estaba' : `${repetidas} imágenes ya estaban`,
          'Se reutilizó el archivo existente en lugar de duplicarlo',
        )
      }
    }
    if (entrada.current) entrada.current.value = ''
  }

  function reordenar(desde: number, hasta: number) {
    if (desde === hasta) return
    const copia = [...imagenes]
    const [movida] = copia.splice(desde, 1)
    if (movida) {
      copia.splice(hasta, 0, movida)
      alCambiar(copia)
    }
  }

  return (
    <div>
      {imagenes.length > 0 && (
        <div className="galeria">
          {imagenes.map((imagen, indice) => (
            <figure
              key={imagen.archivoId}
              className={[
                indice === 0 ? 'principal' : '',
                arrastrando === indice ? 'arrastrando' : '',
                destino === indice && arrastrando !== indice ? 'destino' : '',
              ].join(' ')}
              draggable
              onDragStart={() => setArrastrando(indice)}
              onDragEnd={() => {
                setArrastrando(null)
                setDestino(null)
              }}
              onDragOver={(evento) => {
                evento.preventDefault()
                setDestino(indice)
              }}
              onDrop={(evento) => {
                evento.preventDefault()
                if (arrastrando !== null) reordenar(arrastrando, indice)
                setArrastrando(null)
                setDestino(null)
              }}
            >
              <img src={imagen.tarjeta} alt={imagen.textoAlt} title={imagen.textoAlt} />
              <button
                type="button"
                className="quitar"
                title="Quitar"
                aria-label={`Quitar ${imagen.textoAlt}`}
                onClick={() => alCambiar(imagenes.filter((otra) => otra.archivoId !== imagen.archivoId))}
              >
                <X size={13} />
              </button>
              <figcaption>
                {indice === 0 ? (
                  <>
                    <Star size={11} fill="currentColor" /> Principal
                  </>
                ) : (
                  `Foto ${indice + 1}`
                )}
              </figcaption>
            </figure>
          ))}
        </div>
      )}

      <div
        className={`zona-suelta ${encima ? 'encima' : ''} ${imagenes.length > 0 ? 'ocupada' : ''}`}
        role="button"
        tabIndex={0}
        onClick={() => subiendo === 0 && entrada.current?.click()}
        onKeyDown={(evento) => {
          if (evento.key === 'Enter' || evento.key === ' ') {
            evento.preventDefault()
            entrada.current?.click()
          }
        }}
        onDragOver={(evento) => {
          evento.preventDefault()
          setEncima(true)
        }}
        onDragLeave={() => setEncima(false)}
        onDrop={(evento) => {
          evento.preventDefault()
          setEncima(false)
          // Una figura de la galería arrastrada sobre la zona no es un archivo
          // nuevo: sin esta comprobación, reordenar abriría el selector.
          if (evento.dataTransfer.files.length > 0) {
            void subir(evento.dataTransfer.files)
          }
        }}
      >
        {subiendo > 0 ? (
          <div className="subiendo">
            <Loader2 size={16} className="girando" />
            Procesando {subiendo} {subiendo === 1 ? 'imagen' : 'imágenes'}…
          </div>
        ) : (
          <>
            <ImagePlus size={imagenes.length > 0 ? 20 : 26} className="icono-suelta" />
            <p>
              {imagenes.length > 0
                ? 'Añadir más imágenes'
                : 'Arrastra las fotos aquí o pulsa para elegirlas'}
            </p>
            <small>
              {imagenes.length > 0
                ? 'Arrastra una foto sobre otra para cambiar el orden'
                : 'JPEG, PNG o WebP · máximo 10 MB cada una'}
            </small>
          </>
        )}
      </div>

      <input
        ref={entrada}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        multiple
        hidden
        onChange={(evento) => void subir(evento.target.files)}
      />
    </div>
  )
}
