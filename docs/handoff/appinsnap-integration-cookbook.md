# AppInSnap Integration Cookbook

**Audience:** AppInSnap mobile engineers  
**Base URL (local):** `http://localhost:8080` (**HTTP OK** in Docker sandbox; UAT/prod **HTTPS**)  
**Start here (handoff pack):** [README.md](./README.md) · **[APPINSNAP-API-GUIDE.md](./APPINSNAP-API-GUIDE.md)** · [APPINSNAP-POSTMAN-GUIDE.md](./APPINSNAP-POSTMAN-GUIDE.md) · [APPINSNAP-TEST-DATA.md](./APPINSNAP-TEST-DATA.md)  
**OpenAPI:** `docs/openapi/assanpay-switch-v1.yaml`  
**Contracts:** [headers-and-auth.md](./headers-and-auth.md) · [required-fields-matrix.md](./required-fields-matrix.md) · [error-handling.md](./error-handling.md)

**Consumability lock:** Phase-0 **happy path** needs **zero verbal Q&A**. Mock OTP = **`1234`**. Frozen execution: [../plans/PHASE-0-EXECUTION.md](../plans/PHASE-0-EXECUTION.md).

---

## 0. Bring-up

```bash
cp .env.example .env
docker compose up --build
curl -s http://localhost:8080/api/v1/system/health
```

Expect: `status=UP`, `mockImal=true`. Then run [smoke-test-checklist.md](./smoke-test-checklist.md).

---

## 1. Seed credentials & accounts

| Item | Value |
|------|-------|
| `client_id` | `appinsnap-sandbox` |
| `client_secret` | `change_me_sandbox_secret` |
| Demo CNIC (even → auto KYC) | `4210112345678` |
| Odd CNIC (manual review demo) | `4210112345679` |
| Username / password | `ali.khan` / `Sandbox@123` |
| Funded #1 (from) | `0345001234567` / IBAN `PK00BMAL0000000345001234567` |
| Funded #2 (IFT to) | `0345001234568` / IBAN `PK00BMAL0000000345001234568` |
| Zero balance | `0345009999999` |
| Invalid | `0345000000000` |
| Mock OTP | **`1234`** (`MOCK_OTP=true`) |
| Fee | Always **`0.00`** in mock (no fee API) |

---

## 2. Formats (LOCKED)

| Field | Format |
|-------|--------|
| Amount | String 2 dp, no commas: `"100.00"` |
| CNIC | 13 digits (dashes stripped) |
| Mobile | `03XXXXXXXXX` (`+92` normalized) |
| strDate / strTime | `yyyyMMdd` / `HHmmss` |
| transmissionDateTime | `yyyy-MM-dd HH:mm:ss.SSS` Asia/Karachi |
| Statement dates | `yyyy-MM-dd` |
| Idempotency-Key | Mobile UUID on open/IFT/IBFT |
| stan / intSTAN | Omit → Switch generates |
| Images | Base64 ≤ **2MB** decoded each |

---

## 3. Auth (LOCKED)

### Channel

```http
POST /api/v1/auth/token
{ "client_id":"...", "client_secret":"...", "grant_type":"client_credentials" }
```

Response: `access_token`, `token_type=Bearer`, `expires_in=900`, `refresh_token`, `refresh_expires_in=604800`, `correlationId`.

All later calls: `Authorization: Bearer <access_token>`.

### Customer

Login → `strToken`. On customer ops: put **`strToken` in JSON body (REQUIRED)**. Optional alias header `X-Str-Token`.

Expired channel or customer → **HTTP 401** (`91` / lockout `75`). See error-handling.md.

Logout: single `POST /auth/logout` (optional also revoke customer via `strToken`).

---

## 4. Lookups first

Channel JWT only. Load before UI pickers (payments/ops):

`banks`, `purpose-of-payment`, `response-codes`, `branches`, `currencies`, `app-config`, `version`.

**Do not hardcode banks or purpose codes.** Honor ETag (304). Empty list = `items:[]` + `00`, not error.

Onboarding form catalogs (provinces, occupations, finger-indexes, onboarding-steps, etc.) are **AIS-owned** — not exposed by the switch.

Pagination (banks): opaque `nextCursor` e.g. `"eyJvIjoxMH0"` — pass as `?cursor=`.

---

## 5. Onboarding (iMal orchestration)

KYC, username/password, and mobile login are **AIS-side**. Switch exposes two APIs (channel JWT only, no `strToken`).

### Flow 1 — New account opening (`POST /account/open`)

