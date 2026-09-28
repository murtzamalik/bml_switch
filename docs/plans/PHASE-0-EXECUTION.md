# PHASE 0 EXECUTION PLAN — FROZEN

**Status:** FROZEN — ready for implementation (next session)  
**Date:** 2026-09-28  
**OTP:** Mock SMS hardcoded **`1234`** (`MOCK_OTP=true`)  
**Primary focus:** Happy-path end-to-end sandbox for AppInSnap  
**Do not implement in this planning turn** — start Waves 0–7 on next user message.

Related: [MASTER-PLAN.md](./MASTER-PLAN.md) · [phase-0-mvp-scope.md](./phase-0-mvp-scope.md) · [E2E-READINESS.md](./E2E-READINESS.md) · [../handoff/](../handoff/)

---

## 1. Phase 0 goal

AppInSnap can `docker compose up` the Bank Al Murqarmah Integration Switch sandbox and complete the **happy path** without verbal Q&A: channel auth → lookups (banks + purposes) → onboarding/open/register/login (even CNIC) → balance → IFT (567→568) → IBFT (banks→title→**OTP 1234**→pay→status) → mini-statement / limits / receipt. Critical stuck-prevention negatives (NSF, bad IMD, auth 401, idempotency replay, odd-CNIC+approve-mock) ship with Phase 0 but are secondary to happy-flow delivery order.

---

## 2. In / Out of scope

### IN (MUST)
- All Phase-0 MUST routes (API freeze §5)
- Consumability locks (headers, required-fields, errors) — OTP **`1234`**
- Lookups `ref_*` incl. occupations, purpose-of-account, ≥16 MOCK banks
- MockImalAdapter + MySQL ledger (2 funded accounts)
- Mock KYC + approve-mock + change/reset password
- Idempotency on open/IFT/IBFT; ibft-status; limits; receipt; fee `0.00`
- Docker Compose + Flyway + OpenAPI examples + Postman + smoke checklist

### OUT
- Real iMAL, live SMS/NADRA/Unikrew, RAAST, mTLS, HA, admin UI  
- Server beneficiaries, fee schedule API, official production IMDs  
- Kafka / microservices split  

---

## 3. Happy-path E2E script (acceptance story)

| Step | Action | Expect |
|------|--------|--------|
| 1 | `docker compose up --build` | Healthy app + MySQL |
| 2 | `GET /api/v1/system/health` | `UP`, `mockImal=true` |
| 3 | `POST /auth/token` | `access_token`, `expires_in=900` |
| 4 | `GET /lookups/banks?active=true&supportsIbft=true` | ≥16 banks |
| 5 | `GET /lookups/purpose-of-payment`, `/occupations`, `/purpose-of-account` | Non-empty |
| 6 | Demo user already seeded **or** Path A: KYC→open→register | accountNumber + IBAN |
| 7 | `POST /account/login` (`ali.khan` / `Sandbox@123`) | `strToken` |
| 8 | `POST /inquiry/balance` (`0345001234567`) | capital `Return.Amount` |
| 9 | `POST /payment/ift` → `0345001234568` amount `100.00` | nested `return.status=00` |
| 10 | Pick bank IMD from lookups → `inquiry/ibft-title` | Double-nested title |
| 11 | `auth/otp/send` → `auth/otp/verify` with **`otp=1234`** | `otpTicket` |
| 12 | `payment/ibft` + otpTicket + Idempotency-Key | Flat `Response_Code=00` |
| 13 | Poll `inquiry/ibft-status` every 2s ≤30s | `POSTED` |
| 14 | `inquiry/mini-statement`, `limits`, `receipt` | Consistent ledger + fee `0.00` |

**Critical negatives (also Phase 0, lower priority in wave order):** NSF `51`; bad IMD `76`; missing strToken HTTP 401/`91`; idempotency replay; odd CNIC `79` → `approve-mock` → open.

---

## 4. Workstreams & task board (Waves 0–7)

**Total estimate: ~18 engineering days** (1 senior-equivalent). Validated by Planning/Architect.

