'use client'

import { useCallback, useEffect, useState } from 'react'
import { MapPin, Pencil, Plus, Star, Trash2 } from 'lucide-react'

import { FormularioDireccion } from '@/componentes/cuenta/FormularioDireccion'
import { ZonaPrivada } from '@/componentes/cuenta/ZonaPrivada'
import { Boton } from '@/componentes/ui/Boton'
import { SinResultados } from '@/componentes/ui/Estados'
import { peticion } from '@/lib/api.cliente'
import { ErrorApi, mensajeDeError } from '@/lib/errores'
import type { Direccion, PeticionDireccion } from '@/lib/tipos'
import { useAvisos } from '@/funcionalidades/avisos/ProveedorAvisos'
import { useSesion } from '@/funcionalidades/sesion/ProveedorSesion'

/**
 * Mis direcciones.
 *
 * Lista, alta, edicion, predeterminada y borrado.
 *
 * **El borrado se confirma antes de ocurrir.** Es la misma regla que sigue el
 * panel: una accion que no se puede deshacer desde la interfaz no puede
 * dispararse con un solo clic.
 */
export default function PaginaDirecciones() {
  return (
    <ZonaPrivada titulo="Mis direcciones">
      <Contenido />
    </ZonaPrivada>
  )
}

type Vista = { modo: 'lista' } | { modo: 'nueva' } | { modo: 'editar'; direccion: Direccion }

