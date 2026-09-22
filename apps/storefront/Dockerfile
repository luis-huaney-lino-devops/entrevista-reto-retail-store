# Tienda publica (Next.js App Router, SSR).
#   Contexto de build: apps/storefront/
#
# Todo lo que empieza por NEXT_PUBLIC_ se incrusta en el bundle al construir,
# no se lee al arrancar. Por eso son ARG y no solo ENV: si se pasan como
# variables de entorno del contenedor, el navegador sigue viendo el valor por
# defecto de localhost.

FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

ARG NEXT_PUBLIC_API_URL
ARG NEXT_PUBLIC_URL_TIENDA
ARG NEXT_PUBLIC_GOOGLE_CLIENT_ID=
ARG API_URL_INTERNO
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL \
    NEXT_PUBLIC_URL_TIENDA=$NEXT_PUBLIC_URL_TIENDA \
    NEXT_PUBLIC_GOOGLE_CLIENT_ID=$NEXT_PUBLIC_GOOGLE_CLIENT_ID \
    API_URL_INTERNO=$API_URL_INTERNO \
    NEXT_TELEMETRY_DISABLED=1

# next.config.mjs lee NEXT_PUBLIC_API_URL y API_URL_INTERNO aqui para autorizar
# los hosts de next/image (remotePatterns). Si faltan, las fotos del catalogo
# salen en 400 desde el optimizador y no hay error que lo explique.
RUN npm run build

FROM node:22-alpine AS run
WORKDIR /app
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0
RUN addgroup -S nodejs && adduser -S next -G nodejs

# `output: 'standalone'` deja en .next/standalone un server.js con solo las
# dependencias que el codigo toca. public/ y .next/static quedan fuera a
# proposito y hay que copiarlos aparte.
COPY --from=build --chown=next:nodejs /app/.next/standalone ./
COPY --from=build --chown=next:nodejs /app/.next/static ./.next/static
COPY --from=build --chown=next:nodejs /app/public ./public

USER next
EXPOSE 3000
CMD ["node", "server.js"]