| Wave | Focus | Owner domains | Depends | Days | DoD (wave exit) |
|------|-------|---------------|---------|------|-----------------|
| **W0** | Scaffold: Maven modules, `com.bankalmurqarmah.switchapp`, docker-compose, Flyway skeleton, health | DevOps + Spring | — | 1.5 | `compose up`; `GET /system/health` |
| **W1** | Security filter chain; `POST /auth/token|refresh|logout`; **OTP send/verify (`1234`)** | Spring Security | W0 | 2.5 | Channel JWT works; OTP returns otpTicket |
| **W2** | Lookups controllers + all `ref_*` Flyway seed (≥16 banks, purposes, occupations, …) | Spring + DB | W0, W1 channel | 2 | Banks list non-empty; ETag 304 |
| **W3** | `ImalPort` + `MockImalAdapter`; ledger seed 2 funded + zero + invalid | Spring + DB | W0 | 2 | Adapter unit tests; balances consistent |
| **W4** | KYC mocks, open/register/login, approve-mock, change/reset password; strToken on customer APIs | Spring | W1, W3 | 3 | Path A happy; odd→approve-mock |
| **W5** | Balance/titles/cnic; IFT/IBFT; idempotency; ibft-status | Spring | W2, W3, W4, W1 OTP | 3.5 | Happy IFT+IBFT+status; NSF/IMD negatives |
| **W6** | Mini/statement; limits; receipt | Spring | W5 | 2 | Empty list `[]`+00; receipt by stan |
| **W7** | OpenAPI examples, Postman fill-out, smoke checklist green, handoff sign-off | QA + Docs | W0–W6 | 1.5 | AppInSnap “no verbal blockers” |

### Task IDs by wave (map to MASTER-PLAN)

| Wave | Task IDs |
|------|----------|
| W0 | T-FND-01..03, T-DB-01 (skeleton), T-OPS-01..02, T-SYS-01 |
| W1 | T-SEC-01..05, T-AUTH-01..02, T-AUTH-04 |
| W2 | T-DB-03/04 (ref_*), T-LKP-01..06 |
| W3 | T-ADP-01..03, T-DB-02/04 (ledger) |
| W4 | T-AUTH-03, T-ONB-01..04, T-ACC-01..04 |
| W5 | T-PAY-01..04, T-INQ-01..02, T-INQ-04 |
| W6 | T-INQ-03, T-INQ-05 |
| W7 | T-QA-01..04, T-HOF-01..02, T-OPS-03 |

**Critical path:** W0 → W1 → W2 → W3 → W4 → W5 → W6 → W7  
(W2 Lookups before W5 IBFT; W1 OTP before W5 IBFT pay.)

---

