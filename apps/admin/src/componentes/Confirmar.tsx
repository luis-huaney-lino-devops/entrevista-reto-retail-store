import { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { AlertTriangle } from 'lucide-react'
import Dialogo from './Dialogo'

/**
 * La confirmación previa a una acción que no se deshace de un clic.
 *
 * <p>Se expone como una función que devuelve una promesa —`if (!(await
 * confirmar({...}))) return`— en lugar de como un componente que cada página
 * monta por su cuenta. La diferencia importa: con un componente hay que
 * acordarse de añadirlo, y el día que alguien lo olvide el botón borra sin
 * preguntar. Así el que borra tiene que esperar la respuesta para continuar.
 */

export type PeticionConfirmacion = {
  titulo: string
  /** Qué va a pasar exactamente. Nada de «¿estás seguro?» a secas. */
  mensaje: ReactNode
  /** El texto del botón que confirma: «Eliminar», «Desactivar», «Restaurar». */
  etiquetaAccion?: string
  /** Pinta la acción en rojo. Para lo que quita cosas. */
  peligro?: boolean
}

type Confirmar = (peticion: PeticionConfirmacion) => Promise<boolean>

const Contexto = createContext<Confirmar | null>(null)

export function ProveedorConfirmacion({ children }: { children: ReactNode }) {
  const [peticion, setPeticion] = useState<PeticionConfirmacion | null>(null)
  const resolver = useRef<((aceptado: boolean) => void) | null>(null)

  const cerrar = useCallback((aceptado: boolean) => {
    resolver.current?.(aceptado)
    resolver.current = null
    setPeticion(null)
  }, [])

  const confirmar = useCallback<Confirmar>((nueva) => {
    // Si ya había una pendiente se resuelve como cancelada: dejar una promesa
    // colgada dejaría al que la espera esperando para siempre.
    resolver.current?.(false)
    setPeticion(nueva)
    return new Promise<boolean>((resuelve) => {
      resolver.current = resuelve
    })
  }, [])

  const api = useMemo(() => confirmar, [confirmar])

  return (
    <Contexto.Provider value={api}>
      {children}
      {peticion && (
        <Dialogo titulo={peticion.titulo} alCerrar={() => cerrar(false)}>
          <div className="confirmacion">
            <span className={`icono-confirmacion ${peticion.peligro ? 'peligro' : ''}`}>
              <AlertTriangle size={20} />
            </span>
            <div className="mensaje">{peticion.mensaje}</div>
          </div>
          <footer className="pie-confirmacion">
            <button type="button" onClick={() => cerrar(false)}>
              Cancelar
            </button>
            {/* autoFocus en cancelar y no en confirmar: quien pulsa Intro por
                inercia no debe acabar borrando algo. */}
            <button
              type="button"
              className={peticion.peligro ? 'peligro' : 'primario'}
              onClick={() => cerrar(true)}
            >
              {peticion.etiquetaAccion ?? 'Confirmar'}
            </button>
          </footer>
        </Dialogo>
      )}
    </Contexto.Provider>
  )
}

export function useConfirmar(): Confirmar {
  const contexto = useContext(Contexto)
  if (!contexto) {
    throw new Error('useConfirmar se usó fuera de ProveedorConfirmacion')
  }
  return contexto
}

/**
 * El texto de un borrado, igual en todas las listas.
 *
 * <p>Dice qué se elimina, que no se pierde y dónde queda: sin esa última
 * frase la gente duda, y la duda delante de un botón rojo acaba en un correo
 * preguntando si se puede recuperar.
 */
export function confirmacionDeBorrado(que: string, nombre: string): PeticionConfirmacion {
  return {
    titulo: `¿Eliminar ${que}?`,
    peligro: true,
    etiquetaAccion: 'Eliminar',
    mensaje: (
      <>
        <p>
          Se va a eliminar <strong>{nombre}</strong>.
        </p>
        <p className="secundario">
          No se borra de la base de datos: queda en la papelera con tu nombre y la fecha, y desde ahí
          se puede restaurar.
        </p>
      </>
    ),
  }
}
