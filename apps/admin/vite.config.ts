import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// El panel corre en 5173 y la API en 8080. Se habla con la API por su URL
// absoluta (VITE_API_URL) en lugar de con un proxy de Vite: así el código de
// desarrollo y el de producción hacen exactamente lo mismo, y el CORS y las
// cookies se prueban desde el primer día en vez de aparecer al desplegar.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
  },
})
