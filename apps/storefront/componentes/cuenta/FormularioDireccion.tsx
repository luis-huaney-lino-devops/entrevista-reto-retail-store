'use client'

import dynamic from 'next/dynamic'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { Loader2, MapPin } from 'lucide-react'

import { Boton } from '@/componentes/ui/Boton'
import { CampoTexto } from '@/componentes/ui/Campo'
import { Combobox, normalizarTexto, type OpcionCombobox } from '@/componentes/ui/Combobox'
import { peticion } from '@/lib/api.cliente'
import { ErrorApi, mensajeDeError } from '@/lib/errores'
import type { Direccion, PeticionDireccion, UbigeoItem } from '@/lib/tipos'

import { geocodificarInverso } from './geocodificacion'
import type { MensajeMapa, Punto } from './MapaUbicacion'

/**
 * Formulario de direccion, con ubigeo encadenado y punto en el mapa.
 *
 * **El mapa se carga con `next/dynamic` y `ssr: false`.** Leaflet toca `window`
 * al inicializarse: renderizarlo en servidor revienta el build. Ademas, asi su
 * JavaScript solo se descarga cuando alguien abre este formulario, no en cada
 * visita a la tienda.
 *
 * **Los tres selectores son `<Combobox/>`, no `<select>`.** El ubigeo peruano
 * tiene 25 departamentos, 196 provincias y **1 828 distritos**: un desplegable
 * nativo con 43 distritos de Lima obliga a recorrerlos con la vista, y la
 * busqueda por primera letra del navegador no sirve cuando media lista empieza
 * por "SAN". Escribir tres letras si sirve.
 *
 * Los tres se encadenan: elegir departamento pide sus provincias, elegir
 * provincia pide sus distritos, y cambiar uno de arriba **limpia los de abajo**.
 * Dejar una provincia de Lima colgando bajo un departamento de Cusco es la
 * forma mas rapida de guardar una direccion imposible.
 *
 * **Rellenar desde el mapa.** Con un punto marcado —por geolocalizacion, por un
 * clic o arrastrando el marcador— se puede pedir la direccion a Nominatim y
 * precargar calle, numero, codigo postal y los tres selectores. El cruce con el
 * ubigeo no puede ser literal: Nominatim devuelve "Miraflores" y el ubigeo
 * guarda "MIRAFLORES", y ademas hay "CAÑETE" contra "Cañete". Por eso se
 * compara normalizado —sin acentos y en minusculas— y con tolerancia: primero
 * exacto, luego por prefijo, luego por contencion.
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

/** Lo que sobra delante o detras del nombre de una division peruana. */
const RUIDO = /^(departamento|provincia|distrito|region|municipalidad|municipio)\s+(constitucional\s+)?(de|del|d)?\s*/

/** Deja el nombre en su forma comparable: sin acentos, sin "Provincia de". */
function comparable(nombre: string): string {
  return normalizarTexto(nombre).replace(RUIDO, '').trim()
}

/**
 * Busca en el ubigeo el primero de varios nombres candidatos.
 *
 * La tolerancia va de mas a menos estricta y **en ese orden**: si se empezara
 * por contencion, "LIMA" encontraria "LIMA" pero tambien podria enganchar otra
 * cosa antes. Exacto primero, y solo si nada cuadra se afloja.
 */
function emparejar(lista: UbigeoItem[], candidatos: string[]): UbigeoItem | null {
  for (const candidato of candidatos) {
    const buscado = comparable(candidato)
    if (!buscado) continue
    const exacto = lista.find((i) => comparable(i.nombre) === buscado)
    if (exacto) return exacto
    // Aflojar con menos de cuatro letras empareja cualquier cosa: "ica" esta
    // dentro de "CHINCHA ALTA". A partir de ahi solo se prueba con nombres
    // largos, y los cortos se quedan sin sugerencia, que es lo correcto.
    if (buscado.length < 4) continue
    const largos = lista.filter((i) => comparable(i.nombre).length >= 4)
    const prefijo = largos.find(
      (i) => comparable(i.nombre).startsWith(buscado) || buscado.startsWith(comparable(i.nombre)),
    )
    if (prefijo) return prefijo
    const contenido = largos.find(
      (i) => comparable(i.nombre).includes(buscado) || buscado.includes(comparable(i.nombre)),
    )
    if (contenido) return contenido
  }
  return null
}

/** "la calle, el distrito y la provincia" */
function enumerar(partes: string[]): string {
  if (partes.length <= 1) return partes[0] ?? ''
  return `${partes.slice(0, -1).join(', ')} y ${partes[partes.length - 1]}`
}

