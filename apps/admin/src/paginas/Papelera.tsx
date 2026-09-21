import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { RotateCcw, Search, Trash2 } from 'lucide-react'
import { papelera as api } from '../api/recursos'
import type { ElementoEliminado } from '../api/tipos'
import { AvisoError } from '../componentes/Aviso'
import BotonIcono from '../componentes/BotonIcono'
import { useConfirmar } from '../componentes/Confirmar'
import { fecha } from '../componentes/Insignia'
import SinDatos from '../componentes/SinDatos'
import { useToast } from '../componentes/Toast'

/**
 * Lo eliminado, con nombre y apellidos.
 *
 * <p>Existe porque en este sistema nada se borra de verdad (RN-086): eliminar
 * marca la fila y anota quién y cuándo. Sin una pantalla que lo muestre, esa
 * garantía sería una promesa que solo se puede comprobar entrando a la base de
 * datos.
 */
export default function Papelera() {
  const clienteConsultas = useQueryClient()
  const toast = useToast()
  const confirmar = useConfirmar()
  const [busqueda, setBusqueda] = useState('')

  const consulta = useQuery({ queryKey: ['papelera'], queryFn: api.listar })

  const restaurar = useMutation({
    mutationFn: ({ tipo, id }: { tipo: string; id: number }) => api.restaurar(tipo, id),
    onSuccess: (restaurado) => {
      // Todo lo que pueda contener lo restaurado se invalida: si solo se
      // refrescara la papelera, la lista de origen seguiría sin mostrarlo.
      void clienteConsultas.invalidateQueries()
      toast.exito('Restaurado', `${restaurado.nombre} vuelve desactivado, no se publica solo.`)
    },
    onError: (fallo) => toast.error(fallo, 'No se pudo restaurar'),
  })

  async function pedirRestauracion(elemento: ElementoEliminado) {
    const aceptado = await confirmar({
      titulo: `¿Restaurar ${elemento.etiquetaTipo.toLowerCase()}?`,
      etiquetaAccion: 'Restaurar',
      mensaje: (
        <>
          <p>
            <strong>{elemento.nombre}</strong> volverá a existir.
          </p>
          <p className="secundario">
            Vuelve <strong>desactivado</strong>: restaurar no es republicar. Tendrás que activarlo a
            mano cuando lo compruebes.
          </p>
        </>
      ),
    })
    if (aceptado) {
      restaurar.mutate({ tipo: elemento.tipo, id: elemento.id })
    }
  }

  const termino = busqueda.trim().toLowerCase()
  const items = (consulta.data ?? []).filter(
    (elemento) =>
      !termino ||
      elemento.nombre.toLowerCase().includes(termino) ||
      elemento.etiquetaTipo.toLowerCase().includes(termino) ||
      elemento.eliminadoPor.toLowerCase().includes(termino),
  )

  return (
    <>
      <div className="encabezado">
        <div>
          <h2>Papelera</h2>
          <p>Nada se borra del todo: aquí queda lo eliminado, con quién lo eliminó y cuándo.</p>
        </div>
      </div>

      <div className="barra-filtros">
        <div className="busqueda">
          <Search size={15} />
          <input
            type="search"
            placeholder="Buscar por nombre, tipo o quién lo eliminó…"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
          />
        </div>
      </div>

      <AvisoError error={consulta.error} />

      <div className="tarjeta plana">
        {consulta.isLoading ? (
          <p className="cargando">Cargando…</p>
        ) : items.length > 0 ? (
          <div className="envoltura-tabla">
            <table>
              <thead>
                <tr>
                  <th>Tipo</th>
                  <th>Nombre</th>
                  <th>Detalle</th>
                  <th>Lo eliminó</th>
                  <th>Cuándo</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {items.map((elemento) => (
                  <tr key={`${elemento.tipo}-${elemento.id}`}>
                    <td>
                      <span className="insignia gris sin-punto">{elemento.etiquetaTipo}</span>
                    </td>
                    <td className="principal">{elemento.nombre}</td>
                    <td className="secundario">{elemento.detalle ?? '—'}</td>
                    <td>{elemento.eliminadoPor}</td>
                    <td className="secundario">{fecha(elemento.eliminadoEn)}</td>
                    <td className="acciones">
                      <BotonIcono
                        icono={RotateCcw}
                        etiqueta="Restaurar"
                        desactivado={restaurar.isPending}
                        alPulsar={() => void pedirRestauracion(elemento)}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <SinDatos
            icono={Trash2}
            titulo={busqueda ? 'Nada coincide' : 'La papelera está vacía'}
            detalle={
              busqueda ? 'Prueba con otro término.' : 'No se ha eliminado nada todavía.'
            }
          />
        )}
      </div>
    </>
  )
}
