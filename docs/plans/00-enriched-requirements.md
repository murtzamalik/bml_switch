# Final Enriched Requirements — `bml_switch`

**Document ID:** `REQ-BML-SWITCH-MVP-2026-09-28`  
**Status:** FINAL for MVP planning (0 of 2 clarification rounds used; smart defaults applied)  
**Source:** AssanPay Third-Party API Docs + Orchestrator business brief

Full detailed FR/NFR matrices live in consultation output; this file is the committed handoff. See also [MASTER-PLAN.md](./MASTER-PLAN.md).

---

## 1. Actors & boundary

AppInSnap (untrusted mobile channel) → **bml_switch** (this middleware) → Mock/Real **iMAL** (core). Bank Al Murqarmah owns policy/secrets. NADRA/Unikrew/Liveliness/1LINK mocked in MVP behind dedicated ports.

**Rules:** AppInSnap never calls iMAL directly; Switch never exposes JPA entities; bank config fields stripped/overwritten if sent by mobile; REST only to AppInSnap.

---

## 2. Functional domains (MVP)

| Domain | Must deliver |
|--------|--------------|
| Auth | Channel JWT + customer `strToken`; refresh; logout; lockout |
| Onboarding | Register, Unikrew×2, liveliness, NADRA fingers (mock), customer detail, account list |
| Accounts | Asaan Digital open (idempotent), account information |
| Payments | IFT + IBFT with idempotency + status machine |
| Inquiry | Balance, IFT/IBFT title, CNIC accounts |
| Statements | Mini (N=10) + date range (≤90d) |
| Platform | Audit, correlationId, outbox, CB, env injection, OpenAPI |

Success codes: AssanPay `Response_Code=00`; NADRA `Status_Code=100`; Liveliness `StatusCode=200`.

---

## 3. Non-functional (MVP)

- TLS 1.2+; secrets from env; JWT entropy ≥256-bit; BCrypt ≥12  
- Mask CNIC; never log passwords/tokens/biometrics  
- Idempotency TTL 24h; dual-write outbox on financial posts  
- Resilience4j on ImalPort; no blind payment retry  
- Conceptual SBP Asaan limits via config; SanctionsPort stub ALLOW  
- Money: BigDecimal / DECIMAL(19,4) + PKR  

---

## 4. Field ownership

**Switch injects:** envelope (`strAppID`, ADC codes, delimiter, netId, merName, MCC, channelId), processing codes, agent/branch/IMD/IBAN bank code, iMAL + vendor secrets, JWT, feature flags, Asaan limits.

**Mobile provides:** CNIC/user/device, credentials, accounts/amounts/IBAN/IMD (**IMD from lookups/banks**), KYC payloads, statement filters, idempotency/correlation refs.

**Lookups:** Channel JWT only; mobile never authors bank/purpose/province catalogs.

**Hybrid:** dates/times (generate if missing); `strToken` must match Switch; payment envelope `password` ignored as channel secret.

---

## 5. Open questions (non-blocking — API wiring locked)

**Bank/iMAL (when docs arrive):** official APIs, real IMDs, reversals, live SMS OTP.  
**AppInSnap product (non-API):** UI copy, branding — **not** blockers.  
**Locked for AppInSnap API:** strToken in body, auth JSON, formats, OTP mock, errors — see handoff/.

---

## 6. Phase split

- **0 MVP:** Lookups + mock OTP + consumability handoff + limits/receipt MUST; AppInSnap integrates without verbal Q&A  
- **1 Hardening:** Real SMS OTP/MPIN, rate-limit tuning, chaos, official IMD migration  
- **2 Real iMAL:** adapter swap ~15–22d  
- **3 Prod:** HA, mTLS, live KYC, DR  

Assumptions A-22..A-26: mock OTP, fee 0.00, strToken body required, limits/receipt MUST, sandbox approve/reset. See [../handoff/consumability-locked-decisions.md](../handoff/consumability-locked-decisions.md).
