#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'TXT'
Usage: DB_ALLOW_RESTORE=true scripts/deployment/mysql-restore.sh BACKUP.sql.gz --confirm

Required environment: DB_URL, DB_USERNAME, DB_PASSWORD, DB_ALLOW_RESTORE=true
Restore is intentionally guarded because it changes the configured database.
Take a fresh backup of the target database before restoring.
TXT
}
[[ "${1:-}" != "--help" ]] || { usage; exit 0; }
[[ $# -eq 2 && "$2" == "--confirm" ]] || { usage >&2; exit 2; }
[[ "${DB_ALLOW_RESTORE:-false}" == "true" ]] || { echo 'DB_ALLOW_RESTORE=true is required' >&2; exit 1; }
backup=$1
[[ -r "$backup" && "$backup" == *.sql.gz ]] || { echo 'Readable .sql.gz backup is required' >&2; exit 1; }
for cmd in mysql gzip; do command -v "$cmd" >/dev/null || { echo "Missing required command: $cmd" >&2; exit 1; }; done
: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

url=${DB_URL#jdbc:mysql://}
authority=${url%%/*}
rest=${url#*/}
database=${rest%%\?*}
host=${authority%%:*}
if [[ "$authority" == *:* ]]; then port=${authority##*:}; else port=3306; fi
[[ -n "$host" && -n "$database" ]] || { echo 'Unable to parse DB_URL' >&2; exit 1; }

export MYSQL_PWD="$DB_PASSWORD"
trap 'unset MYSQL_PWD' EXIT

echo "[restore] restoring $backup into database '$database' on '$host:$port'"
gzip -dc "$backup" | mysql --host="$host" --port="$port" --user="$DB_USERNAME" --default-character-set=utf8mb4 "$database"
printf '[restore] restore completed; run migration status and application smoke tests before reopening traffic\n'
