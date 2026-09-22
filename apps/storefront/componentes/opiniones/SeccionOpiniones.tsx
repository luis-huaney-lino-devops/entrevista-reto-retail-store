import { MessageSquareQuote } from 'lucide-react'

import { FormularioOpinion } from '@/componentes/opiniones/FormularioOpinion'
import { MasOpiniones } from '@/componentes/opiniones/MasOpiniones'
import { ResumenCalificacion } from '@/componentes/opiniones/ResumenCalificacion'
import { TarjetaOpinion } from '@/componentes/opiniones/TarjetaOpinion'
import type { Opinion, Pagina, ProductoDetalle } from '@/lib/tipos'

/**
 * La seccion de opiniones de la ficha.
 *
 * Componente de servidor, y eso es la decision que importa: el texto de las
 * resenas viaja en el HTML: lo lee el rastreador y lo ve quien entra en 4G sin
 * esperar a que arranque el JavaScript. Solo el formulario es una isla, porque
 * es lo unico que necesita saber quien eres.
 *
 * El orden es el del lector: primero el promedio y el desglose -lo que decide
 * si sigue leyendo-, despues su propia opinion o el formulario, y por ultimo
 * las de los demas.
 *
 * El promedio y el conteo salen del producto y no se recuentan aqui: la API los
 * deriva de las opiniones vivas en cada escritura (RN-093). Si esta seccion los
 * calculara por su cuenta, habria dos verdades y un dia dirian cosas distintas.
 */

type Propiedades = {
  producto: ProductoDetalle
  /** Primera pagina. `null` si la API no respondio: la seccion se pinta igual. */
  opiniones: Pagina<Opinion> | null
}

export function SeccionOpiniones({ producto, opiniones }: Propiedades) {
  const conteo = producto.calificacionConteo ?? 0
  const items = opiniones?.items ?? []

  return (
    <section id="opiniones" aria-labelledby="titulo-opiniones" className="mt-14 scroll-mt-24">
      <h2 id="titulo-opiniones" className="mb-5 font-marca text-xl font-bold text-tinta">
        Opiniones de quienes lo compraron
      </h2>

      <div className="grid gap-8 lg:grid-cols-[minmax(0,300px)_minmax(0,1fr)] lg:gap-10">
        <div className="lg:sticky lg:top-24 lg:self-start">
          <ResumenCalificacion
            promedio={producto.calificacionPromedio}
            conteo={conteo}
            desglose={producto.desgloseCalificacion ?? []}
          />
        </div>

        <div>
          <FormularioOpinion productoId={producto.id} slug={producto.slug} />

          {items.length > 0 ? (
            <>
              <ol>
                {items.map((opinion) => (
                  <li key={opinion.id}>
                    <TarjetaOpinion opinion={opinion} />
                  </li>
                ))}
              </ol>
              {opiniones && opiniones.totalPaginas > 1 && (
                <MasOpiniones slug={producto.slug} totalPaginas={opiniones.totalPaginas} />
              )}
            </>
          ) : (
            <p className="flex items-start gap-2 rounded-marca border border-dashed border-borde-fuerte bg-white px-4 py-6 text-sm text-texto-suave">
              <MessageSquareQuote size={18} className="mt-0.5 shrink-0 text-borde-fuerte" aria-hidden />
              Todavia nadie ha escrito sobre este producto.
            </p>
          )}
        </div>
      </div>
    </section>
  )
}
