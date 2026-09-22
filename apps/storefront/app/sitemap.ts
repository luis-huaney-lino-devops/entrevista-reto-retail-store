import type { MetadataRoute } from 'next'

import { esCategoriaDeTienda, listarCategorias, listarProductos, tolerante } from '@/lib/api.servidor'
import { baseUrl } from '@/lib/jsonld'

/**
 * El sitemap, generado desde la API.
 *
 * Solo entra lo que es **contenido publico e indexable**: portada, catalogo sin
 * parametros, categorias, subcategorias y fichas de producto. El carrito, los
 * favoritos, el comparador y la cuenta quedan fuera —no son contenido, y en
 * algun caso son datos de una persona.
 *
 * Se recorre el catalogo pagina a pagina con el tope que impone la API (48).
 * Si la API no responde, se devuelve al menos la portada: un sitemap corto es
 * mejor que un `500` en la ruta que el rastreador pide primero.
 */

export const revalidate = 3600

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const base = baseUrl()
  const ahora = new Date()

  const entradas: MetadataRoute.Sitemap = [
    { url: `${base}/`, lastModified: ahora, changeFrequency: 'daily', priority: 1 },
    { url: `${base}/productos`, lastModified: ahora, changeFrequency: 'daily', priority: 0.9 },
  ]

  const categorias = (await tolerante(listarCategorias(), [])).filter(esCategoriaDeTienda)
  for (const c of categorias) {
    entradas.push({
      url: `${base}/c/${c.slug}`,
      lastModified: ahora,
      changeFrequency: 'weekly',
      priority: 0.8,
    })
    for (const s of c.subcategorias) {
      entradas.push({
        url: `${base}/c/${c.slug}/${s.slug}`,
        lastModified: ahora,
        changeFrequency: 'weekly',
        priority: 0.7,
      })
    }
  }

  let pagina = 1
  let totalPaginas = 1
  // Tope de seguridad: 20 paginas de 48 son 960 productos. Mas que eso pide un
  // sitemap con indice, no una lista mas larga.
  while (pagina <= totalPaginas && pagina <= 20) {
    const respuesta = await tolerante(listarProductos({ tamanoPagina: 48, pagina }, { revalidar: 3600 }), null)
    if (!respuesta) break
    totalPaginas = respuesta.totalPaginas
    for (const p of respuesta.items) {
      entradas.push({
        url: `${base}/productos/${p.slug}`,
        lastModified: ahora,
        changeFrequency: 'weekly',
        priority: 0.6,
      })
    }
    pagina += 1
  }

  return entradas
}
