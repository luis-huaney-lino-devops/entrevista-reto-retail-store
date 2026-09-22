'use client'

import { useEffect, useMemo, useRef, useState } from 'react'
import { MapContainer, Marker, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import L from 'leaflet'
import { Crosshair, Loader2 } from 'lucide-react'

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
 * Tres detalles que lo hacen usable:
 *
 * - **El icono por defecto se arregla a mano.** Leaflet resuelve las rutas de
 *   sus PNG relativas al CSS, y con el empaquetado de Next esas rutas no
 *   existen: el marcador sale invisible. Se construye un icono con SVG en
 *   `data:` y no hay que copiar nada a `public/`.
 * - **El mapa se recentra cuando cambia el distrito**, pero solo si el usuario
 *   no ha puesto ya un punto: mover el marcador que alguien acaba de colocar es
 *   de lo mas irritante que puede hacer un formulario.
 * - **"Usar mi ubicacion actual"** pide geolocalizacion al navegador y trata
 *   los tres fallos por separado (permiso denegado, no disponible, agotado el
 *   tiempo), porque la accion que resuelve cada uno es distinta.
 */

/** Plaza de Armas de Lima: el centro por defecto cuando no hay nada mejor. */
const LIMA: [number, number] = [-12.0464, -77.0428]

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

type Propiedades = {
  valor: Punto | null
  alCambiar: (punto: Punto) => void
  /** Centro sugerido, normalmente el del distrito elegido. */
  centroSugerido?: Punto | null
}

export function MapaUbicacion({ valor, alCambiar, centroSugerido }: Propiedades) {
  const [buscando, setBuscando] = useState(false)
  const [aviso, setAviso] = useState<string | null>(null)

  const centro = useMemo<[number, number]>(() => {
    if (valor) return [valor.latitud, valor.longitud]
    if (centroSugerido) return [centroSugerido.latitud, centroSugerido.longitud]
    return LIMA
  }, [valor, centroSugerido])

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
        alCambiar({ latitud: posicion.coords.latitude, longitud: posicion.coords.longitude })
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
        <Boton type="button" variante="sutil" tamano="sm" onClick={usarMiUbicacion} disabled={buscando}>
          {buscando ? <Loader2 size={14} className="animate-spin" aria-hidden /> : <Crosshair size={14} aria-hidden />}
          {buscando ? 'Buscando...' : 'Usar mi ubicacion actual'}
        </Boton>
      </div>

      <div className="h-72 overflow-hidden rounded-marca border border-borde">
        <MapContainer center={centro} zoom={valor ? 16 : 12} className="h-full w-full" scrollWheelZoom>
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            maxZoom={19}
          />
          <CapturaClic alCambiar={alCambiar} />
          <Recentrado centro={centro} hayPunto={valor !== null} />
          {valor && <Marker position={[valor.latitud, valor.longitud]} icon={ICONO} />}
        </MapContainer>
      </div>

      <p className="text-xs text-texto-suave">
        {valor ? (
          <>
            Punto guardado:{' '}
            <span className="cifra font-medium text-texto-medio">
              {valor.latitud.toFixed(5)}, {valor.longitud.toFixed(5)}
            </span>
          </>
        ) : (
          'Pulsa en el mapa para marcar donde entregamos. Es opcional, pero ayuda mucho al repartidor.'
        )}
      </p>

      {aviso && (
        <p role="status" className="rounded-marca bg-aviso-suave px-3 py-2 text-xs font-medium text-aviso">
          {aviso}
        </p>
      )}
    </div>
  )
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
