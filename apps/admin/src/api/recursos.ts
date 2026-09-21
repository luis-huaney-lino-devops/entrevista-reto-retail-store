/**
 * Los endpoints del panel, en un solo sitio.
 *
 * Un módulo por recurso sería más ordenado en un proyecto grande; aquí caben
 * todos y tenerlos juntos hace evidente qué superficie consume el panel.
 */
import { peticion } from './cliente'
import type {
  Administrador,
  Adjunto,
  Archivo,
  Bandeja,
  Categoria,
  Cliente,
  ClienteDetalle,
  ConversacionDetalle,
  ConversacionResumen,
  Cupon,
  ElementoEliminado,
  EstadoOrden,
  Marca,
  Mensaje,
  Metricas,
  Notificacion,
  OrdenDetalle,
  OrdenResumen,
  ProductoAdmin,
  RespuestaPagina,
  Sesion,
  Subcategoria,
} from './tipos'

// ----------------------------------------------------------------- sesión

export const sesion = {
  acceder: (usuario: string, contrasena: string) =>
    peticion<Sesion>('/admin/acceso', {
      metodo: 'POST',
      cuerpo: { usuario, contrasena },
      sinReintento: true,
    }),

  salir: () => peticion<void>('/admin/salir', { metodo: 'POST', sinReintento: true }),

  yo: () => peticion<Administrador>('/admin/administradores/yo'),

  cambiarContrasena: (contrasenaActual: string, contrasenaNueva: string) =>
    peticion<void>('/admin/administradores/yo/contrasena', {
      metodo: 'POST',
      cuerpo: { contrasenaActual, contrasenaNueva },
    }),
}

// --------------------------------------------------------------- productos

export type FiltroProductos = {
  texto?: string
  activo?: boolean
  subcategoria?: string
  marca?: string
  orden?: string
  pagina?: number
  tamanoPagina?: number
}

export type ProductoCuerpo = {
  sku?: string
  nombre: string
  descripcionCorta: string | null
  descripcion: string | null
  subcategoriaId: number
  marcaId: number | null
  precio: number
  precioAnterior: number | null
  stock: number
  destacado: boolean
  imagenIds: number[]
}

export const productos = {
  listar: (filtro: FiltroProductos) =>
    peticion<RespuestaPagina<ProductoAdmin>>(`/admin/productos${consulta(filtro)}`),

  porId: (id: number) => peticion<ProductoAdmin>(`/admin/productos/${id}`),

  crear: (cuerpo: ProductoCuerpo) =>
    peticion<ProductoAdmin>('/admin/productos', { metodo: 'POST', cuerpo }),

  actualizar: (id: number, cuerpo: ProductoCuerpo) =>
    peticion<ProductoAdmin>(`/admin/productos/${id}`, { metodo: 'PUT', cuerpo }),

  cambiarEstado: (id: number, activo: boolean) =>
    peticion<ProductoAdmin>(`/admin/productos/${id}/estado`, { metodo: 'PATCH', cuerpo: { activo } }),

  /** Un clic desde la lista: no hay que reenviar el producto entero. */
  cambiarDestacado: (id: number, destacado: boolean) =>
    peticion<ProductoAdmin>(`/admin/productos/${id}/destacado`, {
      metodo: 'PATCH',
      cuerpo: { destacado },
    }),

  eliminar: (id: number) => peticion<void>(`/admin/productos/${id}`, { metodo: 'DELETE' }),
}

// -------------------------------------------------------------- categorías

export const categorias = {
  listar: () => peticion<RespuestaPagina<Categoria>>('/admin/categorias?tamanoPagina=100'),

  porId: (id: number) => peticion<Categoria>(`/admin/categorias/${id}`),

  crear: (cuerpo: { nombre: string; descripcion: string | null; orden: number; imagenId: number | null }) =>
    peticion<Categoria>('/admin/categorias', { metodo: 'POST', cuerpo }),

  actualizar: (
    id: number,
    cuerpo: { nombre: string; descripcion: string | null; orden: number; imagenId: number | null; activa: boolean },
  ) => peticion<Categoria>(`/admin/categorias/${id}`, { metodo: 'PUT', cuerpo }),

  cambiarEstado: (id: number, activo: boolean) =>
    peticion<Categoria>(`/admin/categorias/${id}/estado`, { metodo: 'PATCH', cuerpo: { activo } }),

  eliminar: (id: number) => peticion<void>(`/admin/categorias/${id}`, { metodo: 'DELETE' }),
}

// ------------------------------------------------------------ subcategorías

