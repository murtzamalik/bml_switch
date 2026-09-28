# Smoke cURL Outline (Golden Path)

Replace secrets from seed. Local base: `http://localhost:8080`.

```bash
BASE=http://localhost:8080
CID=$(uuidgen)

# 1) Channel token
TOKEN=$(curl -s -X POST "$BASE/api/v1/auth/token" \
  -H "Content-Type: application/json" -H "X-Correlation-Id: $CID" \
  -d '{"client_id":"appinsnap-sandbox","client_secret":"change_me_sandbox_secret","grant_type":"client_credentials"}' \
  | jq -r .access_token)

# 2) Banks (no hardcode)
curl -s "$BASE/api/v1/lookups/banks?active=true&supportsIbft=true" \
  -H "Authorization: Bearer $TOKEN" | jq '.items[0]'

# 3) Login
STR=$(curl -s -X POST "$BASE/api/v1/account/login" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"UserName":"ali.khan","CNIC":"4210112345678","password":"Sandbox@123"}' \
  | jq -r .strToken)

# 4) Balance
curl -s -X POST "$BASE/api/v1/inquiry/balance" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"strToken\":\"$STR\",\"fromAccount\":\"0345001234567\"}" | jq .

# 5) IFT to second funded account
IDEMP=$(uuidgen)
curl -s -X POST "$BASE/api/v1/payment/ift" \
  -H "Authorization: Bearer $TOKEN" -H "Idempotency-Key: $IDEMP" \
  -H "Content-Type: application/json" \
  -d "{\"strToken\":\"$STR\",\"fromAccount\":\"0345001234567\",\"toAccount\":\"0345001234568\",\"amount\":\"100.00\",\"PurposeOfPayment\":\"FAM\",\"Description\":\"smoke ift\"}" | jq .

# 6) IBFT: title → otp → pay → status
IMD=$(curl -s "$BASE/api/v1/lookups/banks?supportsIbft=true" -H "Authorization: Bearer $TOKEN" | jq -r '.items[0].imd')
# ... ibft-title with toIMD=$IMD + toIBAN from fixture ...
# OTP send/verify → otpTicket
# payment/ibft with otpTicket + Idempotency-Key
# poll inquiry/ibft-status every 2s up to 30s

# 7) Limits + receipt
curl -s -X POST "$BASE/api/v1/inquiry/limits" \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d "{\"strToken\":\"$STR\",\"fromAccount\":\"0345001234567\"}" | jq .
```

Full request bodies for title/IBFT/OTP: see [appinsnap-integration-cookbook.md](./appinsnap-integration-cookbook.md) and [required-fields-matrix.md](./required-fields-matrix.md).

**Automated smoke (OTP=1234):** `./scripts/smoke.sh`
