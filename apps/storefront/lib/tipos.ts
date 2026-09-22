/**
 * Tipos del contrato REST.
 *
 * Escritos a mano contra las respuestas reales de la API
 * (`curl http://localhost:8080/api/v1/productos?tamanoPagina=2`) y cotejados
 * con `contracts/openapi.yaml`. No hay campos inventados: lo que no devuelve la
 * API no esta aqui.
 *
 * Todo campo que la API puede omitir se declara `| null`, porque el contrato de
 * OpenAPI no marca `required` y Jackson omite o envia `null` segun el caso.
 */

/** Referencia ligera a otra entidad: la forma que devuelve la API para marca,
 *  subcategoria y categoria dentro de un producto. */
export type Referencia = {
  id: number
  nombre: string
  slug: string
}

/** Un archivo del CDN con sus cuatro variantes. Las claves del mapa `urls`
 *  llegan en MAYUSCULAS porque son el nombre del enum del backend. */
export type Archivo = {
  id: number
  url: string
  urls: Partial<Record<'MINIATURA' | 'TARJETA' | 'DETALLE' | 'ORIGINAL', string>>
  textoAlt: string | null
  ancho: number | null
  alto: number | null
  bytes: number | null
  nombreOriginal: string | null
  creadoEn: string | null
}

/** Imagen dentro del detalle de producto. Aqui las variantes no vienen en un
 *  mapa sino como campos sueltos: es otra forma, no un descuido. */
export type ImagenProducto = {
  archivoId: number
  miniatura: string | null
  tarjeta: string | null
  detalle: string | null
  original: string | null
  textoAlt: string | null
  ancho: number | null
  alto: number | null
}

/** Producto tal y como llega en un listado. */
export type ProductoResumen = {
  id: number
  nombre: string
  slug: string
  descripcionCorta: string | null
  precio: number
  precioAnterior: number | null
  porcentajeDescuento: number | null
  hayStock: boolean
  destacado: boolean
  calificacionPromedio: number | null
  calificacionConteo: number | null
  imagen: string | null
  textoAltImagen: string | null
  marca: Referencia | null
  subcategoria: Referencia | null
}

/** Producto completo. Anade `descripcion` (Markdown), `stock`, la galeria y la
 *  categoria, que el resumen no trae. */
export type ProductoDetalle = {
  id: number
  nombre: string
  slug: string
  descripcionCorta: string | null
  descripcion: string | null
  precio: number
  precioAnterior: number | null
  porcentajeDescuento: number | null
  stock: number
  hayStock: boolean
  destacado: boolean
  calificacionPromedio: number | null
  calificacionConteo: number | null
  /** Las cinco barras, de 5 a 1. Vienen siempre, aunque el producto no tenga
   *  ninguna opinion: una barra ausente se lee como un dato que falta. */
  desgloseCalificacion: DesgloseCalificacion[]
  imagenes: ImagenProducto[]
  marca: Referencia | null
  subcategoria: Referencia | null
  categoria: Referencia | null
}

export type Subcategoria = {
  id: number
  nombre: string
  slug: string
  descripcion: string | null
  imagen: Archivo | null
  orden: number
  activa: boolean
  categoria: Referencia | null
}

export type Categoria = {
  id: number
  nombre: string
  slug: string
  descripcion: string | null
  imagen: Archivo | null
  orden: number
  activa: boolean
  subcategorias: Subcategoria[]
}

export type Marca = {
  id: number
  nombre: string
  slug: string
  descripcion: string | null
  logo: Archivo | null
  activa: boolean
}

export type Pagina<T> = {
  items: T[]
  pagina: number
  tamanoPagina: number
  totalItems: number
  totalPaginas: number
}

/* ---------------------------------------------------------------- opiniones */

/** Una barra del desglose por estrellas de un producto. */
export type DesgloseCalificacion = {
  estrellas: number
  cantidad: number
  /** Lo calcula la API para que el servidor y la tienda no puedan discrepar. */
  porcentaje: number
}

/**
 * Una opinion publicada.
 *
 * `autor` llega ya abreviado por la API -«Maria Q.»- y no hay forma de pedir el
 * nombre completo ni el correo: no viajan. La tienda lo pinta tal cual.
 */
export type Opinion = {
  id: number
  productoId: number
  calificacion: number
  titulo: string
  cuerpo: string
  compraVerificada: boolean
  autor: string
  creadoEn: string
  actualizadoEn: string
  /** `actualizadoEn` distinto de `creadoEn`. Lo decide la API, no la tienda. */
  editada: boolean
}

/** Lo que se envia al escribir o editar. El autor sale del token y la insignia
 *  la calcula el servidor: ninguno de los dos se manda desde aqui. */
export type PeticionOpinion = {
  calificacion: number
  titulo: string
  cuerpo: string
}

/** Topes que impone la API. Se repiten aqui para avisar antes de enviar. */
export const TITULO_OPINION_MAXIMO = 120
export const CUERPO_OPINION_MAXIMO = 2000

