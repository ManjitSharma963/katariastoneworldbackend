#!/usr/bin/env bash
# Apply ALL_MIGRATIONS_CONSOLIDATED.mysql.sql via docker exec (MySQL running in a container).
#
# Usage:
#   chmod +x run-consolidated-migration-docker.sh
#   ./run-consolidated-migration-docker.sh <container_name> [database] [mysql_user]
#
# Examples:
#   ./run-consolidated-migration-docker.sh mysql-db katariastoneworld root
#   ./run-consolidated-migration-docker.sh some-mysql-1 myapp root
#
# Backup first (from host, replace names):
#   docker exec mysql-db mysqldump -uroot -p --single-transaction katariastoneworld > backup_before_migrate.sql

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/ALL_MIGRATIONS_CONSOLIDATED.mysql.sql"

CONTAINER="${1:-}"
DATABASE="${2:-katariastoneworld}"
MYSQL_USER="${3:-root}"

if [[ -z "$CONTAINER" ]]; then
  echo "Usage: $0 <docker_container_name> [database] [mysql_user]" >&2
  echo "Example: $0 mysql-db katariastoneworld root" >&2
  exit 1
fi

if [[ ! -f "$SQL_FILE" ]]; then
  echo "SQL file not found: $SQL_FILE" >&2
  exit 1
fi

echo -n "MySQL password for user '${MYSQL_USER}': "
read -rs MYSQL_PWD
echo

export MYSQL_PWD
docker exec -i "$CONTAINER" mysql -u"$MYSQL_USER" --default-character-set=utf8mb4 "$DATABASE" < "$SQL_FILE"
unset MYSQL_PWD

echo "Done. If there were no errors, migrations completed."