1. Channel token  
2. `POST /api/v1/account/open` with CNIC + customer fields + `productCode` + `accGl`  
3. Switch: `createRetailCif` → `validateRetailCif` → list → **Option P** (same product/accGl → return list; else createGeneralAccount → authorizeGeneralAccount → list)  
4. Response: `accounts[]` (with balance) + `cifCreated` + `accountCreated` + `cifNo`

### Flow 2 — Existing iMal account, mobile registration

1. AIS registers/logs in the user on AIS  
2. `POST /api/v1/account/account-list` with `{ "CNIC": "..." }` (channel JWT)  
3. Response: `accounts[]` with balances  

**Auth:** every API call uses `Authorization: Bearer <SWITCH_STATIC_TOKEN>` (static, non-expiring). No channel JWT exchange.

---

## 6. Money sequences

### IFT

1. Purpose from `/lookups/purpose-of-payment`  
2. Optional `inquiry/ift-title`  
3. `payment/ift` with Idempotency-Key: from `0345001234567` → to **`0345001234568`**, amount `"100.00"`  
4. Nested `return.status=00`  
5. Confirm: balance / mini-statement / limits  

### IBFT (LOCKED sequence)

```
GET  lookups/banks
POST inquiry/ibft-title          # toIBAN + toIMD (IMD from banks)
POST auth/otp/send
POST auth/otp/verify             # otp=1234 → otpTicket
POST payment/ibft                # otpTicket + Idempotency-Key; fee 0.00
POST inquiry/ibft-status         # poll every 2s, max 30s
```

**Fields:** Prefer `toIBAN` + `toIMD`. `toAccount` optional if IBAN present. `fromIBAN` from account-list. Missing/invalid `otpTicket` → `78`.

**Status request:**

```json
{ "strToken":"...", "transactionID":"...", "stan":"..." }
```

Provide at least one of `transactionID` / `stan` / `intRefNum`. Terminals: `POSTED` | `FAILED` | `REVERSED`. Pending → keep polling until 30s then show timeout UX (call support with correlationId).

### No OTP in Phase 0 for IFT

Title confirmation screen is enough for IFT. OTP **required for IBFT only**.

---

## 7. Statements / limits / receipt (MUST)

### Mini-statement request/response

```json
// request
{ "strToken":"...", "fromAccount":"0345001234567", "limit": 10 }

// response
{
  "Response_Code":"00", "Response_Desc":"Success", "correlationId":"...",
  "transactions": [
    {
      "bookingDate":"2026-09-28",
      "narration":"IFT to 0345001234568",
      "direction":"DEBIT",
      "amount":"100.00",
      "currency":"PKR",
      "balanceAfter":"149900.00",
      "stan":"123456",
      "ref":"..."
    }
  ]
}
```

Empty → `transactions:[]` + `00` (not error).

### Date-range statement

```json
{
  "strToken":"...",
  "fromAccount":"0345001234567",
  "fromDate":"2026-09-01",
  "toDate":"2026-09-28",
  "page":0,
  "size":50
}
```

Max range 90 days → else validation 400 or business `61`.

### Limits

```json
// request
{ "strToken":"...", "fromAccount":"0345001234567" }
// response
{
  "Response_Code":"00",
  "dailyDebitLimit":"200000.00",
  "dailyDebitUsed":"100.00",
  "dailyDebitRemaining":"199900.00",
  "maxBalance":"1000000.00",
  "currency":"PKR",
  "correlationId":"..."
}
```

### Receipt

```json
{ "strToken":"...", "stan":"123456" }
```

Returns amount, accounts, status, timestamps, fee `"0.00"`.

---

## 8. Password lifecycle

- `POST /account/change-password` — current + new (strToken)  
- `POST /account/reset-password-mock` — channel JWT + CNIC + newPassword (**sandbox only**)

---

## 9. Beneficiaries

**Mobile-local storage** in MVP. Re-run title fetch before pay.

---

## 10. Error branching (quick)

1. HTTP 401 → refresh/login / lockout  
2. HTTP 400 → fieldErrors  
3. HTTP 200 + `Response_Code!=00` → map via `/lookups/response-codes`  
4. Always keep `correlationId`

---

## 11. Handoff files

| File | Use |
|------|-----|
| [headers-and-auth.md](./headers-and-auth.md) | Tokens & headers |
| [required-fields-matrix.md](./required-fields-matrix.md) | What to send |
| [error-handling.md](./error-handling.md) | Status vs codes |
| [smoke-test-checklist.md](./smoke-test-checklist.md) | 15-min QA |
| [smoke-curl.md](./smoke-curl.md) | cURL outline |
| [postman-collection.stub.json](./postman-collection.stub.json) | Import skeleton |
| [../plans/consumability-audit.md](../plans/consumability-audit.md) | Audit log |
