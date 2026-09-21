import { useRef, useState } from 'react'
import { Image as ImageIcon, Loader2, Trash2 } from 'lucide-react'
import { archivos as api } from '../api/recursos'
import type { Archivo } from '../api/tipos'
import { motivoDeRechazo, nombreLegible, pesoLegible } from './subida'
import { useToast } from './Toast'

type Props = {
  /** Imagen actual, o null. */
  archivo: Archivo | null
  alCambiar: (archivo: Archivo | null) => void
  /**
   * Con qué describir la imagen si el administrador no escribe nada: el nombre
   * de la marca, de la categoría, del producto (RN-074).
   */
  textoAltBase: string
  etiqueta?: string
}

/**
 * Una imagen, con arrastrar y soltar y vista previa.
 *
 * <p>La vista previa se pinta desde el archivo local **antes** de que termine
 * la subida. El procesado —convertir a WebP y sacar cuatro tamaños— tarda
 * cerca de un segundo, y una caja gris durante ese segundo hace dudar de si el
 * clic funcionó.
 */
export default function CampoImagen({ archivo, alCambiar, textoAltBase, etiqueta }: Props) {
  const toast = useToast()
  const entrada = useRef<HTMLInputElement>(null)
  const [encima, setEncima] = useState(false)
  const [subiendo, setSubiendo] = useState(false)
  const [previaLocal, setPreviaLocal] = useState<string | null>(null)

  async function subir(archivos: FileList | null) {
    const elegido = archivos?.[0]
    if (!elegido) return

    const motivo = motivoDeRechazo(elegido)
    if (motivo) {
      toast.error(motivo)
      return
    }

    const urlLocal = URL.createObjectURL(elegido)
    setPreviaLocal(urlLocal)
    setSubiendo(true)
    try {
      const subido = await api.subir(elegido, textoAltBase.trim() || nombreLegible(elegido.name))
      alCambiar(subido)
      toast.exito('Imagen subida', `Convertida a WebP · ${subido.ancho}×${subido.alto}`)
    } catch (fallo) {
      toast.error(fallo)
    } finally {
      setSubiendo(false)
      setPreviaLocal(null)
      // Sin revocar, cada intento deja un blob retenido en memoria.
      URL.revokeObjectURL(urlLocal)
      if (entrada.current) entrada.current.value = ''
    }
  }

  const urlVisible = previaLocal ?? archivo?.urls.TARJETA ?? null

  return (
    <div className="campo">
      {etiqueta && <label>{etiqueta}</label>}

      {urlVisible ? (
        <div className="tarjeta" style={{ padding: 12 }}>
          <div className="vista-previa">
            <img src={urlVisible} alt={archivo?.textoAlt ?? 'Vista previa'} />
            <div className="datos">
              {subiendo ? (
                <div className="subiendo" style={{ justifyContent: 'flex-start', padding: 0 }}>
                  <Loader2 size={15} className="girando" />
                  Procesando y convirtiendo a WebP…
                </div>
              ) : (
                archivo && (
                  <>
                    <div className="nombre">{archivo.textoAlt}</div>
                    <small>
                      {archivo.ancho}×{archivo.alto} · {pesoLegible(archivo.bytes)} · WebP
                    </small>
                  </>
                )
              )}
            </div>
            {!subiendo && (
              <div className="acciones">
                <button type="button" onClick={() => entrada.current?.click()}>
                  Cambiar
                </button>
                <button
                  type="button"
                  className="icono"
                  title="Quitar imagen"
                  aria-label="Quitar imagen"
                  onClick={() => alCambiar(null)}
                >
                  <Trash2 size={15} />
                </button>
              </div>
            )}
          </div>
        </div>
      ) : (
        <div
          className={`zona-suelta ${encima ? 'encima' : ''} ${subiendo ? 'inactiva' : ''}`}
          role="button"
          tabIndex={0}
          onClick={() => !subiendo && entrada.current?.click()}
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
            void subir(evento.dataTransfer.files)
          }}
        >
          {subiendo ? (
            <div className="subiendo">
              <Loader2 size={16} className="girando" />
              Subiendo…
            </div>
          ) : (
            <>
              <ImageIcon size={26} className="icono-suelta" />
              <p>Arrastra una imagen o pulsa para elegirla</p>
              <small>JPEG, PNG o WebP · máximo 10 MB · mínimo 200×200</small>
            </>
          )}
        </div>
      )}

      <input
        ref={entrada}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        hidden
        onChange={(evento) => void subir(evento.target.files)}
      />
    </div>
  )
}
