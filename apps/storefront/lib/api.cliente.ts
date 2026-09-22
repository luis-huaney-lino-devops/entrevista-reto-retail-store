'use client'

import { comoErrorApi, ErrorApi } from './errores'

/**
 * Cliente HTTP del navegador.
 *
 * Es el mismo patron que `apps/admin/src/api/cliente.ts`, y las tres decisiones
 * que lo forman son las mismas:
 *
 * 1. **El token de acceso vive en memoria**, en una variable de este modulo, y
 *    nunca en `localStorage`. Lo que esta en `localStorage` lo lee cualquier
 *    script que consiga un XSS; una variable de modulo desaparece al recargar,
 *    que es exactamente lo que se quiere.
 *
 * 2. **La sesion se recupera al arrancar** pidiendo `/cuenta/refrescar`. La
 *    cookie de refresco es `httpOnly`, asi que este codigo no puede leerla,
 *    pero el navegador si la envia.
 *
 * 3. **Un 401 reintenta una vez.** Si el token caduco a mitad de una accion, se
 *    refresca y se repite la peticion. Solo una vez: si el refresco tampoco
 *    vale, la sesion termino de verdad.
 *
 * Lo que se anade respecto al panel, y tiene motivo:
 *
 * - **Un solo refresco en vuelo.** El refresco rota el token: si salen tres a la
 *   vez, dos presentan uno ya usado, el backend lo lee como robo y revoca la
 *   familia entera. Expulsar a alguien por una condicion de carrera propia es el
 *   peor resultado posible. Por eso hay una promesa compartida.
 *
 * - **El 401 del arranque no es un error.** La inmensa mayoria de quien entra a
 *   una tienda no tiene sesion. Ese primer refresco va con `silencioso`, que no
 *   dispara el aviso de sesion perdida.
 */

const BASE = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1'

let tokenAcceso: string | null = null
let alPerderSesion: (() => void) | null = null
let refrescoEnVuelo: Promise<boolean> | null = null

export function fijarToken(token: string | null): void {
  tokenAcceso = token
}

export function tokenActual(): string | null {
  return tokenAcceso
}

export function alExpirarSesion(accion: (() => void) | null): void {
  alPerderSesion = accion
}

export type Opciones = {
  metodo?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  cuerpo?: unknown
  /** Las rutas que refrescan no deben reintentar refrescando: son el refresco. */
  sinReintento?: boolean
  /** Un 401 aqui no significa "te echaron": significa "no habias entrado". */
  silencioso?: boolean
  senal?: AbortSignal
}

export async function peticion<T>(ruta: string, opciones: Opciones = {}): Promise<T> {
  let respuesta: Response
  try {
    respuesta = await enviar(ruta, opciones)
  } catch {
    throw new ErrorApi(0, 'RED', 'No pudimos contactar con la tienda.')
  }

  if (respuesta.status === 401 && !opciones.sinReintento) {
    const renovado = await intentarRefrescar()
    if (renovado) {
      try {
        return await interpretar<T>(await enviar(ruta, opciones))
      } catch (e) {
        if (e instanceof ErrorApi) throw e
        throw new ErrorApi(0, 'RED', 'No pudimos contactar con la tienda.')
      }
    }
    if (!opciones.silencioso) alPerderSesion?.()
  }

  return interpretar<T>(respuesta)
}

async function enviar(ruta: string, opciones: Opciones): Promise<Response> {
  const cabeceras: Record<string, string> = { Accept: 'application/json' }
  if (tokenAcceso) cabeceras.Authorization = `Bearer ${tokenAcceso}`

  let cuerpo: BodyInit | undefined
  if (opciones.cuerpo !== undefined) {
    cuerpo = JSON.stringify(opciones.cuerpo)
    cabeceras['Content-Type'] = 'application/json'
  }

  return fetch(`${BASE}${ruta}`, {
    method: opciones.metodo ?? 'GET',
    headers: cabeceras,
    body: cuerpo,
    signal: opciones.senal,
    // Imprescindible: sin esto el navegador no envia ni recibe la cookie de
    // refresco en peticiones a otro origen, y cada recarga cerraria la sesion.
    credentials: 'include',
  })
}

async function interpretar<T>(respuesta: Response): Promise<T> {
  if (respuesta.status === 204) return undefined as T

  const texto = await respuesta.text()
  let cuerpo: unknown = null
  try {
    cuerpo = texto ? JSON.parse(texto) : null
  } catch {
    cuerpo = null
  }

  if (!respuesta.ok) {
    throw comoErrorApi(respuesta.status, cuerpo, respuesta.headers.get('Retry-After'))
  }
  return cuerpo as T
}

/** Renueva el token con la cookie. Un solo refresco en vuelo (ver cabecera). */
export function intentarRefrescar(): Promise<boolean> {
  if (refrescoEnVuelo) return refrescoEnVuelo

  refrescoEnVuelo = (async () => {
    try {
      const respuesta = await fetch(`${BASE}/cuenta/refrescar`, {
        method: 'POST',
        credentials: 'include',
        headers: { Accept: 'application/json' },
      })
      if (!respuesta.ok) {
        tokenAcceso = null
        return false
      }
      const sesion = (await respuesta.json()) as { tokenAcceso: string }
      tokenAcceso = sesion.tokenAcceso
      return true
    } catch {
      tokenAcceso = null
      return false
    } finally {
      // Se libera en el siguiente tick para que las peticiones que llegaron
      // mientras el refresco estaba en vuelo compartan su resultado.
      setTimeout(() => {
        refrescoEnVuelo = null
      }, 0)
    }
  })()

  return refrescoEnVuelo
}
