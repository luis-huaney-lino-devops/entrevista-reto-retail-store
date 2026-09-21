import { ChevronLeft, ChevronRight } from 'lucide-react'

export default function Paginacion({
  pagina,
  totalPaginas,
  totalItems,
  alCambiar,
}: {
  pagina: number
  totalPaginas: number
  totalItems: number
  alCambiar: (pagina: number) => void
}) {
  if (totalItems === 0) return null

  return (
    <div className="paginacion">
      <span>
        {totalItems} {totalItems === 1 ? 'registro' : 'registros'} · página {pagina} de{' '}
        {Math.max(totalPaginas, 1)}
      </span>
      <div className="acciones">
        <button type="button" disabled={pagina <= 1} onClick={() => alCambiar(pagina - 1)}>
          <ChevronLeft size={15} />
          Anterior
        </button>
        <button type="button" disabled={pagina >= totalPaginas} onClick={() => alCambiar(pagina + 1)}>
          Siguiente
          <ChevronRight size={15} />
        </button>
      </div>
    </div>
  )
}
