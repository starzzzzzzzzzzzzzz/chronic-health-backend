#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
umask 077
if [[ ! -f "$ROOT/.env.demo" ]]; then
  (set -o noclobber; printf 'HEALTH_DEMO_API_TOKEN=%s\n' "$(openssl rand -hex 32)" > "$ROOT/.env.demo")
fi
source "$ROOT/.env.demo"
export HEALTH_DEMO_API_TOKEN
export HEALTH_DEMO_ENABLED=true
export SPRING_PROFILES_ACTIVE=local
exec "$ROOT/scripts/run.sh" "$@"
