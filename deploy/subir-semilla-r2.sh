#!/usr/bin/env bash
#
# Sube las imágenes de la semilla a Cloudflare R2.
#
# Con ALMACEN_TIPO=local las 604 imágenes viajan dentro de la imagen Docker de
# la API, que las sirve por /archivos. Con ALMACEN_TIPO=r2 ese controlador deja
# de existir (@ConditionalOnProperty) y las imágenes tienen que estar en el
# bucket: nadie las copia sola.
#
# Las claves se conservan tal cual ('semilla/marca/x.webp'), porque son las que
# guarda la columna `archivo.clave` y las que V004 usa para reconstruir las URL.
#
#   R2_ACCESS_KEY_ID=...  R2_SECRET_ACCESS_KEY=...  ./deploy/subir-semilla-r2.sh
#
# No necesita instalar la CLI de AWS: usa su imagen oficial.

set -euo pipefail

# Git Bash reescribe las rutas que empiezan por / al pasarlas a docker. Sin
# esto, /data se convierte en C:/Program Files/... y el montaje falla.
export MSYS_NO_PATHCONV=1

AQUI="$(cd "$(dirname "$0")" && pwd)"

# Las credenciales pueden venir del entorno o de deploy/.env.r2, que .gitignore
# excluye. Lo segundo evita que el secreto quede en el historial del shell.
if [ -f "$AQUI/.env.r2" ]; then
    echo "Leyendo credenciales de deploy/.env.r2"
    set -a
    # shellcheck disable=SC1091
    . "$AQUI/.env.r2"
    set +a
fi

: "${R2_ACCESS_KEY_ID:?falta R2_ACCESS_KEY_ID (ponlo en deploy/.env.r2 o en el entorno)}"
: "${R2_SECRET_ACCESS_KEY:?falta R2_SECRET_ACCESS_KEY}"

R2_ENDPOINT="${R2_ENDPOINT:-https://6c1db82f0c64bb2d7721120af8ac674c.r2.cloudflarestorage.com}"
R2_BUCKET="${R2_BUCKET:-hidro-jass}"

RAIZ="$(cd "$AQUI/.." && pwd)"
SEMILLA="$RAIZ/backend/java/datos/archivos/semilla"

[ -d "$SEMILLA" ] || { echo "No encuentro $SEMILLA" >&2; exit 1; }

TOTAL=$(find "$SEMILLA" -type f -name '*.webp' | wc -l)
echo "Subiendo $TOTAL imágenes a s3://$R2_BUCKET/semilla/"
echo "Endpoint: $R2_ENDPOINT"
echo

docker run --rm \
    -v "$SEMILLA:/data:ro" \
    -e AWS_ACCESS_KEY_ID="$R2_ACCESS_KEY_ID" \
    -e AWS_SECRET_ACCESS_KEY="$R2_SECRET_ACCESS_KEY" \
    -e AWS_DEFAULT_REGION=auto \
    amazon/aws-cli:latest \
    s3 cp /data "s3://$R2_BUCKET/semilla" \
        --recursive \
        --endpoint-url "$R2_ENDPOINT" \
        --content-type image/webp \
        --cache-control "public, max-age=31536000, immutable" \
        --only-show-errors

echo
echo "Subido. Comprobando una al azar..."

# La clave lleva el hash del contenido en el nombre, así que el navegador puede
# cachearla para siempre; de ahí el Cache-Control de arriba.
MUESTRA="semilla/marca/cemento-andino-tarjeta.webp"
if [ -n "${ALMACEN_URL_PUBLICA:-}" ]; then
    CODIGO=$(curl -s -o /dev/null -w '%{http_code}' "$ALMACEN_URL_PUBLICA/$MUESTRA" || echo 000)
    echo "GET $ALMACEN_URL_PUBLICA/$MUESTRA -> HTTP $CODIGO"
    [ "$CODIGO" = "200" ] && echo "OK" || echo "Revisa que el bucket tenga acceso público habilitado."
else
    echo "Define ALMACEN_URL_PUBLICA para comprobar el acceso público."
fi
