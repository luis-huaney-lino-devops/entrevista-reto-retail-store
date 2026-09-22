'use client'

import { useEffect, useState } from 'react'
import { Heart } from 'lucide-react'

import { Contenedor, Migas } from '@/componentes/disposicion/Seccion'
import { RejillaProductos } from '@/componentes/producto/RejillaProductos'
import { EnlaceBoton } from '@/componentes/ui/Boton'
import { EsqueletoRejilla, SinResultados } from '@/componentes/ui/Estados'
import { peticion } from '@/lib/api.cliente'
import type { ProductoResumen } from '@/lib/tipos'
import { useFavoritos } from '@/funcionalidades/favoritos/ProveedorFavoritos'
import { useSesion } from '@/funcionalidades/sesion/ProveedorSesion'

/**
 * Mis favoritos.
 *
 * Cliente, porque los ids viven en `localStorage` mientras no hay sesion.
 *
 * Con sesion se pide `GET /cuenta/favoritos`, que devuelve los productos
 * enteros. Sin sesion solo hay ids, asi que hay que traer cada producto: se
 * hace con un `GET /productos/{slug}`... salvo que no se tiene el slug, solo el
 * id. La forma de resolverlo sin inventar un endpoint es recorrer el catalogo
 * una vez (48 por pagina, 113 productos) y quedarse con los marcados. Es una
 * lectura mas, ocurre solo en esta pagina y solo sin sesion.
 */
export default function PaginaFavoritos() {
  const { ids, montado } = useFavoritos()
  const { estado: estadoSesion } = useSesion()
  const [productos, setProductos] = useState<ProductoResumen[] | null>(null)

  useEffect(() => {
    if (!montado || estadoSesion === 'desconocida') return

    let vivo = true
    void (async () => {
      if (ids.size === 0) {
        if (vivo) setProductos([])
        return
      }

      if (estadoSesion === 'autenticado') {
        try {
          const remotos = await peticion<ProductoResumen[]>('/cuenta/favoritos')
          if (vivo) setProductos(remotos)
          return
        } catch {
          // La API de cuenta todavia no responde: se resuelve como invitado.
        }
      }

      try {
        const encontrados = await porIds(ids)
        if (vivo) setProductos(encontrados)
      } catch {
        if (vivo) setProductos([])
      }
    })()

    return () => {
      vivo = false
    }
  }, [ids, montado, estadoSesion])

  return (
    <Contenedor className="pb-12">
      <Migas
        items={[
          { nombre: 'Inicio', href: '/' },
          { nombre: 'Mis favoritos', href: '/favoritos' },
        ]}
      />

      <header className="mb-6">
        <h1 className="font-marca text-2xl font-bold text-tinta sm:text-3xl">Mis favoritos</h1>
        <p className="mt-1 text-sm text-texto-suave">
          {estadoSesion === 'autenticado'
            ? 'Guardados en tu cuenta, disponibles desde cualquier dispositivo.'
            : 'Guardados en este navegador. Si creas una cuenta, los subimos contigo.'}
        </p>
      </header>

      {productos === null ? (
        <EsqueletoRejilla cuantas={4} />
      ) : productos.length === 0 ? (
        <SinResultados
          icono={<Heart size={38} strokeWidth={1.5} />}
          titulo="Todavia no guardaste nada"
          descripcion="Pulsa el corazon de cualquier producto y lo encontraras aqui."
          accion={
            <EnlaceBoton href="/productos" variante="primario">
              Ver el catalogo
            </EnlaceBoton>
          }
        />
      ) : (
        <RejillaProductos productos={productos} columnas="ancha" />
      )}
    </Contenedor>
  )
}

/** Recorre el catalogo y devuelve los productos cuyos ids estan marcados. */
async function porIds(ids: ReadonlySet<number>): Promise<ProductoResumen[]> {
  const encontrados = new Map<number, ProductoResumen>()
  let pagina = 1
  let totalPaginas = 1

  // Tope de seguridad: si el catalogo creciera mucho, esto dejaria de ser
  // razonable y haria falta un endpoint que acepte una lista de ids.
  while (pagina <= totalPaginas && pagina <= 10 && encontrados.size < ids.size) {
    const respuesta = await peticion<{ items: ProductoResumen[]; totalPaginas: number }>(
      `/productos?tamanoPagina=48&pagina=${pagina}`,
    )
    totalPaginas = respuesta.totalPaginas
    for (const p of respuesta.items) {
      if (ids.has(p.id)) encontrados.set(p.id, p)
    }
    pagina += 1
  }

  // Se respeta el orden en que se marcaron.
  return [...ids].map((id) => encontrados.get(id)).filter((p): p is ProductoResumen => p !== undefined)
}
