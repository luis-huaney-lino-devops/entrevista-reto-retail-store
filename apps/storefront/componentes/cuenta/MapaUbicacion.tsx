'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { MapContainer, Marker, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import L, { type Map as MapaLeaflet } from 'leaflet'
import { Crosshair, Loader2, MapPinned } from 'lucide-react'

import 'leaflet/dist/leaflet.css'

import { Boton } from '@/componentes/ui/Boton'

/**
 * Selector de ubicacion sobre un mapa.
 *
 * **Leaflet + OpenStreetMap**, que no necesita clave de API ni cuenta: una
 * tienda de demostracion no deberia exigir una credencial de un tercero para
 * que alguien guarde su direccion.
 *
 * Este componente es forzosamente de cliente y **nunca debe renderizarse en el
 * servidor**: Leaflet toca `window` y `document` al inicializarse. Quien lo usa
 * lo carga con `next/dynamic` y `ssr: false`. Ver `FormularioDireccion`.
 *
 * Cinco detalles que lo hacen usable:
 *
 * - **El icono por defecto se arregla a mano.** Leaflet resuelve las rutas de
 *   sus PNG relativas al CSS, y con el empaquetado de Next esas rutas no
 *   existen: el marcador sale invisible. Se construye un icono con SVG en
 *   `data:` y no hay que copiar nada a `public/`.
 * - **El mapa se recentra cuando cambia el distrito**, pero solo si el usuario
 *   no ha puesto ya un punto: mover el marcador que alguien acaba de colocar es
 *   de lo mas irritante que puede hacer un formulario.
 * - **"Usar mi ubicacion actual" mueve el mapa.** Antes obtenia las coordenadas,
 *   ponia el marcador y dejaba la vista donde estaba: el marcador aterrizaba
 *   fuera de la pantalla y parecia que el boton no hacia nada. Ahora el mapa
 *   **vuela** hasta el punto (`flyTo`) y se acerca a nivel de calle. Se guarda
 *   la instancia del mapa con un componente puente, porque `useMap` solo existe
 *   dentro del `<MapContainer/>`.
 * - **El marcador se arrastra**, y arrastrarlo no dispara ninguna peticion: la
 *   geocodificacion inversa la pide un boton aparte, "Rellenar desde el mapa".
 *   Traducir a direccion en cada `dragend` serian decenas de llamadas por
 *   arrastre y Nominatim admite **una por segundo**.
 * - **"Usar mi ubicacion actual"** trata los tres fallos de geolocalizacion por
 *   separado (permiso denegado, no disponible, agotado el tiempo), porque la
 *   accion que resuelve cada uno es distinta.
 */

/** Plaza de Armas de Lima: el centro por defecto cuando no hay nada mejor. */
const LIMA: [number, number] = [-12.0464, -77.0428]

/** Nivel de calle: se ve la manzana y el numero de la puerta. */
const ZOOM_CERCA = 17

const ICONO = L.divIcon({
  className: '',
  html: `<svg xmlns="http://www.w3.org/2000/svg" width="30" height="40" viewBox="0 0 24 32" aria-hidden="true">
    <path d="M12 0C5.7 0 .6 5.1.6 11.4.6 20 12 32 12 32s11.4-12 11.4-20.6C23.4 5.1 18.3 0 12 0z" fill="#ef6407"/>
    <circle cx="12" cy="11.2" r="4.3" fill="#fff"/>
  </svg>`,
  iconSize: [30, 40],
  iconAnchor: [15, 40],
})

export type Punto = { latitud: number; longitud: number }

export type MensajeMapa = { texto: string; tono: 'exito' | 'aviso' }

type Propiedades = {
  valor: Punto | null
  alCambiar: (punto: Punto) => void
  /** Centro sugerido, normalmente el del distrito elegido. */
  centroSugerido?: Punto | null
  /** Traduce el punto a una direccion. Lo resuelve quien usa el componente. */
  alRellenar?: ((punto: Punto) => void) | undefined
  /** Hay un rellenado en curso: lo lleva el formulario, que es quien pide. */
  rellenando?: boolean
  /** Como fue el ultimo rellenado. Tambien del formulario. */
  mensajeRelleno?: MensajeMapa | null
}

