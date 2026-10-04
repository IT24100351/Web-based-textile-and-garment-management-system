#!/usr/bin/env bash
set -euo pipefail

fail() { printf '[deploy:env] ERROR: %s\n' "$1" >&2; exit 1; }
need() { [[ -n "${!1:-}" ]] || fail "$1 is required"; }

for name in CLIENT_ORIGIN DB_URL DB_USERNAME DB_PASSWORD JWT_SECRET; do need "$name"; done

[[ "$CLIENT_ORIGIN" == https://* ]] || fail 'CLIENT_ORIGIN must use https:// in production'
[[ "$CLIENT_ORIGIN" != *"*"* ]] || fail 'CLIENT_ORIGIN must not contain a wildcard'
[[ "$CLIENT_ORIGIN" != */ ]] || fail 'CLIENT_ORIGIN must be an exact origin without a trailing slash'
[[ "$CLIENT_ORIGIN" != *"?"* && "$CLIENT_ORIGIN" != *"#"* ]] || fail 'CLIENT_ORIGIN must not contain query/fragment data'
[[ "$DB_URL" == jdbc:mysql://* ]] || fail 'DB_URL must be a MySQL JDBC URL'
[[ ${#JWT_SECRET} -ge 32 ]] || fail 'JWT_SECRET must contain at least 32 characters/bytes'
[[ "${COOKIE_SECURE:-true}" == "true" ]] || fail 'COOKIE_SECURE must be true in production'
[[ "${DB_ALLOW_ROLLBACK:-false}" != "true" ]] || fail 'DB_ALLOW_ROLLBACK must remain false in production'

if [[ "${SMTP_ENABLED:-false}" == "true" ]]; then
  for name in MAIL_FROM SMTP_HOST SMTP_PORT SMTP_USERNAME SMTP_PASSWORD; do need "$name"; done
fi

printf '[deploy:env] PASS: production environment contract is structurally valid\n'
