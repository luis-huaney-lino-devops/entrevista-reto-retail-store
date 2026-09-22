import { dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

/**
 * Configuracion de Next para la tienda.
 *
 * `remotePatterns` autoriza al optimizador de `next/image` a traer las imagenes
 * del backend. En desarrollo el backend sirve los archivos desde
 * `localhost:8080/archivos/...`; en produccion sera el CDN, y por eso el host se
 * lee del entorno en vez de estar escrito aqui.
 */

/** @param {string | undefined} url */
function comoPatron(url) {
  if (!url) return null
  try {
    const u = new URL(url)
    return {
      protocol: u.protocol.replace(':', ''),
      hostname: u.hostname,
      port: u.port || '',
      pathname: '/**',
    }
  } catch {
    return null
  }
}

const patrones = [
  comoPatron(process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1'),
  comoPatron(process.env.NEXT_PUBLIC_CDN_URL),
  comoPatron(process.env.API_URL_INTERNO),
].filter(Boolean)

/** @type {import('next').NextConfig} */
const config = {
  reactStrictMode: true,
  output: 'standalone',
  // El monorepo tiene varios lockfiles y Next elige mal la raiz por su cuenta.
  // Fijarla aqui evita que el build arrastre medio disco en las trazas.
  // `fileURLToPath` y no `new URL(...).pathname`: en Windows ese `pathname`
  // devuelve `/C:/...`, con una barra inicial que rompe el copiado.
  outputFileTracingRoot: dirname(fileURLToPath(import.meta.url)),
  images: {
    // @ts-expect-error los patrones se construyen arriba y ya estan filtrados
    remotePatterns: patrones,
    formats: ['image/webp'],
  },
}

export default config
