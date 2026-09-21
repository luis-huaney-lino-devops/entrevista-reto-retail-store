# ADR-0001: Next.js (SSR) para la tienda, React SPA (CSR) para el admin

## Contexto
El reto pide React en frontend. La tienda pública se beneficia de SEO y de un primer
pintado rápido en móvil; el panel de administración va detrás de login y es interacción
pesada (tablas, formularios).

## Decisión
- **Storefront**: Next.js (App Router). Server Components para catálogo y detalle
  (`generateMetadata`, ISR); Client Components solo donde hay estado interactivo
  (carrito, favoritos, filtros).
- **Admin**: React + Vite, SPA pura, sin SSR.

## Consecuencias
- Next.js es React: cumple el requisito, pero se documenta explícitamente para que no
  se lea como "no usó React puro".
- Cero lógica de negocio en Next (nada de Route Handlers como backend): toda regla vive
  en la API REST, que es el requisito explícito del reto de separar frontend y backend.
- Dos apps de frontend = doble build. Se compensa con tipos generados desde
  `contracts/openapi.yaml` (paquete `packages/api-types`) para no duplicar contratos a mano.
