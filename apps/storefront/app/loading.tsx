import { Contenedor } from '@/componentes/disposicion/Seccion'
import { EsqueletoRejilla } from '@/componentes/ui/Estados'

/**
 * Esqueleto de la portada.
 *
 * Imita la forma real —banda superior y rejilla de tarjetas— para que cuando
 * lleguen los datos no haya salto de maquetacion. Un spinner centrado provoca
 * justo ese salto, y lo penaliza la metrica de CLS.
 */
export default function Cargando() {
  return (
    <>
      <div className="h-[320px] animate-pulse bg-tinta sm:h-[420px]" />
      <Contenedor className="py-12">
        <div className="mb-5 h-7 w-52 animate-pulse rounded bg-borde" />
        <EsqueletoRejilla cuantas={8} />
      </Contenedor>
    </>
  )
}
