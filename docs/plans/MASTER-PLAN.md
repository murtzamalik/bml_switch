# MASTER PLAN — `bml_switch` Integration Switch

**Bank:** Bank Al Murqarmah (Pakistan)  
**Product:** Integration Switch between AppInSnap and iMAL  
**Date:** 2026-09-28 (rev 4a — **Phase 0 IMPLEMENTED**)  
**Status:** **Phase 0 COMPLETE** — [PHASE-0-IMPLEMENTATION-NOTES.md](./PHASE-0-IMPLEMENTATION-NOTES.md)  
**Execution plan:** [PHASE-0-EXECUTION.md](./PHASE-0-EXECUTION.md)  
**E2E readiness:** [E2E-READINESS.md](./E2E-READINESS.md) — **YES**  
**Mock OTP:** **`1234`** (`MOCK_OTP=true`)  
**Next step:** Phase 1+ (real iMAL) when scheduled; smoke: `./scripts/smoke.sh`  
**Clarification rounds used:** 0 / 2 (smart defaults applied)

---

## 0. Gaps found & remediations

| Rev | Focus | Doc |
|-----|-------|-----|
| 2 | Lookups / master data / IBFT status | [gap-analysis.md](./gap-analysis.md) |
| 3 | **API consumability blanks** (auth JSON, required fields, OTP, account-open, errors) | [consumability-audit.md](./consumability-audit.md) |

**DoD:** AppInSnap wires Phase-0 happy paths from handoff docs **without verbal Q&A**.

| Gap (rev 3) | Remediation |
|-------------|-------------|
| Auth/token JSON blank | Locked response in headers-and-auth |
| strToken dual option | Body REQUIRED; X-Str-Token alias |
| AccountOpenRequest blank | Full JSON in required-fields-matrix |
| IBFT confirm OTP blank | Mock OTP MUST (**`1234`**) |
| Odd CNIC dead-end | `approve-mock` sandbox |
| Limits/receipt SHOULD blank | Promoted MUST |
| Occupations / purpose-of-account | New lookups |
| Password recovery blank | change-password + reset-password-mock |
| Second IFT account missing | Seed `0345001234568` |
| Postman missing | `postman-collection.stub.json` + smoke scripts |
---

## 1. Executive summary

Build a **modular monolith** Spring Boot 3.x service that exposes **AssanPay-compatible JSON** under `/api/v1/**` to AppInSnap, plus **lookup/master-data APIs** so mobile can complete flows without guessing. Persist mock ledgers + `ref_*` tables in **MySQL**. Core banking via **`ImalPort`** / **`MockImalAdapter`** (`MOCK_IMAL=true`).

**Critical path (Phase 0, rev 2):**  
`Foundations → Flyway (incl. ref_*) → Security → Auth → Lookups → Mock adapter → Titles → IFT → IBFT (after banks) → IBFT status → Contract QA + Handoff`

---

## 2. Assumptions (documented)

| ID | Assumption |
|----|------------|
| A-01 | Bank name: **Bank Al Murqarmah** |
| A-02 | Currency: **PKR** only in MVP |
| A-03 | Modular monolith; **in-process** MockImalAdapter |
| A-04 | AssanPay body shapes preserved; routes under `/api/v1/` |
| A-05 | IBFT = mock 1LINK behind iMAL façade |
| A-06 | Account open = Asaan Digital–style CURRENT; response includes accountNumber + IBAN |
| A-07 | Placeholder limits: max balance 1,000,000 PKR; daily debit 200,000 PKR |
| A-08 | IBAN mock bank code `BMAL`; IMD placeholder configurable |
| A-09 | Mini-statement last 10; statement max range 90 days |
| A-10 | Dual auth: channel client_credentials JWT + customer `strToken` |
| A-11 | Unikrew even/odd CNIC mock parity from AssanPay QA doc |
| A-12 | NADRA / Unikrew / Liveliness always mocked in MVP |
| A-13 | RAAST out of MVP |
| A-14 | Preserve AssanPay typos as wire aliases |
| A-15 | Timezone `Asia/Karachi` |
| A-16 | Real iMAL adapter ~**15–22 days** after docs (unknown risk buffer +5–10) |
| A-17 | No Kafka in MVP — transactional outbox + in-process publisher |
| A-18 | SanctionsPort stub ALLOW |
| A-19 | **Lookups require channel JWT only** (no anonymous; no strToken) |
| A-20 | Bank IMD list seeded as **MOCK** until bank provides official 1LINK members |
| A-21 | Beneficiaries = **mobile-local** in MVP |
| A-22 | **Mock OTP** for IBFT (`MOCK_OTP=true`, fixed **`1234`**) — real SMS later |
| A-23 | Fee always `0.00` in mock (no fee API) |
| A-24 | strToken in **body required**; `X-Str-Token` alias |
| A-25 | Amount string 2 dp; CNIC 13; Mobile `03…`; dates as cookbook |
| A-26 | Mobile generates Idempotency-Key; Switch generates STAN if blank |
| A-27 | Limits + receipt = Phase 0 **MUST** |
| A-28 | Seed second funded account `0345001234568` for IFT |
| A-29 | Sandbox `approve-mock` + `reset-password-mock` only when mock/local profile |
---

