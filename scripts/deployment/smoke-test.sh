#!/usr/bin/env bash
set -euo pipefail

base=${TGMS_BASE_URL:-${1:-}}
[[ -n "$base" ]] || { echo 'Usage: TGMS_BASE_URL=https://host scripts/deployment/smoke-test.sh' >&2; exit 2; }
base=${base%/}
[[ "$base" == https://* ]] || { echo 'TGMS_BASE_URL must use https:// for production smoke tests' >&2; exit 2; }
command -v curl >/dev/null || { echo 'curl is required' >&2; exit 1; }

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

status=$(curl --fail-with-body --silent --show-error --connect-timeout 5 --max-time 15 -o "$tmp/health" -w '%{http_code}' "$base/api/health")
[[ "$status" == 200 ]] || { echo "health returned $status" >&2; exit 1; }
grep -q '"status"[[:space:]]*:[[:space:]]*"ok"' "$tmp/health" || { echo 'health payload is not ok' >&2; exit 1; }

status=$(curl --silent --show-error --connect-timeout 5 --max-time 15 -o "$tmp/products" -w '%{http_code}' "$base/api/products")
[[ "$status" == 200 ]] || { echo "public products returned $status" >&2; exit 1; }

status=$(curl --silent --show-error --connect-timeout 5 --max-time 15 -o "$tmp/profile" -w '%{http_code}' "$base/api/profile")
[[ "$status" == 401 ]] || { echo "protected profile should return 401 without a session, got $status" >&2; exit 1; }

status=$(curl --silent --show-error --connect-timeout 5 --max-time 15 -o "$tmp/index" -w '%{http_code}' "$base/")
[[ "$status" == 200 ]] || { echo "frontend root returned $status" >&2; exit 1; }

printf '[smoke] PASS: frontend, public API, health, and protected API boundary are reachable at %s\n' "$base"
