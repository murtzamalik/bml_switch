# Smoke cURL Outline (Onboarding Flows)

Existing handoff file. Used by AppInSnap/QA. Covers Flow 1 open + Flow 2 account-list.
Instruction: Implement the iMal Account Open + Existing Registration Plan.

Replace secrets from seed. Local base: `http://localhost:8080`.

```bash
BASE=http://localhost:8080
CID=$(uuidgen)

# 1) Static non-expiring API token (no /auth/token call)
TOKEN="${SWITCH_STATIC_TOKEN:-BML-POC-STATIC-TOKEN-2026-AIS}"

# 2) Banks (no hardcode)
curl -s "$BASE/api/v1/lookups/banks?active=true&supportsIbft=true" \
  -H "Authorization: Bearer $TOKEN" | jq '.items[0]'

# 3) Flow 2 — existing accounts by CNIC (after AIS login)
curl -s -X POST "$BASE/api/v1/account/account-list" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"CNIC":"4210112345678"}' | jq .

# 4) Flow 1 — open new account
curl -s -X POST "$BASE/api/v1/account/open" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{
    "CNIC":"4589652158800",
    "fullName":"Smoke Test",
    "MobileNo":"03001234567",
    "productCode":"ASAAN_DIGITAL",
    "accGl":"203153",
    "dateOfBirth":"1995-10-10",
    "idDeliveryDate":"2018-01-01",
    "idExpiryDate":"2030-01-01",
    "gender":"M",
    "maritalStatus":"M",
    "mailingAddress":"House 1 Karachi",
    "city":"KARACHI"
  }' | jq .

# Full payment golden path: deferred — switch no longer issues strToken via /account/login.
```

Also: `scripts/smoke.sh` covers Flow 1 Option P + second product.