## 3. Suggested package structure

```
com.bankalmurqarmah.switchapp
├── SwitchApplication
├── shared.{kernel,api,security,persistence}
├── identity | onboarding | accounts | payments | inquiry | statements
├── lookups                    # NEW — ref_* master data, read APIs
├── audit
└── adapter.imal.{port,mock,real,mapping}
```

Maven modules (optional): `bootstrap`, `api`, `application`, `domain`, `infrastructure`.

---

## 4. API route list (canonical)

### Auth / Account / Payment / Inquiry (AssanPay + additive)
```
POST /api/v1/auth/token|refresh|logout
POST /api/v1/auth/otp/send|verify          # Mock OTP MUST for IBFT
POST /api/v1/account/login|register|customer-detail|account-list|open
POST /api/v1/account/upload-documents-unikrew|validate-document-unikrew|verify-liveliness|verify-fingers
POST /api/v1/account/approve-mock          # sandbox odd-CNIC
POST /api/v1/account/change-password
POST /api/v1/account/reset-password-mock   # sandbox
POST /api/v1/payment/ift|ibft
POST /api/v1/inquiry/balance|ift-title|ibft-title|cnic|mini-statement|statement
POST /api/v1/inquiry/ibft-status           # MUST
POST /api/v1/inquiry/limits|receipt        # MUST (promoted)
POST /api/v1/accounts/information
GET  /api/v1/lookups/banks|banks/{imd}|purpose-of-payment|purpose-of-account|occupations|...
GET  /api/v1/system/health|version
```

### Lookups (Phase 0 CRITICAL — AppInSnap-facing master data)
```
GET  /api/v1/lookups/banks
GET  /api/v1/lookups/banks/{imd}
GET  /api/v1/lookups/purpose-of-payment
GET  /api/v1/lookups/purpose-of-account
GET  /api/v1/lookups/occupations
GET  /api/v1/lookups/response-codes
GET  /api/v1/lookups/account-types
GET  /api/v1/lookups/provinces
GET  /api/v1/lookups/id-types
GET  /api/v1/lookups/app-config
GET  /api/v1/lookups/finger-indexes
GET  /api/v1/lookups/onboarding-steps
GET  /api/v1/lookups/branches
GET  /api/v1/lookups/currencies
GET  /api/v1/lookups/version
```

### System (AppInSnap smoke)
```
GET  /api/v1/system/health
GET  /api/v1/system/version
```

Field ownership: [../architecture/03-appinsnap-facing-api-contract.md](../architecture/03-appinsnap-facing-api-contract.md)  
Lookups detail: [../architecture/04-lookups-master-data.md](../architecture/04-lookups-master-data.md)  
Client sequences: [../handoff/appinsnap-integration-cookbook.md](../handoff/appinsnap-integration-cookbook.md)

---

## 5. DB ER summary

