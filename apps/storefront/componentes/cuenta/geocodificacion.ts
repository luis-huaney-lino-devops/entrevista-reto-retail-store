'use client'

/**
 * Geocodificacion inversa con Nominatim (OpenStreetMap).
 *
 * De un par de coordenadas saca una direccion postal. Se usa en un unico sitio
 * —el formulario de direcciones— y solo **cuando la persona lo pide**: al pulsar
 * "Usar mi ubicacion actual" o "Rellenar desde el mapa".
 *
 * Las dos reglas de uso de Nominatim que no son opcionales, y como se cumplen:
 *
 * 1. **Identificarse.** La politica pide una aplicacion identificable. Desde un
 *    navegador el `User-Agent` es una cabecera prohibida —`fetch` la descarta en
 *    silencio, no se puede sobrescribir— asi que quien identifica de verdad es
 *    el `Referer`, que el navegador manda solo con el origen de la tienda. Se
 *    envia igualmente la cabecera de aplicacion para que el dia que esto corra
 *    desde el servidor (donde si viaja) no haya que acordarse.
 *
 * 2. **Una peticion por segundo como maximo.** Aqui no se llama en cada
 *    movimiento del marcador —eso serian decenas de peticiones arrastrando— sino
 *    solo tras una accion explicita. Aun asi, `esperarTurno` **reserva el hueco
 *    antes de esperar**, asi que dos llamadas seguidas se espacian solas aunque
 *    alguien pulse dos veces.
 *
 * Nada de esto es critico: si Nominatim falla, tarda o no conoce el punto, la
 * funcion devuelve `null` y quien llama sigue con el punto marcado y los campos
 * a mano. Una ayuda que rompe el formulario cuando falla no es una ayuda.
 */

const SERVICIO = 'https://nominatim.openstreetmap.org/reverse'

/** Nombre y version con los que se presenta la tienda. */
const APLICACION = 'RetailStoreTienda/1.0 (https://github.com/retail-store; tienda de demostracion)'

/** El limite es 1 r/s; se pide 1,2 s para no rozarlo por un reloj desajustado. */
const INTERVALO_MS = 1200

/** Mas de esto y no compensa esperar: se rellena a mano y listo. */
const TIEMPO_MAXIMO_MS = 10_000

export type DireccionInversa = {
  calle: string | null
  numero: string | null
  codigoPostal: string | null
  /** Nombres candidatos, del mas especifico al menos, para cruzar con el ubigeo. */
  departamento: string[]
  provincia: string[]
  distrito: string[]
  /** La direccion completa tal cual la escribe Nominatim, para ensenarla. */
  resumen: string | null
}

type RespuestaNominatim = {
  error?: string
  display_name?: string
  address?: Record<string, string | undefined>
}

let proximoHueco = 0

/**
 * Reserva el siguiente hueco libre y espera a que llegue.
 *
 * Reservar **antes** de esperar es lo que hace que funcione: si se leyera el
 * reloj despues de dormir, dos llamadas lanzadas a la vez cogerian el mismo
 * hueco y saldrian juntas, que es justo lo que el limite prohibe.
 */
async function esperarTurno(): Promise<void> {
  const ahora = Date.now()
  const inicio = Math.max(ahora, proximoHueco)
  proximoHueco = inicio + INTERVALO_MS
  const espera = inicio - ahora
  if (espera > 0) await new Promise((listo) => setTimeout(listo, espera))
}

/** Primer valor no vacio de una lista de claves de `address`. */
function primero(fuente: Record<string, string | undefined>, claves: string[]): string[] {
  const vistos: string[] = []
  for (const clave of claves) {
    const valor = fuente[clave]?.trim()
    if (valor && !vistos.includes(valor)) vistos.push(valor)
  }
  return vistos
}

/**
 * Pide a Nominatim la direccion de un punto.
 *
 * Devuelve `null` —nunca lanza— si la red falla, si tarda mas de la cuenta, si
 * el servicio responde con error o si el punto cae en mitad del mar.
 */
export async function geocodificarInverso(latitud: number, longitud: number): Promise<DireccionInversa | null> {
  await esperarTurno()

  const url = new URL(SERVICIO)
  url.searchParams.set('format', 'jsonv2')
  url.searchParams.set('lat', String(latitud))
  url.searchParams.set('lon', String(longitud))
  // `zoom=18` es el nivel de "edificio / numero de puerta": con menos, Nominatim
  // devuelve el barrio y se pierde la calle, que es justo lo que se venia a por.
  url.searchParams.set('zoom', '18')
  url.searchParams.set('addressdetails', '1')
  url.searchParams.set('accept-language', 'es')

  const corte = new AbortController()
  const temporizador = setTimeout(() => corte.abort(), TIEMPO_MAXIMO_MS)

  try {
    const respuesta = await fetch(url, {
      signal: corte.signal,
      headers: {
        Accept: 'application/json',
        // El navegador descarta esta cabecera (es de las prohibidas) y en su
        // lugar identifica con el `Referer`. Se deja escrita a proposito.
        'User-Agent': APLICACION,
      },
    })
    if (!respuesta.ok) return null

    const datos = (await respuesta.json()) as RespuestaNominatim
    if (datos.error || !datos.address) return null

    const a = datos.address
    return {
      calle: a.road?.trim() ?? a.pedestrian?.trim() ?? a.footway?.trim() ?? null,
      numero: a.house_number?.trim() ?? null,
      codigoPostal: a.postcode?.trim() ?? null,
      // En Peru el ubigeo es departamento > provincia > distrito, y Nominatim no
      // usa esos nombres: reparte lo mismo entre `state`, `county`, `city` y
      // media docena de claves mas segun la zona. Se prueban todas por orden.
      departamento: primero(a, ['state', 'region', 'state_district']),
      provincia: primero(a, ['county', 'state_district', 'province', 'city', 'town']),
      distrito: primero(a, ['city_district', 'district', 'municipality', 'town', 'city', 'suburb', 'village']),
      resumen: datos.display_name?.trim() ?? null,
    }
  } catch {
    // Red caida, CORS, abortado por tiempo... todo acaba igual: se rellena a mano.
    return null
  } finally {
    clearTimeout(temporizador)
  }
}
