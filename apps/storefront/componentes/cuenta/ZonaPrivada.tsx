'use client'

import { useEffect, type ReactNode } from 'react'
import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { MapPin, Package, User } from 'lucide-react'

import { Contenedor, Migas } from '@/componentes/disposicion/Seccion'
import { clases } from '@/lib/formato'
import { useSesion } from '@/funcionalidades/sesion/ProveedorSesion'

/**
 * La zona de cuenta: guarda de sesion y navegacion lateral.
 *
 * **No hay paginas privadas renderizadas en servidor en esta tienda, y no hace
 * falta que las haya.** La cookie de refresco la emite la API y es de ese host;
 * el servidor de Next no puede leerla. Asi que la guarda es de cliente: espera
 * a que la sesion se resuelva y, si es anonima, manda a `/acceso` con un
 * `volverA` para no perder el destino.
 *
 * Mientras la sesion es *desconocida* se pinta un esqueleto. Redirigir antes de
 * saberlo echaria a todo el mundo en cada recarga.
 */

const SECCIONES = [
  { href: '/mi-cuenta', nombre: 'Mis datos', icono: User },
  { href: '/mi-cuenta/direcciones', nombre: 'Mis direcciones', icono: MapPin },
  { href: '/mi-cuenta/pedidos', nombre: 'Mis pedidos', icono: Package },
] as const

export function ZonaPrivada({ titulo, children }: { titulo: string; children: ReactNode }) {
  const { estado } = useSesion()
  const router = useRouter()
  const ruta = usePathname()

  useEffect(() => {
    if (estado === 'anonima') {
      router.replace(`/acceso?volverA=${encodeURIComponent(ruta)}`)
    }
  }, [estado, router, ruta])

  if (estado !== 'autenticado') {
    return (
      <Contenedor className="py-12">
        <div className="grid gap-8 lg:grid-cols-[220px_minmax(0,1fr)]">
          <div className="h-48 animate-pulse rounded-marca bg-white" />
          <div className="h-80 animate-pulse rounded-marca bg-white" />
        </div>
        <p className="sr-only" role="status">
          Comprobando tu sesion...
        </p>
      </Contenedor>
    )
  }

  return (
    <Contenedor className="pb-12">
      <Migas
        items={[
          { nombre: 'Inicio', href: '/' },
          { nombre: 'Mi cuenta', href: '/mi-cuenta' },
          ...(ruta === '/mi-cuenta' ? [] : [{ nombre: titulo, href: ruta }]),
        ]}
      />

      <h1 className="mb-6 font-marca text-2xl font-bold text-tinta sm:text-3xl">{titulo}</h1>

      <div className="grid gap-8 lg:grid-cols-[220px_minmax(0,1fr)]">
        <nav aria-label="Secciones de mi cuenta">
          <ul className="flex gap-1 overflow-x-auto lg:flex-col lg:gap-0.5">
            {SECCIONES.map(({ href, nombre, icono: Icono }) => {
              const activa = ruta === href
              return (
                <li key={href}>
                  <Link
                    href={href}
                    aria-current={activa ? 'page' : undefined}
                    className={clases(
                      'flex items-center gap-2.5 whitespace-nowrap rounded-marca px-3 py-2 text-sm transition',
                      activa
                        ? 'bg-marca-suave font-semibold text-marca-oscura'
                        : 'text-texto-medio hover:bg-white hover:text-texto',
                    )}
                  >
                    <Icono size={16} aria-hidden />
                    {nombre}
                  </Link>
                </li>
              )
            })}
          </ul>
        </nav>

        <div className="min-w-0">{children}</div>
      </div>
    </Contenedor>
  )
}
