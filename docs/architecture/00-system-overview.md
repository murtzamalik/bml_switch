# System Overview — Bank Al Murqarmah Integration Switch (`bml_switch`)

**Status:** Binding architecture (plan phase)  
**Date:** 2026-09-28  
**Stack:** Spring Boot 3.x modular monolith · MySQL 8 · Docker Compose · OpenAPI 3.0 · JWT

---

## 1. Four parties

| Party | Role |
|-------|------|
| **AppInSnap** | Builds the Bank Al Murqarmah mobile banking app; sole consumer of Switch public REST APIs |
| **bml_switch (THIS PROJECT)** | Integration Switch / middle layer: AssanPay-compatible façade, auth, audit, idempotency, orchestration |
| **iMAL (Azentio / Path Solutions)** | Islamic core banking (accounts, balances, transfers, statements). Official APIs **not yet available** → Mock first |
| **Bank Al Murqarmah** | Bank / compliance / config owner (channel codes, IMD, limits, secrets). Not a runtime caller in MVP |

```
┌─────────────┐     HTTPS + Channel JWT + strToken      ┌──────────────────────────────┐
│  AppInSnap  │ ───────────────────────────────────────►│  bml_switch (Spring Boot)    │
│  Mobile App │   /api/v1/** AssanPay + Lookups         │  Auth · Lookups · Onboarding │
└─────────────┘                                         │  Payments · Inquiry · Audit  │
                                                        │           │                  │
                                                        │      ImalPort (hexagonal)    │
                                                        │           │                  │
                                                        │    MockImalAdapter (MVP)     │
                                                        │    RealImalAdapter (later)   │
                                                        └───────────┬──────────────────┘
                                                                    │ JDBC
                                                                    ▼
                                                              ┌──────────┐
                                                              │  MySQL   │
                                                              │ core+ref_*│
                                                              └──────────┘
```

**Topology decision:** One Spring Boot service + MySQL. Mock iMAL is **in-process** (`MockImalAdapter`) behind `ImalPort`. **Lookups/master data** are first-class AppInSnap APIs (not internal-only). Optional separate `mock-imal` container is deferred.

---

## 2. Design intent

`Mobile App (AppInSnap) → Integration Switch (THIS) → iMAL Core Banking`

AppInSnap previously integrated with a switch that exposed **AssanPay Third-Party API** JSON shapes. We preserve those request/response bodies under `/api/v1/...` so AppInSnap can integrate **now**. When real iMAL docs arrive, only the adapter implementation changes.

---

## 3. Sequence — Digital onboarding (happy path)

```mermaid
sequenceDiagram
  participant M as AppInSnap
  participant S as bml_switch
  participant U as UnikrewPort (mock)
  participant L as LivelinessPort (mock)
  participant N as NadraPort (mock)
  participant I as ImalPort (mock)
  participant DB as MySQL

  M->>S: POST /api/v1/auth/token (client_credentials)
  S-->>M: channel JWT
  M->>S: GET /api/v1/lookups/onboarding-steps|provinces|finger-indexes|account-types
  S->>DB: ref_* reads
  S-->>M: catalogs (ETag)
  M->>S: Unikrew validate → upload → liveliness → fingers
  S->>U: OCR + face compare (even/odd CNIC rule)
  S->>L: mock score
  S->>N: mock Status_Code 100
  M->>S: POST /api/v1/account/open
  S->>I: open Asaan Digital account
  S->>DB: accounts + ledger_balances + outbox
  S-->>M: accountNumber + IBAN (required)
  M->>S: POST /api/v1/account/register (bind credentials)
  M->>S: POST /api/v1/account/login
  S-->>M: strToken + Response_Code 00
```

**Path A (new Asaan):** KYC → **open** → **register** → login. **Path B (existing account):** register → login (skip open).  
**Saga states:** `STARTED → DOCS_UPLOADED → CNIC_VALIDATED → LIVELINESS_OK → BIOMETRIC_OK → ACCOUNT_OPENED → REGISTERED | MANUAL_REVIEW | REJECTED`

---

## 4. Sequence — IFT (intra-bank transfer)

