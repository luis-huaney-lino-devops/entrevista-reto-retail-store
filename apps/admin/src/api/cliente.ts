/**
 * Cliente HTTP del panel.
 *
 * Tres decisiones que conviene entender antes de tocar este archivo:
 *
 * 1. **El token de acceso vive en memoria**, en una variable de este módulo, y
 *    nunca en `localStorage`. Lo que está en `localStorage` lo lee cualquier
 *    script que consiga un XSS; una variable de módulo desaparece al recargar,
 *    que es exactamente lo que se quiere.
 *
 * 2. **La sesión se recupera al arrancar** pidiendo `/admin/refrescar`. La
 *    cookie de refresco es `httpOnly`, así que este código no puede leerla,
 *    pero el navegador sí la envía. Por eso una recarga no expulsa a nadie
 *    aunque el token de acceso se haya perdido.
 *
 * 3. **Un 401 reintenta una vez.** Si el token caducó a mitad de una acción, se
 *    refresca y se repite la petición en lugar de devolver al formulario de
 *    acceso. Solo una vez: si el refresco tampoco vale, la sesión terminó de
 *    verdad.
 */

const BASE: string = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1'

let tokenAcceso: string | null = null
let alPerderSesion: (() => void) | null = null

/** Un error de la API, ya interpretado. */
export class ErrorApi extends Error {
  constructor(
    readonly estado: number,
    /** Contrato estable. Ramifícate por esto, nunca por el mensaje. */
    readonly codigo: string,
    mensaje: string,
    /** Fallos por campo, con el nombre exacto que envió el formulario. */
    readonly errores: { field: string; message: string }[] = [],
    readonly extra: Record<string, unknown> = {},
  ) {
    super(mensaje)
    this.name = 'ErrorApi'
  }

  /** Mensaje de un campo concreto, para pintarlo junto a su input. */
  deCampo(campo: string): string | undefined {
    return this.errores.find((e) => e.field === campo)?.message
  }
}

export function fijarToken(token: string | null): void {
  tokenAcceso = token
}

export function haySesion(): boolean {
  return tokenAcceso !== null
}

/** Qué hacer cuando la sesión se pierde de verdad. Lo instala el contexto. */
export function alExpirarSesion(accion: () => void): void {
  alPerderSesion = accion
}

type Opciones = {
  metodo?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  cuerpo?: unknown
  /** FormData para la subida de archivos: el navegador pone el Content-Type. */
  formulario?: FormData
  /** Las rutas de acceso no deben reintentar refrescando: son las que refrescan. */
  sinReintento?: boolean
}

export async function peticion<T>(ruta: string, opciones: Opciones = {}): Promise<T> {
  const respuesta = await enviar(ruta, opciones)

  if (respuesta.status === 401 && !opciones.sinReintento) {
    const renovado = await intentarRefrescar()
    if (renovado) {
      return interpretar<T>(await enviar(ruta, opciones))
    }
    alPerderSesion?.()
  }

  return interpretar<T>(respuesta)
}

async function enviar(ruta: string, opciones: Opciones): Promise<Response> {
  const cabeceras: Record<string, string> = { Accept: 'application/json' }
  if (tokenAcceso) {
    cabeceras.Authorization = `Bearer ${tokenAcceso}`
  }

  let cuerpo: BodyInit | undefined
  if (opciones.formulario) {
    cuerpo = opciones.formulario
  } else if (opciones.cuerpo !== undefined) {
    cuerpo = JSON.stringify(opciones.cuerpo)
    cabeceras['Content-Type'] = 'application/json'
  }

  return fetch(`${BASE}${ruta}`, {
    method: opciones.metodo ?? 'GET',
    headers: cabeceras,
    body: cuerpo,
    // Imprescindible: sin esto el navegador no envía ni recibe la cookie de
    // refresco en peticiones a otro origen, y cada recarga cerraría la sesión.
    credentials: 'include',
  })
}

async function interpretar<T>(respuesta: Response): Promise<T> {
  if (respuesta.status === 204) {
    return undefined as T
  }

  const texto = await respuesta.text()
  const cuerpo: unknown = texto ? JSON.parse(texto) : null

  if (!respuesta.ok) {
    throw comoError(respuesta.status, cuerpo)
  }
  return cuerpo as T
}

function comoError(estado: number, cuerpo: unknown): ErrorApi {
  if (cuerpo && typeof cuerpo === 'object') {
    const problema = cuerpo as Record<string, unknown>
    const { code, detail, title, errors, ...resto } = problema
    return new ErrorApi(
      estado,
      typeof code === 'string' ? code : 'INTERNAL_ERROR',
      (typeof detail === 'string' ? detail : undefined) ??
        (typeof title === 'string' ? title : undefined) ??
        'No se pudo completar la operación.',
      Array.isArray(errors) ? (errors as { field: string; message: string }[]) : [],
      resto,
    )
  }
  return new ErrorApi(estado, 'INTERNAL_ERROR', 'No se pudo contactar con el servidor.')
}

/** Renueva el token con la cookie. Devuelve false si ya no hay sesión. */
export async function intentarRefrescar(): Promise<boolean> {
  try {
    const respuesta = await fetch(`${BASE}/admin/refrescar`, {
      method: 'POST',
      credentials: 'include',
    })
    if (!respuesta.ok) {
      tokenAcceso = null
      return false
    }
    const sesion = (await respuesta.json()) as { tokenAcceso: string }
    tokenAcceso = sesion.tokenAcceso
    return true
  } catch {
    // Red caída: no es una sesión expirada, pero tampoco se puede continuar.
    tokenAcceso = null
    return false
  }
}