function Contenido() {
  const { estado } = useSesion()
  const { avisar } = useAvisos()
  const [direcciones, setDirecciones] = useState<Direccion[] | null>(null)
  const [noDisponible, setNoDisponible] = useState(false)
  const [vista, setVista] = useState<Vista>({ modo: 'lista' })
  const [confirmando, setConfirmando] = useState<Direccion | null>(null)

  const cargar = useCallback(async () => {
    try {
      setDirecciones(await peticion<Direccion[]>('/cuenta/direcciones'))
      setNoDisponible(false)
    } catch (e) {
      if (e instanceof ErrorApi && e.estado === 404) {
        setNoDisponible(true)
        setDirecciones([])
      } else {
        avisar(mensajeDeError(e), 'error')
        setDirecciones([])
      }
    }
  }, [avisar])

  useEffect(() => {
    if (estado === 'autenticado') void cargar()
  }, [estado, cargar])

  async function guardar(datos: PeticionDireccion) {
    if (vista.modo === 'editar') {
      await peticion(`/cuenta/direcciones/${vista.direccion.id}`, { metodo: 'PUT', cuerpo: datos })
      avisar('Actualizamos la direccion.', 'exito')
    } else {
      await peticion('/cuenta/direcciones', { metodo: 'POST', cuerpo: datos })
      avisar('Guardamos la direccion.', 'exito')
    }
    setVista({ modo: 'lista' })
    await cargar()
  }

  async function marcarPredeterminada(d: Direccion) {
    try {
      await peticion(`/cuenta/direcciones/${d.id}/predeterminada`, { metodo: 'POST' })
      await cargar()
    } catch (e) {
      avisar(mensajeDeError(e), 'error')
    }
  }

  async function borrar(d: Direccion) {
    setConfirmando(null)
    try {
      await peticion(`/cuenta/direcciones/${d.id}`, { metodo: 'DELETE' })
      avisar('Eliminamos la direccion.', 'exito')
      await cargar()
    } catch (e) {
      avisar(mensajeDeError(e), 'error')
    }
  }

  if (vista.modo !== 'lista') {
    return (
      <section className="rounded-marca border border-borde bg-white p-5">
        <h2 className="mb-5 font-marca text-base font-semibold text-tinta">
          {vista.modo === 'editar' ? 'Editar direccion' : 'Nueva direccion'}
        </h2>
        <FormularioDireccion
          inicial={vista.modo === 'editar' ? vista.direccion : null}
          alGuardar={guardar}
          alCancelar={() => setVista({ modo: 'lista' })}
        />
      </section>
    )
  }

  return (
    <div className="space-y-4">
      {noDisponible && (
        <p role="status" className="rounded-marca bg-aviso-suave px-3 py-2.5 text-sm font-medium text-aviso">
          La gestion de direcciones todavia no esta disponible en este entorno. El formulario se puede abrir para
          revisarlo, pero guardar dara error hasta que la API responda.
        </p>
      )}

      <div className="flex justify-end">
        <Boton variante="primario" onClick={() => setVista({ modo: 'nueva' })}>
          <Plus size={16} aria-hidden />
          Nueva direccion
        </Boton>
      </div>

      {direcciones === null ? (
        <div className="h-40 animate-pulse rounded-marca bg-white" />
      ) : direcciones.length === 0 ? (
        <SinResultados
          icono={<MapPin size={38} strokeWidth={1.5} />}
          titulo="Todavia no guardaste ninguna direccion"
          descripcion="Guarda una y la tendras lista para tus proximos pedidos, con el punto exacto en el mapa."
        />
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {direcciones.map((d) => (
            <li key={d.id} className="flex flex-col rounded-marca border border-borde bg-white p-4">
              <div className="mb-2 flex items-start justify-between gap-2">
                <p className="font-semibold text-texto">{d.etiqueta}</p>
                {d.predeterminada && (
                  <span className="inline-flex items-center gap-1 rounded-full bg-marca-suave px-2 py-0.5 text-[11px] font-semibold text-marca-oscura">
                    <Star size={11} className="fill-marca text-marca" aria-hidden />
                    Predeterminada
                  </span>
                )}
              </div>

              <address className="not-italic text-sm leading-relaxed text-texto-medio">
                {d.calle}
                {d.numero ? ` ${d.numero}` : ''}
                <br />
                {[d.distrito?.nombre, d.provincia?.nombre, d.departamento?.nombre].filter(Boolean).join(', ')}
                {d.referencia && (
                  <>
                    <br />
                    <span className="text-xs text-texto-suave">Ref.: {d.referencia}</span>
                  </>
                )}
                <br />
                <span className="text-xs text-texto-suave">
                  {d.destinatario} &middot; {d.telefono}
                </span>
              </address>

              {d.latitud !== null && d.longitud !== null && (
                <p className="mt-2 inline-flex items-center gap-1 text-xs text-texto-suave">
                  <MapPin size={12} aria-hidden />
                  <span className="cifra">
                    {d.latitud.toFixed(4)}, {d.longitud.toFixed(4)}
                  </span>
                </p>
              )}

              <div className="mt-auto flex flex-wrap gap-2 pt-4">
                <Boton variante="sutil" tamano="sm" onClick={() => setVista({ modo: 'editar', direccion: d })}>
                  <Pencil size={13} aria-hidden />
                  Editar
                </Boton>
                {!d.predeterminada && (
                  <Boton variante="sutil" tamano="sm" onClick={() => void marcarPredeterminada(d)}>
                    <Star size={13} aria-hidden />
                    Hacer predeterminada
                  </Boton>
                )}
                <Boton
                  variante="peligro"
                  tamano="sm"
                  onClick={() => setConfirmando(d)}
                  aria-label={`Eliminar la direccion ${d.etiqueta}`}
                >
                  <Trash2 size={13} aria-hidden />
                  Eliminar
                </Boton>
              </div>
            </li>
          ))}
        </ul>
      )}

      {confirmando && (
        <div className="fixed inset-0 z-[70] flex items-center justify-center p-4" role="dialog" aria-modal="true">
          <button
            type="button"
            aria-label="Cancelar"
            tabIndex={-1}
            onClick={() => setConfirmando(null)}
            className="absolute inset-0 cursor-default bg-tinta/40"
          />
          <div className="relative w-full max-w-sm rounded-marca border border-borde bg-white p-5 shadow-l">
            <h2 className="font-marca text-base font-semibold text-tinta">Eliminar esta direccion?</h2>
            <p className="mt-1.5 text-sm text-texto-medio">
              Vas a eliminar <span className="font-semibold">{confirmando.etiqueta}</span>. Esta accion no se puede
              deshacer desde aqui.
            </p>
            <div className="mt-5 flex justify-end gap-2">
              <Boton variante="sutil" onClick={() => setConfirmando(null)}>
                Cancelar
              </Boton>
              <Boton variante="peligro" onClick={() => void borrar(confirmando)}>
                Si, eliminar
              </Boton>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
