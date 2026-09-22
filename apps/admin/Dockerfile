# Panel de administracion (React + Vite, renderizado en el cliente).
#   Contexto de build: apps/admin/
#
# El resultado es HTML/JS estatico servido por nginx. VITE_API_URL se incrusta
# al construir: Vite no lee variables al arrancar porque no hay servidor que
# las lea, solo archivos.

FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .

ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL

# `npm run build` es `tsc -b && vite build`: el typecheck es parte del build y
# no hay linter (ver CLAUDE.md). Un error de tipos tumba el despliegue, que es
# lo que se quiere.
RUN npm run build

FROM nginx:1.27-alpine AS run
RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/panel.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