export function FormularioDireccion({ inicial, alGuardar, alCancelar }: Propiedades) {
  const [departamentos, setDepartamentos] = useState<UbigeoItem[]>([])
  const [provincias, setProvincias] = useState<UbigeoItem[]>([])
  const [distritos, setDistritos] = useState<UbigeoItem[]>([])
  const [ubigeoNoDisponible, setUbigeoNoDisponible] = useState(false)

  // El estado de los selectores es texto porque el valor de una opcion lo es.
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
  const [rellenando, setRellenando] = useState(false)
  const [mensajeRelleno, setMensajeRelleno] = useState<MensajeMapa | null>(null)

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

  const alMoverPunto = useCallback((p: Punto) => {
    setPunto(p)
    // El punto ya no corresponde a lo que dijo el ultimo rellenado.
    setMensajeRelleno(null)
  }, [])

  /**
   * Traduce el punto del mapa a una direccion y precarga lo que encuentre.
   *
   * **Nunca deja el formulario peor de como estaba.** Solo escribe los campos
   * que Nominatim devuelve con contenido, y si algo falla —el servicio, la red,
   * el ubigeo, o simplemente un punto que nadie ha cartografiado— lo unico que
   * pasa es que el mensaje lo dice y se rellena a mano. El punto sigue marcado.
   */
  const rellenarDesdeMapa = useCallback(
    (p: Punto) => {
      void (async () => {
        setRellenando(true)
        setMensajeRelleno(null)
        try {
          const hallado = await geocodificarInverso(p.latitud, p.longitud)
          if (!hallado) {
            setMensajeRelleno({
              texto:
                'No pudimos leer la direccion de ese punto. El punto queda marcado; completa los campos a mano.',
              tono: 'aviso',
            })
            return
          }

          const puestos: string[] = []
          if (hallado.calle) {
            setCalle(hallado.calle)
            puestos.push('la calle')
          }
          if (hallado.numero) {
            setNumero(hallado.numero)
            puestos.push('el numero')
          }
          if (hallado.codigoPostal) {
            setCodigoPostal(hallado.codigoPostal)
            puestos.push('el codigo postal')
          }

          // El ubigeo se baja en cascada: sin departamento no hay provincia, y
          // sin provincia no hay distrito. Cada escalon se busca en la lista
          // real que devuelve la API, no en un mapa de nombres inventado aqui.
          try {
            const departamento = emparejar(departamentos, hallado.departamento)
            if (departamento) {
              setDepartamentoId(String(departamento.id))
              puestos.push('el departamento')

              const listaProvincias = await peticion<UbigeoItem[]>(
                `/ubigeo/departamentos/${departamento.id}/provincias`,
              )
              setProvincias(listaProvincias)
              const provincia = emparejar(listaProvincias, hallado.provincia)
              if (provincia) {
                setProvinciaId(String(provincia.id))
                puestos.push('la provincia')

                const listaDistritos = await peticion<UbigeoItem[]>(`/ubigeo/provincias/${provincia.id}/distritos`)
                setDistritos(listaDistritos)
                const distrito = emparejar(listaDistritos, hallado.distrito)
                if (distrito) {
                  setDistritoId(String(distrito.id))
                  puestos.push('el distrito')
                } else {
                  setDistritoId('')
                }
              } else {
                setProvinciaId('')
                setDistritoId('')
              }
            }
          } catch {
            // El ubigeo no respondio. Lo de la calle ya esta puesto igualmente.
          }

          setMensajeRelleno(
            puestos.length > 0
              ? {
                  texto: `Rellenamos ${enumerar(puestos)} desde el mapa. Revisa que sea correcto antes de guardar.`,
                  tono: 'exito',
                }
              : {
                  texto:
                    'Encontramos el punto, pero no pudimos deducir la direccion. El punto queda marcado; completa los campos a mano.',
                  tono: 'aviso',
                },
          )
        } finally {
          setRellenando(false)
        }
      })()
    },
    [departamentos],
  )

  const opcionesDepartamento = useMemo<OpcionCombobox[]>(
    () => departamentos.map((d) => ({ valor: String(d.id), etiqueta: d.nombre })),
    [departamentos],
  )
  const opcionesProvincia = useMemo<OpcionCombobox[]>(
    () => provincias.map((p) => ({ valor: String(p.id), etiqueta: p.nombre })),
    [provincias],
  )
  const opcionesDistrito = useMemo<OpcionCombobox[]>(
    () => distritos.map((d) => ({ valor: String(d.id), etiqueta: d.nombre })),
    [distritos],
  )

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
          <Combobox
            etiqueta="Departamento"
            opciones={opcionesDepartamento}
            valor={departamentoId}
            error={errores.departamentoId}
            deshabilitado={departamentos.length === 0}
            marcador={departamentos.length === 0 ? 'Cargando...' : 'Elige...'}
            alCambiar={(valor) => {
              setDepartamentoId(valor)
              // Cambiar arriba limpia lo de abajo: una provincia colgando de
              // otro departamento es una direccion imposible.
              setProvinciaId('')
              setDistritoId('')
            }}
          />

          <Combobox
            etiqueta="Provincia"
            opciones={opcionesProvincia}
            valor={provinciaId}
            error={errores.provinciaId}
            deshabilitado={!departamentoId || provincias.length === 0}
            marcador={departamentoId ? 'Elige...' : 'Elige antes el departamento'}
            alCambiar={(valor) => {
              setProvinciaId(valor)
              setDistritoId('')
            }}
          />

          <Combobox
            etiqueta="Distrito"
            opciones={opcionesDistrito}
            valor={distritoId}
            error={errores.distritoId}
            deshabilitado={!provinciaId || distritos.length === 0}
            marcador={provinciaId ? 'Elige...' : 'Elige antes la provincia'}
            alCambiar={setDistritoId}
          />
        </div>

        <MapaUbicacion
          valor={punto}
          alCambiar={alMoverPunto}
          alRellenar={rellenarDesdeMapa}
          rellenando={rellenando}
          mensajeRelleno={mensajeRelleno}
        />
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
