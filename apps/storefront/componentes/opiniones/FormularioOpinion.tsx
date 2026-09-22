'use client'

import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { useRouter } from 'next/navigation'
import { Loader2, PencilLine, Star, Trash2 } from 'lucide-react'

import { refrescarFichaProducto } from '@/app/productos/[slug]/acciones'
import { TarjetaOpinion } from '@/componentes/opiniones/TarjetaOpinion'
import { Boton, EnlaceBoton } from '@/componentes/ui/Boton'
import { CampoArea, CampoTexto } from '@/componentes/ui/Campo'
import { peticion } from '@/lib/api.cliente'
import { ErrorApi, mensajeDeError } from '@/lib/errores'
import { clases } from '@/lib/formato'
import {
  CUERPO_OPINION_MAXIMO,
  TITULO_OPINION_MAXIMO,
  type Opinion,
  type PeticionOpinion,
} from '@/lib/tipos'
import { useAvisos } from '@/funcionalidades/avisos/ProveedorAvisos'
import { useSesion } from '@/funcionalidades/sesion/ProveedorSesion'

/**
 * La unica isla de la seccion de opiniones: escribir, editar y retirar la
 * propia.
 *
 * Tres decisiones la explican:
 *
 * **Si ya opinaste, se edita la tuya.** No hay un segundo formulario ni un
 * boton distinto: al montar se pregunta `GET /cuenta/opiniones/producto/{id}`
 * y, si existe, el formulario nace relleno y el envio va por `PUT`. La regla
 * del servidor es una opinion por persona y producto (RN-090), asi que la
 * alternativa -ofrecer siempre «escribir»- seria pasear al comprador hasta un
 * `409`.
 *
 * **Sin sesion no se pinta un formulario deshabilitado**, se pinta el enlace a
 * `/acceso`. Un formulario que no se puede enviar es una promesa que la
 * interfaz no cumple.
 *
 * **Tras escribir se invalida la ficha en el servidor** con una accion de
 * servidor, y solo entonces se refresca. La ficha es ISR de 300 s: sin eso, la
 * opinion recien publicada no apareceria en la lista -ni se moveria el
 * promedio- hasta que la pagina caducara, y quien la escribio concluiria que no
 * se guardo.
 */

type Propiedades = {
  productoId: number
  slug: string
}

const NOTAS = [1, 2, 3, 4, 5] as const

