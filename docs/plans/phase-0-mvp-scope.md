# Phase 0 — MVP Scope (FROZEN — happy flow primary)

**Status:** FROZEN — see [PHASE-0-EXECUTION.md](./PHASE-0-EXECUTION.md)  
**Goal:** AppInSnap demos **happy-path** E2E without verbal Q&A. Mock OTP = **`1234`**.  
**Out of scope:** Real iMAL, live NADRA/Unikrew/SMS, RAAST, mTLS, HA, admin UI, server beneficiaries.

Critical negatives (NSF, bad IMD, auth fail, idempotency, odd-CNIC+approve-mock) remain in Phase 0 but delivery prioritizes happy flow (Waves 0–7).

Rev 2: lookups. Rev 3: consumability — [consumability-audit.md](./consumability-audit.md).

---

## 1. In scope

| Area | Capability |
|------|------------|
| Auth | Token/refresh/logout **locked JSON**; login strToken; **mock OTP** for IBFT; soft rate limits |
| Lookups | Banks, purposes (payment + account), occupations, response-codes, provinces, … |
| Onboarding | KYC mocks; **approve-mock**; open full body → accountNumber+IBAN; change/reset password |
| Money | IFT (2 funded accounts); IBFT banks→title→OTP→pay→status; fee `0.00` |
| Inquiry | Balance, titles, CNIC, mini/statement, **ibft-status**, **limits**, **receipt** (all MUST) |
| Handoff | Cookbook, required-fields matrix, headers, errors, smoke, Postman stub |
| Seed | Even+odd CNIC; funded `…567` + `…568`; zero; invalid; ≥16 MOCK banks |

---

## 2. MVP API checklist

- [ ] Auth: token|refresh|logout|otp/send|otp/verify  
- [ ] Account: login|register|open|list|detail|KYC×4|approve-mock|change-password|reset-password-mock|information  
- [ ] Payment: ift|ibft (otpTicket)  
- [ ] Inquiry: balance|titles|cnic|mini|statement|ibft-status|limits|receipt  
- [ ] Lookups: banks… + occupations + purpose-of-account  
- [ ] System: health|version  

---

## 3. Acceptance (consumability)

| Theme | Pass |
|-------|------|
| **AUTH-JSON** | Token response has access_token, expires_in=900, refresh_* |
| **STRTOKEN** | Body required; missing → HTTP 401 code 91 |
| **OPEN-JSON** | Full AccountOpenRequest fields accepted; returns account# + IBAN |
| **OTP** | IBFT without otpTicket → 78; with **`1234`** → success |
| **APPROVE** | Odd CNIC open blocked 79; approve-mock then open OK |
| **IFT-SEED** | Transfer 567→568 succeeds |
| **STATUS** | Poll ≤30s reaches POSTED |
| **LIMITS** | limits + receipt return documented JSON |
| **ERRORS** | 400 fieldErrors; 200+non-00 business; auth always 401 |
| **LOOKUPS** | Zero hardcoded banks; occupations + purpose-of-account non-empty |
| **SMOKE** | 15-min checklist all green |
| **HANDOFF** | Postman stub importable; required-fields matrix used |

(Plus prior LOOKUPS/CONTRACT/IDEM/LEDGER/KYC themes from rev 2.)

---

## 4. Definition of Done

- [ ] All Phase-0 routes + consumability docs locked  
- [ ] Handoff folder complete (matrix, headers, errors, smoke, postman stub)  
- [ ] AppInSnap sign-off: “no verbal blockers for happy path”  
- [ ] Deferred items only: real SMS, real iMAL, fees API, beneficiaries, RAAST  

See [PHASE-0-EXECUTION.md](./PHASE-0-EXECUTION.md). OTP locked to **`1234`**.
