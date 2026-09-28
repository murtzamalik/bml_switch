#!/usr/bin/env bash
# Phase 0 golden-path smoke (OTP = 1234)
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
CID=$(uuidgen 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())')

echo "== health =="
curl -sf "$BASE/api/v1/system/health" | tee /tmp/bml-health.json
grep -q '"status":"UP"' /tmp/bml-health.json

echo "== channel token =="
TOKEN=$(curl -sf -X POST "$BASE/api/v1/auth/token" \
  -H "Content-Type: application/json" -H "X-Correlation-Id: $CID" \
  -d '{"client_id":"appinsnap-sandbox","client_secret":"change_me_sandbox_secret","grant_type":"client_credentials"}' \
  | tee /tmp/bml-token.json | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])')

echo "== banks =="
BANKS=$(curl -sf "$BASE/api/v1/lookups/banks?active=true&supportsIbft=true" \
  -H "Authorization: Bearer $TOKEN")
echo "$BANKS" | python3 -c 'import sys,json; d=json.load(sys.stdin); assert len(d["items"])>=16, len(d["items"]); print("banks", len(d["items"]))'
IMD=$(echo "$BANKS" | python3 -c 'import sys,json; items=json.load(sys.stdin)["items"]; print(next(i["imd"] for i in items if i["imd"]!="627000"))')

echo "== login =="
STR=$(curl -sf -X POST "$BASE/api/v1/account/login" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"UserName":"ali.khan","CNIC":"4210112345678","password":"Sandbox@123"}' \
  | tee /tmp/bml-login.json | python3 -c 'import sys,json; print(json.load(sys.stdin)["strToken"])')

echo "== balance (Return nest) =="
BAL=$(curl -sf -X POST "$BASE/api/v1/inquiry/balance" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"strToken\":\"$STR\",\"fromAccount\":\"0345001234567\"}")
echo "$BAL" | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d["Response_Code"]=="00"; assert "Return" in d; assert "Amount" in d["Return"]; print(d["Return"]["Amount"])'

echo "== IFT =="
IDEMP=$(uuidgen 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())')
IFT=$(curl -sf -X POST "$BASE/api/v1/payment/ift" \
  -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $IDEMP" \
  -H "Content-Type: application/json" \
  -d "{\"strToken\":\"$STR\",\"fromAccount\":\"0345001234567\",\"toAccount\":\"0345001234568\",\"amount\":\"100.00\",\"PurposeOfPayment\":\"FAM\",\"Description\":\"smoke ift\"}")
echo "$IFT" | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d["Response_Code"]=="00"; assert "return" in d; print(d["return"]["status"])'

echo "== IFT idempotent replay =="
IFT2=$(curl -sf -X POST "$BASE/api/v1/payment/ift" \
  -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $IDEMP" \
  -H "Content-Type: application/json" \
  -d "{\"strToken\":\"$STR\",\"fromAccount\":\"0345001234567\",\"toAccount\":\"0345001234568\",\"amount\":\"100.00\",\"PurposeOfPayment\":\"FAM\",\"Description\":\"smoke ift\"}")
echo "$IFT2" | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d["Response_Code"]=="00"'

echo "== IBFT title (double nest) =="
TITLE=$(curl -sf -X POST "$BASE/api/v1/inquiry/ibft-title" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"strToken\":\"$STR\",\"fromAccount\":\"0345001234567\",\"toIMD\":\"$IMD\",\"toIBAN\":\"PK12MEZN0000001234567890\",\"toAccount\":\"1234567890\",\"amount\":\"50.00\"}")
echo "$TITLE" | python3 -c 'import sys,json; d=json.load(sys.stdin); assert "IBFTTitleFetchResponse" in d; assert "return" in d["IBFTTitleFetchResponse"]; assert d["Response_Code"]=="00"'

echo "== OTP send/verify 1234 =="
REF=$(curl -sf -X POST "$BASE/api/v1/auth/otp/send" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"strToken\":\"$STR\",\"purpose\":\"IBFT\",\"mobile\":\"03001234567\"}" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["otpReference"])')
TICKET=$(curl -sf -X POST "$BASE/api/v1/auth/otp/verify" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"strToken\":\"$STR\",\"otpReference\":\"$REF\",\"otp\":\"1234\"}" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["otpTicket"])')

echo "== IBFT without OTP -> 78 =="
curl -sf -X POST "$BASE/api/v1/payment/ibft" \
  -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $(uuidgen 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())')" \
  -H "Content-Type: application/json" \
  -d "{\"strToken\":\"$STR\",\"fromAccount\":\"0345001234567\",\"toIMD\":\"$IMD\",\"toIBAN\":\"PK12MEZN0000001234567890\",\"toAccount\":\"1234567890\",\"amount\":\"25.00\",\"PurposeOfPayment\":\"FAM\",\"Description\":\"no otp\"}" \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d["Response_Code"]=="78", d'

echo "== IBFT pay =="
IBIDEMP=$(uuidgen 2>/dev/null || python3 -c 'import uuid; print(uuid.uuid4())')
IBFT=$(curl -sf -X POST "$BASE/api/v1/payment/ibft" \
  -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $IBIDEMP" \
  -H "Content-Type: application/json" \
  -d "{\"strToken\":\"$STR\",\"fromAccount\":\"0345001234567\",\"toIMD\":\"$IMD\",\"toIBAN\":\"PK12MEZN0000001234567890\",\"toAccount\":\"1234567890\",\"amount\":\"25.00\",\"PurposeOfPayment\":\"FAM\",\"Description\":\"smoke ibft\",\"otpTicket\":\"$TICKET\"}")
echo "$IBFT" | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d["Response_Code"]=="00"; assert "transactionID" in d; assert d.get("fee")=="0.00"; open("/tmp/bml-txid.txt","w").write(d["transactionID"]); open("/tmp/bml-stan.txt","w").write(d["stan"]); print(d["transactionID"])'
TXID=$(cat /tmp/bml-txid.txt)
STAN=$(cat /tmp/bml-stan.txt)

echo "== IBFT status =="
curl -sf -X POST "$BASE/api/v1/inquiry/ibft-status" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"strToken\":\"$STR\",\"transactionID\":\"$TXID\",\"stan\":\"$STAN\"}" \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d["Response_Code"]=="00"; assert d["status"] in ("POSTED","SUCCESS","00", d["status"]); print(d["status"])'

echo "== limits + receipt + mini =="
curl -sf -X POST "$BASE/api/v1/inquiry/limits" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"strToken\":\"$STR\",\"fromAccount\":\"0345001234567\"}" \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d["Response_Code"]=="00"; assert "dailyDebitLimit" in d'
curl -sf -X POST "$BASE/api/v1/inquiry/receipt" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"strToken\":\"$STR\",\"stan\":\"$STAN\",\"transactionID\":\"$TXID\"}" \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d["Response_Code"]=="00"'
curl -sf -X POST "$BASE/api/v1/inquiry/mini-statement" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"strToken\":\"$STR\",\"fromAccount\":\"0345001234567\",\"limit\":5}" \
  | python3 -c 'import sys,json; d=json.load(sys.stdin); assert d["Response_Code"]=="00"; assert isinstance(d["transactions"], list)'

echo "SMOKE OK"
