#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'TXT'
Usage: scripts/deployment/mysql-backup.sh [OUTPUT_DIRECTORY]

Required environment: DB_URL, DB_USERNAME, DB_PASSWORD
DB_URL format: jdbc:mysql://host[:port]/database?parameters
Creates a gzip-compressed, transaction-consistent logical backup using mysqldump.
TXT
}
[[ "${1:-}" != "--help" ]] || { usage; exit 0; }

for cmd in mysqldump gzip; do command -v "$cmd" >/dev/null || { echo "Missing required command: $cmd" >&2; exit 1; }; done
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

out_dir=${1:-backups}
mkdir -p "$out_dir"
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
out="$out_dir/tgms-${database}-${timestamp}.sql.gz"

# MYSQL_PWD avoids placing the password in the process command line. The value still
# belongs in the platform secret manager/environment and is unset immediately afterward.
export MYSQL_PWD="$DB_PASSWORD"
trap 'unset MYSQL_PWD' EXIT

mysqldump \
  --host="$host" --port="$port" --user="$DB_USERNAME" \
  --single-transaction --quick --routines --triggers --events \
  --set-gtid-purged=OFF --default-character-set=utf8mb4 \
  "$database" | gzip -9 > "$out"

[[ -s "$out" ]] || { echo 'Backup file is empty' >&2; exit 1; }
printf '[backup] created %s\n' "$out"
