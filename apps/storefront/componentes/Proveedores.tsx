'use client'

import type { ReactNode } from 'react'

import { ProveedorAvisos } from '@/funcionalidades/avisos/ProveedorAvisos'
import { ProveedorCarrito } from '@/funcionalidades/carrito/ProveedorCarrito'
import { ProveedorComparador } from '@/funcionalidades/comparador/ProveedorComparador'
import { ProveedorFavoritos } from '@/funcionalidades/favoritos/ProveedorFavoritos'
import { ProveedorSesion } from '@/funcionalidades/sesion/ProveedorSesion'

/**
 * Los proveedores de cliente, en un solo archivo.
 *
 * El orden importa y no es casual:
 *
 * 1. `ProveedorAvisos` primero: el carrito le avisa de los errores.
 * 2. `ProveedorSesion` despues: los favoritos necesitan saber si hay sesion
 *    para decidir si suben los de invitado.
 * 3. El resto.
 *
 * `children` llega como prop y por tanto **sigue siendo servidor**. Es el
 * detalle que hace que envolver el layout entero no convierta toda la tienda en
 * una SPA: React renderiza los hijos en el servidor y aqui solo se envuelven.
 */
export function Proveedores({ children }: { children: ReactNode }) {
  return (
    <ProveedorAvisos>
      <ProveedorSesion>
        <ProveedorFavoritos>
          <ProveedorComparador>
            <ProveedorCarrito>{children}</ProveedorCarrito>
          </ProveedorComparador>
        </ProveedorFavoritos>
      </ProveedorSesion>
    </ProveedorAvisos>
  )
}
