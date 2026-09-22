import type { ProductoDetalle } from './tipos'

/**
 * Datos estructurados.
 *
 * Son componentes de servidor que pintan un `<script type="application/ld+json">`:
 * **no anaden ni un byte de JavaScript al cliente**.
 *
 * **Con `aggregateRating`, y solo cuando lo hay.** Ahora la calificacion sale de
 * las opiniones reales del producto: la API la deriva de la tabla `opinion` en
 * cada escritura (RN-093), asi que declararla es contarle al buscador algo que
 * de verdad ocurrio. Lo que no se declara es la de un producto **sin ninguna
 * opinion**: `ratingCount: 0` es dato invalido para Google, y un 0 de promedio
 * significa «todavia nadie», no «pesimo». Esa es la unica condicion.
 */

export function baseUrl(): string {
  return (process.env.NEXT_PUBLIC_URL_TIENDA ?? 'http://localhost:3000').replace(/\/$/, '')
}

function bloque(datos: unknown) {
  return (
    <script
      type="application/ld+json"
      // El contenido es JSON generado aqui, no texto de usuario: `JSON.stringify`
      // escapa las comillas y no hay forma de cerrar la etiqueta desde el dato.
      dangerouslySetInnerHTML={{ __html: JSON.stringify(datos).replace(/</g, '\\u003c') }}
    />
  )
}

export function ProductoJsonLd({ producto }: { producto: ProductoDetalle }) {
  const primera = producto.imagenes[0]
  const imagen = primera?.detalle ?? primera?.tarjeta ?? undefined

  // La condicion es el conteo y no el promedio: un producto cuyas opiniones
  // fueran todas de una estrella tiene un promedio bajo y una valoracion
  // perfectamente real.
  const conteo = producto.calificacionConteo ?? 0
  const promedio = producto.calificacionPromedio ?? 0
  const valoracion =
    conteo > 0
      ? {
          '@type': 'AggregateRating',
          ratingValue: promedio.toFixed(1),
          reviewCount: conteo,
          bestRating: 5,
          worstRating: 1,
        }
      : undefined

  return bloque({
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: producto.nombre,
    description: producto.descripcionCorta ?? undefined,
    image: imagen ? [imagen] : undefined,
    brand: producto.marca ? { '@type': 'Brand', name: producto.marca.nombre } : undefined,
    category: producto.subcategoria?.nombre,
    url: `${baseUrl()}/productos/${producto.slug}`,
    aggregateRating: valoracion,
    offers: {
      '@type': 'Offer',
      price: producto.precio.toFixed(2),
      priceCurrency: 'PEN',
      availability: producto.hayStock
        ? 'https://schema.org/InStock'
        : 'https://schema.org/OutOfStock',
      url: `${baseUrl()}/productos/${producto.slug}`,
    },
  })
}

export type Miga = { nombre: string; href: string }

/** Inicio > Categoria > Subcategoria > Producto. Es la recompensa directa de
 *  haber elegido URL anidadas: las migas salen de la ruta. */
export function MigasJsonLd({ migas }: { migas: Miga[] }) {
  return bloque({
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: migas.map((m, i) => ({
      '@type': 'ListItem',
      position: i + 1,
      name: m.nombre,
      item: `${baseUrl()}${m.href}`,
    })),
  })
}

export function TiendaJsonLd() {
  return bloque({
    '@context': 'https://schema.org',
    '@type': 'Store',
    name: 'Retail Store',
    url: baseUrl(),
    image: `${baseUrl()}/logo.webp`,
    description: 'Materiales de construccion, herramientas, ferreteria y acabados para tu obra o tu casa.',
  })
}
