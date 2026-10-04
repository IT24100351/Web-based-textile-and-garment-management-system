#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

usage() {
  cat <<'TXT'
Usage: scripts/demo/seed-business-data.sh --confirm ADD_TGMS_BUSINESS_DATA

Required environment:
  DB_URL, DB_USERNAME, DB_PASSWORD
  DEMO_DATA_ALLOWED=true

Safety:
  - Preserves all existing users and password hashes.
  - Replaces all non-user records in the current local demo database.
  - Refuses production-labelled environments.
TXT
}

[[ "${1:-}" != "--help" ]] || { usage; exit 0; }
[[ "${1:-}" == "--confirm" && "${2:-}" == "ADD_TGMS_BUSINESS_DATA" ]] || { usage >&2; exit 2; }
[[ "${DEMO_DATA_ALLOWED:-false}" == "true" ]] || {
  echo "Business seed refused. Set DEMO_DATA_ALLOWED=true only for a disposable demo/dev database." >&2
  exit 1
}
case ",${SPRING_PROFILES_ACTIVE:-},${TGMS_ENV:-},${NODE_ENV:-}," in
  *prod*|*production*) echo "Business seed refused for a production-labelled environment." >&2; exit 1 ;;
esac
command -v mysql >/dev/null || { echo "Missing required command: mysql (MySQL client)." >&2; exit 1; }
command -v node >/dev/null || { echo "Missing required command: node." >&2; exit 1; }
: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
exec node "$ROOT/scripts/demo/seed-business-data.mjs"
