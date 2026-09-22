/**
 * Errores de la API, interpretados.
 *
 * La API responde `application/problem+json` (RFC 9457) con dos extensiones que
 * importan: `code`, que es el contrato estable, y `correlationId`, que es lo
 * que permite que alguien diga "me salio 0f9c2b1e" y se encuentre la linea de
 * log exacta.
 *
 * La regla es una sola y no admite excepciones: **ramificar por `code`, nunca
 * por `detail` ni por `title`**. El `detail` es texto para humanos que puede
 * cambiar; el `code` es una constante de protocolo.
 */

export class ErrorApi extends Error {
  constructor(
    readonly estado: number,
    readonly codigo: string,
    mensaje: string,
    readonly errores: { field: string; message: string }[] = [],
    /** Los campos extra del problem+json: `disponible`, `subtotalMinimo`... */
    readonly extra: Record<string, unknown> = {},
    readonly correlationId: string | null = null,
  ) {
    super(mensaje)
    this.name = 'ErrorApi'
  }

  /** Mensaje de un campo concreto, para pintarlo junto a su input. */
  deCampo(campo: string): string | undefined {
    return this.errores.find((e) => e.field === campo)?.message
  }

  private numero(clave: string): number | null {
    const v = this.extra[clave]
    return typeof v === 'number' ? v : null
  }

  private texto(clave: string): string | null {
    const v = this.extra[clave]
    return typeof v === 'string' ? v : null
  }

  /**
   * Un mensaje que le dice al comprador que hacer.
   *
   * "Stock insuficiente" a secas no le dice nada; "solo quedan 3" si. La
   * diferencia son campos que el backend ya esta enviando y que aqui se usan.
   */
  get mensajeUsuario(): string {
    switch (this.codigo) {
      case 'INSUFFICIENT_STOCK': {
        const d = this.numero('disponible')
        return d === null
          ? 'No hay stock suficiente para esa cantidad.'
          : d === 0
            ? 'Ese producto se quedo sin stock.'
            : `Solo quedan ${d} ${d === 1 ? 'unidad' : 'unidades'}.`
      }
      case 'PRODUCT_INACTIVE': {
        const n = this.texto('productoNombre')
        return n ? `"${n}" ya no esta disponible.` : 'Ese producto ya no esta disponible.'
      }
      case 'PRODUCT_NOT_FOUND':
        return 'No encontramos ese producto.'
      case 'COUPON_NOT_FOUND':
        return 'No encontramos ese codigo.'
      case 'COUPON_NOT_APPLICABLE':
        return 'Ese cupon no esta vigente.'
      case 'COUPON_EXHAUSTED':
        return 'Ese cupon ya se agoto.'
      case 'COUPON_MIN_NOT_MET': {
        const actual = this.numero('subtotalActual')
        const minimo = this.numero('subtotalMinimo')
        if (actual !== null && minimo !== null) {
          const falta = Math.max(0, minimo - actual)
          return `Te faltan ${formatoSimple(falta)} para usar este cupon.`
        }
        return 'Tu compra no llega al minimo que pide este cupon.'
      }
      case 'CART_NOT_FOUND':
        return 'Tu carrito ya no existe. Empezamos uno nuevo.'
      case 'VALIDATION_ERROR':
        return this.errores[0]?.message ?? 'Revisa los datos del formulario.'
      case 'INVALID_CREDENTIALS':
        // Un solo mensaje para correo inexistente, contrasena equivocada y
        // cuenta desactivada. Distinguirlos reabriria el agujero que el backend
        // cierra a proposito.
        return 'Correo o contrasena incorrectos.'
      case 'EMAIL_NOT_VERIFIED':
        return 'Tienes que verificar tu correo para usar esta seccion.'
      case 'EMAIL_NOT_VERIFIED_BY_PROVIDER':
        return 'Entra con tu contrasena y vincula Google desde tu perfil.'
      case 'LAST_LOGIN_METHOD':
        return 'Establece una contrasena antes de desvincular Google, o te quedarias sin forma de entrar.'
      case 'TOO_MANY_REQUESTS': {
        const s = this.numero('retryAfter')
        if (s === null) return 'Demasiados intentos. Vuelve a intentarlo en un rato.'
        const min = Math.ceil(s / 60)
        return `Demasiados intentos. Vuelve a intentarlo en ${min} ${min === 1 ? 'minuto' : 'minutos'}.`
      }
      case 'UNAUTHORIZED':
        return 'Tu sesion termino. Vuelve a entrar.'
      case 'RED':
        return 'No pudimos contactar con la tienda. Revisa tu conexion.'
      default:
        return this.correlationId
          ? `No pudimos completar la operacion. Codigo de referencia: ${this.correlationId.slice(0, 8)}`
          : 'No pudimos completar la operacion.'
    }
  }
}

/** Formato de moneda minimo para los mensajes de error. El de la interfaz vive
 *  en `formato.ts`; aqui se repite para no arrastrar una dependencia circular. */
function formatoSimple(v: number): string {
  return `S/ ${v.toFixed(2)}`
}

/** Convierte un cuerpo `problem+json` en un `ErrorApi`. */
export function comoErrorApi(estado: number, cuerpo: unknown, retryAfter?: string | null): ErrorApi {
  if (cuerpo && typeof cuerpo === 'object') {
    const p = cuerpo as Record<string, unknown>
    const { code, detail, title, errors, correlationId, type, status, instance, ...resto } = p
    void type
    void status
    void instance
    if (retryAfter) {
      const s = Number(retryAfter)
      if (Number.isFinite(s)) resto.retryAfter = s
    }
    return new ErrorApi(
      estado,
      typeof code === 'string' ? code : estado === 401 ? 'UNAUTHORIZED' : 'INTERNAL_ERROR',
      (typeof detail === 'string' ? detail : undefined) ??
        (typeof title === 'string' ? title : undefined) ??
        'No se pudo completar la operacion.',
      Array.isArray(errors) ? (errors as { field: string; message: string }[]) : [],
      resto,
      typeof correlationId === 'string' ? correlationId : null,
    )
  }
  return new ErrorApi(estado, estado === 401 ? 'UNAUTHORIZED' : 'INTERNAL_ERROR', 'No se pudo completar la operacion.')
}

/** El mensaje que se le ensena a una persona, venga de donde venga el fallo. */
export function mensajeDeError(e: unknown): string {
  if (e instanceof ErrorApi) return e.mensajeUsuario
  if (e instanceof Error && e.name === 'TypeError') {
    return 'No pudimos contactar con la tienda. Revisa tu conexion.'
  }
  return 'No pudimos completar la operacion.'
}
