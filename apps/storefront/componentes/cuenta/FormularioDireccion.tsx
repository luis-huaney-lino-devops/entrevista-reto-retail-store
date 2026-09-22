'use client'

import dynamic from 'next/dynamic'
import { useCallback, useEffect, useState } from 'react'
import { Loader2, MapPin } from 'lucide-react'

import { Boton } from '@/componentes/ui/Boton'
import { CampoSelect, CampoTexto } from '@/componentes/ui/Campo'
import { peticion } from '@/lib/api.cliente'
import { ErrorApi, mensajeDeError } from '@/lib/errores'
import type { Direccion, PeticionDireccion, UbigeoItem } from '@/lib/tipos'

import type { Punto } from './MapaUbicacion'

/**
 * Formulario de direccion, con ubigeo encadenado y punto en el mapa.
 *
 * **El mapa se carga con `next/dynamic` y `ssr: false`.** Leaflet toca `window`
 * al inicializarse: renderizarlo en servidor revienta el build. Ademas, asi su
 * JavaScript solo se descarga cuando alguien abre este formulario, no en cada
 * visita a la tienda.
 *
 * Los tres selects se alimentan del ubigeo y se encadenan: elegir departamento
 * pide sus provincias, elegir provincia pide sus distritos, y cambiar uno de
 * arriba **limpia los de abajo**. Dejar una provincia de Lima colgando bajo un
 * departamento de Cusco es la forma mas rapida de guardar una direccion
 * imposible.
 *
 * Si el ubigeo aun no existe (404), el formulario lo dice y no finge: un select
 * vacio sin explicacion parece un fallo del navegador.
 */

const MapaUbicacion = dynamic(() => import('./MapaUbicacion').then((m) => m.MapaUbicacion), {
  ssr: false,
  loading: () => (
    <div className="flex h-72 items-center justify-center rounded-marca border border-borde bg-superficie-alt text-sm text-texto-suave">
      <Loader2 size={18} className="mr-2 animate-spin" aria-hidden />
      Cargando el mapa...
    </div>
  ),
})

type Propiedades = {
  inicial?: Direccion | null
  alGuardar: (datos: PeticionDireccion) => Promise<void>
  alCancelar: () => void
}

