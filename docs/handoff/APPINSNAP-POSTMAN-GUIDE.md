# AppInSnap — Postman Collection Guide

**Audience:** AppInSnap mobile / QA engineers  
**Collection file:** [`BML-Switch-Phase0.postman_collection.json`](./BML-Switch-Phase0.postman_collection.json)  
**Companion API doc:** [APPINSNAP-API-GUIDE.md](./APPINSNAP-API-GUIDE.md)  
**Test data:** [APPINSNAP-TEST-DATA.md](./APPINSNAP-TEST-DATA.md)

**Mock OTP = `1234`** (never `123456`).

---

## 1. Prerequisites

1. Switch running locally:
   ```bash
   docker compose up --build -d
   curl -s http://46.224.146.158:18080/api/v1/system/health
   ```
   Expect `"status":"UP"`.
2. Postman Desktop or Postman web with ability to import a collection JSON.

---

## 2. Import the collection

1. Open Postman → **Import**.
2. Choose **File** → select  
   `docs/handoff/BML-Switch-Phase0.postman_collection.json`  
   (or drag-and-drop the file).
3. Confirm collection name: **BML Switch Phase 0 — AppInSnap**.
4. Open the collection → **Variables** tab. You should see:

| Variable | Default | Notes |
|----------|---------|-------|
| `baseUrl` | `http://46.224.146.158:18080` | Sandbox IP `46.224.146.158` port `18080` |
| `clientId` | `appinsnap-sandbox` | |
| `clientSecret` | `change_me_sandbox_secret` | Sandbox only |
| `accessToken` | *(empty)* | Auto-set by **01 auth/token** |
| `refreshToken` | *(empty)* | Auto-set by token |
| `strToken` | *(empty)* | Auto-set by **03 account/login** |
| `otpReference` | *(empty)* | Auto-set by OTP send |
| `otpTicket` | *(empty)* | Auto-set by OTP verify |
| `imd` | `601004` | Meezan sample from seed |
| `toIban` | `PK12MEZN0000001234567890` | Sample beneficiary |
| `toAccount` | `1234567890` | Sample beneficiary |
| `fromAccount` | `0345001234567` | Funded seed |
| `toAccountIft` | `0345001234568` | Funded seed |
| `stan` | *(empty)* | Auto-set after IBFT |
| `transactionId` | *(empty)* | Auto-set after IBFT |

No separate Postman Environment file is required for local sandbox — collection variables are enough. For a shared team Environment, copy the same keys into an Environment named `BML Switch Sandbox` and select it in the top-right dropdown.

---

## 3. Happy-path run order

Run requests **top to bottom** inside folder **01 — Happy path (in order)**:

| # | Request | Saves variables |
|---|---------|-----------------|
| 1 | `system/health` | — |
| 2 | `auth/token` | `accessToken`, `refreshToken` |
| 3 | `lookups/banks` | optionally peek `imd` (already default `601004`) |
| 4 | `account/login` | `strToken` |
| 5 | `inquiry/balance` | — |
| 6 | `payment/ift` | — |
| 7 | `inquiry/ibft-title` | — |
| 8 | `auth/otp/send` | `otpReference` |
| 9 | `auth/otp/verify (1234)` | `otpTicket` |
| 10 | `payment/ibft` | `stan`, `transactionId` |
| 11 | `inquiry/ibft-status` | — |
| 12 | `inquiry/limits` | — |
| 13 | `inquiry/receipt` | — |

**Tip:** Use Postman’s **Collection Runner** on folder `01 — Happy path (in order)` with iterations = 1.

Other folders (`02 — Lookups`, `03 — Negative checks`) are optional after the happy path works.

---

## 4. How variables are chained

```
auth/token
  └─ Tests script → accessToken, refreshToken

account/login
  └─ Authorization: Bearer {{accessToken}}
  └─ Tests script → strToken

auth/otp/send
  └─ body.strToken = {{strToken}}
  └─ Tests script → otpReference

auth/otp/verify
  └─ body.otp = "1234"
  └─ body.otpReference = {{otpReference}}
  └─ Tests script → otpTicket

payment/ibft
  └─ body.otpTicket = {{otpTicket}}
  └─ body.toIMD = {{imd}}   (601004)
  └─ Tests script → stan, transactionId

inquiry/ibft-status / receipt
  └─ use {{stan}} / {{transactionId}}
```

If a Tests script did not run (failed request), variables stay empty — fix the failing step and re-run from that point (or from **auth/token** if the channel JWT expired).

---

## 5. OTP reminder

| Step | Value |
|------|-------|
| Verify body | `"otp": "1234"` |
| Wrong OTP / missing ticket on IBFT | `Response_Code` **`78`** |
| Sandbox flag | `MOCK_OTP=true` |

---

## 6. Idempotency in Postman

IFT and IBFT requests set header:

```
Idempotency-Key: {{$guid}}
```

Each click generates a new UUID (new payment attempt). To test **replay**, freeze a UUID in the header (replace `{{$guid}}` with a fixed value) and send the same request twice — second response should match the first success body.

---

## 7. Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| HTTP 401, `CHANNEL_TOKEN_*` | Missing/expired Bearer | Re-run **auth/token**; check `Authorization` header |
| HTTP 401, `Response_Code` `91` | Missing/expired `strToken` | Re-run **account/login**; ensure body includes `strToken` |
| HTTP 401, `75` | Lockout after 5 bad passwords | Wait 30 min or use `reset-password-mock` (sandbox) |
| `Response_Code` `78` on IBFT | No / invalid `otpTicket` | Run OTP send → verify with **`1234`**, then IBFT |
| `Response_Code` `51` | Insufficient funds | Use funded `0345001234567`, smaller amount; avoid zero account |
| `Response_Code` `76` | Bad / inactive IMD | Use `601004` or pick from **lookups/banks** |
| `Response_Code` `14` | Bad account / credentials | Check seed accounts & password `Sandbox@123` |
| Connection refused | Stack down | `docker compose up --build -d` |
| Empty `accessToken` after token | Tests script not executed | Open request → Tests tab; ensure 200 + JSON `access_token` |

---

## 8. Automated alternative

Without Postman:

```bash
./scripts/smoke.sh
```

Same happy path (OTP `1234`) against `http://46.224.146.158:18080`.

---

## 9. Sharing with the team

1. Share this folder: `docs/handoff/` (at least README, API guide, Postman guide, test data, collection JSON).
2. Tell engineers: **import collection → run folder `01 — Happy path` in order → OTP is `1234`**.
3. Point them to [APPINSNAP-API-GUIDE.md](./APPINSNAP-API-GUIDE.md) for request/response contracts.
