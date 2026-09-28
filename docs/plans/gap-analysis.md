# Gap Analysis — AppInSnap End-to-End Completeness

**Date:** 2026-09-28 (revision 2)  
**Trigger:** User review — system must not be half-cooked; mobile needs **lookup/master data** to use transaction APIs.  
**Status:** Gaps remediated in plan (docs only). See remediations in MASTER-PLAN + related architecture docs.

---

## 1. What was missing (prioritized)

| P | Gap | Impact | Remediation |
|---|-----|--------|-------------|
| **P0** | No AppInSnap-facing **Lookups / master-data APIs** (`mock_banks` was internal-only) | Cannot build IBFT bank picker; PurposeOfPayment, provinces, account types hardcoded or guessed | **Lookups BC** + `GET /api/v1/lookups/**`; rename `mock_banks` → `ref_banks`; Flyway seed Pakistan banks |
| **P0** | IBFT pay returns **flat** Code/Desc only; status inquiry was Phase 1 | Mobile cannot poll transfer outcome after pay | Elevate `POST /api/v1/inquiry/ibft-status` to **Phase 0 MUST** |
| **P0** | No integration cookbook / seed credentials / error catalog in handoff | AppInSnap cannot demo without guessing | New `docs/handoff/appinsnap-integration-cookbook.md`; DoD requires Postman + OpenAPI examples |
| **P0** | Account open response did not **mandate** accountNumber + IBAN | Next screens blocked | Contract: open **MUST** return both |
| **P0** | Onboarding path ambiguity (register vs open order) | Wrong client sequence | Locked: **new Asaan** = KYC → open → register → login; **existing** = register → login |
| **P1** | Soft rate limits deferred entirely | Abuse risk in sandbox | Phase 0: soft limits on auth/payments; full tuning Phase 1 |
| **P1** | Limits inquiry / receipt by STAN | UX incomplete for “remaining limit” / receipt share | Phase 0 **SHOULD-HAVE** (`inquiry/limits`, `inquiry/receipt`) |
| **P2** | OTP / MPIN not in AssanPay doc | Step-up auth gap for go-live | Explicitly **deferred Phase 1+**; note as product gap |
| **P2** | Beneficiaries server store | Not in AssanPay | **Mobile-local MVP**; no Switch CRUD |
| **P2** | Fees, reversals, RAAST, admin UI, mTLS, live KYC | Not required for sandbox demo | Deferred with rationale |

---

## 2. What was added (this replan)

### Lookups (Phase 0 CRITICAL)
- Banks, purpose-of-payment, response-codes, account-types, provinces, id-types, app-config, finger-indexes, onboarding-steps, branches, currencies, version
- **Rev 3:** `occupations`, `purpose-of-account`

### Money / system / auth consumability (rev 3)
- ibft-status MUST; **limits + receipt MUST**
- Mock OTP send/verify; approve-mock; change/reset password
- Second funded IFT account; fee always 0.00
- Full handoff: required-fields, headers-and-auth, error-handling, smoke, postman stub

### Data model
- Typed `ref_*` (+ occupations, purpose_of_account, otp_challenges)
- Catalog version + ETag caching

---

## 3. Explicitly deferred (with why)

| Item | Defer to | Why |
|------|----------|-----|
| Beneficiaries CRUD on Switch | Never in MVP (mobile-local) | AssanPay had none; title/pay validates payee each time |
| OTP / MPIN set-change | Real SMS/MPIN Phase 1+ | **Mock OTP in Phase 0** for IBFT confirm UX |
| Fee schedule API | Phase 2 | Always `0.00` in mock; document in cookbook |
| Reversal API | Phase 2 | Needs real iMAL advice/reverse semantics |
| RAAST | Phase 2 | Out of AssanPay MVP path |
| Admin UI for ref data | Phase 3 | Flyway seed refresh enough for sandbox |
| Anonymous lookups | Never | Channel JWT required (scrape protection) |
| Live NADRA/Unikrew/1LINK | Phase 2–3 | Mock sufficient for AppInSnap sandbox |
| Separate mock-imal microservice | Optional Phase 1 | In-process adapter is enough |
| Official 1LINK IMD list | When bank provides | Seed marked **MOCK** IMDs until then |

---

## 4. Audit checklist vs “AppInSnap can fully demo”

| Flow | Before | After |
|------|--------|-------|
| IBFT bank picker | ❌ Hardcode | ✅ Lookups banks |
| Purpose of payment | ❌ Guess | ✅ Lookups purpose |
| IBFT after flat pay | ⚠️ No status | ✅ ibft-status |
| Onboarding UX steps | ⚠️ Implicit | ✅ onboarding-steps + cookbook |
| Account open → next screen | ⚠️ Incomplete | ✅ accountNumber + IBAN required |
| Error UX mapping | ❌ Missing | ✅ response-codes subset |
| App config (limits display) | ❌ Env only | ✅ app-config public keys |
| Sandbox smoke | ⚠️ Actuator only | ✅ system/health + version |
| Handoff | ⚠️ Partial | ✅ Cookbook + DoD Postman/OpenAPI |

---

## 5. Related docs

- [MASTER-PLAN.md](./MASTER-PLAN.md) — Gaps & remediations section + T-LKP tasks  
- [phase-0-mvp-scope.md](./phase-0-mvp-scope.md)  
- [../architecture/04-lookups-master-data.md](../architecture/04-lookups-master-data.md)  
- [../handoff/appinsnap-integration-cookbook.md](../handoff/appinsnap-integration-cookbook.md)  
