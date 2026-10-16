#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -e "$ROOT/.env" ]]; then
  echo '已有 .env，保留现有配置。'
  exit 0
fi
umask 077
( set -o noclobber
  printf 'MYSQL_PORT=13306\nMYSQL_PASSWORD=%s\nMYSQL_ROOT_PASSWORD=%s\n' "$(openssl rand -hex 24)" "$(openssl rand -hex 32)" > "$ROOT/.env"
)
echo '已生成本地 .env（权限 600，已加入 Git 忽略）。'