export const subcategorias = {
  listar: () => peticion<RespuestaPagina<Subcategoria>>('/admin/subcategorias?tamanoPagina=200'),

  crear: (cuerpo: {
    categoriaId: number
    nombre: string
    descripcion: string | null
    orden: number
    imagenId: number | null
  }) => peticion<Subcategoria>('/admin/subcategorias', { metodo: 'POST', cuerpo }),

  actualizar: (
    id: number,
    cuerpo: {
      categoriaId: number
      nombre: string
      descripcion: string | null
      orden: number
      imagenId: number | null
      activa: boolean
    },
  ) => peticion<Subcategoria>(`/admin/subcategorias/${id}`, { metodo: 'PUT', cuerpo }),

  cambiarEstado: (id: number, activo: boolean) =>
    peticion<Subcategoria>(`/admin/subcategorias/${id}/estado`, { metodo: 'PATCH', cuerpo: { activo } }),

  eliminar: (id: number) => peticion<void>(`/admin/subcategorias/${id}`, { metodo: 'DELETE' }),
}

// ------------------------------------------------------------------ marcas

export const marcas = {
  listar: (texto?: string) =>
    peticion<RespuestaPagina<Marca>>(`/admin/marcas${consulta({ texto, tamanoPagina: 100 })}`),

  crear: (cuerpo: { nombre: string; descripcion: string | null; logoId: number | null }) =>
    peticion<Marca>('/admin/marcas', { metodo: 'POST', cuerpo }),

  actualizar: (
    id: number,
    cuerpo: { nombre: string; descripcion: string | null; logoId: number | null; activa: boolean },
  ) => peticion<Marca>(`/admin/marcas/${id}`, { metodo: 'PUT', cuerpo }),

  cambiarEstado: (id: number, activo: boolean) =>
    peticion<Marca>(`/admin/marcas/${id}/estado`, { metodo: 'PATCH', cuerpo: { activo } }),

  eliminar: (id: number) => peticion<void>(`/admin/marcas/${id}`, { metodo: 'DELETE' }),
}

// ----------------------------------------------------------------- cupones

export type CuponCuerpo = {
  codigo: string
  tipo: 'PORCENTAJE' | 'MONTO_FIJO'
  valor: number
  subtotalMinimo: number
  iniciaEn: string
  terminaEn: string
  usosMaximos: number | null
  activo: boolean
}

export const cupones = {
  listar: (pagina = 1) => peticion<RespuestaPagina<Cupon>>(`/admin/cupones?pagina=${pagina}`),

  crear: (cuerpo: CuponCuerpo) => peticion<Cupon>('/admin/cupones', { metodo: 'POST', cuerpo }),

  actualizar: (id: number, cuerpo: CuponCuerpo) =>
    peticion<Cupon>(`/admin/cupones/${id}`, { metodo: 'PUT', cuerpo }),

  eliminar: (id: number) => peticion<void>(`/admin/cupones/${id}`, { metodo: 'DELETE' }),
}

// ---------------------------------------------------------------- archivos

/**
 * Los archivos no tienen sección propia: se suben desde el formulario que los
 * necesita. Un almacén de imágenes separado obliga a subir primero, recordar
 * cuál era y volver al producto a elegirla, que son tres pasos para lo que es
 * uno solo.
 */
export const archivos = {
  subir: (archivo: File, textoAlt: string) => {
    const formulario = new FormData()
    formulario.append('archivo', archivo)
    formulario.append('textoAlt', textoAlt)
    return peticion<Archivo>('/admin/archivos', { metodo: 'POST', formulario })
  },
}

// ----------------------------------------------------------------- órdenes

export const ordenes = {
  listar: (estado: string, pagina = 1) =>
    peticion<RespuestaPagina<OrdenResumen>>(`/admin/ordenes${consulta({ estado, pagina })}`),

  porId: (id: number) => peticion<OrdenDetalle>(`/admin/ordenes/${id}`),

  cambiarEstado: (id: number, estado: EstadoOrden) =>
    peticion<OrdenDetalle>(`/admin/ordenes/${id}/estado`, { metodo: 'PATCH', cuerpo: { estado } }),
}

// ----------------------------------------------------------------- tablero

export const metricas = {
  /** Todo el tablero en una petición: o está entero, o no está. */
  obtener: () => peticion<Metricas>('/admin/metricas'),
}

// --------------------------------------------------------- administradores

