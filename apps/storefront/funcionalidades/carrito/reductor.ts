import type { Carrito, ItemCarrito, ProductoResumen } from '@/lib/tipos'

/**
 * El reductor del carrito.
 *
 * Es una funcion pura: se lee de arriba abajo y se puede probar sin React, sin
 * render y sin mocks.
 *
 * Dos cosas que respeta al pie de la letra, porque son donde una implementacion
 * ingenua se desvia del servidor:
 *
 * - **Un producto, una linea.** Agregar algo que ya esta **suma** a la linea
 *   existente; no crea una segunda.
 * - **No calcula el descuento del cupon.** Los cupones tienen tipo, minimo de
 *   compra, vigencia y usos maximos; reimplementar eso aqui seria duplicar
 *   reglas de negocio en el cliente, que es justo lo que prohibe el ADR-0001.
 *   Mientras hay un cambio en vuelo, descuento y total se marcan como
 *   provisionales y se confirman con `SINCRONIZADO`.
 *
 * Lo que si recalcula: `totalLinea`, `subtotal` y `totalUnidades`. Cantidad por
 * precio unitario es aritmetica, no una regla de negocio, y es lo que hace que
 * el cambio se vea antes de que responda la red.
 */

export type EstadoCarrito = {
  estado: 'inicial' | 'hidratando' | 'listo' | 'error'
  /** Lo que se pinta: puede ser optimista. */
  carrito: Carrito | null
  /** Ultimo carrito autoritativo, para revertir. */
  servidor: Carrito | null
  /** Mutaciones en vuelo. Mientras sea mayor que 0 los totales son provisionales. */
  pendientes: number
  /** Numero de la ultima mutacion despachada. */
  secuencia: number
  /** Codigo del ultimo error, no su mensaje. */
  ultimoError: string | null
}

export const ESTADO_INICIAL: EstadoCarrito = {
  estado: 'inicial',
  carrito: null,
  servidor: null,
  pendientes: 0,
  secuencia: 0,
  ultimoError: null,
}

export type AccionCarrito =
  | { tipo: 'HIDRATANDO' }
  | { tipo: 'HIDRATADO'; carrito: Carrito | null }
  | { tipo: 'AGREGAR'; producto: ProductoResumen; cantidad: number; secuencia: number }
  | { tipo: 'FIJAR_CANTIDAD'; idItem: number; cantidad: number; secuencia: number }
  | { tipo: 'QUITAR'; idItem: number; secuencia: number }
  | { tipo: 'VACIAR'; secuencia: number }
  | { tipo: 'CUPON_EN_CURSO'; secuencia: number }
  | { tipo: 'SINCRONIZADO'; secuencia: number; carrito: Carrito }
  | { tipo: 'FALLO'; secuencia: number; codigo: string }
  | { tipo: 'LIMPIAR_ERROR' }

function carritoVacio(id: string): Carrito {
  return {
    id,
    items: [],
    totalUnidades: 0,
    subtotal: 0,
    descuento: 0,
    total: 0,
    cuponAplicado: null,
    cuponActivo: false,
    motivoCuponInactivo: null,
  }
}

/** Dinero con dos decimales. El servidor manda; esto solo evita que el
 *  optimista ensene 79.00000000000001 durante los 200 ms que dura. */
function redondear(v: number): number {
  return Math.round(v * 100) / 100
}

/** Recalcula lo que es aritmetica. El descuento se conserva tal y como lo dejo
 *  el servidor: no se toca porque aqui no se sabe calcular. */
function recalcular(carrito: Carrito, items: ItemCarrito[]): Carrito {
  const conTotales = items.map((i) => ({ ...i, totalLinea: redondear(i.precioUnitario * i.cantidad) }))
  const subtotal = redondear(conTotales.reduce((s, i) => s + i.totalLinea, 0))
  const descuento = conTotales.length === 0 ? 0 : carrito.descuento
  return {
    ...carrito,
    items: conTotales,
    totalUnidades: conTotales.reduce((s, i) => s + i.cantidad, 0),
    subtotal,
    descuento,
    total: redondear(Math.max(0, subtotal - descuento)),
  }
}

