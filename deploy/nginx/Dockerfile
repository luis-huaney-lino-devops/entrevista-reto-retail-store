# nginx de entrada: reparte los tres dominios entre tienda, panel y API.
#   Contexto de build: deploy/nginx/
#
# La configuracion va DENTRO de la imagen y no como bind mount: asi el
# despliegue no depende de que el orquestador sepa montar rutas relativas del
# repositorio, y la imagen es reproducible por si sola.
FROM nginx:1.27-alpine

# La imagen trae un default.conf que escucha en el 80 como `default_server`.
# Si se queda, nginx se niega a arrancar con "duplicate default server".
RUN rm /etc/nginx/conf.d/default.conf

COPY proxy.conf                /etc/nginx/proxy.conf
COPY gateway.conf.template     /etc/nginx/templates/gateway.conf.template

# El entrypoint de la imagen corre envsubst sobre /etc/nginx/templates y deja
# el resultado en /etc/nginx/conf.d. El filtro acota la sustitucion a las
# variables DOMINIO_*: sin el, envsubst tocaria cualquier nombre que coincida
# con una variable de entorno del contenedor.
ENV NGINX_ENVSUBST_FILTER="^DOMINIO_"

EXPOSE 80