### Core
| Table | Purpose |
|-------|---------|
| `api_clients` | Channel client_id + BCrypt secret hash, scopes, status |
| `refresh_tokens` | JWT refresh family, rotation, revoke |
| `customers` | CNIC hash/encrypted, profile, `str_token_hash`, status |
| `accounts` | Account number, IBAN, title, type, currency, status, product |
| `ledger_balances` | available/ledger `DECIMAL(19,4)`, currency, as_of |
| `transactions` | IFT/IBFT posts, STAN, status INITIATED/POSTED/FAILED/REVERSED |
| `statement_entries` | Statement projection / mini + range reads |
| `onboarding_applications` | KYC saga steps, step_id/status_id |
| `idempotency_keys` | client + key → cached response |
| `outbox_events` | Dual-write events NEW/PUBLISHED/FAILED |
| `audit_log` | Append-only security/business audit |

### Reference / Lookups (replaces lone `mock_banks`)
| Table | Purpose |
|-------|---------|
| `ref_banks` | IBFT banks (IMD, names, ibanBankCode, supportsIbft, active) |
| `ref_purpose_codes` | PurposeOfPayment |
| `ref_response_codes` | UX-mappable business codes |
| `ref_account_types` | CURRENT / ASAAN_DIGITAL / … |
| `ref_provinces` | Onboarding / NADRA area |
| `ref_id_types` | Title-fetch id_type |
| `ref_app_config` | Public mobile config keys |
| `ref_finger_indexes` | NADRA finger indexes |
| `ref_onboarding_steps` | KYC step catalog |
| `ref_occupations` | Account open occupation codes |
| `ref_purpose_of_account` | Account open purpose (≠ payment purpose) |
| `ref_branches` | Optional mock branches |
| `ref_currencies` | PKR (+ future) |
| `otp_challenges` | Mock OTP references / tickets (sandbox) |
**Conventions:** UUID `CHAR(36)` PKs; soft delete where applicable; Flyway sequential migrations; money never float; catalog version for ETag.

---

## 6. Config / env catalog (high level)

| Area | Keys |
|------|------|
| Runtime | `SPRING_PROFILES_ACTIVE`, `SERVER_PORT`, `MOCK_IMAL` |
| DB | `MYSQL_*`, `SPRING_DATASOURCE_*`, `FLYWAY_ENABLED` |
| JWT | `JWT_SECRET` or RS256 keys, `JWT_ACCESS_TTL_SECONDS`, `JWT_REFRESH_TTL_SECONDS` |
| Switch envelope | `SWITCH_APP_ID`, `SWITCH_ADC_TRAN_CODE_*`, `SWITCH_DELIMITER`, `SWITCH_NET_ID`, `SWITCH_MERCHANT_NAME`, `SWITCH_MCC`, `SWITCH_CHANNEL_ID` |
| Bank | `BANK_AGENT_ACCOUNT`, `BANK_BRANCH_DEFAULT`, `BANK_IMD`, `BANK_IBAN_CODE` |
| iMAL | `IMAL_BASE_URL`, `IMAL_CLIENT_ID`, `IMAL_CLIENT_SECRET`, timeouts |
| Vendors | `UNIKREW_*`, `LIVELINESS_*`, `NADRA_VERIFY_URL` |
| Limits | `ASAAN_MAX_BALANCE`, `ASAAN_DAILY_DEBIT_LIMIT` |
| Lookups cache | `LOOKUPS_CACHE_MAX_AGE_SECONDS` (default 300) |
| Resilience | `R4J_CB_*` |
| Actuator | health/info/prometheus in uat; **env disabled** |

Full `.env.example` guidance: [../devops/docker-runtime.md](../devops/docker-runtime.md).

---

## 7. How real iMAL swap works

1. Implement `RealImalAdapter` (WebClient, TLS 1.2+, Resilience4j).  
2. Map domain commands ↔ vendor payloads.  
3. `MOCK_IMAL=false`; secrets from Vault/env.  
4. **Lookups stay Switch-owned** (bank may replace MOCK IMDs via Flyway when official 1LINK list arrives).  
5. **No AppInSnap AssanPay body break.**  

Effort: **~15–22 days** (+5–10 if SOAP/severe divergence). Live NADRA/Unikrew separate.

---

## 8. Questions for bank / iMAL / AppInSnap

1. Official 1LINK member bank list + real IMDs / IBAN bank codes?  
2. REST vs SOAP; STAN rules; sync vs async IBFT; status inquiry SLA?  
3. Purpose-of-payment official code list?  
4. Reversal / fee APIs?  
5. Asaan limits & KYC mandatory steps?  
6. AppInSnap: OTP/MPIN product need for Phase 1?  
7. AppInSnap: confirm lookups caching + bank picker from Switch only?  
8. mTLS / SIEM / log retention for prod?

