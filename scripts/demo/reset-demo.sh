#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SQL_FILE="$ROOT/database/demo/reset-and-seed.sql"

usage() {
  cat <<'TXT'
Usage: scripts/demo/reset-demo.sh --confirm RESET_TGMS_DEMO

Required environment:
  DB_URL, DB_USERNAME, DB_PASSWORD
  DEMO_DATA_ALLOWED=true
  DEMO_USER_PASSWORD (12-72 UTF-8 bytes; used for all six demo accounts)

Safety:
  - Refuses production Spring/TGMS environment labels.
  - Deletes/recreates only TGMS records carrying the reserved demo markers.
  - Requires an explicit confirmation string.
TXT
}

[[ "${1:-}" != "--help" ]] || { usage; exit 0; }
[[ "${1:-}" == "--confirm" && "${2:-}" == "RESET_TGMS_DEMO" ]] || { usage >&2; exit 2; }
[[ "${DEMO_DATA_ALLOWED:-false}" == "true" ]] || {
  echo "Demo reset refused. Set DEMO_DATA_ALLOWED=true only for a disposable demo/dev database." >&2
  exit 1
}
case ",${SPRING_PROFILES_ACTIVE:-},${TGMS_ENV:-},${NODE_ENV:-}," in
  *prod*|*production*) echo "Demo reset refused for a production-labelled environment." >&2; exit 1 ;;
esac
command -v mysql >/dev/null || { echo "Missing required command: mysql (MySQL client)." >&2; exit 1; }
command -v htpasswd >/dev/null || { echo "Missing required command: htpasswd (Apache password utility)." >&2; exit 1; }
: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
: "${DEMO_USER_PASSWORD:?DEMO_USER_PASSWORD is required}"
[[ -f "$SQL_FILE" ]] || { echo "Missing demo SQL: $SQL_FILE" >&2; exit 1; }

password_bytes=$(printf '%s' "$DEMO_USER_PASSWORD" | wc -c | tr -d '[:space:]')
if (( password_bytes < 12 || password_bytes > 72 )); then
  echo "Demo reset refused. DEMO_USER_PASSWORD must contain 12-72 UTF-8 bytes." >&2
  exit 1
fi
demo_password_hash=$(printf '%s\n' "$DEMO_USER_PASSWORD" | htpasswd -niBC 10 '')
demo_password_hash=${demo_password_hash#:}
[[ "$demo_password_hash" == \$2* ]] || {
  echo "Unable to generate the demo bcrypt password hash." >&2
  exit 1
}

url=${DB_URL#jdbc:mysql://}
authority=${url%%/*}
rest=${url#*/}
database=${rest%%\?*}
host=${authority%%:*}
if [[ "$authority" == *:* ]]; then port=${authority##*:}; else port=3306; fi
[[ -n "$host" && -n "$database" ]] || { echo "Unable to parse DB_URL." >&2; exit 1; }

export MYSQL_PWD="$DB_PASSWORD"
trap 'unset MYSQL_PWD' EXIT
{
  printf "SET @demo_password_hash = '%s';\n" "$demo_password_hash"
  sed -n '1,$p' "$SQL_FILE"
} | mysql --host="$host" --port="$port" --user="$DB_USERNAME" --database="$database" \
  --default-character-set=utf8mb4 --show-warnings

echo "[demo] reset complete for database '$database'."
echo "[demo] users: demo.admin/demo.supplier/demo.inventory/demo.production/demo.sales/demo.customer @tgms.example"
echo "[demo] password: supplied at runtime through DEMO_USER_PASSWORD (not stored by the seed)."
