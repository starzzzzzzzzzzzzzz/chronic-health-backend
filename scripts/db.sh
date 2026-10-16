#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
case "${1:-status}" in
  up) ./scripts/init-env.sh; docker compose up -d --wait mysql ;;
  stop) docker compose stop mysql ;;
  status) docker compose ps ;;
  check) docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 -uhealth_app chronic_health -e "SELECT VERSION(), DATABASE(), CURRENT_USER(), @@character_set_server, @@session.time_zone;"' ;;
  shell) docker compose exec mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 -uhealth_app chronic_health' ;;
  *) echo 'Usage: scripts/db.sh {up|stop|status|check|shell}' >&2; exit 2 ;;
esac
