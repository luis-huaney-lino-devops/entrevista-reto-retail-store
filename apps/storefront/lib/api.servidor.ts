import 'server-only'

import { comoErrorApi } from './errores'
import type {
  Carrito,
  Categoria,
  Marca,
  Opinion,
  Pagina,
  ProductoDetalle,
  ProductoResumen,
  Subcategoria,
  FiltrosProductos,
} from './tipos'

/**
 * Cliente HTTP servidor -> servidor.
 *
 * Va marcado con `server-only`: si alguien lo importa desde un componente
 * cliente, **falla la compilacion** en lugar de filtrar la URL interna de la API
 * al HTML. Un comentario pidiendo que no se haga no impide que se haga.
 *
 * La URL que usa no lleva prefijo `NEXT_PUBLIC_`, asi que nunca viaja al
 * navegador. En Docker apunta a la red interna del compose (`http://api:8080`),
 * que no sale a internet y es mas rapida y mas barata que dar la vuelta.
 */
const BASE = process.env.API_URL_INTERNO ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1'

type Opciones = {
  /** Segundos de validez en la cache de Next. `0` fuerza peticion en cada visita. */
  revalidar?: number
  etiquetas?: string[]
}

async function traer<T>(ruta: string, opciones: Opciones = {}): Promise<T> {
  const respuesta = await fetch(`${BASE}${ruta}`, {
    headers: { Accept: 'application/json' },
    next:
      opciones.revalidar === 0
        ? { revalidate: 0 }
        : { revalidate: opciones.revalidar ?? 300, tags: opciones.etiquetas },
  })

  if (respuesta.status === 204) return undefined as T

  const texto = await respuesta.text()
  const cuerpo: unknown = texto ? JSON.parse(texto) : null

  if (!respuesta.ok) {
    throw comoErrorApi(respuesta.status, cuerpo, respuesta.headers.get('Retry-After'))
  }
  return cuerpo as T
}

/**
 * Una lectura que puede fallar sin tumbar la pagina.
 *
 * La portada pide cuatro cosas a la vez; que una seccion secundaria no
 * responda no justifica un `error.tsx` a pantalla completa. Se devuelve el
 * valor de reserva y la seccion sencillamente no se pinta.
 */
export async function tolerante<T>(promesa: Promise<T>, reserva: T): Promise<T> {
  try {
    return await promesa
  } catch (e) {
    console.error('[storefront] lectura tolerante fallida:', e)
    return reserva
  }
}

export function consultaProductos(filtros: FiltrosProductos): string {
  const p = new URLSearchParams()
  if (filtros.texto) p.set('texto', filtros.texto)
  if (filtros.categoria) p.set('categoria', filtros.categoria)
  if (filtros.subcategoria) p.set('subcategoria', filtros.subcategoria)
  if (filtros.marca) p.set('marca', filtros.marca)
  if (filtros.precioMinimo !== undefined) p.set('precioMinimo', String(filtros.precioMinimo))
  if (filtros.precioMaximo !== undefined) p.set('precioMaximo', String(filtros.precioMaximo))
  if (filtros.conStock) p.set('conStock', 'true')
  if (filtros.destacado) p.set('destacado', 'true')
  if (filtros.orden) p.set('orden', filtros.orden)
  if (filtros.pagina && filtros.pagina > 1) p.set('pagina', String(filtros.pagina))
  if (filtros.tamanoPagina) p.set('tamanoPagina', String(filtros.tamanoPagina))
  const q = p.toString()
  return q ? `?${q}` : ''
}

/* --------------------------------------------------------------- catalogo */

export function listarProductos(filtros: FiltrosProductos, opciones?: Opciones) {
  return traer<Pagina<ProductoResumen>>(`/productos${consultaProductos(filtros)}`, opciones)
}

export function productoPorSlug(slug: string, opciones?: Opciones) {
  return traer<ProductoDetalle>(`/productos/${encodeURIComponent(slug)}`, opciones)
}

export function relacionados(slug: string, opciones?: Opciones) {
  return traer<ProductoResumen[]>(`/productos/${encodeURIComponent(slug)}/relacionados`, opciones)
}

/**
 * Las opiniones de un producto, publicas y paginadas.
 *
 * Se piden con la misma validez que la ficha -300 s- y no en el navegador: el
 * texto de las resenas es contenido indexable, y traerlo al montar dejaria el
 * HTML de la pagina sin una sola palabra de lo que dice la gente.
 *
 * La contrapartida es que una opinion recien escrita tarda en aparecerle al
 * resto. Por eso quien escribe la suya invalida esta pagina con la accion de
 * servidor de `app/productos/[slug]/acciones.ts`, en vez de esperar.
 */
export function opinionesDeProducto(slug: string, pagina = 1, opciones?: Opciones) {
  const consulta = pagina > 1 ? `?pagina=${pagina}` : ''
  return traer<Pagina<Opinion>>(`/productos/${encodeURIComponent(slug)}/opiniones${consulta}`, opciones)
}

export function listarCategorias(opciones?: Opciones) {
  return traer<Categoria[]>('/categorias', { revalidar: 600, ...opciones })
}

export function categoriaPorSlug(slug: string, opciones?: Opciones) {
  return traer<Categoria>(`/categorias/${encodeURIComponent(slug)}`, { revalidar: 600, ...opciones })
}

export function subcategoriaPorSlug(slug: string, opciones?: Opciones) {
  return traer<Subcategoria>(`/subcategorias/${encodeURIComponent(slug)}`, { revalidar: 600, ...opciones })
}

export function listarMarcas(opciones?: Opciones) {
  return traer<Marca[]>('/marcas', { revalidar: 600, ...opciones })
}

export function marcaPorSlug(slug: string, opciones?: Opciones) {
  return traer<Marca>(`/marcas/${encodeURIComponent(slug)}`, { revalidar: 600, ...opciones })
}

export function carritoPorId(id: string) {
  return traer<Carrito>(`/carritos/${encodeURIComponent(id)}`, { revalidar: 0 })
}

/**
 * Las categorias que se pintan como mosaico en la portada y en el menu.
 *
 * Se descartan las que no tienen imagen ni subcategorias con producto: el
 * sembrado y las pruebas de humo dejan categorias tecnicas
 * (`categoria-prueba-...`) que no son contenido de tienda. No se "arregla" el
 * dato desde aqui: solo no se ensena lo que no tiene nada que ensenar.
 */
export function esCategoriaDeTienda(c: Categoria): boolean {
  if (!c.activa) return false
  if (/^categoria-(prueba|gemela)-/.test(c.slug)) return false
  return c.imagen !== null && c.subcategorias.length > 0
}