---

## 9. Phase 0 task breakdown

See [phase-0-mvp-scope.md](./phase-0-mvp-scope.md).

### Foundations / Security / Database
| ID | Title | Owner | Depends | DoD |
|----|-------|-------|---------|-----|
| T-FND-01 | Maven skeleton + package root (incl. `lookups`) | Backend | — | App boots; OpenAPI stub |
| T-FND-02 | Shared AssanPay DTOs + correlationId filter | Backend | T-FND-01 | Errors carry correlationId |
| T-FND-03 | Hexagonal `ImalPort` skeleton | Architect/Backend | T-FND-01 | No domain→infra leaks |
| T-SEC-01 | Dual auth filter chain | Security | T-FND-01, T-DB-01 | 401/403 correct |
| T-SEC-02 | Deny-by-default ACLs (lookups = channel JWT) | Security | T-SEC-01 | Lookups need channel only |
| T-SEC-03 | Security headers; secrets from env | Security | T-FND-01 | No hardcoded secrets |
| T-SEC-04 | Audit writer | Security | T-DB-01 | Money/auth audited |
| T-SEC-05 | Soft rate limits (auth/pay/lookups) | Security | T-SEC-01 | 429 on burst |
| T-DB-01 | Flyway: core + **all `ref_*` tables** | Database | T-FND-01 | Migrate clean |
| T-DB-02 | JPA: clients, tokens, customers, accounts, balances | Database | T-DB-01 | CRUD smoke |
| T-DB-03 | JPA: txns, statements, onboarding, idempotency, outbox, audit, **ref_*** | Database | T-DB-01 | Constraints OK |
| T-DB-04 | Seed api_client + **Pakistan MOCK banks** + purpose/provinces/… + demo ledger | Database | T-DB-01 | Lookups return ≥16 banks |

### Lookups (CRITICAL — before IBFT pay)
| ID | Title | Owner | Depends | DoD |
|----|-------|-------|---------|-----|
| T-LKP-01 | Lookups controllers + DTOs + ETag/Cache-Control | Backend | T-DB-03, T-SEC-02 | 304 works |
| T-LKP-02 | `GET .../banks` + `GET .../banks/{imd}` pagination/filters | Backend | T-LKP-01, T-DB-04 | IBFT picker data complete |
| T-LKP-03 | purpose / response-codes / account-types / provinces / id-types | Backend | T-LKP-01, T-DB-04 | Non-empty active lists |
| T-LKP-04 | app-config + finger-indexes + onboarding-steps + **occupations** + **purpose-of-account** | Backend | T-LKP-01, T-DB-04 | No secrets leaked |
| T-LKP-05 | branches + currencies + `/lookups/version` | Backend | T-LKP-01, T-DB-04 | version matches seed |
| T-LKP-06 | Contract tests: banks picker DoD | QA | T-LKP-02 | AC green |

