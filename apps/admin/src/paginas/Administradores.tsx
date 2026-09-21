import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { KeyRound, Plus, ShieldCheck } from 'lucide-react'
import { ErrorApi } from '../api/cliente'
import { administradores as api } from '../api/recursos'
import type { Administrador } from '../api/tipos'
import { AvisoError, AvisoInfo } from '../componentes/Aviso'
import Campo from '../componentes/Campo'
import Combobox from '../componentes/Combobox'
import Dialogo from '../componentes/Dialogo'
import { Disponibilidad, fecha } from '../componentes/Insignia'
import SinDatos from '../componentes/SinDatos'
import { useToast } from '../componentes/Toast'

const ROLES = [
  { valor: 'ADMINISTRADOR', etiqueta: 'Administrador', grupo: 'catálogo' },
  { valor: 'SUPERADMINISTRADOR', etiqueta: 'Superadministrador', grupo: 'catálogo y cuentas' },
]

export default function Administradores() {
  const [creando, setCreando] = useState(false)
  const [restableciendo, setRestableciendo] = useState<Administrador | null>(null)
  const consulta = useQuery({ queryKey: ['administradores'], queryFn: api.listar })

  return (
    <>
      <div className="encabezado">
        <div>
          <h2>Administradores</h2>
          <p>
            Sin registro público ni recuperación por correo: si alguien pierde la contraseña, otro se
            la restablece desde aquí.
          </p>
        </div>
        <button type="button" className="primario" onClick={() => setCreando(true)}>
          <Plus size={15} />
          Nuevo administrador
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
                  <th>Usuario</th>
                  <th>Nombre</th>
                  <th>Rol</th>
                  <th>Último acceso</th>
                  <th>Estado</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {consulta.data.items.map((administrador) => (
                  <tr key={administrador.id}>
                    <td>
                      <code>{administrador.usuario}</code>
                    </td>
                    <td className="principal">{administrador.nombre}</td>
                    <td>
                      <span
                        className={`insignia ${
                          administrador.rol === 'SUPERADMINISTRADOR' ? 'azul' : 'gris'
                        }`}
                      >
                        {administrador.rol === 'SUPERADMINISTRADOR' ? 'Superadministrador' : 'Administrador'}
                      </span>
                    </td>
                    <td className="secundario">{fecha(administrador.ultimoAccesoEn)}</td>
                    <td>
                      <Disponibilidad activa={administrador.activo} />
                    </td>
                    <td>
                      <button
                        type="button"
                        className="enlace"
                        onClick={() => setRestableciendo(administrador)}
                      >
                        <KeyRound size={13} />
                        Restablecer contraseña
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <SinDatos icono={ShieldCheck} titulo="No hay administradores" />
        )}
      </div>

      {creando && <FormularioAdministrador alCerrar={() => setCreando(false)} />}
      {restableciendo && (
        <RestablecerContrasena administrador={restableciendo} alCerrar={() => setRestableciendo(null)} />
      )}
    </>
  )
}

function FormularioAdministrador({ alCerrar }: { alCerrar: () => void }) {
  const clienteConsultas = useQueryClient()
  const toast = useToast()
  const [usuario, setUsuario] = useState('')
  const [nombre, setNombre] = useState('')
  const [contrasena, setContrasena] = useState('')
  const [rol, setRol] = useState('ADMINISTRADOR')
  const [error, setError] = useState<unknown>(null)

  const crear = useMutation({
    mutationFn: () => api.crear({ usuario: usuario.trim(), contrasena, nombre: nombre.trim(), rol }),
    onSuccess: (creado) => {
      void clienteConsultas.invalidateQueries({ queryKey: ['administradores'] })
      toast.exito('Administrador creado', `${creado.usuario} ya puede entrar al panel`)
      alCerrar()
    },
    onError: (fallo) => {
      setError(fallo)
      toast.error(fallo, 'No se pudo crear')
    },
  })

  function enviar(evento: FormEvent) {
    evento.preventDefault()
    setError(null)
    crear.mutate()
  }

  const errorApi = error instanceof ErrorApi ? error : null

  return (
    <Dialogo
      titulo="Nuevo administrador"
      alCerrar={alCerrar}
      pie={
        <>
          <button type="button" onClick={alCerrar}>
            Cancelar
          </button>
          <button type="submit" form="formulario-admin" className="primario" disabled={crear.isPending}>
            {crear.isPending ? 'Creando…' : 'Crear'}
          </button>
        </>
      }
    >
      <form id="formulario-admin" onSubmit={enviar}>
        <AvisoError error={error} />

        <Campo
          nombre="usuario"
          etiqueta="Usuario"
          ayuda="Minúsculas, dígitos, punto, guion y guion bajo. No se puede cambiar después."
          error={errorApi?.deCampo('usuario')}
        >
          {({ id, className }) => (
            <input
              id={id}
              className={className}
              type="text"
              autoFocus
              spellCheck={false}
              value={usuario}
              onChange={(e) => setUsuario(e.target.value.toLowerCase())}
            />
          )}
        </Campo>

        <Campo nombre="nombre" etiqueta="Nombre" error={errorApi?.deCampo('nombre')}>
          {({ id, className }) => (
            <input
              id={id}
              className={className}
              type="text"
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
            />
          )}
        </Campo>

        <Campo
          nombre="contrasena"
          etiqueta="Contraseña"
          ayuda="Mínimo 10 caracteres. Una frase larga vale más que una palabra con símbolos."
          error={errorApi?.deCampo('contrasena')}
        >
          {({ id, className }) => (
            <input
              id={id}
              className={className}
              type="password"
              autoComplete="new-password"
              value={contrasena}
              onChange={(e) => setContrasena(e.target.value)}
            />
          )}
        </Campo>

        <Campo nombre="rol" etiqueta="Rol" error={errorApi?.deCampo('rol')}>
          {({ id, className }) => (
            <Combobox id={id} className={className} opciones={ROLES} valor={rol} alCambiar={setRol} />
          )}
        </Campo>
      </form>
    </Dialogo>
  )
}

function RestablecerContrasena({
  administrador,
  alCerrar,
}: {
  administrador: Administrador
  alCerrar: () => void
}) {
  const toast = useToast()
  const [contrasena, setContrasena] = useState('')
  const [error, setError] = useState<unknown>(null)
  const [hecho, setHecho] = useState(false)

  const restablecer = useMutation({
    mutationFn: () => api.restablecerContrasena(administrador.id, contrasena),
    onSuccess: () => {
      setHecho(true)
      toast.exito('Contraseña restablecida', 'Sus sesiones abiertas se cerraron')
    },
    onError: (fallo) => {
      setError(fallo)
      toast.error(fallo)
    },
  })

  function enviar(evento: FormEvent) {
    evento.preventDefault()
    setError(null)
    restablecer.mutate()
  }

  const errorApi = error instanceof ErrorApi ? error : null

  return (
    <Dialogo
      titulo={`Restablecer la contraseña de ${administrador.usuario}`}
      alCerrar={alCerrar}
      pie={
        <>
          <button type="button" onClick={alCerrar}>
            {hecho ? 'Cerrar' : 'Cancelar'}
          </button>
          {!hecho && (
            <button
              type="submit"
              form="formulario-restablecer"
              className="primario"
              disabled={restablecer.isPending}
            >
              {restablecer.isPending ? 'Guardando…' : 'Restablecer'}
            </button>
          )}
        </>
      }
    >
      <form id="formulario-restablecer" onSubmit={enviar}>
        <AvisoError error={error} />

        {hecho ? (
          <AvisoInfo>
            Comunícale la contraseña por un canal distinto del correo, y pídele que la cambie al
            entrar.
          </AvisoInfo>
        ) : (
          <Campo
            nombre="contrasenaNueva"
            etiqueta="Contraseña nueva"
            ayuda="Se muestra en claro para que puedas copiarla. Mínimo 10 caracteres."
            error={errorApi?.deCampo('contrasenaNueva')}
          >
            {({ id, className }) => (
              <input
                id={id}
                className={className}
                type="text"
                autoComplete="off"
                autoFocus
                value={contrasena}
                onChange={(e) => setContrasena(e.target.value)}
              />
            )}
          </Campo>
        )}
      </form>
    </Dialogo>
  )
}