export function FormularioDireccion({ inicial, alGuardar, alCancelar }: Propiedades) {
  const [departamentos, setDepartamentos] = useState<UbigeoItem[]>([])
  const [provincias, setProvincias] = useState<UbigeoItem[]>([])
  const [distritos, setDistritos] = useState<UbigeoItem[]>([])
  const [ubigeoNoDisponible, setUbigeoNoDisponible] = useState(false)

  // El estado de los selects es texto porque el valor de un `<option>` lo es.
  // El id se convierte a numero al enviar, que es como lo espera la API.
  const [departamentoId, setDepartamentoId] = useState(String(inicial?.departamento?.id ?? ''))
  const [provinciaId, setProvinciaId] = useState(String(inicial?.provincia?.id ?? ''))
  const [distritoId, setDistritoId] = useState(String(inicial?.distrito?.id ?? ''))

  const [etiqueta, setEtiqueta] = useState(inicial?.etiqueta ?? 'Casa')
  const [destinatario, setDestinatario] = useState(inicial?.destinatario ?? '')
  const [telefono, setTelefono] = useState(inicial?.telefono ?? '')
  const [calle, setCalle] = useState(inicial?.calle ?? '')
  const [numero, setNumero] = useState(inicial?.numero ?? '')
  const [referencia, setReferencia] = useState(inicial?.referencia ?? '')
  const [codigoPostal, setCodigoPostal] = useState(inicial?.codigoPostal ?? '')
  const [predeterminada, setPredeterminada] = useState(inicial?.predeterminada ?? false)

  const [punto, setPunto] = useState<Punto | null>(
    inicial?.latitud !== null && inicial?.latitud !== undefined && inicial?.longitud !== null && inicial?.longitud !== undefined
      ? { latitud: inicial.latitud, longitud: inicial.longitud }
      : null,
  )

  const [errores, setErrores] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  // Departamentos, una sola vez.
  useEffect(() => {
    let vivo = true
    void (async () => {
      try {
        const lista = await peticion<UbigeoItem[]>('/ubigeo/departamentos')
        if (vivo) setDepartamentos(lista)
      } catch (e) {
        if (vivo && e instanceof ErrorApi && e.estado === 404) setUbigeoNoDisponible(true)
      }
    })()
    return () => {
      vivo = false
    }
  }, [])

  // Provincias del departamento elegido.
  useEffect(() => {
    if (!departamentoId) {
      setProvincias([])
      return
    }
    let vivo = true
    void (async () => {
      try {
        const lista = await peticion<UbigeoItem[]>(`/ubigeo/departamentos/${departamentoId}/provincias`)
        if (vivo) setProvincias(lista)
      } catch {
        if (vivo) setProvincias([])
      }
    })()
    return () => {
      vivo = false
    }
  }, [departamentoId])

  // Distritos de la provincia elegida.
  useEffect(() => {
    if (!provinciaId) {
      setDistritos([])
      return
    }
    let vivo = true
    void (async () => {
      try {
        const lista = await peticion<UbigeoItem[]>(`/ubigeo/provincias/${provinciaId}/distritos`)
        if (vivo) setDistritos(lista)
      } catch {
        if (vivo) setDistritos([])
      }
    })()
    return () => {
      vivo = false
    }
  }, [provinciaId])

  const alMoverPunto = useCallback((p: Punto) => setPunto(p), [])

  function validar(): boolean {
    const nuevos: Record<string, string> = {}
    if (!distritoId) nuevos.distritoId = 'Elige el distrito.'
    if (etiqueta.trim() === '') nuevos.etiqueta = 'Ponle un nombre (Casa, Obra, Oficina...).'
    if (destinatario.trim().length < 2) nuevos.destinatario = 'Escribe quien recibe.'
    if (telefono.trim().length < 6) nuevos.telefono = 'Escribe un telefono de contacto.'
    if (calle.trim().length < 3) nuevos.calle = 'Escribe la calle o avenida.'
    setErrores(nuevos)
    return Object.keys(nuevos).length === 0
  }

  async function enviar(e: React.FormEvent) {
    e.preventDefault()
    if (enviando || !validar()) return
    setError(null)
    setEnviando(true)
    try {
      await alGuardar({
        distritoId: Number(distritoId),
        etiqueta: etiqueta.trim(),
        destinatario: destinatario.trim(),
        telefono: telefono.trim(),
        calle: calle.trim(),
        ...(numero.trim() ? { numero: numero.trim() } : {}),
        ...(referencia.trim() ? { referencia: referencia.trim() } : {}),
        ...(codigoPostal.trim() ? { codigoPostal: codigoPostal.trim() } : {}),
        ...(punto ? { latitud: punto.latitud, longitud: punto.longitud } : {}),
        predeterminada,
      })
    } catch (err: unknown) {
      if (err instanceof ErrorApi && err.codigo === 'VALIDATION_ERROR') {
        const porCampo: Record<string, string> = {}
        for (const campo of err.errores) porCampo[campo.field] = campo.message
        setErrores(porCampo)
      } else {
        setError(mensajeDeError(err))
      }
    } finally {
      setEnviando(false)
    }
  }

  return (
    <form onSubmit={enviar} className="space-y-5" noValidate>
      {ubigeoNoDisponible && (
        <p role="status" className="rounded-marca bg-aviso-suave px-3 py-2.5 text-sm font-medium text-aviso">
          El listado de departamentos, provincias y distritos todavia no esta disponible en este entorno. El resto del
          formulario si funciona.
        </p>
      )}

      <fieldset className="space-y-4">
        <legend className="mb-1 text-[13px] font-semibold uppercase tracking-wide text-texto">Ubicacion</legend>

        <div className="grid gap-4 sm:grid-cols-3">
          <CampoSelect
            etiqueta="Departamento"
            value={departamentoId}
            error={errores.departamentoId}
            onChange={(e) => {
              setDepartamentoId(e.target.value)
              // Cambiar arriba limpia lo de abajo: una provincia colgando de
              // otro departamento es una direccion imposible.
              setProvinciaId('')
              setDistritoId('')
            }}
          >
            <option value="">Elige...</option>
            {departamentos.map((d) => (
              <option key={d.id} value={d.id}>
                {d.nombre}
              </option>
            ))}
          </CampoSelect>

          <CampoSelect
            etiqueta="Provincia"
            value={provinciaId}
            disabled={!departamentoId || provincias.length === 0}
            error={errores.provinciaId}
            onChange={(e) => {
              setProvinciaId(e.target.value)
              setDistritoId('')
            }}
          >
            <option value="">{departamentoId ? 'Elige...' : 'Elige antes el departamento'}</option>
            {provincias.map((p) => (
              <option key={p.id} value={p.id}>
                {p.nombre}
              </option>
            ))}
          </CampoSelect>

          <CampoSelect
            etiqueta="Distrito"
            value={distritoId}
            disabled={!provinciaId || distritos.length === 0}
            error={errores.distritoId}
            onChange={(e) => setDistritoId(e.target.value)}
          >
            <option value="">{provinciaId ? 'Elige...' : 'Elige antes la provincia'}</option>
            {distritos.map((d) => (
              <option key={d.id} value={d.id}>
                {d.nombre}
              </option>
            ))}
          </CampoSelect>
        </div>

        <MapaUbicacion valor={punto} alCambiar={alMoverPunto} />
      </fieldset>

      <fieldset className="space-y-4">
        <legend className="mb-1 text-[13px] font-semibold uppercase tracking-wide text-texto">Direccion</legend>

        <div className="grid gap-4 sm:grid-cols-[2fr_1fr]">
          <CampoTexto
            etiqueta="Calle o avenida"
            value={calle}
            required
            autoComplete="address-line1"
            error={errores.calle}
            onChange={(e) => setCalle(e.target.value)}
          />
          <CampoTexto
            etiqueta="Numero"
            value={numero}
            autoComplete="address-line2"
            error={errores.numero}
            onChange={(e) => setNumero(e.target.value)}
          />
        </div>

        <CampoTexto
          etiqueta="Referencia"
          value={referencia}
          error={errores.referencia}
          ayuda="Un punto conocido cerca: frente al parque, al lado de la farmacia..."
          onChange={(e) => setReferencia(e.target.value)}
        />

        <div className="grid gap-4 sm:grid-cols-2">
          <CampoTexto
            etiqueta="Codigo postal"
            value={codigoPostal}
            autoComplete="postal-code"
            error={errores.codigoPostal}
            onChange={(e) => setCodigoPostal(e.target.value)}
          />
          <CampoTexto
            etiqueta="Nombre de la direccion"
            value={etiqueta}
            required
            error={errores.etiqueta}
            ayuda="Casa, Obra, Oficina..."
            onChange={(e) => setEtiqueta(e.target.value)}
          />
        </div>
      </fieldset>

      <fieldset className="space-y-4">
        <legend className="mb-1 text-[13px] font-semibold uppercase tracking-wide text-texto">Quien recibe</legend>

        <div className="grid gap-4 sm:grid-cols-2">
          <CampoTexto
            etiqueta="Nombre del destinatario"
            value={destinatario}
            required
            autoComplete="name"
            error={errores.destinatario}
            onChange={(e) => setDestinatario(e.target.value)}
          />
          <CampoTexto
            etiqueta="Telefono"
            type="tel"
            value={telefono}
            required
            autoComplete="tel"
            error={errores.telefono}
            onChange={(e) => setTelefono(e.target.value)}
          />
        </div>

        <label className="flex cursor-pointer items-center gap-2.5 text-sm text-texto-medio">
          <input
            type="checkbox"
            checked={predeterminada}
            onChange={(e) => setPredeterminada(e.target.checked)}
            className="h-4 w-4 rounded border-borde-fuerte accent-marca"
          />
          Usar como direccion predeterminada
        </label>
      </fieldset>

      {error && (
        <p role="alert" className="rounded-marca bg-peligro-suave px-3 py-2.5 text-sm font-medium text-peligro">
          {error}
        </p>
      )}

      <div className="flex flex-wrap gap-3">
        <Boton type="submit" variante="primario" disabled={enviando}>
          {enviando ? <Loader2 size={16} className="animate-spin" aria-hidden /> : <MapPin size={16} aria-hidden />}
          {enviando ? 'Guardando...' : inicial ? 'Guardar cambios' : 'Guardar direccion'}
        </Boton>
        <Boton type="button" variante="sutil" onClick={alCancelar} disabled={enviando}>
          Cancelar
        </Boton>
      </div>
    </form>
  )
}
