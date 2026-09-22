'use client'

import { useState } from 'react'
import { Loader2 } from 'lucide-react'

import { TarjetaOpinion } from '@/componentes/opiniones/TarjetaOpinion'
import { Boton } from '@/componentes/ui/Boton'
import { peticion } from '@/lib/api.cliente'
import { mensajeDeError } from '@/lib/errores'
import type { Opinion, Pagina } from '@/lib/tipos'

/**
 * «Ver mas opiniones».
 *
 * Isla minima: la primera pagina la pinta el servidor -es el contenido que se
 * indexa- y esto solo anade las siguientes cuando alguien las pide. Traerlas
 * todas de golpe en el servidor haria la ficha mas pesada para el 90% que no
 * pasa de las primeras.
 *
 * El endpoint es publico, asi que esta llamada funciona con sesion y sin ella.
 */

type Propiedades = {
  slug: string
  totalPaginas: number
}

export function MasOpiniones({ slug, totalPaginas }: Propiedades) {
  const [extra, setExtra] = useState<Opinion[]>([])
  const [ultimaCargada, setUltimaCargada] = useState(1)
  const [cargando, setCargando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const quedan = ultimaCargada < totalPaginas

  async function cargarSiguiente() {
    if (cargando || !quedan) return
    setCargando(true)
    setError(null)
    try {
      const siguiente = ultimaCargada + 1
      const pagina = await peticion<Pagina<Opinion>>(
        `/productos/${encodeURIComponent(slug)}/opiniones?pagina=${siguiente}`,
      )
      // Se filtra por id: si alguien escribio una opinion entre la carga de la
      // pagina 1 y esta, el desplazamiento podria devolver una repetida.
      setExtra((previas) => {
        const vistas = new Set([...previas.map((o) => o.id)])
        return [...previas, ...pagina.items.filter((o) => !vistas.has(o.id))]
      })
      setUltimaCargada(siguiente)
    } catch (e) {
      setError(mensajeDeError(e))
    } finally {
      setCargando(false)
    }
  }

  return (
    <>
      {extra.length > 0 && (
        <ol className="mt-0">
          {extra.map((opinion) => (
            <li key={opinion.id}>
              <TarjetaOpinion opinion={opinion} />
            </li>
          ))}
        </ol>
      )}

      {error && (
        <p role="alert" className="mt-3 text-sm font-medium text-peligro">
          {error}
        </p>
      )}

      {quedan && (
        <div className="mt-4">
          <Boton type="button" variante="sutil" onClick={() => void cargarSiguiente()} disabled={cargando}>
            {cargando && <Loader2 size={16} className="animate-spin" aria-hidden />}
            {cargando ? 'Cargando...' : 'Ver mas opiniones'}
          </Boton>
        </div>
      )}
    </>
  )
}
