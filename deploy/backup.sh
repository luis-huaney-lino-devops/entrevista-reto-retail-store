#!/usr/bin/env bash
# Respaldo diario de Postgres. Agregar a cron: 0 3 * * * /ruta/al/repo/deploy/backup.sh
set -euo pipefail
cd "$(dirname "$0")"

BACKUP_DIR="./backups"
mkdir -p "$BACKUP_DIR"
STAMP=$(date +%F_%H%M)

docker compose -f docker-compose.prod.yml exec -T db \
    pg_dump -U "${POSTGRES_USER}" "${POSTGRES_DB}" | gzip > "$BACKUP_DIR/retailstore_${STAMP}.sql.gz"

# Conserva los últimos 14 respaldos
ls -1t "$BACKUP_DIR"/retailstore_*.sql.gz | tail -n +15 | xargs -r rm --