## 5. API freeze list (Phase 0 contract)

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/api/v1/auth/token` | Public | Locked token JSON |
| POST | `/api/v1/auth/refresh` | Public | Rotate refresh |
| POST | `/api/v1/auth/logout` | Channel (±strToken) | Optional customer revoke |
| POST | `/api/v1/auth/otp/send` | Channel + strToken | Mock |
| POST | `/api/v1/auth/otp/verify` | Channel + strToken | **otp=`1234`** → otpTicket |
| POST | `/api/v1/account/login` | Channel | Issues strToken |
| POST | `/api/v1/account/register` | Channel | Bind credentials |
| POST | `/api/v1/account/customer-detail` | Channel + strToken | |
| POST | `/api/v1/account/account-list` | Channel + strToken | IBANs |
| POST | `/api/v1/account/open` | Channel + strToken + Idempotency-Key | account#+IBAN |
| POST | `/api/v1/account/upload-documents-unikrew` | Channel + strToken | ≤2MB images |
| POST | `/api/v1/account/validate-document-unikrew` | Channel + strToken | |
| POST | `/api/v1/account/verify-liveliness` | Channel + strToken | |
| POST | `/api/v1/account/verify-fingers` | Channel + strToken | |
| POST | `/api/v1/account/approve-mock` | Channel | Sandbox only |
| POST | `/api/v1/account/change-password` | Channel + strToken | |
| POST | `/api/v1/account/reset-password-mock` | Channel | Sandbox only |
| POST | `/api/v1/accounts/information` | Channel + strToken | |
| POST | `/api/v1/payment/ift` | Channel + strToken + Idempotency-Key | Nested return |
| POST | `/api/v1/payment/ibft` | Channel + strToken + Idempotency-Key + **otpTicket** | Flat response |
| POST | `/api/v1/inquiry/balance` | Channel + strToken | Capital `Return` |
| POST | `/api/v1/inquiry/ift-title` | Channel + strToken | |
| POST | `/api/v1/inquiry/ibft-title` | Channel + strToken | Double-nested |
| POST | `/api/v1/inquiry/cnic` | Channel + strToken | |
| POST | `/api/v1/inquiry/ibft-status` | Channel + strToken | Poll 2s/30s |
| POST | `/api/v1/inquiry/mini-statement` | Channel + strToken | |
| POST | `/api/v1/inquiry/statement` | Channel + strToken | max 90d |
| POST | `/api/v1/inquiry/limits` | Channel + strToken | MUST |
| POST | `/api/v1/inquiry/receipt` | Channel + strToken | MUST |
| GET | `/api/v1/lookups/banks` | Channel | |
| GET | `/api/v1/lookups/banks/{imd}` | Channel | |
| GET | `/api/v1/lookups/purpose-of-payment` | Channel | |
| GET | `/api/v1/lookups/purpose-of-account` | Channel | |
| GET | `/api/v1/lookups/occupations` | Channel | |
| GET | `/api/v1/lookups/response-codes` | Channel | |
| GET | `/api/v1/lookups/account-types` | Channel | |
| GET | `/api/v1/lookups/provinces` | Channel | |
| GET | `/api/v1/lookups/id-types` | Channel | |
| GET | `/api/v1/lookups/app-config` | Channel | Public keys |
| GET | `/api/v1/lookups/finger-indexes` | Channel | |
| GET | `/api/v1/lookups/onboarding-steps` | Channel | |
| GET | `/api/v1/lookups/branches` | Channel | Optional seed |
| GET | `/api/v1/lookups/currencies` | Channel | PKR |
| GET | `/api/v1/lookups/version` | Channel | |
| GET | `/api/v1/system/health` | Public lite OK | |
| GET | `/api/v1/system/version` | Channel | |

Contract docs: `docs/handoff/headers-and-auth.md`, `required-fields-matrix.md`, `error-handling.md`, `openapi/assanpay-switch-v1.yaml`.

---

## 6. Data freeze (seed)

| Item | Value |
|------|-------|
| client_id / secret | `appinsnap-sandbox` / `change_me_sandbox_secret` |
| User | `ali.khan` / `Sandbox@123` |
| CNIC even (auto KYC) | `4210112345678` |
| CNIC odd (approve-mock) | `4210112345679` |
| Funded #1 | `0345001234567` (~150000.00 PKR) |
| Funded #2 (IFT to) | `0345001234568` |
| Zero / invalid | `0345009999999` / `0345000000000` |
| Mock OTP | **`1234`** |
| Fee | `0.00` |
| Banks | ≥16 Pakistan MOCK IMDs in `ref_banks` |
| Own bank IMD | from `BANK_IMD` env |

---

## 7. Definition of Done (Phase 0 gate)

- [x] Waves 0–7 complete; `docker compose up` healthy  
- [x] Happy-path E2E script (§3) green  
- [x] OTP **`1234`** → otpTicket; IBFT without ticket → `78`  
- [x] Lookups banks ≥16; zero hardcoded IMDs on mobile  
- [x] Dual auth; strToken body required; 401/`91` on miss  
- [x] IFT 567→568; IBFT status `POSTED` ≤30s  
- [x] Idempotency replay; fee `0.00`; limits + receipt  
- [x] AssanPay shape fixtures (IFT nested, IBFT flat, title double-nest, Balance `Return`)  
- [x] Odd CNIC + approve-mock path works  
- [x] NSF / bad IMD / auth fail covered  
- [x] OpenAPI examples + Postman + smoke checklist signed by AppInSnap liaison  
- [x] No secrets in git; `.env.example` only  

---

## 8. Risks & mitigations (Phase 0 implementation)

| Risk | Mitigation |
|------|------------|
| AssanPay shape drift | Golden fixtures in W5/W7; fail CI on nest/casing |
| Auth ACL mistakes on money APIs | Method security tests; route matrix in freeze |
| OTP/docs mismatch | Single lock `1234` in consumability-locked-decisions |
| Scope creep (real iMAL, fees) | OUT list; reject in review |
| Seed/IMD inconsistency | Flyway-only seed; IBFT validates `ref_banks` |
| Handoff half-cooked | W7 is exit gate — no “code done, docs later” |

---

## 9. Kickoff brief — start implementation next turn

### First 5 modules/files to create
1. Root `pom.xml` (+ module poms if multi-module)  
2. `SwitchApplication.java` under `com.bankalmurqarmah.switchapp`  
3. `docker-compose.yml` + `Dockerfile` + `.env.example`  
4. `shared/security/` (JWT filter stubs)  
5. `adapter/imal/ImalPort.java` + empty `MockImalAdapter`  

### First Flyway migrations
1. `V001__api_clients_refresh_tokens.sql`  
2. `V002__customers_accounts_ledger_balances.sql`  
3. `V003__ref_lookups_all.sql` (`ref_banks`, purposes, occupations, …)  
4. `V004__transactions_idempotency_outbox_audit.sql`  
5. `V005__seed_client_banks_ledger_demo.sql` (OTP not stored — constant in code/config)  

### First 3 APIs to implement
1. `GET /api/v1/system/health`  
2. `POST /api/v1/auth/token`  
3. `GET /api/v1/lookups/banks`  

Then proceed Wave order W1→W7 without reopening freeze unless AppInSnap contract break is proven.

---

## 10. Explicit next step

**This document freezes Phase 0.**  
**Implementation starts on the next user message** — do not write Spring/Flyway/Docker application code until then.
