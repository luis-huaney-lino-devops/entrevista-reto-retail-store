import { Star } from 'lucide-react'

import { Estrellas } from '@/componentes/producto/Estrellas'
import type { DesgloseCalificacion } from '@/lib/tipos'

/**
 * El promedio y el desglose por estrellas, arriba del todo.
 *
 * Es lo primero que mira quien va a leer resenas, y por eso va antes que la
 * lista: un 4,3 con cincuenta cincos y diez unos no es el mismo producto que un
 * 4,3 con todo cuatros, y esa diferencia no se ve en el numero.
 *
 * Las cinco barras vienen siempre de la API, aunque alguna valga cero. Una
 * barra ausente se lee como un dato que falta, no como un cero.
 *
 * Un producto sin ninguna opinion no ensena un 0,0: cero no significa «malo»,
 * significa «todavia nadie», y pintarlo como nota seria mentir con un numero.
 */

type Propiedades = {
  promedio: number | null
  conteo: number
  desglose: DesgloseCalificacion[]
}

export function ResumenCalificacion({ promedio, conteo, desglose }: Propiedades) {
  if (conteo === 0) {
    return (
      <div className="rounded-marca border border-dashed border-borde-fuerte bg-white p-5 text-center">
        <p className="font-marca text-base font-semibold text-tinta">Sin opiniones todavia</p>
        <p className="mt-1 text-sm text-texto-suave">
          Nadie ha valorado este producto. Si lo tienes, la tuya sera la primera.
        </p>
      </div>
    )
  }

  const nota = promedio ?? 0

  return (
    <div className="rounded-marca border border-borde bg-white p-5">
      <div className="flex items-center gap-4">
        <p className="cifra font-marca text-4xl font-bold leading-none text-tinta">{nota.toFixed(1)}</p>
        <div>
          <Estrellas promedio={nota} tamano={17} mostrarCifra={false} />
          <p className="mt-1 text-xs text-texto-suave">
            {conteo} {conteo === 1 ? 'opinion' : 'opiniones'}
          </p>
        </div>
      </div>

      {/* Una lista y no una tabla: son cinco pares etiqueta-valor, y cada fila
          se anuncia entera con su aria-label para que el lector de pantalla no
          tenga que reconstruirla a partir de tres trozos sueltos. */}
      <ul className="mt-4 space-y-1.5">
        {desglose.map((fila) => (
          <li
            key={fila.estrellas}
            className="flex items-center gap-2"
            aria-label={`${fila.estrellas} ${fila.estrellas === 1 ? 'estrella' : 'estrellas'}: ${fila.cantidad} ${
              fila.cantidad === 1 ? 'opinion' : 'opiniones'
            }, ${fila.porcentaje}%`}
          >
            <span aria-hidden className="flex w-7 shrink-0 items-center gap-0.5 text-xs text-texto-medio">
              <span className="cifra">{fila.estrellas}</span>
              <Star size={11} className="fill-marca text-marca" strokeWidth={1.8} />
            </span>
            <span aria-hidden className="h-2 flex-1 overflow-hidden rounded-full bg-superficie-fondo">
              <span
                className="block h-full rounded-full bg-marca"
                style={{ width: `${fila.porcentaje}%` }}
              />
            </span>
            <span aria-hidden className="cifra w-6 shrink-0 text-right text-xs text-texto-suave">
              {fila.cantidad}
            </span>
          </li>
        ))}
      </ul>
    </div>
  )
}
