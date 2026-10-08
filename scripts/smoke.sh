#!/usr/bin/env bash
# Onboarding smoke with static non-expiring Bearer token (SWITCH_STATIC_TOKEN).
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
TOKEN="${SWITCH_STATIC_TOKEN:-BML-POC-STATIC-TOKEN-2026-AIS}"
CID=$(uuidgen 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())')

echo "== health =="
curl -sf "$BASE/api/v1/system/health" | tee /tmp/bml-health.json
grep -q '"status":"UP"' /tmp/bml-health.json

echo "== banks (static token) =="
BANKS=$(curl -sf "$BASE/api/v1/lookups/banks?active=true&supportsIbft=true" \
  -H "Authorization: Bearer $TOKEN")
echo "$BANKS" | python3 -c 'import sys,json; d=json.load(sys.stdin); assert len(d["items"])>=16, len(d["items"]); print("banks", len(d["items"]))'

echo "== Flow 2 account-list (seeded CNIC) =="
LIST=$(curl -sf -X POST "$BASE/api/v1/account/account-list" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"CNIC":"4210112345678"}')
echo "$LIST" | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d["Response_Code"]=="00"; assert len(d["accounts"])>=1; assert "balance" in d["accounts"][0]; print("accounts", len(d["accounts"]))'

echo "== Flow 1 open (new CNIC) =="
NEW_CNIC="4589652$(python3 -c 'import random; print(f"{random.randint(100000,999999)}")')"
OPEN=$(curl -sf -X POST "$BASE/api/v1/account/open" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "X-Correlation-Id: $CID" \
  -d "{\"CNIC\":\"$NEW_CNIC\",\"fullName\":\"Smoke Test\",\"firstName\":\"Smoke\",\"lastName\":\"Test\",\"MobileNo\":\"03001234567\",\"dateOfBirth\":\"1995-10-10\",\"idDeliveryDate\":\"2018-01-01\",\"idExpiryDate\":\"2030-01-01\",\"gender\":\"M\",\"maritalStatus\":\"M\",\"email\":\"smoke@example.com\",\"mailingAddress\":\"House 1 Karachi\",\"city\":\"KARACHI\",\"productCode\":\"ASAAN_DIGITAL\",\"accGl\":\"203153\"}")
echo "$OPEN" | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d["Response_Code"]=="00", d; assert d["accountCreated"] is True; assert d["cifCreated"] is True; assert len(d["accounts"])>=1; print("cif", d["cifNo"], "acct", d["accounts"][0]["accountNumber"])'

echo "== Flow 1 Option P (same product skip) =="
OPEN2=$(curl -sf -X POST "$BASE/api/v1/account/open" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"CNIC\":\"$NEW_CNIC\",\"fullName\":\"Smoke Test\",\"MobileNo\":\"03001234567\",\"productCode\":\"ASAAN_DIGITAL\",\"accGl\":\"203153\"}")
echo "$OPEN2" | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d["Response_Code"]=="00", d; assert d["accountCreated"] is False; assert d["cifCreated"] is False; print("skipped create ok")'

echo "== Flow 1 different accGl creates second account =="
OPEN3=$(curl -sf -X POST "$BASE/api/v1/account/open" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"CNIC\":\"$NEW_CNIC\",\"fullName\":\"Smoke Test\",\"MobileNo\":\"03001234567\",\"productCode\":\"CURRENT\",\"accGl\":\"203154\"}")
echo "$OPEN3" | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d["Response_Code"]=="00", d; assert d["accountCreated"] is True; assert len(d["accounts"])>=2; print("second product ok", len(d["accounts"]))'

echo "SMOKE OK (onboarding + static token)"
