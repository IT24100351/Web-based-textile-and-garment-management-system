#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_ROOT=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
ENV_FILE="$PROJECT_ROOT/backend/.env"
ENV_EXAMPLE="$PROJECT_ROOT/backend/.env.example"
CHECK_ONLY=false

if [[ "${1:-}" == "--check" ]]; then
  CHECK_ONLY=true
elif [[ $# -gt 0 ]]; then
  echo "Usage: ./run.sh [--check]" >&2
  exit 2
fi

fail() {
  echo "[TGMS] Error: $1" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "$2"
}

node_is_supported() {
  "$1" -e '
    const [major, minor, patch] = process.versions.node.split(".").map(Number);
    const atLeast = (requiredMinor, requiredPatch) =>
      minor > requiredMinor || (minor === requiredMinor && patch >= requiredPatch);
    process.exit((major === 22 && atLeast(22, 2))
      || (major === 24 && atLeast(15, 0))
      || major >= 26 ? 0 : 1);
  ' >/dev/null 2>&1
}

# NVM can leave an older Node first on PATH even when Homebrew has a compatible
# version installed. Prefer that existing installation before reporting an error.
if command -v node >/dev/null 2>&1 && ! node_is_supported "$(command -v node)"; then
  if command -v brew >/dev/null 2>&1; then
    HOMEBREW_NODE_BIN="$(brew --prefix node 2>/dev/null)/bin"
    if [[ -x "$HOMEBREW_NODE_BIN/node" ]] && node_is_supported "$HOMEBREW_NODE_BIN/node"; then
      export PATH="$HOMEBREW_NODE_BIN:$PATH"
      echo "[TGMS] Using compatible Homebrew Node.js $(node --version)."
    fi
  fi
fi

require_command node "Node.js 22.22.2+, 24.15.0+, or 26+ is required: https://nodejs.org/"
require_command npm "npm is required and is normally installed with Node.js."
require_command java "Java 17 or newer is required."

node -e '
const [major, minor, patch] = process.versions.node.split(".").map(Number);
const atLeast = (requiredMinor, requiredPatch) =>
  minor > requiredMinor || (minor === requiredMinor && patch >= requiredPatch);
const supported = (major === 22 && atLeast(22, 2))
  || (major === 24 && atLeast(15, 0))
  || major >= 26;
if (!supported) {
  console.error(`[TGMS] Error: Node.js 22.22.2+, 24.15.0+, or 26+ is required; found ${process.versions.node}.`);
  process.exit(1);
}
'

cd "$PROJECT_ROOT"

if [[ ! -f "$ENV_FILE" ]]; then
  [[ -f "$ENV_EXAMPLE" ]] || fail "Missing backend/.env.example."
  if [[ "$CHECK_ONLY" == true ]]; then
    fail "backend/.env is missing. Run ./run.sh once to create it, then configure the database credentials."
  fi
  cp "$ENV_EXAMPLE" "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  echo "[TGMS] Created backend/.env from backend/.env.example."
fi

chmod 600 "$ENV_FILE"

if grep -Eq '^JWT_SECRET=$|^JWT_SECRET=replace_with_' "$ENV_FILE"; then
  if [[ "$CHECK_ONLY" == true ]]; then
    fail "JWT_SECRET is missing in backend/.env. Run ./run.sh to generate it."
  fi
  node - "$ENV_FILE" <<'NODE'
const crypto = require('node:crypto');
const fs = require('node:fs');
const file = process.argv[2];
const contents = fs.readFileSync(file, 'utf8');
const updated = contents.replace(/^JWT_SECRET=(?:|replace_with_[^\r\n]*)$/m,
  `JWT_SECRET=${crypto.randomBytes(48).toString('hex')}`);
if (updated === contents) throw new Error('Could not locate JWT_SECRET in backend/.env.');
fs.writeFileSync(file, updated, { mode: 0o600 });
NODE
  echo "[TGMS] Generated JWT_SECRET in backend/.env."
fi

if grep -Eq '^DB_PASSWORD=$|^DB_PASSWORD=replace_with_local_password$' "$ENV_FILE"; then
  fail "Set DB_PASSWORD in backend/.env to the password for MySQL user tgms_user. See README.md > Database setup."
fi

if grep -Eq '^JWT_SECRET=$|^JWT_SECRET=replace_with_' "$ENV_FILE"; then
  fail "Set JWT_SECRET in backend/.env to a random value of at least 32 bytes."
fi

node scripts/check-database.mjs backend/.env

if [[ ! -d "$PROJECT_ROOT/node_modules/concurrently" ]]; then
  if [[ "$CHECK_ONLY" == true ]]; then
    fail "Node.js dependencies are missing. Run npm ci before using --check."
  fi
  echo "[TGMS] Installing dependencies from package-lock.json..."
  npm ci
fi

node scripts/check-smtp.mjs backend/.env ||
  fail "Authentication email cannot be delivered. Correct the SMTP settings in backend/.env."

if command -v mysqladmin >/dev/null 2>&1 && ! mysqladmin ping --silent >/dev/null 2>&1; then
  echo "[TGMS] Warning: MySQL did not answer a local ping. Start MySQL before using database features." >&2
fi

if [[ "$CHECK_ONLY" == true ]]; then
  echo "[TGMS] Environment check passed."
  exit 0
fi

echo "[TGMS] Starting LankaWear Apparel..."
echo "[TGMS] Frontend: http://localhost:5173"
echo "[TGMS] API:      http://localhost:3000/api/health"
echo "[TGMS] Press Ctrl+C to stop both servers."

exec npm run dev
