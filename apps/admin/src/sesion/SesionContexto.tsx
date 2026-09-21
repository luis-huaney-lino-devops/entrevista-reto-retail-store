import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { alExpirarSesion, fijarToken, intentarRefrescar } from '../api/cliente'
import { sesion as api } from '../api/recursos'
import type { Administrador } from '../api/tipos'

type EstadoSesion = {
  administrador: Administrador | null
  /** true mientras se comprueba si la cookie de refresco sigue viva. */
  comprobando: boolean
  acceder: (usuario: string, contrasena: string) => Promise<void>
  salir: () => Promise<void>
}

const Contexto = createContext<EstadoSesion | null>(null)

export function ProveedorSesion({ children }: { children: ReactNode }) {
  const [administrador, setAdministrador] = useState<Administrador | null>(null)
  const [comprobando, setComprobando] = useState(true)
  // El proveedor vive dentro del router a propósito: cerrar sesión no es solo
  // olvidar el token, es llevar a la persona a algún sitio.
  const navegar = useNavigate()

  // Al arrancar se intenta recuperar la sesión con la cookie httpOnly. Sin
  // esto, pulsar F5 en el panel expulsaría a quien está trabajando, aunque su
  // sesión siga siendo válida durante doce horas.
  useEffect(() => {
    let vigente = true

    async function recuperar() {
      const renovado = await intentarRefrescar()
      if (!vigente) return
      if (renovado) {
        try {
          setAdministrador(await api.yo())
        } catch {
          setAdministrador(null)
        }
      }
      if (vigente) setComprobando(false)
    }

    void recuperar()
    return () => {
      vigente = false
    }
  }, [])

  // Cuando el cliente HTTP agota su reintento, el estado de React tiene que
  // enterarse: si no, la interfaz seguiría mostrando el panel sin sesión.
  useEffect(() => {
    alExpirarSesion(() => {
      setAdministrador(null)
      // Caducar a mitad de una acción tiene que acabar igual que cerrar
      // sesión a mano: en el formulario, con la URL diciéndolo.
      navegar('/login', { replace: true })
    })
  }, [navegar])

  const acceder = useCallback(async (usuario: string, contrasena: string) => {
    const nueva = await api.acceder(usuario, contrasena)
    fijarToken(nueva.tokenAcceso)
    setAdministrador(nueva.administrador)
  }, [])

  const salir = useCallback(async () => {
    try {
      await api.salir()
    } finally {
      // Pase lo que pase en el servidor, aquí la sesión se cierra: dejar al
      // usuario "dentro" porque falló la red es peor que cerrarla de más.
      fijarToken(null)
      setAdministrador(null)
      navegar('/login', { replace: true })
    }
  }, [navegar])

  const valor = useMemo(
    () => ({ administrador, comprobando, acceder, salir }),
    [administrador, comprobando, acceder, salir],
  )

  return <Contexto.Provider value={valor}>{children}</Contexto.Provider>
}

export function useSesion(): EstadoSesion {
  const contexto = useContext(Contexto)
  if (!contexto) {
    throw new Error('useSesion se usó fuera de ProveedorSesion')
  }
  return contexto
}

export function esSuperadministrador(administrador: Administrador | null): boolean {
  return administrador?.rol === 'SUPERADMINISTRADOR'
}