export function reductor(estado: EstadoCarrito, accion: AccionCarrito): EstadoCarrito {
  switch (accion.tipo) {
    case 'HIDRATANDO':
      return { ...estado, estado: 'hidratando' }

    case 'HIDRATADO':
      return {
        ...estado,
        estado: 'listo',
        carrito: accion.carrito,
        servidor: accion.carrito,
        pendientes: 0,
        ultimoError: null,
      }

    case 'AGREGAR': {
      const base = estado.carrito ?? carritoVacio('pendiente')
      const existente = base.items.find((i) => i.productoId === accion.producto.id)
      const items: ItemCarrito[] = existente
        ? base.items.map((i) =>
            i.productoId === accion.producto.id
              ? { ...i, cantidad: Math.min(i.cantidad + accion.cantidad, i.stockDisponible || 99) }
              : i,
          )
        : [
            ...base.items,
            {
              // El id lo pone el servidor. Hasta que responda, la linea se
              // identifica por `productoId`, que es unico por invariante.
              id: null,
              productoId: accion.producto.id,
              nombre: accion.producto.nombre,
              slug: accion.producto.slug,
              imagen: accion.producto.imagen,
              precioUnitario: accion.producto.precio,
              cantidad: accion.cantidad,
              stockDisponible: 99,
              totalLinea: redondear(accion.producto.precio * accion.cantidad),
            },
          ]
      return {
        ...estado,
        carrito: recalcular(base, items),
        pendientes: estado.pendientes + 1,
        secuencia: accion.secuencia,
        ultimoError: null,
      }
    }

    case 'FIJAR_CANTIDAD': {
      if (!estado.carrito) return estado
      const items = estado.carrito.items.map((i) =>
        i.id === accion.idItem ? { ...i, cantidad: accion.cantidad } : i,
      )
      return {
        ...estado,
        carrito: recalcular(estado.carrito, items),
        pendientes: estado.pendientes + 1,
        secuencia: accion.secuencia,
        ultimoError: null,
      }
    }

    case 'QUITAR': {
      if (!estado.carrito) return estado
      const items = estado.carrito.items.filter((i) => i.id !== accion.idItem)
      return {
        ...estado,
        carrito: recalcular(estado.carrito, items),
        pendientes: estado.pendientes + 1,
        secuencia: accion.secuencia,
        ultimoError: null,
      }
    }

    case 'VACIAR': {
      if (!estado.carrito) return estado
      // Se conserva el id del carrito: vaciarlo no lo destruye.
      return {
        ...estado,
        carrito: carritoVacio(estado.carrito.id),
        pendientes: estado.pendientes + 1,
        secuencia: accion.secuencia,
        ultimoError: null,
      }
    }

    case 'CUPON_EN_CURSO':
      // No se toca el descuento. Solo se marca que hay algo en vuelo para que
      // la interfaz atenue el total en lugar de ensenar una cifra que va a
      // cambiar.
      return {
        ...estado,
        pendientes: estado.pendientes + 1,
        secuencia: accion.secuencia,
        ultimoError: null,
      }

    case 'SINCRONIZADO': {
      const pendientes = Math.max(0, estado.pendientes - 1)
      // Una respuesta rezagada no puede hacer retroceder el carrito. Si su
      // secuencia no es la ultima despachada, se descarta: son cuatro lineas
      // que eliminan un fallo muy dificil de diagnosticar despues.
      if (accion.secuencia !== estado.secuencia) {
        return { ...estado, pendientes }
      }
      return {
        ...estado,
        estado: 'listo',
        carrito: accion.carrito,
        servidor: accion.carrito,
        pendientes,
        ultimoError: null,
      }
    }

    case 'FALLO': {
      const pendientes = Math.max(0, estado.pendientes - 1)
      if (accion.secuencia !== estado.secuencia) {
        return { ...estado, pendientes, ultimoError: accion.codigo }
      }
      // Revertir es volver al ultimo carrito autoritativo. Por eso hay dos
      // copias: sin `servidor` no habria punto al que volver.
      return {
        ...estado,
        carrito: estado.servidor,
        pendientes,
        ultimoError: accion.codigo,
      }
    }

    case 'LIMPIAR_ERROR':
      return { ...estado, ultimoError: null }

    default:
      return estado
  }
}

/** Numero de unidades a pintar en la insignia. */
export function unidades(estado: EstadoCarrito): number {
  return estado.carrito?.totalUnidades ?? 0
}
