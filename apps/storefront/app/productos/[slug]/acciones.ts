'use server'

import { revalidatePath } from 'next/cache'

/**
 * Invalida la ficha de un producto en la cache de Next.
 *
 * Existe por una razon concreta. La ficha es ISR con 300 s de validez, y las
 * opiniones se renderizan en el servidor para que su texto viaje en el HTML.
 * Sin esto, quien acaba de escribir la suya no la veria aparecer -ni veria
 * moverse el promedio- hasta que la pagina caducara, y lo normal es que
 * concluyera que no se guardo y volviera a enviarla.
 *
 * `router.refresh()` por si solo no basta: en una ruta estatica devuelve la
 * version cacheada. Lo que hay que invalidar es la cache del servidor, y eso
 * solo se puede hacer desde el servidor.
 *
 * No escribe nada ni recibe datos del formulario: la opinion se envia a la API
 * de Java con el token del navegador, que este proceso no tiene. Esto es
 * exclusivamente «esa pagina quedo vieja».
 */
export async function refrescarFichaProducto(slug: string): Promise<void> {
  revalidatePath(`/productos/${slug}`)
}
