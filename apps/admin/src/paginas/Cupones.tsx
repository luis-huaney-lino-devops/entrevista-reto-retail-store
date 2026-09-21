import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Pencil, Plus, Ticket, Trash2 } from 'lucide-react'
import { ErrorApi } from '../api/cliente'
import { cupones as api } from '../api/recursos'
import type { Cupon } from '../api/tipos'
import { AvisoError } from '../componentes/Aviso'
import BotonIcono from '../componentes/BotonIcono'
import Campo from '../componentes/Campo'
import Combobox from '../componentes/Combobox'
import { confirmacionDeBorrado, useConfirmar } from '../componentes/Confirmar'
import Dialogo from '../componentes/Dialogo'
import { fechaCorta, soles } from '../componentes/Insignia'
import Paginacion from '../componentes/Paginacion'
import SinDatos from '../componentes/SinDatos'
import { useToast } from '../componentes/Toast'

const TIPOS = [
  { valor: 'PORCENTAJE', etiqueta: 'Porcentaje' },
  { valor: 'MONTO_FIJO', etiqueta: 'Monto fijo' },
]

export default function Cupones() {
  const clienteConsultas = useQueryClient()
  const toast = useToast()
  const confirmar = useConfirmar()
  const [pagina, setPagina] = useState(1)
  const [editando, setEditando] = useState<Cupon | null>(null)
  const [creando, setCreando] = useState(false)

  const consulta = useQuery({ queryKey: ['cupones', pagina], queryFn: () => api.listar(pagina) })

  const eliminar = useMutation({
    mutationFn: (id: number) => api.eliminar(id),
    onSuccess: () => {
      void clienteConsultas.invalidateQueries({ queryKey: ['cupones'] })
      void clienteConsultas.invalidateQueries({ queryKey: ['papelera'] })
      toast.exito('Cupón eliminado', 'Queda en la papelera por si hay que recuperarlo.')
    },
    onError: (fallo) => toast.error(fallo, 'No se pudo eliminar'),
  })

  async function pedirBorrado(cupon: Cupon) {
    if (await confirmar(confirmacionDeBorrado('el cupón', cupon.codigo))) {
      eliminar.mutate(cupon.id)
    }
  }

  return (
    <>
      <div className="encabezado">
        <div>
          <h2>Cupones</h2>
          <p>Un carrito admite como mucho un cupón. El descuento nunca supera el subtotal.</p>
        </div>
        <button type="button" className="primario" onClick={() => setCreando(true)}>
          <Plus size={15} />
          Nuevo cupón
        </button>
      </div>

      <AvisoError error={consulta.error} />

      <div className="tarjeta plana">
        {consulta.isLoading ? (
          <p className="cargando">Cargando…</p>
        ) : consulta.data && consulta.data.items.length > 0 ? (
          <div className="envoltura-tabla">
            <table>
              <thead>
                <tr>
                  <th>Código</th>
                  <th>Descuento</th>
                  <th className="numero">Compra mínima</th>
                  <th>Vigencia</th>
                  <th className="numero">Usos</th>
                  <th>Estado</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {consulta.data.items.map((cupon) => (
                  <tr key={cupon.id}>
                    <td>
                      <code>{cupon.codigo}</code>
                    </td>
                    <td className="principal">
                      {cupon.tipo === 'PORCENTAJE' ? `${cupon.valor}%` : soles(cupon.valor)}
                    </td>
                    <td className="numero secundario">
                      {cupon.subtotalMinimo > 0 ? soles(cupon.subtotalMinimo) : '—'}
                    </td>
                    <td className="secundario">
                      {fechaCorta(cupon.iniciaEn)} → {fechaCorta(cupon.terminaEn)}
                    </td>
                    <td className="numero secundario">
                      {cupon.usosActuales}
                      {cupon.usosMaximos !== null ? ` / ${cupon.usosMaximos}` : ' / ∞'}
                    </td>
                    <td>
                      {/* «Activo» es lo que marcó el administrador; «vigente» es
                          si hoy descontaría de verdad. Verlo junto ahorra
                          buscar por qué un cupón no aplica. */}
                      <span className={`insignia ${cupon.vigente ? 'verde' : cupon.activo ? 'ambar' : 'gris'}`}>
                        {cupon.vigente ? 'Vigente' : cupon.activo ? 'Fuera de fecha' : 'Desactivado'}
                      </span>
                    </td>
                    <td className="acciones">
                      <BotonIcono icono={Pencil} etiqueta="Editar" alPulsar={() => setEditando(cupon)} />
                      <BotonIcono
                        icono={Trash2}
                        etiqueta="Eliminar"
                        peligro
                        desactivado={eliminar.isPending}
                        alPulsar={() => void pedirBorrado(cupon)}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <SinDatos
            icono={Ticket}
            titulo="Todavía no hay cupones"
            detalle="Un cupón por porcentaje o por monto fijo, con vigencia y compra mínima."
          />
        )}
      </div>

      {consulta.data && (
        <Paginacion
          pagina={consulta.data.pagina}
          totalPaginas={consulta.data.totalPaginas}
          totalItems={consulta.data.totalItems}
          alCambiar={setPagina}
        />
      )}

      {(creando || editando) && (
        <FormularioCupon
          cupon={editando}
          alCerrar={() => {
            setCreando(false)
            setEditando(null)
          }}
        />
      )}
    </>
  )
}

/** ISO 8601 → valor de un input datetime-local, en hora local. */
function aLocal(iso: string): string {
  const momento = new Date(iso)
  const desfase = momento.getTimezoneOffset() * 60_000
  return new Date(momento.getTime() - desfase).toISOString().slice(0, 16)
}

function FormularioCupon({ cupon, alCerrar }: { cupon: Cupon | null; alCerrar: () => void }) {
  const clienteConsultas = useQueryClient()
  const toast = useToast()
  const ahora = new Date()
  const enUnMes = new Date(ahora.getTime() + 30 * 24 * 3600 * 1000)

  const [codigo, setCodigo] = useState(cupon?.codigo ?? '')
  const [tipo, setTipo] = useState<'PORCENTAJE' | 'MONTO_FIJO'>(cupon?.tipo ?? 'PORCENTAJE')
  const [valor, setValor] = useState(String(cupon?.valor ?? 10))
  const [minimo, setMinimo] = useState(String(cupon?.subtotalMinimo ?? 0))
  const [inicia, setInicia] = useState(aLocal(cupon?.iniciaEn ?? ahora.toISOString()))
  const [termina, setTermina] = useState(aLocal(cupon?.terminaEn ?? enUnMes.toISOString()))
  const [maximos, setMaximos] = useState(
    cupon === null || cupon.usosMaximos === null ? '' : String(cupon.usosMaximos),
  )
  const [activo, setActivo] = useState(cupon?.activo ?? true)
  const [error, setError] = useState<unknown>(null)

  const guardar = useMutation({
    mutationFn: () =>
      {
        const cuerpo = {
          codigo: codigo.trim(),
          tipo,
          valor: Number(valor),
          subtotalMinimo: Number(minimo) || 0,
          // El input entrega hora local sin zona; se convierte a ISO con zona
          // para que el servidor no tenga que adivinar de dónde viene.
          iniciaEn: new Date(inicia).toISOString(),
          terminaEn: new Date(termina).toISOString(),
          usosMaximos: maximos ? Number(maximos) : null,
          activo,
        }
        return cupon ? api.actualizar(cupon.id, cuerpo) : api.crear(cuerpo)
      },
    onSuccess: (guardado) => {
      void clienteConsultas.invalidateQueries({ queryKey: ['cupones'] })
      toast.exito(cupon ? 'Cupón actualizado' : 'Cupón creado', guardado.codigo)
      alCerrar()
    },
    onError: (fallo) => {
      setError(fallo)
      toast.error(fallo, 'No se pudo guardar')
    },
  })

  function enviar(evento: FormEvent) {
    evento.preventDefault()
    setError(null)
    guardar.mutate()
  }

  const errorApi = error instanceof ErrorApi ? error : null

  return (
    <Dialogo
      titulo={cupon ? `Editar ${cupon.codigo}` : 'Nuevo cupón'}
      alCerrar={alCerrar}
      pie={
        <>
          <button type="button" onClick={alCerrar}>
            Cancelar
          </button>
          <button type="submit" form="formulario-cupon" className="primario" disabled={guardar.isPending}>
            {guardar.isPending ? 'Guardando…' : 'Guardar'}
          </button>
        </>
      }
    >
      <form id="formulario-cupon" onSubmit={enviar}>
        <AvisoError error={error} />

        <Campo
          nombre="codigo"
          etiqueta="Código"
          ayuda="Se guarda en mayúsculas. El comprador puede escribirlo como quiera."
          error={errorApi?.deCampo('codigo')}
        >
          {({ id, className }) => (
            <input
              id={id}
              className={className}
              type="text"
              spellCheck={false}
              disabled={cupon !== null}
              value={codigo}
              onChange={(e) => setCodigo(e.target.value.toUpperCase())}
            />
          )}
        </Campo>

        <div className="rejilla">
          <Campo nombre="tipo" etiqueta="Tipo" error={errorApi?.deCampo('tipo')}>
            {({ id, className }) => (
              <Combobox
                id={id}
                className={className}
                opciones={TIPOS}
                valor={tipo}
                alCambiar={(valor) => setTipo(valor as 'PORCENTAJE' | 'MONTO_FIJO')}
              />
            )}
          </Campo>

          <Campo
            nombre="valor"
            etiqueta={tipo === 'PORCENTAJE' ? 'Porcentaje' : 'Monto'}
            error={errorApi?.deCampo('valor')}
          >
            {({ id, className }) => (
              <div className="entrada-prefijo">
                <span>{tipo === 'PORCENTAJE' ? '%' : 'S/'}</span>
                <input
                  id={id}
                  className={className}
                  type="number"
                  step="0.01"
                  min="0.01"
                  max={tipo === 'PORCENTAJE' ? 100 : undefined}
                  value={valor}
                  onChange={(e) => setValor(e.target.value)}
                />
              </div>
            )}
          </Campo>
        </div>

        <div className="rejilla">
          <Campo nombre="subtotalMinimo" etiqueta="Compra mínima" error={errorApi?.deCampo('subtotalMinimo')}>
            {({ id, className }) => (
              <div className="entrada-prefijo">
                <span>S/</span>
                <input
                  id={id}
                  className={className}
                  type="number"
                  step="0.01"
                  min="0"
                  value={minimo}
                  onChange={(e) => setMinimo(e.target.value)}
                />
              </div>
            )}
          </Campo>

          <Campo
            nombre="usosMaximos"
            etiqueta="Usos máximos"
            ayuda="Vacío = sin límite."
            error={errorApi?.deCampo('usosMaximos')}
          >
            {({ id, className }) => (
              <input
                id={id}
                className={className}
                type="number"
                min="1"
                placeholder="Sin límite"
                value={maximos}
                onChange={(e) => setMaximos(e.target.value)}
              />
            )}
          </Campo>
        </div>

        <div className="rejilla">
          <Campo nombre="iniciaEn" etiqueta="Inicia" error={errorApi?.deCampo('iniciaEn')}>
            {({ id, className }) => (
              <input
                id={id}
                className={className}
                type="datetime-local"
                value={inicia}
                onChange={(e) => setInicia(e.target.value)}
              />
            )}
          </Campo>

          <Campo nombre="terminaEn" etiqueta="Termina" error={errorApi?.deCampo('terminaEn')}>
            {({ id, className }) => (
              <input
                id={id}
                className={className}
                type="datetime-local"
                value={termina}
                onChange={(e) => setTermina(e.target.value)}
              />
            )}
          </Campo>
        </div>

        <label className="casilla">
          <input type="checkbox" checked={activo} onChange={(e) => setActivo(e.target.checked)} />
          Activo
        </label>
      </form>
    </Dialogo>
  )
}
