#!/usr/bin/env bash
# Run as root on the cloud host. Uses MySQL root's local socket authentication.
set -euo pipefail
umask 077
install -d -m 700 /var/backups/chronic-health
backup_path="/var/backups/chronic-health/chronic_health-$(date -u +%Y%m%dT%H%M%SZ).sql.gz"
trap 'rm -f "${backup_path}.partial"' EXIT
mysqldump --single-transaction --routines --triggers --events \
  --set-gtid-purged=OFF --no-tablespaces chronic_health | gzip > "${backup_path}.partial"
gzip -t "${backup_path}.partial"
mv "${backup_path}.partial" "$backup_path"
echo "Database backup saved: $backup_path"
# No automatic pruning. Copy verified backups off-host and monitor disk usage.
