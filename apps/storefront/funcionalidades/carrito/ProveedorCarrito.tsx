'use client'

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useReducer,
  useRef,
  useState,
  type ReactNode,
} from 'react'

import { ErrorApi } from '@/lib/errores'
import type { Carrito, ProductoResumen } from '@/lib/tipos'
import { CANTIDAD_MAXIMA } from '@/lib/tipos'
import { useAvisos } from '@/funcionalidades/avisos/ProveedorAvisos'

import * as api from './api'
import { ESTADO_INICIAL, reductor, type EstadoCarrito } from './reductor'

/**
 * El carrito: `useReducer` + Context.
 *
 * **Dos contextos, no uno.** Context repinta a todos sus consumidores en cada
 * cambio. El boton "Agregar al carrito" de cada tarjeta consume solo el de
 * acciones, cuya identidad nunca cambia, asi que una rejilla de 24 tarjetas no
 * se repinta cuando se sube una cantidad en el panel lateral.
 *
 * **El carrito se crea con el primer anadido, no al cargar la pagina.** Crearlo
 * al entrar genera una fila por cada visitante y por cada rastreador que pase,
 * y la inmensa mayoria no comprara nada.
 *
 * **El id del carrito vive en `localStorage`**, no en una cookie. Leer cookies
 * en un componente de servidor marca la ruta como dinamica, y eso mataria el
 * ISR de la portada y del detalle: se cambiaria la cache de todo el catalogo
 * por un numero en un icono.
 */

const CLAVE = 'retailstore.carrito'

type AccionesCarrito = {
  agregar: (producto: ProductoResumen, cantidad?: number) => Promise<void>
  fijarCantidad: (idItem: number, cantidad: number) => void
  quitar: (idItem: number) => Promise<void>
  aplicarCupon: (codigo: string) => Promise<boolean>
  quitarCupon: () => Promise<void>
  abrirPanel: () => void
  cerrarPanel: () => void
}

type EstadoPublico = EstadoCarrito & {
  /** `false` hasta que el provider monta. Evita el desajuste de hidratacion:
   *  el servidor no tiene `localStorage` y no puede saber si hay 0 o 3. */
  montado: boolean
  panelAbierto: boolean
}

const ContextoEstado = createContext<EstadoPublico | null>(null)
const ContextoAcciones = createContext<AccionesCarrito | null>(null)