/* ------------------------------------------------------------------ carrito */

export type ItemCarrito = {
  /** Puede llegar `null` en la respuesta del POST de alta; ver `api.cliente.ts`. */
  id: number | null
  productoId: number
  nombre: string
  slug: string
  imagen: string | null
  precioUnitario: number
  cantidad: number
  stockDisponible: number
  totalLinea: number
}

export type Carrito = {
  id: string
  items: ItemCarrito[]
  totalUnidades: number
  subtotal: number
  descuento: number
  total: number
  /** El codigo del cupon, no un objeto. */
  cuponAplicado: string | null
  cuponActivo: boolean
  motivoCuponInactivo: string | null
}

/* ------------------------------------------------------------------ cuenta */

export type Cliente = {
  id: number
  email: string
  emailVerificado: boolean
  nombre: string
  telefono: string | null
  tieneContrasena: boolean
}

export type Sesion = {
  tokenAcceso: string
  expiraEnSegundos: number
  cliente: Cliente
}

/** Departamento, provincia o distrito. El `id` es **numerico**: asi lo
 *  devuelve `GET /ubigeo/departamentos`, comprobado contra la API viva. */
export type UbigeoItem = {
  id: number
  nombre: string
}

export type Direccion = {
  id: number
  etiqueta: string
  destinatario: string
  telefono: string
  calle: string
  numero: string | null
  referencia: string | null
  codigoPostal: string | null
  latitud: number | null
  longitud: number | null
  predeterminada: boolean
  distrito: UbigeoItem | null
  provincia: UbigeoItem | null
  departamento: UbigeoItem | null
}

export type PeticionDireccion = {
  distritoId: number
  etiqueta: string
  destinatario: string
  telefono: string
  calle: string
  numero?: string
  referencia?: string
  codigoPostal?: string
  latitud?: number
  longitud?: number
  predeterminada: boolean
}

/* ------------------------------------------------------------------ filtros */

/** Los cinco valores que acepta `orden`. La API rechaza cualquier otro con
 *  `400 VALIDATION_ERROR`, asi que la lista es cerrada tambien aqui. */
export const ORDENES = ['recientes', 'precio_asc', 'precio_desc', 'nombre_asc', 'calificacion'] as const
export type Orden = (typeof ORDENES)[number]

export const ETIQUETAS_ORDEN: Record<Orden, string> = {
  recientes: 'Mas recientes',
  precio_asc: 'Precio: de menor a mayor',
  precio_desc: 'Precio: de mayor a menor',
  nombre_asc: 'Nombre (A-Z)',
  calificacion: 'Mejor calificados',
}

export function esOrden(v: string | undefined): v is Orden {
  return v !== undefined && (ORDENES as readonly string[]).includes(v)
}

/** Tope que impone la API (`tamanoPagina` no puede superar 48). */
export const TAMANO_PAGINA_MAXIMO = 48
/** Tope que impone la API a la cantidad de una linea de carrito. */
export const CANTIDAD_MAXIMA = 99

export type FiltrosProductos = {
  texto?: string
  categoria?: string
  subcategoria?: string
  marca?: string
  precioMinimo?: number
  precioMaximo?: number
  conStock?: boolean
  destacado?: boolean
  orden?: Orden
  pagina?: number
  tamanoPagina?: number
}

/* ------------------------------------------------------------------ pedidos */

/**
 * Una linea de pedido. Los datos estan **copiados** del producto, no
 * referenciados (RN-052): una orden es un hecho ocurrido y tiene que seguir
 * diciendo que se compro y a que precio aunque el producto cambie despues.
 * Por eso hay `nombreProducto` y `precioUnitario` aqui, y no solo un id.
 */
export type LineaPedido = {
  productoId: number
  nombreProducto: string
  sku: string
  precioUnitario: number
  cantidad: number
  totalLinea: number
}

export type Pedido = {
  id: number
  numero: string
  nombreContacto: string
  email: string
  telefono: string | null
  direccion: string
  subtotal: number
  descuento: number
  total: number
  codigoCupon: string | null
  estado: string
  estadoEtiqueta: string
  clienteId: number | null
  clienteNombre: string | null
  /** La conversacion abierta sobre este pedido, si ya hay una. */
  conversacionId: number | null
  transicionesPermitidas: string[]
  items: LineaPedido[]
  creadoEn: string
  actualizadoEn: string | null
  actualizadoPor: string | null
}

/**
 * Lo que viaja al confirmar la compra.
 *
 * **No lleva ni un importe, y es deliberado** (RN-051): el servidor recalcula
 * subtotal, descuento y total desde el carrito. Al no existir el campo, no hay
 * forma de mandar un total aunque se quiera.
 */
export type CrearPedido = {
  carritoId: string
  nombreContacto: string
  email: string
  telefono?: string
  direccion: string
}
