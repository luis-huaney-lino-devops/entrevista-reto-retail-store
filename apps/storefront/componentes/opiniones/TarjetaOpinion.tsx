import { BadgeCheck } from 'lucide-react'

import { Estrellas } from '@/componentes/producto/Estrellas'
import { clases, fecha } from '@/lib/formato'
import type { Opinion } from '@/lib/tipos'

/**
 * Una opinion de la lista.
 *
 * Componente de servidor: solo pinta lo que recibe. La isla es el formulario,
 * no la resena.
 *
 * El autor llega ya abreviado por la API -«Maria Q.»- y no hay forma de pedir
 * el nombre completo: no viaja. Aqui no se recorta nada, para que lo que se ve
 * sea exactamente lo que el servidor decidio publicar.
 *
 * La fecha es absoluta y no «hace 3 dias»: el servidor y el navegador
 * renderizan en instantes distintos y un tiempo relativo rompe la hidratacion.
 */

type Propiedades = {
  opinion: Opinion
  /** Marca la del propio lector. Solo la conoce el formulario, que tiene sesion. */
  propia?: boolean
}

export function TarjetaOpinion({ opinion, propia = false }: Propiedades) {
  return (
    <article
      className={clases(
        'border-t border-borde py-5 first:border-t-0 first:pt-0',
        propia && 'rounded-marca border border-marca/30 bg-marca-suave/40 p-4 first:pt-4',
      )}
    >
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <Estrellas promedio={opinion.calificacion} tamano={14} mostrarCifra={false} />
        <h3 className="font-marca text-[15px] font-semibold text-tinta">{opinion.titulo}</h3>
      </div>

      <p className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-texto-suave">
        <span className="font-semibold text-texto-medio">{opinion.autor}</span>
        <span aria-hidden>.</span>
        <time dateTime={opinion.creadoEn} className="cifra">
          {fecha(opinion.creadoEn)}
        </time>
        {opinion.editada && <span>(editada)</span>}
        {opinion.compraVerificada && (
          <span className="inline-flex items-center gap-1 rounded-full bg-exito-suave px-2 py-0.5 font-semibold text-exito">
            <BadgeCheck size={13} aria-hidden />
            Compra verificada
          </span>
        )}
        {propia && (
          <span className="inline-flex items-center rounded-full bg-marca px-2 py-0.5 font-semibold text-white">
            Tu opinion
          </span>
        )}
      </p>

      {/* `whitespace-pre-line` y no Markdown: lo que escribe un comprador es
          texto, y pasarlo por un parser abriria la puerta a que una resena
          traiga enlaces o encabezados. */}
      <p className="mt-2 whitespace-pre-line text-sm leading-relaxed text-texto-medio">{opinion.cuerpo}</p>
    </article>
  )
}
