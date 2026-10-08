#!/usr/bin/env bash
# Bank machine one-shot deploy. Callers: bank ops (git clone or ZIP).
# Uses docker-compose.server.yml (MOCK_IMAL=false — live open+IFT; list/balance local).
# User: "sara code git pe push ... bank ky system pe git se hi deploy ... mock off"
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [[ -f "${SCRIPT_DIR}/docker-compose.server.yml" ]]; then
  ROOT="${SCRIPT_DIR}"
elif [[ -f "${SCRIPT_DIR}/../docker-compose.server.yml" ]]; then
  ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
else
  echo "ERROR: docker-compose.server.yml not found (git clone repo root or unzip package)."
  exit 1
fi
cd "$ROOT"

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: docker not found. Install Docker Engine first."
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  echo "ERROR: 'docker compose' plugin not found."
  exit 1
fi

if [[ ! -f .env ]]; then
  echo "== creating .env from .env.example =="
  cp .env.example .env
  echo "    Review .env if needed (IMAL_* URLs / token), then continue."
fi

echo "== docker compose build + up (host port 18080, MOCK_IMAL=false) =="
docker compose -p bml_switch -f docker-compose.server.yml up -d --build

echo
echo "== waiting for health on :18080 =="
for i in $(seq 1 40); do
  if curl -sf "http://127.0.0.1:18080/api/v1/system/health" >/tmp/bml_health.json 2>/dev/null; then
    echo "HEALTH OK:"
    cat /tmp/bml_health.json
    echo
    echo "API base : http://<this-machine-ip>:18080"
    echo "Token    : BML-POC-STATIC-TOKEN-2026-AIS"
    echo "Expect mockImal=false on bank POC."
    echo
    echo "Next: docker logs -f bml_switch_app"
    exit 0
  fi
  echo "  waiting... ($i/40)"
  sleep 5
done

echo "ERROR: health timed out. Run: docker logs bml_switch_app"
exit 1
