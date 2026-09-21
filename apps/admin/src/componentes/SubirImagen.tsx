import { useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { archivos as api } from '../api/recursos'
import type { Archivo } from '../api/tipos'
import { AvisoError } from './Aviso'

/**
 * Sube una imagen y devuelve el archivo ya procesado.
 *
 * El texto alternativo se pide <strong>antes</strong> de subir y es obligatorio
 * (RN-074): pedirlo después convierte un requisito de accesibilidad en un
 * trámite que se rellena con cualquier cosa para quitárselo de encima.
 */
export default function SubirImagen({ alSubir }: { alSubir: (archivo: Archivo) => void }) {
  const entrada = useRef<HTMLInputElement>(null)
  const [textoAlt, setTextoAlt] = useState('')
  const [error, setError] = useState<unknown>(null)

  const subir = useMutation({
    mutationFn: (archivo: File) => api.subir(archivo, textoAlt),
    onSuccess: (archivo) => {
      alSubir(archivo)
      setTextoAlt('')
      if (entrada.current) entrada.current.value = ''
    },
    onError: setError,
  })

  return (
    <div>
      <AvisoError error={error} />

      <div className="campo">
        <label htmlFor="texto-alt">Texto alternativo de la imagen</label>
        <input
          id="texto-alt"
          type="text"
          placeholder="Qué se ve en la foto"
          value={textoAlt}
          onChange={(e) => setTextoAlt(e.target.value)}
        />
        <div className="ayuda">
          Obligatorio. Lo lee quien navega con lector de pantalla y lo usa el buscador.
        </div>
      </div>

      <input
        ref={entrada}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        disabled={!textoAlt.trim() || subir.isPending}
        onChange={(evento) => {
          const archivo = evento.target.files?.[0]
          if (archivo) {
            setError(null)
            subir.mutate(archivo)
          }
        }}
      />
      <div className="ayuda">
        JPEG, PNG o WebP. Máximo 10 MB. Se convierte a WebP y se generan cuatro tamaños.
        {subir.isPending && ' Procesando…'}
      </div>
    </div>
  )
}
