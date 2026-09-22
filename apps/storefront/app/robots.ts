import type { MetadataRoute } from 'next'

import { baseUrl } from '@/lib/jsonld'

/**
 * `robots.txt`.
 *
 * Se excluye lo que no es contenido: carrito, favoritos, comparador y toda la
 * zona de cuenta. Las dos ultimas ademas son datos de una persona.
 *
 * El catalogo filtrado (`/productos?...`) **no se bloquea aqui**, y es
 * deliberado: lleva `noindex, follow` en sus metadatos, que es distinto de
 * prohibir el rastreo. Bloquearlo en `robots.txt` impediria al rastreador leer
 * ese `noindex` y, sobre todo, seguir los enlaces a las fichas de producto, que
 * es justo lo que interesa que siga.
 */
export default function robots(): MetadataRoute.Robots {
  const base = baseUrl()

  return {
    rules: [
      {
        userAgent: '*',
        allow: '/',
        // `/pedido`, `/restablecer` y `/verificar` llevan datos de alguien o
        // un token de un solo uso en la URL. Que un rastreador los siga seria
        // publicar un pedido ajeno o quemar un enlace de recuperacion.
        disallow: [
          '/carrito',
          '/favoritos',
          '/comparar',
          '/mi-cuenta',
          '/acceso',
          '/registro',
          '/checkout',
          '/pedido',
          '/recuperar',
          '/restablecer',
          '/verificar',
        ],
      },
    ],
    sitemap: `${base}/sitemap.xml`,
    host: base,
  }
}
