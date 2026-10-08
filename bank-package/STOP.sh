#!/usr/bin/env bash
# Stop bank deploy. Callers: bank ops (git clone or ZIP).
# User: "sara code git pe push ... bank ky system pe git se hi deploy"
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [[ -f "${SCRIPT_DIR}/docker-compose.server.yml" ]]; then
  ROOT="${SCRIPT_DIR}"
elif [[ -f "${SCRIPT_DIR}/../docker-compose.server.yml" ]]; then
  ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
else
  echo "ERROR: docker-compose.server.yml not found"
  exit 1
fi
cd "$ROOT"
docker compose -p bml_switch -f docker-compose.server.yml down
echo "Stopped. Data volume kept (bml_switch_mysql_data)."
echo "To wipe DB: docker volume rm bml_switch_mysql_data"
