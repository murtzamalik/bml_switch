#!/usr/bin/env bash
# Build offline ZIP for bank machine (no git on target).
# Callers: developer laptop. Output: dist/bml-switch-bank-*.zip
# Contains: source, Dockerfiles, compose, smoke, Postman, DEPLOY.sh
# User: "complete zip bana do jis main source code ho testing script ho ... deploy run"
set -euo pipefail

REPO="$(cd "$(dirname "$0")/.." && pwd)"
STAMP="$(date +%Y%m%d-%H%M)"
NAME="bml-switch-bank-${STAMP}"
OUT_DIR="${REPO}/dist"
STAGE="${OUT_DIR}/${NAME}"
ZIP="${OUT_DIR}/${NAME}.zip"

rm -rf "${STAGE}"
mkdir -p "${STAGE}"

echo "== staging ${NAME} =="

cp "${REPO}/pom.xml" "${STAGE}/"
cp "${REPO}/Dockerfile" "${STAGE}/"
cp "${REPO}/.dockerignore" "${STAGE}/"
cp "${REPO}/docker-compose.server.yml" "${STAGE}/"
cp "${REPO}/.env.example" "${STAGE}/"

mkdir -p "${STAGE}/src"
cp -R "${REPO}/src/." "${STAGE}/src/"

mkdir -p "${STAGE}/scripts" "${STAGE}/docs/handoff" "${STAGE}/docs/iMall-apis"
cp "${REPO}/scripts/smoke.sh" "${STAGE}/scripts/"
cp "${REPO}/docs/handoff/"*.md "${STAGE}/docs/handoff/" 2>/dev/null || true
cp "${REPO}/docs/handoff/"*.json "${STAGE}/docs/handoff/" 2>/dev/null || true
cp -R "${REPO}/docs/iMall-apis/." "${STAGE}/docs/iMall-apis/" 2>/dev/null || true

cp "${REPO}/bank-package/DEPLOY.sh" "${STAGE}/"
cp "${REPO}/bank-package/STOP.sh" "${STAGE}/"
cp "${REPO}/bank-package/LOGS.sh" "${STAGE}/"
cp "${REPO}/bank-package/SMOKE.sh" "${STAGE}/"
cp "${REPO}/bank-package/README-BANK-MACHINE.txt" "${STAGE}/"

chmod +x "${STAGE}/DEPLOY.sh" "${STAGE}/STOP.sh" "${STAGE}/LOGS.sh" "${STAGE}/SMOKE.sh" "${STAGE}/scripts/smoke.sh"

find "${STAGE}" -name '.DS_Store' -delete 2>/dev/null || true
find "${STAGE}" -type d -name 'target' -exec rm -rf {} + 2>/dev/null || true

echo "== zipping =="
mkdir -p "${OUT_DIR}"
rm -f "${ZIP}"
(
  cd "${OUT_DIR}"
  zip -rq "${NAME}.zip" "${NAME}"
)

BYTES=$(wc -c < "${ZIP}" | tr -d ' ')
echo
echo "READY: ${ZIP}"
echo "Size:  ${BYTES} bytes"
echo
echo "Give bank ops:"
echo "  1) Copy ZIP to bank machine"
echo "  2) unzip ${NAME}.zip && cd ${NAME}"
echo "  3) ./DEPLOY.sh"
