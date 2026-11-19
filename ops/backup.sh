#!/usr/bin/env bash
# HU-04 · Daily Postgres backup with 7-day retention. Runs forever inside the "backup" service.
# Restore: gunzip -c backups/shopflow-<date>.sql.gz | docker compose exec -T db psql -U "$DB_USER" "$DB_NAME"
set -euo pipefail

RETENTION_DAYS=7

while true; do
  file="/backups/${PGDATABASE}-$(date -u +%Y-%m-%dT%H%MZ).sql.gz"
  if pg_dump --no-owner --clean --if-exists | gzip > "$file.tmp"; then
    mv "$file.tmp" "$file"
    echo "Backup OK: $file"
  else
    rm -f "$file.tmp"
    echo "Backup FAILED" >&2
  fi
  find /backups -name '*.sql.gz' -mtime +$((RETENTION_DAYS - 1)) -delete
  sleep 86400
done
