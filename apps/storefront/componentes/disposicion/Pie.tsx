import Image from 'next/image'
import Link from 'next/link'

import { esCategoriaDeTienda, listarCategorias, tolerante } from '@/lib/api.servidor'

/** El pie. Componente de servidor puro: ni un byte de JavaScript. */
export async function Pie() {
  const categorias = (await tolerante(listarCategorias(), [])).filter(esCategoriaDeTienda)

  return (
    <footer className="mt-16 border-t border-borde bg-white">
      <div className="mx-auto grid max-w-7xl gap-8 px-4 py-12 sm:px-6 md:grid-cols-[1.2fr_1fr_1fr_1fr]">
        <div>
          <Image src="/logo.webp" alt="Retail Store" width={160} height={48} className="h-10 w-auto object-contain" />
          <p className="mt-3 max-w-xs text-sm text-texto-suave">
            Materiales de construccion, herramientas, ferreteria y acabados. Lo que tu obra necesita, con entrega en
            todo el Peru.
          </p>
        </div>

        <nav aria-labelledby="pie-catalogo">
          <h2 id="pie-catalogo" className="mb-3 text-[13px] font-semibold uppercase tracking-wide text-texto">
            Catalogo
          </h2>
          <ul className="space-y-2 text-sm text-texto-medio">
            <li>
              <Link href="/productos" className="hover:text-marca-oscura">
                Todos los productos
              </Link>
            </li>
            {categorias.slice(0, 5).map((c) => (
              <li key={c.id}>
                <Link href={`/c/${c.slug}`} className="hover:text-marca-oscura">
                  {c.nombre}
                </Link>
              </li>
            ))}
          </ul>
        </nav>

        <nav aria-labelledby="pie-cuenta">
          <h2 id="pie-cuenta" className="mb-3 text-[13px] font-semibold uppercase tracking-wide text-texto">
            Tu cuenta
          </h2>
          <ul className="space-y-2 text-sm text-texto-medio">
            <li>
              <Link href="/acceso" className="hover:text-marca-oscura">
                Iniciar sesion
              </Link>
            </li>
            <li>
              <Link href="/registro" className="hover:text-marca-oscura">
                Crear cuenta
              </Link>
            </li>
            <li>
              <Link href="/mi-cuenta/pedidos" className="hover:text-marca-oscura">
                Mis pedidos
              </Link>
            </li>
            <li>
              <Link href="/favoritos" className="hover:text-marca-oscura">
                Mis favoritos
              </Link>
            </li>
          </ul>
        </nav>

        <nav aria-labelledby="pie-tienda">
          <h2 id="pie-tienda" className="mb-3 text-[13px] font-semibold uppercase tracking-wide text-texto">
            La tienda
          </h2>
          <ul className="space-y-2 text-sm text-texto-medio">
            <li>
              <Link href="/carrito" className="hover:text-marca-oscura">
                Mi carrito
              </Link>
            </li>
            <li>
              <Link href="/comparar" className="hover:text-marca-oscura">
                Comparador
              </Link>
            </li>
            <li>
              <Link href="/productos?orden=precio_asc" className="hover:text-marca-oscura">
                Ofertas
              </Link>
            </li>
          </ul>
        </nav>
      </div>

    </footer>
  )
}
