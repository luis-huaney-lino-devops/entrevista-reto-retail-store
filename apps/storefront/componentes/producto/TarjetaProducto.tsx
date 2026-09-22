import Image from 'next/image'
import Link from 'next/link'

import { clases, recortar } from '@/lib/formato'
import { TAMANOS, variante } from '@/lib/imagenes'
import type { ProductoResumen } from '@/lib/tipos'

import { AccionesRapidas } from './AccionesRapidas'
import { Estrellas } from './Estrellas'
import { Precio } from './Precio'

/**
 * La tarjeta de producto.
 *
 * Es un **componente de servidor**: imagen, nombre, precio e insignias llegan
 * ya en el HTML. Lo unico que viaja como JavaScript es `<AccionesRapidas/>`,
 * que son tres botones.
 *
 * El efecto al pasar el raton es **CSS puro** (`group-hover`), sin estado y sin
 * una sola linea de JS: la tarjeta se levanta y la imagen se acerca un poco.
 * `group-focus-within` hace lo mismo cuando el foco entra por teclado, que es
 * lo que evita que las acciones sean inalcanzables sin raton.
 *
 * **Pasar el raton no mueve ni un pixel de la rejilla.** Antes la fila de
 * acciones crecia de `max-h-0` a `max-h-24` al hacer hover, y eso es altura:
 * la tarjeta se estiraba, la fila del grid se estiraba con ella y las tarjetas
 * vecinas daban un salto. Ahora la fila **ocupa su sitio desde el primer
 * pintado** y lo unico que cambia es la opacidad, que no esta en el flujo del
 * documento. Lo mismo vale para el `-translate-y-0.5` y el `scale` de la
 * imagen: son transformaciones, no reflujo.
 *
 * El estado invisible se gatea con `raton:` —`(hover: hover) and (pointer:
 * fine)`— y no con `sm:`. En tactil no hay hover, asi que ahi las acciones
 * estan siempre visibles y **no queda ningun hueco reservado**: el espacio que
 * ocupan es el que usan. Reservar por ancho de pantalla dejaba a una tablet
 * ancha con un hueco que nunca se rellenaba.
 *
 * Un detalle de maquetacion que importa: el enlace se estira sobre la tarjeta
 * con un pseudo-elemento (`after:absolute after:inset-0`) en vez de envolverla.
 * Meter botones dentro de un `<a>` es HTML invalido y rompe el teclado; asi la
 * tarjeta entera es clicable y los botones siguen siendo botones.
 */

type Propiedades = {
  producto: ProductoResumen
  /** Solo la primera imagen de la primera rejilla de la pagina. Precargar
   *  cinco las pone a competir por el ancho de banda y retrasa justo la que se
   *  queria acelerar. */
  prioritaria?: boolean
  className?: string
}

export function TarjetaProducto({ producto, prioritaria = false, className }: Propiedades) {
  const imagen = variante(producto.imagen, 'tarjeta')

  return (
    <article
      className={clases(
        'group relative flex flex-col overflow-hidden rounded-marca border border-borde bg-white transition-all duration-200',
        'hover:-translate-y-0.5 hover:border-borde-fuerte hover:shadow-l focus-within:border-borde-fuerte focus-within:shadow-l',
        className,
      )}
    >
      <div className="relative aspect-square overflow-hidden bg-superficie-alt">
        {imagen ? (
          <Image
            src={imagen}
            alt={producto.textoAltImagen ?? producto.nombre}
            fill
            sizes={TAMANOS.tarjeta}
            priority={prioritaria}
            // `contain` y no `cover`: `cover` decapita productos altos, y eso
            // hace que una tienda parezca descuidada.
            className="object-contain p-3 transition-transform duration-300 group-hover:scale-[1.06]"
          />
        ) : (
          <div className="flex h-full items-center justify-center text-xs text-texto-suave">Sin imagen</div>
        )}

        {/* Insignias. El estado nunca se comunica solo con color: "Sin stock"
            se dice con palabras. */}
        <div className="pointer-events-none absolute left-2.5 top-2.5 flex flex-col items-start gap-1.5">
          {producto.porcentajeDescuento ? (
            <span className="rounded-full bg-marca px-2 py-0.5 text-[11px] font-bold text-white shadow-s">
              -{producto.porcentajeDescuento}%
            </span>
          ) : null}
          {producto.destacado && (
            <span className="rounded-full bg-tinta px-2 py-0.5 text-[11px] font-semibold text-white shadow-s">
              Destacado
            </span>
          )}
          {!producto.hayStock && (
            <span className="rounded-full bg-white px-2 py-0.5 text-[11px] font-semibold text-peligro shadow-s">
              Sin stock
            </span>
          )}
        </div>
      </div>

      <div className="flex flex-1 flex-col gap-1.5 p-3.5">
        {producto.marca && (
          <p className="text-[11px] font-semibold uppercase tracking-wide text-texto-suave">{producto.marca.nombre}</p>
        )}

        <h3 className="text-sm font-semibold leading-snug text-texto">
          <Link
            href={`/productos/${producto.slug}`}
            className="rounded-sm after:absolute after:inset-0 after:content-[''] hover:text-marca-oscura"
          >
            {producto.nombre}
          </Link>
        </h3>

        {producto.descripcionCorta && (
          <p className="line-clamp-2 text-xs text-texto-suave">{recortar(producto.descripcionCorta, 90)}</p>
        )}

        <Estrellas promedio={producto.calificacionPromedio} conteo={producto.calificacionConteo} className="mt-0.5" />

        <Precio
          precio={producto.precio}
          precioAnterior={producto.precioAnterior}
          porcentajeDescuento={producto.porcentajeDescuento}
          className="mt-auto pt-1.5"
        />
      </div>

      {/* La fila de acciones. `relative z-10` la pone por encima del enlace
          estirado; sin eso, los clics irian a parar a la ficha del producto.

          Nada de lo que cambia al hacer hover afecta al flujo: solo `opacity` y
          `pointer-events`. Ninguna altura, ningun margen, ningun `display`.
          `pointer-events-none` mientras esta invisible evita que se pulse un
          boton que no se ve, y no estorba al hover porque el evento cae en la
          tarjeta, que es el propio `group`. */}
      <div
        className={clases(
          'relative z-10 px-3.5 pb-3.5',
          'raton:pointer-events-none raton:opacity-0 raton:transition-opacity raton:duration-200',
          'raton:group-hover:pointer-events-auto raton:group-hover:opacity-100',
          'raton:group-focus-within:pointer-events-auto raton:group-focus-within:opacity-100',
        )}
      >
        <AccionesRapidas producto={producto} />
      </div>
    </article>
  )
}
