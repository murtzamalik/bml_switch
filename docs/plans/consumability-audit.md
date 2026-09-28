# Consumability Audit — Third Pass

**Date:** 2026-09-28 (rev 3)  
**Goal:** AppInSnap integrates Phase-0 happy paths with **zero verbal Q&A** from switch team.  
**Method:** Walk every Phase-0 route as a mobile consumer; lock blanks with smart defaults.

---

## 1. Blanks found (ranked)

| Rank | Blank | Impact | Resolution |
|------|-------|--------|------------|
| P0 | Auth token/refresh JSON shapes unspecified | Cannot store session | Locked in headers-and-auth + cookbook |
| P0 | `strToken` body vs header dual option | Interop bugs | Body **REQUIRED**; `X-Str-Token` alias |
| P0 | Auth vs business error HTTP semantics dual | Wrong UX branches | `error-handling.md` locked |
| P0 | Required-fields per API incomplete | Guess empty strings | `required-fields-matrix.md` |
| P0 | AccountOpenRequest field list blank | Cannot open account | Full JSON locked |
| P0 | IBFT toAccount vs toIBAN ambiguity | Failed transfers | Prefer toIBAN+toIMD |
| P0 | No second funded IFT account in seed | Cannot demo IFT | Seed `0345001234568` |
| P0 | IBFT status poll contract blank | Stuck after flat pay | 2s / 30s / terminals locked |
| P0 | OTP blank for confirm-transfer UX | Cannot wire IBFT confirm | Mock OTP MUST |
| P0 | Odd-CNIC manual review dead-end in sandbox | Cannot test review path | `approve-mock` sandbox |
| P0 | Headers catalog missing | Wrong Authorization | `headers-and-auth.md` |
| P0 | Limits/receipt SHOULD without contract | Blank APIs | Promoted MUST + samples |
| P1 | KYC image size / sample payloads | Upload failures | 2MB + tiny placeholder |
| P1 | Occupations / purpose-of-account | Open form incomplete | New lookups |
| P1 | Password change/reset | Demo user stuck | change-password + reset-mock |
| P1 | Statement/mini exact JSON | Parser guesswork | Samples locked |
| P1 | Postman file missing | Handoff incomplete | stub collection + smoke scripts |
| P2 | Real SMS OTP / bank fees | Prod later | Mock OTP; fee `0.00` |

---

## 2. Decisions locked (summary)

See also Architect table (session) and handoff docs.

| Topic | Lock |
|-------|------|
| Channel auth response | `access_token`, `token_type=Bearer`, `expires_in=900`, `refresh_token`, `refresh_expires_in=604800`, `correlationId` |
| strToken | Body field **required** on customer ops; header `X-Str-Token` optional alias |
| Auth failures | Always **HTTP 401**; code `91` session, `75` lockout |
| Business failures | Often **HTTP 200** + `Response_Code != 00` (AssanPay legacy) |
| Validation | HTTP **400** + `fieldErrors[]` |
| Amount | String `"1500.00"` (2 dp, no commas) |
| CNIC / Mobile | 13 digits; `03XXXXXXXXX` (normalize dashes/`+92`) |
| Dates | `strDate=yyyyMMdd`, `strTime=HHmmss`, `transmissionDateTime=yyyy-MM-dd HH:mm:ss.SSS`, statement `yyyy-MM-dd` |
| Idempotency | Mobile UUID on IFT/IBFT/open; Switch fills STAN if blank |
| IBFT | `toIBAN`+`toIMD` preferred; fee always `0.00`; OTP verify before pay |
| Mock OTP | Fixed `1234` when `MOCK_OTP=true` |
| Manual review | `POST /account/approve-mock` sandbox-only |
| Limits + receipt | Phase 0 **MUST** |
| TLS | Local HTTP OK; UAT/prod HTTPS |

---

## 3. New APIs added this pass

| Route | Why |
|-------|-----|
| `POST /api/v1/auth/otp/send` | Confirm-transfer UX (mock) |
| `POST /api/v1/auth/otp/verify` | Fixed OTP `1234` |
| `POST /api/v1/account/approve-mock` | Unblock odd-CNIC sandbox |
| `POST /api/v1/account/change-password` | Password lifecycle |
| `POST /api/v1/account/reset-password-mock` | Sandbox recovery |
| `GET /api/v1/lookups/occupations` | Account open form |
| `GET /api/v1/lookups/purpose-of-account` | Account open form (≠ purpose-of-payment) |

Promoted MUST: `inquiry/limits`, `inquiry/receipt`.

---

## 4. Still deferred (and why)

| Item | Why OK to defer |
|------|-----------------|
| Real SMS OTP / NADRA / Unikrew / 1LINK | Mock sufficient; flagged `MOCK_*` |
| Server beneficiaries | Mobile-local |
| Fee schedule API | Always `0.00` in mock; document in UI |
| Reversal API | Phase 2 + real iMAL |
| RAAST | Phase 2 |
| Official production IMDs | MOCK seed until bank list |
| CAPTCHA | Channel-owned UX optional later |
| Admin UI | Flyway seed only |

---

## 5. Confirmation

**Yes — with docs in `docs/handoff/` + OpenAPI stubs, AppInSnap can complete Phase-0 happy paths (auth → lookups → KYC/open → IFT → IBFT+OTP+status → statements) without verbal clarification.** Remaining "confirm with AppInSnap" product questions (e.g. UI copy) are non-blockers for API wiring.

---

## 6. Doc index (this pass)

| Doc | Purpose |
|-----|---------|
| [required-fields-matrix.md](../handoff/required-fields-matrix.md) | Required / optional / ignored per API |
| [headers-and-auth.md](../handoff/headers-and-auth.md) | Headers + token JSON |
| [error-handling.md](../handoff/error-handling.md) | HTTP vs Response_Code |
| [smoke-test-checklist.md](../handoff/smoke-test-checklist.md) | 15-min smoke |
| [smoke-curl.md](../handoff/smoke-curl.md) | cURL golden path |
| [postman-collection.stub.json](../handoff/postman-collection.stub.json) | Collection skeleton |
| [appinsnap-integration-cookbook.md](../handoff/appinsnap-integration-cookbook.md) | Updated sequences |