export function FormularioOpinion({ productoId, slug }: Propiedades) {
  const { estado } = useSesion()
  const { avisar } = useAvisos()
  const router = useRouter()

  const [mia, setMia] = useState<Opinion | null>(null)
  const [consultando, setConsultando] = useState(true)
  const [abierto, setAbierto] = useState(false)
  const [confirmandoRetirada, setConfirmandoRetirada] = useState(false)
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [calificacion, setCalificacion] = useState(5)
  const [titulo, setTitulo] = useState('')
  const [cuerpo, setCuerpo] = useState('')

  const adoptar = useCallback((opinion: Opinion) => {
    setMia(opinion)
    setCalificacion(opinion.calificacion)
    setTitulo(opinion.titulo)
    setCuerpo(opinion.cuerpo)
  }, [])

  // Al entrar se pregunta si esta persona ya opino de este producto.
  useEffect(() => {
    if (estado === 'desconocida') return
    if (estado !== 'autenticado') {
      setMia(null)
      setConsultando(false)
      return
    }

    let vivo = true
    void (async () => {
      try {
        const propia = await peticion<Opinion>(`/cuenta/opiniones/producto/${productoId}`)
        if (vivo) adoptar(propia)
      } catch (e) {
        // El 404 es el caso normal y no es un error: significa «todavia no has
        // opinado». Solo se registra cualquier otra cosa.
        if (!(e instanceof ErrorApi && e.estado === 404)) console.error('[opiniones]', e)
        if (vivo) setMia(null)
      } finally {
        if (vivo) setConsultando(false)
      }
    })()

    return () => {
      vivo = false
    }
  }, [estado, productoId, adoptar])

  async function refrescarLaFicha() {
    try {
      await refrescarFichaProducto(slug)
      router.refresh()
    } catch {
      // Si la revalidacion falla, lo escrito ya esta guardado: la ficha se
      // pondra al dia sola cuando caduque. No es motivo para alarmar a nadie.
    }
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault()
    if (enviando) return

    setEnviando(true)
    setError(null)
    try {
      const datos: PeticionOpinion = { calificacion, titulo: titulo.trim(), cuerpo: cuerpo.trim() }
      const guardada = mia
        ? await peticion<Opinion>(`/cuenta/opiniones/${mia.id}`, { metodo: 'PUT', cuerpo: datos })
        : await peticion<Opinion>('/cuenta/opiniones', {
            metodo: 'POST',
            cuerpo: { productoId, ...datos },
          })

      const era = mia
      adoptar(guardada)
      setAbierto(false)
      avisar(era ? 'Actualizamos tu opinion.' : 'Publicamos tu opinion. Gracias por escribirla.', 'exito')
      await refrescarLaFicha()
    } catch (e) {
      setError(mensajeDeOpinion(e))
    } finally {
      setEnviando(false)
    }
  }

  async function retirar() {
    if (!mia || enviando) return

    setEnviando(true)
    setError(null)
    try {
      await peticion(`/cuenta/opiniones/${mia.id}`, { metodo: 'DELETE' })
      setMia(null)
      setCalificacion(5)
      setTitulo('')
      setCuerpo('')
      setAbierto(false)
      setConfirmandoRetirada(false)
      avisar('Retiramos tu opinion.', 'info')
      await refrescarLaFicha()
    } catch (e) {
      setError(mensajeDeOpinion(e))
    } finally {
      setEnviando(false)
    }
  }

  // Mientras no se sabe si hay sesion no se pinta ni «entra» ni «escribe»: las
  // dos opciones serian un parpadeo para la mitad de la gente.
  if (estado === 'desconocida' || consultando) {
    return <div aria-hidden className="mb-6 h-24 animate-pulse rounded-marca bg-superficie-alt" />
  }

  if (estado !== 'autenticado') {
    return (
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3 rounded-marca border border-borde bg-white p-4">
        <p className="text-sm text-texto-medio">
          <span className="font-semibold text-texto">Entra para opinar.</span> Solo quien tiene cuenta
          puede escribir, editar o retirar una opinion.
        </p>
        <EnlaceBoton href={`/acceso?volver=/productos/${slug}`} variante="primario" tamano="sm">
          Iniciar sesion
        </EnlaceBoton>
      </div>
    )
  }

  const editando = mia !== null
  const restanCuerpo = CUERPO_OPINION_MAXIMO - cuerpo.trim().length

  return (
    <div className="mb-6">
      {mia && !abierto && (
        <div className="mb-3">
          <TarjetaOpinion opinion={mia} propia />
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <Boton type="button" variante="sutil" tamano="sm" onClick={() => setAbierto(true)}>
              <PencilLine size={15} aria-hidden />
              Editar mi opinion
            </Boton>

            {confirmandoRetirada ? (
              <>
                <span className="text-sm text-texto-medio">Se retira de la ficha. Seguro?</span>
                <Boton
                  type="button"
                  variante="peligro"
                  tamano="sm"
                  onClick={() => void retirar()}
                  disabled={enviando}
                >
                  {enviando ? <Loader2 size={15} className="animate-spin" aria-hidden /> : <Trash2 size={15} aria-hidden />}
                  Si, retirarla
                </Boton>
                <Boton
                  type="button"
                  variante="fantasma"
                  tamano="sm"
                  onClick={() => setConfirmandoRetirada(false)}
                  disabled={enviando}
                >
                  Cancelar
                </Boton>
              </>
            ) : (
              <Boton
                type="button"
                variante="fantasma"
                tamano="sm"
                onClick={() => setConfirmandoRetirada(true)}
              >
                <Trash2 size={15} aria-hidden />
                Retirarla
              </Boton>
            )}
          </div>
        </div>
      )}

      {!mia && !abierto && (
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-marca border border-borde bg-white p-4">
          <p className="text-sm text-texto-medio">
            Lo compraste o lo conoces? Cuenta que tal te fue.
          </p>
          <Boton type="button" tamano="sm" onClick={() => setAbierto(true)}>
            Escribir una opinion
          </Boton>
        </div>
      )}

      {abierto && (
        <form onSubmit={enviar} className="space-y-4 rounded-marca border border-borde bg-white p-5">
          <fieldset>
            <legend className="text-[13px] font-semibold text-texto-medio">Tu nota</legend>
            {/* Radios de verdad, ocultos visualmente: se recorren con el
                teclado y el lector de pantalla anuncia «4 estrellas», que es lo
                que un grupo de botones con iconos no hace solo. */}
            <div className="mt-1.5 flex items-center gap-1">
              {NOTAS.map((nota) => (
                <label key={nota} className="cursor-pointer p-0.5">
                  <input
                    type="radio"
                    name="calificacion"
                    value={nota}
                    checked={calificacion === nota}
                    onChange={() => setCalificacion(nota)}
                    className="peer sr-only"
                  />
                  <span className="block rounded peer-focus-visible:outline peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-tinta-claro">
                    <Star
                      size={26}
                      strokeWidth={1.6}
                      className={clases(
                        'transition-colors',
                        nota <= calificacion ? 'fill-marca text-marca' : 'text-borde-fuerte',
                      )}
                      aria-hidden
                    />
                  </span>
                  <span className="sr-only">
                    {nota} {nota === 1 ? 'estrella' : 'estrellas'}
                  </span>
                </label>
              ))}
            </div>
          </fieldset>

          <CampoTexto
            etiqueta="Titulo"
            value={titulo}
            onChange={(e) => setTitulo(e.target.value)}
            maxLength={TITULO_OPINION_MAXIMO}
            required
            placeholder="Resume tu experiencia en una linea"
          />

          <CampoArea
            etiqueta="Tu opinion"
            value={cuerpo}
            onChange={(e) => setCuerpo(e.target.value)}
            maxLength={CUERPO_OPINION_MAXIMO}
            required
            rows={5}
            placeholder="Para que lo usaste, que tal te resulto, que le falta"
            ayuda={`Quedan ${restanCuerpo} caracteres.`}
          />

          {error && (
            <p role="alert" className="text-sm font-medium text-peligro">
              {error}
            </p>
          )}

          <div className="flex flex-wrap items-center gap-2">
            <Boton type="submit" disabled={enviando || !titulo.trim() || !cuerpo.trim()}>
              {enviando && <Loader2 size={16} className="animate-spin" aria-hidden />}
              {editando ? 'Guardar cambios' : 'Publicar opinion'}
            </Boton>
            <Boton
              type="button"
              variante="fantasma"
              onClick={() => {
                setAbierto(false)
                setError(null)
                if (mia) adoptar(mia)
              }}
              disabled={enviando}
            >
              Cancelar
            </Boton>
          </div>

          <p className="text-xs text-texto-suave">
            Se publica con tu nombre abreviado. Si compraste este producto y ya te llego, lleva la
            insignia de compra verificada.
          </p>
        </form>
      )}
    </div>
  )
}

/**
 * Los dos codigos que solo se ven aqui.
 *
 * El resto pasa por `mensajeDeError`, que es el catalogo comun. Estos dos son
 * de opiniones y ocurren cuando la pestana tiene una foto vieja del estado: dos
 * pestanas abiertas, o una opinion retirada desde otro sitio.
 */
function mensajeDeOpinion(e: unknown): string {
  if (e instanceof ErrorApi) {
    if (e.codigo === 'DUPLICATE_REVIEW') {
      return 'Ya tienes una opinion sobre este producto. Recarga la pagina para editarla.'
    }
    if (e.codigo === 'REVIEW_NOT_FOUND') {
      return 'Esa opinion ya no existe. Recarga la pagina.'
    }
  }
  return mensajeDeError(e)
}
