'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { ChevronDown, LogOut, MapPin, Package, User } from 'lucide-react'

import { clases } from '@/lib/formato'
import { useSesion } from '@/funcionalidades/sesion/ProveedorSesion'

/**
 * El bloque de cuenta de la cabecera.
 *
 * Tiene **tres** estados, no dos, y el tercero es el que importa: mientras el
 * refresco esta en vuelo la sesion es *desconocida* y aqui se pinta un hueco
 * neutro del mismo tamano. Si pintara "Entrar", todo cliente con sesion veria
 * ese texto un instante antes de que lo sustituyera "Mi cuenta" —y ademas seria
 * un desajuste de hidratacion, porque el servidor no sabe quien eres.
 */

export function MenuCuenta() {
  const { estado, cliente, salir } = useSesion()
  const [abierto, setAbierto] = useState(false)
  const caja = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!abierto) return
    const fuera = (e: MouseEvent) => {
      if (caja.current && !caja.current.contains(e.target as Node)) setAbierto(false)
    }
    const escape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setAbierto(false)
    }
    document.addEventListener('mousedown', fuera)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('mousedown', fuera)
      document.removeEventListener('keydown', escape)
    }
  }, [abierto])

  // Estado neutro: ocupa lo mismo que ocupara despues, para que la cabecera no
  // se mueva cuando se resuelva.
  if (estado === 'desconocida') {
    return <div className="h-10 w-10 rounded-marca sm:w-28" aria-hidden />
  }

  if (estado === 'anonima') {
    return (
      <Link
        href="/acceso"
        className="inline-flex h-10 items-center gap-2 rounded-marca px-3 text-sm font-semibold text-tinta transition hover:bg-tinta-suave"
      >
        <User size={18} aria-hidden />
        <span className="hidden sm:inline">Entrar</span>
      </Link>
    )
  }

  const nombre = cliente?.nombre ?? 'Mi cuenta'

  return (
    <div ref={caja} className="relative">
      <button
        type="button"
        onClick={() => setAbierto((v) => !v)}
        aria-expanded={abierto}
        aria-haspopup="true"
        className={clases(
          'inline-flex h-10 max-w-[180px] items-center gap-2 rounded-marca px-2.5 text-sm font-semibold transition',
          abierto ? 'bg-tinta text-white' : 'text-tinta hover:bg-tinta-suave',
        )}
      >
        <span
          aria-hidden
          className="inline-flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-marca text-[11px] font-bold text-white"
        >
          {iniciales(nombre)}
        </span>
        <span className="hidden truncate sm:inline">{nombre.split(' ')[0]}</span>
        <ChevronDown size={14} aria-hidden className="hidden sm:inline" />
      </button>

      {abierto && (
        <div className="absolute right-0 top-full z-50 mt-1.5 w-60 overflow-hidden rounded-marca border border-borde bg-white shadow-l animate-entrada">
          <div className="border-b border-borde px-4 py-3">
            <p className="truncate text-sm font-semibold text-texto">{nombre}</p>
            <p className="truncate text-xs text-texto-suave">{cliente?.email}</p>
          </div>
          <nav className="p-1.5" onClick={() => setAbierto(false)}>
            <Opcion href="/mi-cuenta" icono={<User size={16} aria-hidden />}>
              Mis datos
            </Opcion>
            <Opcion href="/mi-cuenta/direcciones" icono={<MapPin size={16} aria-hidden />}>
              Mis direcciones
            </Opcion>
            <Opcion href="/mi-cuenta/pedidos" icono={<Package size={16} aria-hidden />}>
              Mis pedidos
            </Opcion>
          </nav>
          <div className="border-t border-borde p-1.5">
            <button
              type="button"
              onClick={() => {
                setAbierto(false)
                void salir()
              }}
              className="flex w-full items-center gap-2.5 rounded-marca px-3 py-2 text-left text-sm text-texto-medio transition hover:bg-superficie-alt hover:text-peligro"
            >
              <LogOut size={16} aria-hidden />
              Cerrar sesion
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

function Opcion({ href, icono, children }: { href: string; icono: React.ReactNode; children: React.ReactNode }) {
  return (
    <Link
      href={href}
      className="flex items-center gap-2.5 rounded-marca px-3 py-2 text-sm text-texto-medio transition hover:bg-superficie-alt hover:text-texto"
    >
      {icono}
      {children}
    </Link>
  )
}

function iniciales(nombre: string): string {
  const partes = nombre.trim().split(/\s+/).filter(Boolean)
  const a = partes[0]?.[0] ?? '?'
  const b = partes.length > 1 ? (partes[partes.length - 1]?.[0] ?? '') : ''
  return (a + b).toUpperCase()
}
