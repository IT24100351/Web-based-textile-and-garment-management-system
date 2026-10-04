#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail=0

echo "[security] checking tracked environment files"
while IFS= read -r path; do
  case "$path" in
    *.env.example|.env.example) ;;
    *.env|.env|*/.env|*/.env.*)
      echo "ERROR: tracked environment file may contain secrets: $path" >&2
      fail=1
      ;;
  esac
done < <(git ls-files)

echo "[security] scanning working tree for private keys/live credential prefixes"
if grep -RInE \
    --exclude-dir=.git --exclude-dir=node_modules --exclude-dir=dist --exclude-dir=target \
    --exclude=package-lock.json \
    'BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|AKIA[0-9A-Z]{16}|sk_live_[A-Za-z0-9]+' \
    backend frontend scripts database .github 2>/dev/null; then
  echo "ERROR: high-risk secret pattern found in project files." >&2
  fail=1
fi

echo "[security] checking production frontend source for server-secret identifiers"
if find frontend/src -type f \( -name '*.ts' -o -name '*.tsx' \) \
    ! -name '*.test.ts' ! -name '*.test.tsx' -print0 \
    | xargs -0 -r grep -nHE 'JWT_SECRET|DB_PASSWORD|SMTP_PASSWORD|password_hash|passwordHash'; then
  echo "ERROR: server-secret identifier found in production frontend source." >&2
  fail=1
fi

if [[ "$fail" -ne 0 ]]; then
  exit 1
fi

echo "[security] tracked-secret checks passed"