export function ProveedorCarrito({ children }: { children: ReactNode }) {
  const [estado, despachar] = useReducer(reductor, ESTADO_INICIAL)
  const [montado, setMontado] = useState(false)
  const [panelAbierto, setPanelAbierto] = useState(false)
  const { avisar } = useAvisos()

  /** Contador de mutaciones. Un ref y no estado: cambiarlo no debe repintar. */
  const secuencia = useRef(0)
  const idCarrito = useRef<string | null>(null)
  /** Temporizadores de agrupacion por linea, para el +/- del selector. */
  const temporizadores = useRef(new Map<number, ReturnType<typeof setTimeout>>())

  const siguienteSecuencia = () => {
    secuencia.current += 1
    return secuencia.current
  }

  /* --------------------------------------------------------- hidratacion */

  useEffect(() => {
    setMontado(true)
    const guardado = leerId()
    if (!guardado) {
      despachar({ tipo: 'HIDRATADO', carrito: null })
      return
    }
    idCarrito.current = guardado
    despachar({ tipo: 'HIDRATANDO' })
    api
      .obtenerCarrito(guardado)
      .then((carrito) => despachar({ tipo: 'HIDRATADO', carrito }))
      .catch((e: unknown) => {
        // Un carrito purgado a los 90 dias, o de otro entorno, es un caso
        // normal: se limpia en silencio y se empieza de cero. El usuario no
        // hizo nada mal, asi que no merece un aviso.
        if (e instanceof ErrorApi && (e.codigo === 'CART_NOT_FOUND' || e.estado === 404)) {
          borrarId()
          idCarrito.current = null
        }
        despachar({ tipo: 'HIDRATADO', carrito: null })
      })
  }, [])

  /* ------------------------------------------------------------ acciones */

  /** Devuelve el id del carrito, creandolo si es la primera vez. */
  const asegurarCarrito = useCallback(async (): Promise<string> => {
    if (idCarrito.current) return idCarrito.current
    const nuevo = await api.crearCarrito()
    idCarrito.current = nuevo.id
    guardarId(nuevo.id)
    return nuevo.id
  }, [])

  const sincronizar = useCallback(
    async (seq: number, trabajo: () => Promise<Carrito>): Promise<Carrito | null> => {
      try {
        const carrito = await trabajo()
        despachar({ tipo: 'SINCRONIZADO', secuencia: seq, carrito })
        return carrito
      } catch (e: unknown) {
        const codigo = e instanceof ErrorApi ? e.codigo : 'INTERNAL_ERROR'
        despachar({ tipo: 'FALLO', secuencia: seq, codigo })
        // Si el carrito desaparecio, se empieza uno nuevo en silencio.
        if (codigo === 'CART_NOT_FOUND') {
          borrarId()
          idCarrito.current = null
          despachar({ tipo: 'HIDRATADO', carrito: null })
          return null
        }
        avisar(e instanceof ErrorApi ? e.mensajeUsuario : 'No pudimos actualizar el carrito.', 'error')
        return null
      }
    },
    [avisar],
  )

  const agregar = useCallback(
    async (producto: ProductoResumen, cantidad = 1) => {
      const seq = siguienteSecuencia()
      despachar({ tipo: 'AGREGAR', producto, cantidad, secuencia: seq })
      setPanelAbierto(true)
      const id = await asegurarCarrito().catch(() => null)
      if (!id) {
        despachar({ tipo: 'FALLO', secuencia: seq, codigo: 'RED' })
        avisar('No pudimos contactar con la tienda.', 'error')
        return
      }
      // Un `POST /items` **suma** cantidad si el producto ya esta. Reintentarlo
      // tras un timeout de red lo anadiria dos veces, asi que esta mutacion no
      // se reintenta nunca sola: el usuario decide.
      const carrito = await sincronizar(seq, () => api.agregarItem(id, producto.id, cantidad))
      if (carrito) avisar(`Anadimos "${producto.nombre}" a tu carrito.`, 'exito')
    },
    [asegurarCarrito, sincronizar, avisar],
  )

  const quitar = useCallback(
    async (idItem: number) => {
      const id = idCarrito.current
      if (!id) return
      const pendiente = temporizadores.current.get(idItem)
      if (pendiente) {
        clearTimeout(pendiente)
        temporizadores.current.delete(idItem)
      }
      const seq = siguienteSecuencia()
      despachar({ tipo: 'QUITAR', idItem, secuencia: seq })
      await sincronizar(seq, () => api.quitarItem(id, idItem))
    },
    [sincronizar],
  )

  /**
   * Fijar cantidad, con agrupacion.
   *
   * El cambio optimista se aplica al instante pero el `PATCH` se retrasa
   * 400 ms, enviando solo la cantidad final. Tres clics rapidos son una
   * peticion, no tres. Es seguro porque el `PATCH` fija un valor absoluto: la
   * ultima gana y el resultado es el mismo.
   */
  const fijarCantidad = useCallback(
    (idItem: number, cantidad: number) => {
      const id = idCarrito.current
      if (!id) return

      if (cantidad < 1) {
        void quitar(idItem)
        return
      }
      const acotada = Math.min(cantidad, CANTIDAD_MAXIMA)

      const seq = siguienteSecuencia()
      despachar({ tipo: 'FIJAR_CANTIDAD', idItem, cantidad: acotada, secuencia: seq })

      const anterior = temporizadores.current.get(idItem)
      if (anterior) clearTimeout(anterior)

      temporizadores.current.set(
        idItem,
        setTimeout(() => {
          temporizadores.current.delete(idItem)
          void sincronizar(seq, () => api.fijarCantidad(id, idItem, acotada))
        }, 400),
      )
    },
    [quitar, sincronizar],
  )

  const aplicarCupon = useCallback(
    async (codigo: string): Promise<boolean> => {
      const id = idCarrito.current
      if (!id) return false
      const seq = siguienteSecuencia()
      despachar({ tipo: 'CUPON_EN_CURSO', secuencia: seq })
      const carrito = await sincronizar(seq, () => api.aplicarCupon(id, codigo.trim().toUpperCase()))
      if (carrito) {
        if (carrito.cuponActivo) {
          avisar('Cupon aplicado.', 'exito')
        } else {
          // El servidor acepto el codigo pero no lo esta aplicando; el motivo
          // lo dice el propio carrito y se ensena tal cual.
          avisar(carrito.motivoCuponInactivo ?? 'Ese cupon no se puede aplicar ahora.', 'aviso')
        }
        return true
      }
      return false
    },
    [sincronizar, avisar],
  )

  const quitarCuponAccion = useCallback(async () => {
    const id = idCarrito.current
    if (!id) return
    const seq = siguienteSecuencia()
    despachar({ tipo: 'CUPON_EN_CURSO', secuencia: seq })
    await sincronizar(seq, () => api.quitarCupon(id))
  }, [sincronizar])

  /* ------------------------------------------------------------ contexto */

  // Las acciones se memorizan juntas y sin depender del estado: su identidad
  // no cambia nunca, que es lo que evita repintar la rejilla entera.
  const acciones = useMemo<AccionesCarrito>(
    () => ({
      agregar,
      fijarCantidad,
      quitar,
      aplicarCupon,
      quitarCupon: quitarCuponAccion,
      abrirPanel: () => setPanelAbierto(true),
      cerrarPanel: () => setPanelAbierto(false),
    }),
    [agregar, fijarCantidad, quitar, aplicarCupon, quitarCuponAccion],
  )

  const publico = useMemo<EstadoPublico>(
    () => ({ ...estado, montado, panelAbierto }),
    [estado, montado, panelAbierto],
  )

  return (
    <ContextoAcciones.Provider value={acciones}>
      <ContextoEstado.Provider value={publico}>{children}</ContextoEstado.Provider>
    </ContextoAcciones.Provider>
  )
}

export function useCarrito(): EstadoPublico {
  const v = useContext(ContextoEstado)
  if (!v) throw new Error('useCarrito fuera de ProveedorCarrito')
  return v
}

export function useAccionesCarrito(): AccionesCarrito {
  const v = useContext(ContextoAcciones)
  if (!v) throw new Error('useAccionesCarrito fuera de ProveedorCarrito')
  return v
}

/* ---------------------------------------------------------- localStorage */

function leerId(): string | null {
  try {
    return window.localStorage.getItem(CLAVE)
  } catch {
    return null
  }
}

function guardarId(id: string): void {
  try {
    window.localStorage.setItem(CLAVE, id)
  } catch {
    // Modo privado o almacenamiento lleno: el carrito sigue funcionando en
    // esta pestana, solo no sobrevive a la recarga.
  }
}

function borrarId(): void {
  try {
    window.localStorage.removeItem(CLAVE)
  } catch {
    /* nada que hacer */
  }
}
