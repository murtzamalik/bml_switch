# Bounded Contexts — `bml_switch`

**Architecture style:** Modular monolith · Hexagonal (ports & adapters) · DDD light  
**Base package:** `com.bankalmurqarmah.switchapp`

---

## 1. Context map

| Context | Owns | Does not own | Primary ports |
|---------|------|--------------|---------------|
| **Identity & Access** | `api_clients`, channel JWT, refresh rotation, customer `strToken`, lockout | KYC truth, ledger | — |
| **Lookups** | Typed `ref_*` master data, ETag/catalogVersion, AppInSnap read APIs | Ledger, KYC decisions, tokens | — (DB only; no ImalPort) |
| **Onboarding / KYC** | `onboarding_applications` saga, Unikrew/NADRA/Liveliness orchestration | Core ledger balances | `UnikrewPort`, `NadraPort`, `LivelinessPort` |
| **Accounts** | Account open, account–customer link, account list projection | Settlement | `ImalPort` (open/list) |
| **Payments** | IFT/IBFT aggregates, STAN/ref, status machine, idempotency; validates IMD via Lookups | Title fetch | `ImalPort` (pay) |
| **Inquiry** | Balance, IFT/IBFT title, CNIC→accounts, **ibft-status**, limits/receipt | Posting | `ImalPort` (inquiry) |
| **Statements** | Mini + date-range read models | Live post | `ImalPort` (hydrate) |
| **Audit / Platform** | `audit_log`, correlation, outbox publisher, config injection | Business rules | — |
| **Adapter / iMAL** | `ImalPort`, `MockImalAdapter`, `RealImalAdapter`, Resilience4j | Domain decisions | — |

**Shared kernel (thin):** `Money(PKR)`, `Cnic`, `AccountNumber`, `Iban`, `CorrelationId`, `IdempotencyKey`, response code `00` = success.

---

## 2. Package structure

```
com.bankalmurqarmah.switchapp
├── SwitchApplication.java
├── shared
│   ├── kernel
│   ├── api                 # AssanPay envelope DTOs, ErrorResponse + correlationId
│   ├── security
│   └── persistence         # BaseEntity (uuid, softDelete, audit columns)
├── identity
│   ├── domain / application / port.in / port.out
│   └── adapter.in.web / adapter.out.persistence
├── lookups                 # NEW — ref_* read models, GET /api/v1/lookups/**
├── onboarding
├── accounts
├── payments
├── inquiry
├── statements              # CQRS-light: command vs query packages
├── audit
└── adapter.imal
    ├── port                # ImalPort
    ├── mock                # MockImalAdapter
    ├── real                # RealImalAdapter (phase 2)
    └── mapping
```

Optional Maven modules (same deployable): `bootstrap`, `api`, `application`, `domain`, `infrastructure`.

**Rule:** Controllers never call iMAL or repositories directly. Domain never depends on Spring Web / JPA adapters.

---

## 3. Saga ownership

| Saga | Context | Steps (summary) | Compensation |
|------|---------|-----------------|--------------|
| Digital onboarding | Onboarding | Path A: KYC → open (returns account#/IBAN) → register → login | `REJECTED` / `MANUAL_REVIEW`; no financial reverse |
| IFT | Payments | title (recommended) → INITIATED → push → POSTED/FAILED | Timeout → `NEEDS_RECON`; never auto-POSTED |
| IBFT | Payments | **lookups/banks** → title → push → **status poll (Phase 0 MUST)** | REVERSED only via ops + compensating call when API exists |

Orchestration is **DB-backed** (saga/application state rows), not a separate choreography bus. MVP outbox = same MySQL TX + in-process publisher (no Kafka). Lookups are **not** a saga — read-only master data.

---

## 4. API versioning

- Public base: `/api/v1/`
- AssanPay **body shapes** preserved (including nested quirks and known typos as wire aliases)
- Additive non-breaking fields allowed: `correlationId`, pagination, saga ids
- HTTP: `401`/`400` at switch edge; AssanPay-style business codes often still return HTTP 200 with `Response_Code != 00` where that was the legacy pattern

---

## 5. Adapter swap (real iMAL)

```
ImalPort  ──@ConditionalOnProperty MOCK_IMAL=true──►  MockImalAdapter
          ──MOCK_IMAL=false────────────────────────►  RealImalAdapter
```

Controllers and application services **never** reference mock/real types. Effort estimate after vendor docs: **~15–22 engineering days** (see MASTER-PLAN). Add 5–10 days if SOAP/XML or severe field divergence.
