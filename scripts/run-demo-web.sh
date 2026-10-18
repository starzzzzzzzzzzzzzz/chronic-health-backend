#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ ! -f "$ROOT/.env.demo" ]]; then
  echo '请先启动后端：SERVER_PORT=9089 ./scripts/run-demo.sh -o' >&2
  exit 1
fi
if [[ ! -d "$ROOT/demo-web/node_modules" ]]; then
  npm --prefix "$ROOT/demo-web" ci --cache "$ROOT/.dev/npm-cache"
fi
exec npm --prefix "$ROOT/demo-web" run dev
