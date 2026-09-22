import type { Config } from 'tailwindcss'

/**
 * Tema de la tienda.
 *
 * Los colores salen de `apps/admin/src/estilos.css`: son los mismos tokens que
 * el panel, para que las dos aplicaciones parezcan de la misma casa. El naranja
 * manda en lo accionable y el azul tinta en la estructura; usarlos al reves
 * deja una interfaz que grita.
 *
 * Nota de contraste: el naranja sobre blanco ronda 3:1, que no llega para texto
 * pequeno. Por eso los enlaces y el foco van en `acento` (azul), no en naranja,
 * igual que en el panel.
 */
const config: Config = {
  content: [
    './app/**/*.{ts,tsx}',
    './componentes/**/*.{ts,tsx}',
    './funcionalidades/**/*.{ts,tsx}',
    './lib/**/*.{ts,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        marca: {
          DEFAULT: '#ef6407',
          oscura: '#c85104',
          suave: '#fff2e8',
        },
        tinta: {
          DEFAULT: '#12253f',
          claro: '#1b3a6b',
          suave: '#eaeff7',
        },
        superficie: {
          DEFAULT: '#ffffff',
          alt: '#fafbfc',
          fondo: '#f5f6f8',
        },
        borde: {
          DEFAULT: '#e3e6eb',
          fuerte: '#cbd1da',
        },
        texto: {
          DEFAULT: '#151b26',
          medio: '#4a5364',
          suave: '#6b7486',
        },
        exito: { DEFAULT: '#0f7b4f', suave: '#e6f5ed' },
        peligro: { DEFAULT: '#c02717', suave: '#fdecea' },
        aviso: { DEFAULT: '#92610e', suave: '#fdf3e2' },
      },
      fontFamily: {
        marca: ['var(--fuente-marca)', 'Montserrat', 'system-ui', 'sans-serif'],
        base: ['var(--fuente-base)', 'system-ui', 'sans-serif'],
      },
      borderRadius: {
        marca: '10px',
      },
      boxShadow: {
        s: '0 1px 2px rgba(16,24,40,.05), 0 1px 3px rgba(16,24,40,.08)',
        m: '0 4px 6px -1px rgba(16,24,40,.07), 0 2px 4px -2px rgba(16,24,40,.06)',
        l: '0 20px 25px -5px rgba(16,24,40,.12), 0 8px 10px -6px rgba(16,24,40,.08)',
      },
      keyframes: {
        entrada: {
          from: { opacity: '0', transform: 'translateY(6px)' },
          to: { opacity: '1', transform: 'none' },
        },
        deslizaDerecha: {
          from: { transform: 'translateX(100%)' },
          to: { transform: 'none' },
        },
      },
      animation: {
        entrada: 'entrada .18s ease-out',
        'desliza-derecha': 'deslizaDerecha .22s ease-out',
      },
    },
  },
  plugins: [],
}

export default config
