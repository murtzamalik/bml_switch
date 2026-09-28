# Phase 0 Implementation Notes

**Status:** COMPLETE (Waves 0–7)  
**Date:** 2026-09-28  
**OTP lock:** `1234` (`MOCK_OTP=true`)

## What was built

Modular Spring Boot 3.3.5 monolith (`com.bankalmurqarmah.switchapp`) with MySQL 8.4, Flyway, Docker Compose.

| Wave | Delivered |
|------|-----------|
| W0 | Maven, Dockerfile, compose, Flyway V001/V002, `/api/v1/system/health` |
| W1 | Channel JWT + body `strToken`, token/refresh/logout, OTP send/verify (`1234`) |
| W2 | Lookups APIs + `ref_*` seed (18 banks incl. BMAL) |
| W3 | `ImalPort` + `MockImalAdapter`, ledger seed (funded 567/568, zero, invalid) |
| W4 | Login/register/open/KYC mocks/approve-mock/change+reset password |
| W5 | Balance/titles/CNIC, IFT/IBFT, idempotency, ibft-status |
| W6 | Mini/statement, limits, receipt |
| W7 | OpenAPI examples, Postman, `scripts/smoke.sh`, DoD checkboxes |

## How to run

```bash
cp .env.example .env   # if needed
docker compose up --build -d
curl -s http://localhost:8080/api/v1/system/health
./scripts/smoke.sh
```

Swagger: http://localhost:8080/swagger-ui.html

## Seed credentials

| Kind | Value |
|------|-------|
| Channel client | `appinsnap-sandbox` / `change_me_sandbox_secret` |
| Customer | `ali.khan` / CNIC `4210112345678` / `Sandbox@123` |
| Mock OTP | **`1234`** |
| Funded accounts | `0345001234567` (150000 PKR), `0345001234568` (50000 PKR) |
| Zero balance | `0345009999999` |
| Invalid account | `0345000000000` |
| Sample IBFT IMD | from `GET /api/v1/lookups/banks` (e.g. Meezan `601004`) |

Passwords are re-hashed on boot via `SeedPasswordFixer` (Spring BCrypt strength 12).

## AssanPay shapes preserved

- Balance → capital `Return`
- IFT → nested lowercase `return`
- IBFT payment → flat (+ `fee: "0.00"`)
- IBFT title → `IBFTTitleFetchResponse.return` double-nest

Unit fixtures: `src/test/java/.../AssanPayShapeTest.java`  
Examples: `docs/openapi/assanpay-examples.md`  
Postman: `docs/handoff/BML-Switch-Phase0.postman_collection.json`

## Key package layout

```
src/main/java/com/bankalmurqarmah/switchapp/
  SwitchApplication.java
  identity/          # auth + OTP
  accounts/          # login, KYC, open
  lookups/           # banks + ref catalogs
  inquiry/           # balance, titles, statements, limits, receipt
  payments/          # IFT / IBFT
  adapter/imal/      # ImalPort + MockImalAdapter
  shared/security/   # JWT filters, StrTokenSupport
  shared/config/     # SwitchProperties, SeedPasswordFixer
  system/            # health, version
src/main/resources/db/migration/
  V001__schema.sql
  V002__seed.sql
```

## Smoke result (verified)

`./scripts/smoke.sh` → **SMOKE OK**  
Health UP; 18 banks; balance/IFT/idempotency; IBFT title double-nest; OTP 1234; IBFT without ticket → 78; IBFT POSTED; limits/receipt/mini.

## Residuals / known gaps

- Real iMAL adapter not implemented (`MOCK_IMAL=true` only) — Phase 1+
- Refresh-token store scans all active hashes (fine for sandbox; index/lookup needed at scale)
- Full OpenAPI annotations on every DTO not exhaustive (springdoc + examples doc cover W7)
- AppInSnap liaison “sign-off” is engineering-complete; formal stakeholder signature is external
- No frontend (by design)