export const administradores = {
  listar: () => peticion<RespuestaPagina<Administrador>>('/admin/administradores'),

  crear: (cuerpo: { usuario: string; contrasena: string; nombre: string; rol: string }) =>
    peticion<Administrador>('/admin/administradores', { metodo: 'POST', cuerpo }),

  actualizar: (id: number, cuerpo: { nombre: string; rol: string; activo: boolean }) =>
    peticion<Administrador>(`/admin/administradores/${id}`, { metodo: 'PUT', cuerpo }),

  restablecerContrasena: (id: number, contrasenaNueva: string) =>
    peticion<void>(`/admin/administradores/${id}/contrasena`, {
      metodo: 'POST',
      cuerpo: { contrasenaNueva },
    }),
}

// ---------------------------------------------------------------- clientes

export const clientes = {
  listar: (texto: string | undefined, pagina = 1) =>
    peticion<RespuestaPagina<Cliente>>(`/admin/clientes${consulta({ texto, pagina })}`),

  porId: (id: number) => peticion<ClienteDetalle>(`/admin/clientes/${id}`),

  cambiarEstado: (id: number, activo: boolean) =>
    peticion<Cliente>(`/admin/clientes/${id}/estado`, { metodo: 'PATCH', cuerpo: { activo } }),
}

// ----------------------------------------------------------- conversaciones

export const conversaciones = {
  listar: (estado: string | undefined, pagina = 1) =>
    peticion<RespuestaPagina<ConversacionResumen>>(`/admin/conversaciones${consulta({ estado, pagina })}`),

  /** Abrir marca leído el hilo: quien lo tiene delante ya lo leyó. */
  abrir: (id: number) => peticion<ConversacionDetalle>(`/admin/conversaciones/${id}`),

  crear: (cuerpo: { clienteId: number; ordenId: number | null; asunto: string; mensaje: string | null }) =>
    peticion<ConversacionDetalle>('/admin/conversaciones', { metodo: 'POST', cuerpo }),

  /**
   * Responder por REST y no por el WebSocket.
   *
   * El socket es para *recibir*; enviar por aquí deja el mensaje guardado
   * aunque la conexión en vivo se haya caído, y el propio servidor lo devuelve
   * por el socket a quien esté mirando.
   */
  responder: (id: number, cuerpo: { cuerpo: string | null; adjuntoIds: number[] }) =>
    peticion<Mensaje>(`/admin/conversaciones/${id}/mensajes`, { metodo: 'POST', cuerpo }),

  cambiarEstado: (id: number, activo: boolean) =>
    peticion<ConversacionDetalle>(`/admin/conversaciones/${id}/estado`, {
      metodo: 'PATCH',
      cuerpo: { activo },
    }),

  /** Solo imagen o PDF, y el servidor lo revisa antes de aceptarlo. */
  subirAdjunto: (archivo: File) => {
    const formulario = new FormData()
    formulario.append('archivo', archivo)
    return peticion<Adjunto>('/admin/conversaciones/adjuntos', { metodo: 'POST', formulario })
  },

  ticketWs: () =>
    peticion<{ ticket: string; expiraEnSegundos: number }>('/admin/conversaciones/ticket-ws', {
      metodo: 'POST',
    }),
}

// ---------------------------------------------------------- notificaciones

export const notificaciones = {
  bandeja: () => peticion<Bandeja>('/admin/notificaciones'),

  historial: (pagina = 1) =>
    peticion<RespuestaPagina<Notificacion>>(`/admin/notificaciones/historial?pagina=${pagina}`),

  marcarLeida: (id: number) =>
    peticion<{ pendientes: number }>(`/admin/notificaciones/${id}/leida`, { metodo: 'POST' }),

  marcarTodasLeidas: () =>
    peticion<{ pendientes: number }>('/admin/notificaciones/leidas', { metodo: 'POST' }),
}

// ---------------------------------------------------------------- papelera

export const papelera = {
  listar: () => peticion<ElementoEliminado[]>('/admin/papelera'),

  /** Vuelve desactivado: restaurar no es republicar. */
  restaurar: (tipo: string, id: number) =>
    peticion<{ tipo: string; id: number; nombre: string }>('/admin/papelera/restaurar', {
      metodo: 'POST',
      cuerpo: { tipo, id },
    }),
}

/** Construye la query omitiendo lo que no se filtró. */
function consulta(parametros: Record<string, unknown>): string {
  const partes = new URLSearchParams()
  for (const [clave, valor] of Object.entries(parametros)) {
    if (valor !== undefined && valor !== null && valor !== '') {
      partes.append(clave, String(valor))
    }
  }
  const cadena = partes.toString()
  return cadena ? `?${cadena}` : ''
}
