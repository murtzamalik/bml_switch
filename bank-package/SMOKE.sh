#!/usr/bin/env bash
# Smoke against bank deploy :18080. Callers: bank ops after git/ZIP deploy.
# User: "sara code git pe push ... bank ky system pe git se hi deploy"
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [[ -f "${SCRIPT_DIR}/scripts/smoke.sh" ]]; then
  ROOT="${SCRIPT_DIR}"
elif [[ -f "${SCRIPT_DIR}/../scripts/smoke.sh" ]]; then
  ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
else
  echo "ERROR: scripts/smoke.sh not found"
  exit 1
fi
export BASE="${BASE:-http://127.0.0.1:18080}"
export SWITCH_STATIC_TOKEN="${SWITCH_STATIC_TOKEN:-BML-POC-STATIC-TOKEN-2026-AIS}"
cd "$ROOT"
exec bash scripts/smoke.sh
