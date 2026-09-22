import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'

/**
 * La descripcion del producto, que llega en Markdown desde la API.
 *
 * Se renderiza **en el servidor**: `react-markdown` corre durante el render de
 * la pagina y lo que llega al navegador es HTML, no el parser. Cero bytes de
 * JavaScript por esta pieza.
 *
 * `react-markdown` no ejecuta HTML crudo salvo que se le anada `rehype-raw`, y
 * no se le anade: la descripcion la escribe un administrador en el panel, pero
 * renderizar HTML arbitrario del lado del servidor es exactamente como se
 * inyecta un script en una tienda.
 *
 * Los estilos viven en `.markdown` de `globals.css`, no en un plugin de
 * tipografia: son cinco etiquetas y no justifican 30 kB de CSS.
 */

export function Markdown({ texto }: { texto: string | null | undefined }) {
  if (!texto || texto.trim() === '') return null

  return (
    <div className="markdown">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{texto}</ReactMarkdown>
    </div>
  )
}