```mermaid
sequenceDiagram
  participant M as AppInSnap
  participant S as bml_switch
  participant I as ImalPort
  participant DB as MySQL

  M->>S: POST /api/v1/inquiry/ift-title
  S->>I: iftTitleFetch
  S-->>M: return.accountTitle (nested)
  M->>S: POST /api/v1/payment/ift (Idempotency-Key)
  S->>DB: idempotency check + txn INITIATED + outbox (same TX)
  S->>I: iftPayment (no blind retry)
  alt Response_Code 00
    S->>DB: POSTED + ledger debit/credit + statement_entries
  else business fail
    S->>DB: FAILED (no ledger move)
  end
  S-->>M: IftPaymentResponseEntity (nested return)
```

**Status machine:** `INITIATED → POSTED | FAILED`; `POSTED → REVERSED` (compensating only).

---

## 5. Sequence — IBFT (interbank via mock 1LINK façade)

**Mandatory client sequence:** load banks → title → pay → status.

```mermaid
sequenceDiagram
  participant M as AppInSnap
  participant S as bml_switch
  participant I as ImalPort (mock 1LINK)
  participant DB as MySQL

  M->>S: GET /api/v1/lookups/banks?active=true&supportsIbft=true
  S->>DB: ref_banks
  S-->>M: bank picker (imd, names) + ETag
  M->>S: GET /api/v1/lookups/purpose-of-payment
  S-->>M: purpose codes
  M->>S: POST /api/v1/inquiry/ibft-title (toIBAN + toIMD)
  S->>I: ibftTitleFetch
  S-->>M: IBFTTitleFetchResponse.return (double-nested)
  M->>S: POST /api/v1/auth/otp/send → verify (otp=1234)
  S-->>M: otpTicket
  M->>S: POST /api/v1/payment/ibft (otpTicket + Idempotency-Key)
  S->>DB: validate toIMD; INITIATED
  S->>I: ibftPayment
  S-->>M: flat Response_Code / Response_Desc
  M->>S: POST /api/v1/inquiry/ibft-status (poll 2s ≤30s)
  S-->>M: POSTED / FAILED
```

---

## 6. Sequence — Statement

```mermaid
sequenceDiagram
  participant M as AppInSnap
  participant S as bml_switch
  participant I as ImalPort
  participant DB as MySQL

  M->>S: POST /api/v1/inquiry/mini-statement
  S->>DB: last N=10 statement_entries
  S-->>M: transactions[] + Response_Code 00

  M->>S: POST /api/v1/inquiry/statement (fromDate, toDate)
  S->>DB: date-range query (max 90 days)
  opt gap / cold start
    S->>I: statementFetch
    S->>DB: upsert projection
  end
  S-->>M: paginated entries + correlationId
```

---

## 7. Cross-cutting flows

| Concern | Mechanism |
|---------|-----------|
| Correlation | `X-Correlation-Id` minted/propagated; echoed on every response |
| Idempotency | Header `Idempotency-Key` or composite `intRefNum`+`stan` → `idempotency_keys` |
| Envelope injection | Switch overwrites bank config fields from env (never trust mobile) |
| Resilience | Resilience4j circuit breaker on `ImalPort` / vendor ports |
| Audit | Append-only `audit_log`; mask CNIC; never log tokens/passwords/biometrics |
| Feature flag | `MOCK_IMAL=true` (MVP default) → `MockImalAdapter` |
| Lookups | Channel JWT; `ref_*` tables; ETag / catalogVersion |

---

## 8. Related docs

- [01-bounded-contexts.md](./01-bounded-contexts.md)
- [02-imal-mock-api-contract.md](./02-imal-mock-api-contract.md)
- [03-appinsnap-facing-api-contract.md](./03-appinsnap-facing-api-contract.md)
- [04-lookups-master-data.md](./04-lookups-master-data.md)
- [../handoff/appinsnap-integration-cookbook.md](../handoff/appinsnap-integration-cookbook.md)
- [../plans/MASTER-PLAN.md](../plans/MASTER-PLAN.md)
- [../plans/gap-analysis.md](../plans/gap-analysis.md)
- [../security/security-design.md](../security/security-design.md)
- [../devops/docker-runtime.md](../devops/docker-runtime.md)