export function MapaUbicacion({
  valor,
  alCambiar,
  centroSugerido,
  alRellenar,
  rellenando = false,
  mensajeRelleno = null,
}: Propiedades) {
  const [buscando, setBuscando] = useState(false)
  const [aviso, setAviso] = useState<string | null>(null)
  const mapa = useRef<MapaLeaflet | null>(null)

  const centro = useMemo<[number, number]>(() => {
    if (valor) return [valor.latitud, valor.longitud]
    if (centroSugerido) return [centroSugerido.latitud, centroSugerido.longitud]
    return LIMA
  }, [valor, centroSugerido])

  const guardarMapa = useCallback((instancia: MapaLeaflet) => {
    mapa.current = instancia
  }, [])

  /** Lleva la vista al punto. Con animacion, salvo que el sistema pida que no. */
  const volarA = useCallback((punto: Punto) => {
    const destino: [number, number] = [punto.latitud, punto.longitud]
    const sinMovimiento =
      typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (sinMovimiento) mapa.current?.setView(destino, ZOOM_CERCA)
    else mapa.current?.flyTo(destino, ZOOM_CERCA, { duration: 1.1 })
  }, [])

  function usarMiUbicacion() {
    if (!('geolocation' in navigator)) {
      setAviso('Tu navegador no ofrece geolocalizacion. Marca el punto en el mapa.')
      return
    }
    setAviso(null)
    setBuscando(true)
    navigator.geolocation.getCurrentPosition(
      (posicion) => {
        setBuscando(false)
        const punto = { latitud: posicion.coords.latitude, longitud: posicion.coords.longitude }
        alCambiar(punto)
        // Primero la vista: que se vea a donde fue antes de pedir nada a nadie.
        volarA(punto)
        alRellenar?.(punto)
      },
      (error) => {
        setBuscando(false)
        // Cada fallo se resuelve con una accion distinta, asi que cada uno
        // tiene su mensaje. "Error de geolocalizacion" no le dice a nadie que
        // hacer despues.
        if (error.code === error.PERMISSION_DENIED) {
          setAviso('No nos diste permiso de ubicacion. Puedes marcar el punto en el mapa a mano.')
        } else if (error.code === error.POSITION_UNAVAILABLE) {
          setAviso('No pudimos determinar tu ubicacion. Marca el punto en el mapa.')
        } else {
          setAviso('La ubicacion tardo demasiado. Intentalo otra vez o marca el punto a mano.')
        }
      },
      { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 },
    )
  }

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-[13px] font-semibold text-texto-medio">Marca el punto exacto de entrega</p>
        <div className="flex flex-wrap gap-2">
          {alRellenar && (
            <Boton
              type="button"
              variante="sutil"
              tamano="sm"
              onClick={() => valor && alRellenar(valor)}
              disabled={!valor || rellenando || buscando}
              title={valor ? 'Completa calle, distrito, provincia y departamento desde el punto marcado' : undefined}
            >
              {rellenando ? (
                <Loader2 size={14} className="animate-spin" aria-hidden />
              ) : (
                <MapPinned size={14} aria-hidden />
              )}
              {rellenando ? 'Leyendo la direccion...' : 'Rellenar desde el mapa'}
            </Boton>
          )}
          <Boton type="button" variante="sutil" tamano="sm" onClick={usarMiUbicacion} disabled={buscando || rellenando}>
            {buscando ? <Loader2 size={14} className="animate-spin" aria-hidden /> : <Crosshair size={14} aria-hidden />}
            {buscando ? 'Buscando...' : 'Usar mi ubicacion actual'}
          </Boton>
        </div>
      </div>

      {/* `isolate` encierra la numeracion de Leaflet —sus paneles van de 400
          para arriba— dentro de este contenedor. Sin eso, el mapa se pinta por
          encima del desplegable de los tres comboboxes de aqui arriba. */}
      <div className="relative isolate h-72 overflow-hidden rounded-marca border border-borde">
        <MapContainer center={centro} zoom={valor ? ZOOM_CERCA : 12} className="h-full w-full" scrollWheelZoom>
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            maxZoom={19}
          />
          <Puente alCrear={guardarMapa} />
          <CapturaClic alCambiar={alCambiar} />
          <Recentrado centro={centro} hayPunto={valor !== null} />
          {valor && (
            <Marker
              position={[valor.latitud, valor.longitud]}
              icon={ICONO}
              draggable
              eventHandlers={{
                dragend: (evento) => {
                  const { lat, lng } = evento.target.getLatLng()
                  alCambiar({ latitud: lat, longitud: lng })
                },
              }}
            />
          )}
        </MapContainer>
      </div>

      <p className="text-xs text-texto-suave">
        {valor ? (
          <>
            Punto guardado:{' '}
            <span className="cifra font-medium text-texto-medio">
              {valor.latitud.toFixed(5)}, {valor.longitud.toFixed(5)}
            </span>
            . Puedes arrastrar el marcador para afinarlo.
          </>
        ) : (
          'Pulsa en el mapa para marcar donde entregamos. Es opcional, pero ayuda mucho al repartidor.'
        )}
      </p>

      {mensajeRelleno && (
        <p
          role="status"
          className={`rounded-marca px-3 py-2 text-xs font-medium ${
            mensajeRelleno.tono === 'exito' ? 'bg-exito-suave text-exito' : 'bg-aviso-suave text-aviso'
          }`}
        >
          {mensajeRelleno.texto}
        </p>
      )}

      {aviso && (
        <p role="status" className="rounded-marca bg-aviso-suave px-3 py-2 text-xs font-medium text-aviso">
          {aviso}
        </p>
      )}
    </div>
  )
}

/** Saca la instancia del mapa fuera del `<MapContainer/>`, que es el unico
 *  sitio donde `useMap` existe. Sin esto no hay forma de llamar a `flyTo`. */
function Puente({ alCrear }: { alCrear: (mapa: MapaLeaflet) => void }) {
  const mapa = useMap()
  useEffect(() => {
    alCrear(mapa)
  }, [mapa, alCrear])
  return null
}

function CapturaClic({ alCambiar }: { alCambiar: (p: Punto) => void }) {
  useMapEvents({
    click(e) {
      alCambiar({ latitud: e.latlng.lat, longitud: e.latlng.lng })
    },
  })
  return null
}

/** Recentra al cambiar el distrito, pero respeta el punto que ya puso el
 *  usuario: mover el marcador que alguien acaba de colocar es irritante. */
function Recentrado({ centro, hayPunto }: { centro: [number, number]; hayPunto: boolean }) {
  const mapa = useMap()
  const anterior = useRef<string>('')

  useEffect(() => {
    const clave = centro.join(',')
    if (clave === anterior.current) return
    anterior.current = clave
    if (hayPunto) return
    mapa.setView(centro, mapa.getZoom() < 12 ? 12 : mapa.getZoom())
  }, [centro, hayPunto, mapa])

  return null
}