### Auth / Onboarding / Accounts / Adapter / Payments / Inquiry
| ID | Title | Owner | Depends | DoD |
|----|-------|-------|---------|-----|
| T-AUTH-01 | `/auth/token` (locked JSON) | Backend | T-SEC-01, T-DB-02 | JWT issued |
| T-AUTH-02 | refresh + logout | Backend | T-AUTH-01 | Rotation works |
| T-AUTH-03 | Login → strToken (body required) | Backend | T-AUTH-01, T-ACC-01 | Dual-token E2E |
| T-AUTH-04 | Mock OTP send/verify (`MOCK_OTP`, `1234`) | Backend | T-AUTH-03 | otpTicket for IBFT |
| T-ONB-01 | register + onboarding_applications | Backend | T-DB-03, T-SEC-01 | AssanPay shape |
| T-ONB-02 | Unikrew upload + validate | Backend | T-ONB-01, T-ADP-01 | Even/odd parity |
| T-ONB-03 | liveliness + fingers | Backend | T-ONB-02 | Codes 100/122/… |
| T-ONB-04 | customer-detail | Backend | T-ONB-03 | Profile DTO |
| T-ACC-01 | account/login | Backend | T-DB-02, T-SEC-01 | strToken |
| T-ACC-02 | account-list + accounts/information (IBANs) | Backend | T-ACC-01, T-ADP-01 | ≥2 funded accounts in seed |
| T-ACC-03 | account/open full JSON → accountNumber + IBAN | Backend | T-ACC-01, T-DB-02 | Idempotent open |
| T-ACC-04 | approve-mock + change-password + reset-password-mock | Backend | T-ONB-02, T-SEC-02 | Odd CNIC sandbox |
| T-ADP-01 | MockImalAdapter + MOCK_IMAL | Backend | T-FND-03, T-DB-04 | All ops via mock |
| T-ADP-02 | Resilience4j on ImalPort | Backend | T-ADP-01 | CB mapped errors |
| T-ADP-03 | Mock failure profiles | Backend | T-ADP-01 | Documented |
| T-PAY-01 | Idempotency middleware | Backend | T-DB-03 | Replay cached |
| T-PAY-02 | payment/ift + outbox | Backend | T-PAY-01, T-ADP-01 | Ledger consistent |
| T-PAY-03 | payment/ibft (IMD + **otpTicket**) | Backend | T-PAY-02, T-LKP-02, T-AUTH-04 | Missing OTP → 78 |
| T-PAY-04 | Outbox publisher | Backend | T-PAY-02 | Events drain |
| T-INQ-01 | balance + cnic | Backend | T-ACC-02, T-ADP-01 | Authz + shapes |
| T-INQ-02 | ift-title + ibft-title | Backend | T-ADP-01, T-LKP-02 | Nesting correct |
| T-INQ-03 | mini-statement + statement (samples locked) | Backend | T-PAY-02, T-DB-03 | Empty = [] + 00 |
| T-INQ-04 | ibft-status (poll 2s/30s) | Backend | T-PAY-03, T-ADP-01 | Terminals POSTED/FAILED |
| T-INQ-05 | **limits + receipt MUST** | Backend | T-PAY-02, T-ACC-02 | Full JSON |
| T-SYS-01 | `/system/health` + `/system/version` | Backend | T-FND-01 | AppInSnap smoke |

### DevOps / QA / Handoff
| ID | Title | Owner | Depends | DoD |
|----|-------|-------|---------|-----|
| T-OPS-01 | Docker Compose app + MySQL | DevOps | T-DB-01, T-FND-01 | Healthy locally |
| T-OPS-02 | `.env.example` (+ MOCK_OTP) + Actuator | DevOps | T-OPS-01 | Secrets not committed |
| T-OPS-03 | CI build + migrate + lookup seed smoke | DevOps | T-OPS-01, T-LKP-06 | Pipeline green |
| T-QA-01 | Contract tests all Phase-0 routes | QA | routes ready | Fixtures pass |
| T-QA-02 | Dual-auth + IFT/IBFT+OTP+status | QA | T-PAY-03, T-INQ-04, T-AUTH-04 | Replay/401 cases |
| T-QA-03 | Onboarding E2E Path A + odd approve-mock | QA | T-ONB-04, T-ACC-03, T-ACC-04 | Full path green |
| T-QA-04 | Phase-0 exit + consumability smoke checklist | QA | T-QA-01..03, T-LKP-06, T-HOF-02 | Sign-off |
| T-HOF-01 | Postman from stub + OpenAPI examples | Backend/QA | T-QA-01 | Importable |
| T-HOF-02 | Smoke checklist + cookbook consumability | Docs/QA | T-HOF-01 | AppInSnap sign-off |

**Phase 0 critical path (rev 3):**  
`T-FND-01 → T-DB-01 → T-DB-04 → T-SEC-01 → T-AUTH-01 → T-LKP-01 → T-LKP-02 → T-ADP-01 → T-AUTH-04 → T-PAY-02 → T-PAY-03 → T-INQ-04 → T-INQ-05 → T-QA-02 → T-HOF-01`

---

## 10. Later phases

### Phase 1 — Hardening
Real SMS OTP / MPIN product; rate-limit tuning; chaos on outbox; PII scrub; OpenAPI freeze; optional separate mock-imal container; official IMD migration when bank provides list.

