import type { Metadata, Viewport } from 'next'
import { Inter, Montserrat } from 'next/font/google'

import { Cabecera } from '@/componentes/disposicion/Cabecera'
import { Pie } from '@/componentes/disposicion/Pie'
import { PanelCarrito } from '@/componentes/carrito/PanelCarrito'
import { Proveedores } from '@/componentes/Proveedores'
import { baseUrl, TiendaJsonLd } from '@/lib/jsonld'

import './globals.css'

/**
 * El layout de la tienda.
 *
 * Es un componente de **servidor**, y eso es deliberado: la cabecera y el pie
 * piden las categorias en el servidor y llegan ya pintados en el HTML. Los
 * proveedores de cliente envuelven `children` sin convertirlo en cliente,
 * porque llega como prop ya renderizada.
 *
 * Las fuentes van con `next/font`: auto-hospedadas, con `font-display: swap` y
 * sin una conexion mas en la ruta critica. Montserrat manda en titulos y
 * navegacion; Inter en el texto corrido.
 */

const inter = Inter({
  subsets: ['latin'],
  display: 'swap',
  variable: '--fuente-base',
})

const montserrat = Montserrat({
  subsets: ['latin'],
  display: 'swap',
  weight: ['500', '600', '700'],
  variable: '--fuente-marca',
})

export const metadata: Metadata = {
  metadataBase: new URL(baseUrl()),
  title: {
    default: 'Retail Store | Materiales, herramientas y ferreteria',
    template: '%s | Retail Store',
  },
  description:
    'Materiales de construccion, herramientas, ferreteria, electricidad y acabados. Compra en linea con entrega en todo el Peru.',
  openGraph: {
    type: 'website',
    siteName: 'Retail Store',
    locale: 'es_PE',
  },
  icons: { icon: '/favicon.png' },
}

export const viewport: Viewport = {
  themeColor: '#12253f',
  width: 'device-width',
  initialScale: 1,
}

export default function LayoutRaiz({ children }: { children: React.ReactNode }) {
  return (
    <html lang="es-PE" className={`${inter.variable} ${montserrat.variable}`}>
      <body className="flex min-h-screen flex-col">
        <a href="#contenido" className="salto-contenido">
          Saltar al contenido
        </a>

        <Proveedores>
          <Cabecera />
          <main id="contenido" className="flex-1">
            {children}
          </main>
          <Pie />
          <PanelCarrito />
        </Proveedores>

        <TiendaJsonLd />
      </body>
    </html>
  )
}
