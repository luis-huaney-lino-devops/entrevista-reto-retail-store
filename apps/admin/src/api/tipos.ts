/**
 * Tipos del contrato.
 *
 * Están en español y coinciden exactamente con los campos JSON del backend
 * (ADR-0011). Esa coincidencia no es estética: es lo que permite que
 * `errors[].field` del backend se use tal cual como clave del formulario.
 */

export type RespuestaPagina<T> = {
  items: T[]
  pagina: number
  tamanoPagina: number
  totalItems: number
  totalPaginas: number
}

export type Referencia = {
  id: number
  nombre: string
  slug: string
}

export type Archivo = {
  id: number
  url: string
  urls: Record<'MINIATURA' | 'TARJETA' | 'DETALLE' | 'ORIGINAL', string>
  textoAlt: string
  ancho: number
  alto: number
  bytes: number
  nombreOriginal: string
  creadoEn: string
}

export type Imagen = {
  archivoId: number
  miniatura: string
  tarjeta: string
  detalle: string
  original: string
  textoAlt: string
  ancho: number
  alto: number
}

export type Marca = {
  id: number
  nombre: string
  slug: string
  descripcion: string | null
  logo: Archivo | null
  activa: boolean
}

export type Subcategoria = {
  id: number
  nombre: string
  slug: string
  descripcion: string | null
  imagen: Archivo | null
  orden: number
  activa: boolean
  categoria: Referencia
}

export type Categoria = {
  id: number
  nombre: string
  slug: string
  descripcion: string | null
  imagen: Archivo | null
  orden: number
  activa: boolean
  /** null cuando no se pidieron; [] cuando no tiene. No es lo mismo. */
  subcategorias: Subcategoria[] | null
}

export type ProductoAdmin = {
  id: number
  sku: string
  nombre: string
  slug: string
  descripcionCorta: string | null
  descripcion: string | null
  precio: number
  precioAnterior: number | null
  porcentajeDescuento: number | null
  stock: number
  destacado: boolean
  activo: boolean
  calificacionPromedio: number
  calificacionConteo: number
  vistas: number
  imagenes: Imagen[]
  marca: Referencia | null
  subcategoria: Referencia
  categoria: Referencia
  creadoEn: string
  creadoPor: string
  actualizadoEn: string
  actualizadoPor: string
  version: number
}

export type Cupon = {
  id: number
  codigo: string
  tipo: 'PORCENTAJE' | 'MONTO_FIJO'
  valor: number
  subtotalMinimo: number
  iniciaEn: string
  terminaEn: string
  usosMaximos: number | null
  usosActuales: number
  activo: boolean
  vigente: boolean
}

export type Administrador = {
  id: number
  usuario: string
  nombre: string
  rol: 'ADMINISTRADOR' | 'SUPERADMINISTRADOR'
  activo: boolean
  ultimoAccesoEn: string | null
  creadoEn: string
}

export type Sesion = {
  tokenAcceso: string
  expiraEnSegundos: number
  administrador: Administrador
}

export type EstadoOrden = 'PENDIENTE' | 'PAGADA' | 'ENVIADA' | 'ENTREGADA' | 'CANCELADA'

export type OrdenResumen = {
  id: number
  numero: string
  nombreContacto: string
  email: string
  total: number
  estado: EstadoOrden
  estadoEtiqueta: string
  totalUnidades: number
  creadoEn: string
}

export type OrdenDetalle = {
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
  estado: EstadoOrden
  estadoEtiqueta: string
  /** Null en una orden de invitado: compró sin dejar cuenta. */
  clienteId: number | null
  clienteNombre: string | null
  /** La conversación que ya existe sobre esta orden, si la hay. */
  conversacionId: number | null
  /** A dónde se puede mover. Vacío significa que el estado es terminal. */
  transicionesPermitidas: EstadoOrden[]
  items: {
    productoId: number
    nombreProducto: string
    sku: string
    precioUnitario: number
    cantidad: number
    totalLinea: number
  }[]
  creadoEn: string
  actualizadoEn: string
  actualizadoPor: string
}

export type Metricas = {
  resumen: {
    ventas30Dias: number
    ordenes30Dias: number
    /** null cuando no hubo órdenes: un promedio de cero sería mentira. */
    ticketPromedio: number | null
    ordenesPendientes: number
    productosActivos: number
    productosBorrador: number
    productosSinStock: number
    vistasTotales: number
    valorInventario: number
  }
  /** Serie densa: incluye los días sin ventas, con cero. */
  ventasPorDia: { fecha: string; total: number; ordenes: number }[]
  ordenesPorEstado: { estado: EstadoOrden; etiqueta: string; cantidad: number; total: number }[]
  masVistos: { id: number; nombre: string; slug: string; vistas: number; imagen: string | null }[]
  masVendidos: { nombre: string; unidades: number; total: number }[]
  stockBajo: { id: number; nombre: string; sku: string; stock: number }[]
}

// ---------------------------------------------------------------- clientes

export type Cliente = {
  id: number
  email: string
  emailVerificado: boolean
  nombre: string
  telefono: string | null
  activo: boolean
  /** Si nunca puso contraseña compró como invitado: no es una cuenta rota. */
  tieneContrasena: boolean
  ordenes: number
  totalComprado: number
  ultimoAccesoEn: string | null
  creadoEn: string
}

export type ClienteDetalle = {
  cliente: Cliente
  ordenes: OrdenResumen[]
  conversaciones: { id: number; asunto: string; estado: string; noLeidos: number }[]
}

// ------------------------------------------------------------------- chat

export type EstadoConversacion = 'ABIERTA' | 'CERRADA'

export type ConversacionResumen = {
  id: number
  asunto: string
  estado: EstadoConversacion
  clienteId: number
  clienteNombre: string
  clienteEmail: string
  ordenId: number | null
  ordenNumero: string | null
  noLeidos: number
  ultimoMensajeEn: string | null
  creadoEn: string
}

export type Adjunto = {
  id: number
  nombre: string
  tipoMime: string
  bytes: number
  url: string
  /** Falso significa PDF: el único otro tipo que se admite. */
  esImagen: boolean
}

export type Mensaje = {
  id: number
  autor: 'CLIENTE' | 'ADMINISTRADOR'
  autorNombre: string
  cuerpo: string | null
  adjuntos: Adjunto[]
  enviadoEn: string
  leidoEn: string | null
}

export type ConversacionDetalle = {
  conversacion: ConversacionResumen
  mensajes: Mensaje[]
  /** Solo lo devuelve el panel: es la llave del lado cliente del hilo. */
  tokenAcceso: string | null
}

// ---------------------------------------------------------- notificaciones

export type TipoNotificacion =
  | 'STOCK_AGOTADO'
  | 'STOCK_BAJO'
  | 'ORDEN_NUEVA'
  | 'ORDEN_CANCELADA'
  | 'MENSAJE_CLIENTE'

export type Notificacion = {
  id: number
  tipo: TipoNotificacion
  severidad: 'INFO' | 'AVISO' | 'URGENTE'
  titulo: string
  detalle: string | null
  enlace: string | null
  leida: boolean
  creadoEn: string
}

export type Bandeja = { items: Notificacion[]; pendientes: number }

// ---------------------------------------------------------------- papelera

export type ElementoEliminado = {
  /** El nombre técnico, el que espera el endpoint de restaurar. */
  tipo: string
  /** El nombre para leer: «Producto», «Marca»… */
  etiquetaTipo: string
  id: number
  nombre: string
  detalle: string | null
  eliminadoEn: string
  eliminadoPor: string
}
