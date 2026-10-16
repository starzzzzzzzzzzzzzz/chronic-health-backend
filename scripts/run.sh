#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ ! -f "$ROOT/.env" ]]; then
  echo '请先执行 ./scripts/db.sh up 初始化本地数据库。' >&2
  exit 1
fi
# .env 是本机 init-env.sh 生成的可信 shell 配置；不加载外部来源文件。
source "$ROOT/.env"
export DB_URL="${DB_URL:-jdbc:mysql://127.0.0.1:${MYSQL_PORT:-13306}/chronic_health?connectionTimeZone=Asia/Shanghai}"
export DB_USERNAME="${DB_USERNAME:-health_app}"
export DB_PASSWORD="${DB_PASSWORD:-$MYSQL_PASSWORD}"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"
exec "$ROOT/scripts/mvn.sh" spring-boot:run "$@"
