'use client'

import { peticion } from '@/lib/api.cliente'
import type { Carrito } from '@/lib/tipos'

/**
 * Llamadas del carrito.
 *
 * Salen **del navegador**, directas a la API: son mutaciones con estado de
 * usuario y no tiene sentido enrutarlas por el servidor de Next, que ademas
 * obligaria a reenviar cookies a mano.
 *
 * Hay una correccion defensiva que conviene entender: la respuesta del
 * `POST /carritos/{id}/items` devuelve hoy la linea recien creada con
 * `id: null` (el id lo asigna la base al volcar, y la respuesta se serializa
 * antes). Un carrito con lineas sin id no se puede modificar despues, porque
 * el `PATCH` y el `DELETE` se dirigen justamente a ese id. Asi que cuando llega
 * una linea sin id se vuelve a pedir el carrito completo, que si los trae.
 *
 * Es una vuelta de mas, solo en el alta y solo mientras el backend responda
 * asi. Arreglarlo alla es de otro agente; no verlo aqui seria un carrito roto.
 */

async function conIdsCompletos(carrito: Carrito): Promise<Carrito> {
  if (carrito.items.every((i) => i.id !== null)) return carrito
  return peticion<Carrito>(`/carritos/${carrito.id}`)
}

export async function crearCarrito(): Promise<Carrito> {
  return peticion<Carrito>('/carritos', { metodo: 'POST', cuerpo: {} })
}

export async function obtenerCarrito(id: string): Promise<Carrito> {
  return peticion<Carrito>(`/carritos/${id}`)
}

export async function agregarItem(id: string, productoId: number, cantidad: number): Promise<Carrito> {
  const carrito = await peticion<Carrito>(`/carritos/${id}/items`, {
    metodo: 'POST',
    cuerpo: { productoId, cantidad },
  })
  return conIdsCompletos(carrito)
}

export async function fijarCantidad(id: string, idItem: number, cantidad: number): Promise<Carrito> {
  return peticion<Carrito>(`/carritos/${id}/items/${idItem}`, {
    metodo: 'PATCH',
    cuerpo: { cantidad },
  })
}

export async function quitarItem(id: string, idItem: number): Promise<Carrito> {
  // Bajar de 1 quita la linea, y se hace con DELETE y no con `cantidad: 0`:
  // el contrato declara `minimum: 1` para ese campo, asi que un 0 daria 400.
  return peticion<Carrito>(`/carritos/${id}/items/${idItem}`, { metodo: 'DELETE' })
}

export async function aplicarCupon(id: string, codigo: string): Promise<Carrito> {
  return peticion<Carrito>(`/carritos/${id}/cupon`, { metodo: 'POST', cuerpo: { codigo } })
}

export async function quitarCupon(id: string): Promise<Carrito> {
  return peticion<Carrito>(`/carritos/${id}/cupon`, { metodo: 'DELETE' })
}