### Phase 2 — Real iMAL (~15–22d)
`RealImalAdapter`, code mapping, staging soak, compensations/reversals, live vendor hooks.

### Phase 3 — Production
Secrets, TLS/mTLS, backups, observability, cutover/rollback, live KYC, admin ref-data refresh if needed.

---

## 11. Risks

1. AssanPay shape drift → contract tests gate merges.  
2. Dual-auth mis-ACL on money APIs.  
3. Mock→real semantic gaps → schedule slip.  
4. Idempotency/outbox dual-write bugs.  
5. MOCK IMDs diverge from real 1LINK list → migration when bank provides.  
6. Half-cooked handoff if T-HOF-* skipped — **treat as Phase 0 exit gate**.  
7. Official iMAL unknown — adapter isolation mitigates.

---

## 12. Definition of Done (program)

- [x] All Phase-0 routes implemented (AssanPay + lookups + ibft-status + system health)  
- [x] **AppInSnap IBFT bank picker works from `/lookups/banks` with zero hardcoded banks**  
- [x] AssanPay golden fixtures green (IFT nested, IBFT flat, IBFT title double-nest, Balance `Return`)  
- [x] Account open returns accountNumber + IBAN  
- [x] Field injection verified  
- [x] Idempotent IFT/IBFT; status poll works; ledger consistent with statements  
- [x] Audit + correlationId; secrets absent  
- [x] `docker compose up` healthy; system/health OK  
- [x] OpenAPI + Postman + cookbook handed to AppInSnap  
- [x] Phase-0 QA exit checklist signed  

---

## 13. Recommended next implementation order

1. Scaffold + Flyway (**ref_*** + core) + Docker  
2. Security (channel JWT) + OpenAPI stubs  
3. **Lookups APIs + bank seed** (before IBFT UI dependency)  
4. MockImalAdapter + demo ledger  
5. Login / register / KYC / account open (accountNumber+IBAN)  
6. Balance + titles  
7. IFT → IBFT (IMD validation) → **ibft-status**  
8. Statements + limits/receipt (SHOULD)  
9. Contract tests + Postman + cookbook handoff  

---

## 14. Doc index

| Doc | Path |
|------|------|
| Gap analysis | [./gap-analysis.md](./gap-analysis.md) |
| **Phase 0 EXECUTION (FROZEN)** | [./PHASE-0-EXECUTION.md](./PHASE-0-EXECUTION.md) |
| E2E readiness | [./E2E-READINESS.md](./E2E-READINESS.md) |
| Consumability audit | [./consumability-audit.md](./consumability-audit.md) |
| System overview | [../architecture/00-system-overview.md](../architecture/00-system-overview.md) |
| Bounded contexts | [../architecture/01-bounded-contexts.md](../architecture/01-bounded-contexts.md) |
| Mock iMAL | [../architecture/02-imal-mock-api-contract.md](../architecture/02-imal-mock-api-contract.md) |
| AppInSnap contract | [../architecture/03-appinsnap-facing-api-contract.md](../architecture/03-appinsnap-facing-api-contract.md) |
| Lookups master data | [../architecture/04-lookups-master-data.md](../architecture/04-lookups-master-data.md) |
| Phase-0 MVP | [./phase-0-mvp-scope.md](./phase-0-mvp-scope.md) |
| Enriched requirements | [./00-enriched-requirements.md](./00-enriched-requirements.md) |
| Integration cookbook | [../handoff/appinsnap-integration-cookbook.md](../handoff/appinsnap-integration-cookbook.md) |
| Required fields | [../handoff/required-fields-matrix.md](../handoff/required-fields-matrix.md) |
| Headers & auth | [../handoff/headers-and-auth.md](../handoff/headers-and-auth.md) |
| Error handling | [../handoff/error-handling.md](../handoff/error-handling.md) |
| Smoke checklist | [../handoff/smoke-test-checklist.md](../handoff/smoke-test-checklist.md) |
| Security | [../security/security-design.md](../security/security-design.md) |
| DevOps | [../devops/docker-runtime.md](../devops/docker-runtime.md) |
| OpenAPI stub | [../openapi/assanpay-switch-v1.yaml](../openapi/assanpay-switch-v1.yaml) |
