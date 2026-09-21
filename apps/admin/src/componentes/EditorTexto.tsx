import { useEffect, useState } from 'react'
import { EditorContent, useEditor } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import Underline from '@tiptap/extension-underline'
import Link from '@tiptap/extension-link'
import Placeholder from '@tiptap/extension-placeholder'
import { Markdown } from 'tiptap-markdown'
import {
  Bold,
  Heading2,
  Heading3,
  Italic,
  Link2,
  Link2Off,
  List,
  ListOrdered,
  Minus,
  Quote,
  Redo2,
  Strikethrough,
  Underline as UnderlineIcon,
  Undo2,
} from 'lucide-react'

type Props = {
  id?: string
  valor: string
  alCambiar: (markdown: string) => void
  marcador?: string
  alto?: boolean
  conError?: boolean
}

/**
 * Editor de texto enriquecido para las descripciones.
 *
 * <p><strong>Guarda Markdown, no HTML.</strong> Lo que escribe el
 * administrador acaba renderizado en la tienda para cualquier visitante; con
 * HTML almacenado habría que sanearlo en el servidor y confiar en que el
 * saneador no se quede corto. Con Markdown el conjunto de lo que puede
 * producirse está acotado por el renderizador.
 *
 * <p>La barra no tiene fuentes ni colores a propósito. Una descripción de
 * producto que cada quien maqueta a su gusto deja la tienda con seis
 * tipografías; lo que hace falta es estructura —negrita, listas, un par de
 * niveles de título— y eso es lo que hay.
 */
export default function EditorTexto({ id, valor, alCambiar, marcador, alto, conError }: Props) {
  const [enfocado, setEnfocado] = useState(false)

  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        // Un título de nivel 1 es el nombre del producto, y lo pone la página:
        // permitirlo aquí produce dos h1 y confunde al buscador.
        heading: { levels: [2, 3] },
        codeBlock: false,
      }),
      Underline,
      Link.configure({
        openOnClick: false,
        autolink: true,
        // Sin esto, un enlace pegado desde cualquier sitio puede llevar un
        // javascript: y convertirse en un XSS al pulsarlo en la tienda.
        protocols: ['http', 'https', 'mailto'],
        HTMLAttributes: { rel: 'noopener noreferrer nofollow', target: '_blank' },
      }),
      Placeholder.configure({
        placeholder: marcador ?? 'Escribe aquí…',
        emptyEditorClass: 'esta-vacio',
        emptyNodeClass: 'esta-vacio',
      }),
      Markdown.configure({ transformPastedText: true, transformCopiedText: true }),
    ],
    content: valor || '',
    onUpdate: ({ editor }) => {
      alCambiar(editor.storage.markdown.getMarkdown())
    },
    onFocus: () => setEnfocado(true),
    onBlur: () => setEnfocado(false),
    editorProps: {
      attributes: { 'aria-label': 'Editor de texto enriquecido' },
    },
  })

  // Cuando el formulario recibe los datos del servidor, el editor ya está
  // montado y hay que darle el contenido. Se compara antes de escribir: sin
  // esa comprobación, cada pulsación reiniciaría el documento y el cursor
  // saltaría al principio.
  useEffect(() => {
    if (!editor) return
    const actual = editor.storage.markdown.getMarkdown()
    if (valor !== actual) {
      // El segundo argumento es «emitir update»: en false para que
      // rellenar el editor no dispare onUpdate y marque el
      // formulario como modificado sin que nadie haya escrito.
      editor.commands.setContent(valor || '', false)
    }
    // Depende solo de `valor`: reaccionar a `editor` reescribiría en cada render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [valor, editor])

  if (!editor) {
    return <div className="esqueleto" style={{ height: alto ? 230 : 150 }} />
  }

  function alternarEnlace() {
    if (!editor) return
    if (editor.isActive('link')) {
      editor.chain().focus().unsetLink().run()
      return
    }
    const previo = editor.getAttributes('link').href as string | undefined
    const url = window.prompt('Dirección del enlace', previo ?? 'https://')
    if (url === null) return
    if (url.trim() === '') {
      editor.chain().focus().unsetLink().run()
      return
    }
    editor.chain().focus().extendMarkRange('link').setLink({ href: url.trim() }).run()
  }

  const botones = [
    { icono: Bold, titulo: 'Negrita', accion: () => editor.chain().focus().toggleBold().run(), activo: editor.isActive('bold') },
    { icono: Italic, titulo: 'Cursiva', accion: () => editor.chain().focus().toggleItalic().run(), activo: editor.isActive('italic') },
    { icono: UnderlineIcon, titulo: 'Subrayado', accion: () => editor.chain().focus().toggleUnderline().run(), activo: editor.isActive('underline') },
    { icono: Strikethrough, titulo: 'Tachado', accion: () => editor.chain().focus().toggleStrike().run(), activo: editor.isActive('strike') },
    { separador: true },
    { icono: Heading2, titulo: 'Título', accion: () => editor.chain().focus().toggleHeading({ level: 2 }).run(), activo: editor.isActive('heading', { level: 2 }) },
    { icono: Heading3, titulo: 'Subtítulo', accion: () => editor.chain().focus().toggleHeading({ level: 3 }).run(), activo: editor.isActive('heading', { level: 3 }) },
    { separador: true },
    { icono: List, titulo: 'Lista', accion: () => editor.chain().focus().toggleBulletList().run(), activo: editor.isActive('bulletList') },
    { icono: ListOrdered, titulo: 'Lista numerada', accion: () => editor.chain().focus().toggleOrderedList().run(), activo: editor.isActive('orderedList') },
    { icono: Quote, titulo: 'Cita', accion: () => editor.chain().focus().toggleBlockquote().run(), activo: editor.isActive('blockquote') },
    { icono: Minus, titulo: 'Separador', accion: () => editor.chain().focus().setHorizontalRule().run(), activo: false },
    { separador: true },
    { icono: editor.isActive('link') ? Link2Off : Link2, titulo: editor.isActive('link') ? 'Quitar enlace' : 'Enlace', accion: alternarEnlace, activo: editor.isActive('link') },
    { separador: true },
    { icono: Undo2, titulo: 'Deshacer', accion: () => editor.chain().focus().undo().run(), activo: false, inactivo: !editor.can().undo() },
    { icono: Redo2, titulo: 'Rehacer', accion: () => editor.chain().focus().redo().run(), activo: false, inactivo: !editor.can().redo() },
  ]

  return (
    <div className={`editor ${enfocado ? 'enfocado' : ''} ${alto ? 'alto' : ''} ${conError ? 'con-error' : ''}`}>
      <div className="barra">
        {botones.map((boton, indice) =>
          'separador' in boton ? (
            <div key={`sep-${indice}`} className="separador" />
          ) : (
            <button
              key={boton.titulo}
              type="button"
              title={boton.titulo}
              aria-label={boton.titulo}
              aria-pressed={boton.activo}
              disabled={boton.inactivo}
              className={boton.activo ? 'activo' : ''}
              // onMouseDown y no onClick: al pulsar con el ratón el editor
              // pierde el foco antes del click y el comando se aplicaría sin
              // saber dónde está el cursor.
              onMouseDown={(evento) => {
                evento.preventDefault()
                boton.accion()
              }}
            >
              <boton.icono size={15} />
            </button>
          ),
        )}
      </div>
      <EditorContent editor={editor} id={id} />
    </div>
  )
}
