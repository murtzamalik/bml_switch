# Security Design — `bml_switch`

**Classification:** Bank middleware (PII + funds movement)  
**Mode:** Phase-0 Mock iMAL; controls designed for real-mode continuity  
**Standards:** OWASP Top 10 · PCI-adjacent hygiene · KYC/AML patterns (conceptual)

---

## 1. Threat model (summary)

| Asset | Threat | Control |
|-------|--------|---------|
| Channel credentials | Stolen client secret | Env/Vault; short JWT; rotation; mTLS later |
| Customer `strToken` | Replay / log leak | Short TTL; HTTPS; never log; revoke list |
| Accounts & balances | IDOR | Ownership check every inquiry/transfer/statement |
| Funds (IFT/IBFT) | Replay / double-spend | Idempotency + unique constraints + audit |
| CNIC / biometrics | PII / biometric leak | Mask CNIC; no clear biometrics in logs |
| Card data | PCI scope creep | **No PAN/CVV** in phase-0 product |

---

## 2. Authentication model

### 2.1 Channel (AppInSnap → Switch)

- **Preferred:** OAuth2 **client_credentials** → access JWT (15 min) + refresh (7 days, rotate on use).
- Alternative: API-key identifying channel then Switch-issued JWT.
- Claims: `clientId`, `roles`/`scopes`, `jti`.
- Deny-by-default; scopes e.g. `onboarding`, `inquiry`, `transfer`, `statement`.

### 2.2 Customer session (`strToken`)

- Issued by `POST /api/v1/account/login` (AssanPay-compatible field).
- Bound to customer id / CNIC ref / account set.
- Validated on customer-scoped APIs; mismatch → deny.
- Logout / lockout → revocation list (`jti` or token hash).
- Lockout after **5** failed logins (default unlock window 30 min).

### 2.3 Downstream (Switch → iMAL / vendors)

- Credentials only from secrets manager / env.
- Resilience4j circuit breaker on all external ports.
- Mocks must **not** honor mobile-supplied success overrides.

### 2.4 Filter order (Spring Security 6)

1. CorrelationIdFilter  
2. ChannelJwtAuthenticationFilter  
3. StrTokenAuthenticationFilter (skip public auth/health)  
4. Authorization / `@PreAuthorize`  
5. Exception translation → ProblemDetail / AssanPay mapper  

Public (typical): `/actuator/health*`, `/v3/api-docs/**`, `/swagger-ui/**`, `/api/v1/auth/token`, `/api/v1/auth/refresh`.  
**Lookups** (`/api/v1/lookups/**`): channel JWT required; **customer `strToken` not required**; no anonymous bank-list scrape.  
`/api/v1/system/health` and `/system/version`: channel JWT preferred (or health-lite public — pick one in implementation; default **channel JWT** for version, public OK for health-lite status only).

---

## 3. Transport & headers

- TLS 1.2+ only in deployed environments.
- mTLS AppInSnap↔Switch: **optional Phase 1+** (CN ↔ client mapping).
- Response headers:  
  `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Content-Security-Policy: default-src 'self'`.
- Rate-limit headers: `X-RateLimit-Remaining`, `Retry-After`.

**Baseline limits:** login 5/min/IP; payment 30/min/client; global 100/min/client.

---

## 4. Secrets

```yaml
# FORBIDDEN in repo
jwt.secret=hardcoded
imal.password=secret123

# REQUIRED
JWT_SECRET=${JWT_SECRET}                 # ≥256-bit entropy (or RS256 private key)
DB_PASSWORD=${DB_PASSWORD}
IMAL_CLIENT_SECRET=${IMAL_CLIENT_SECRET}
LIVELINESS_API_KEY=${LIVELINESS_API_KEY}
UNIKREW_USER=${UNIKREW_USER}
UNIKREW_PASSWORD=${UNIKREW_PASSWORD}
```

- `.env` gitignored; only `.env.example` committed.
- Dev ≠ prod secrets; rotate on compromise / offboarding / quarterly.
- Agent must never dump secret values.

---

## 5. Logging & data protection

| Data | Rule |
|------|------|
| Password | Never log; BCrypt ≥12 if stored |
| JWT / strToken / API keys | Never log clear |
| CNIC | Mask (default last 4) |
| PAN | Do not collect; if present truncate last 4 |
| CVV | **Never store** |
| Biometrics / base64 images | In-memory process; never clear in app logs |
| Amounts | OK to log with currency |

Passwords for Unikrew/Liveliness on the wire from mobile are **ignored**; Switch injects from env.

---

## 6. Financial integrity

- Idempotency keys on account open, IFT, IBFT (map to intRefNum/STAN).
- Dual-write: business row + `outbox_events` same TX.
- Status: `INITIATED → POSTED | FAILED`; `POSTED → REVERSED` only via compensating path.
- Money: `BigDecimal` / `DECIMAL(19,4)` + currency `PKR`.
- No blind retry on payment POST without safe idempotent replay.
- AML: configurable amount threshold → review queue record (even in mock).

---

## 7. Field ownership (security view)

Mobile **must not** be trusted for:

- `Response_Code` / success flags  
- Balances / limits / fees  
- KYC approve/reject  
- Self-issued `strToken`  
- Unikrew/Liveliness/NADRA credentials  
- `webService` / rail routing  
- Idempotency outcome  
- Customer↔account ownership  
- Channel scopes  

Mobile **may** send (validated): accounts, amounts, CNIC, device metadata, biometric payloads for processing, client-generated stan/refs (uniqueness still enforced server-side).

---

## 8. Audit trail

Append-only `audit_log` (tamper-evident; retention configurable, default ≥365 days conceptual; KYC evidence ≥5 years policy when live).

**Must audit:** login success/fail, lockout, KYC decisions, account open, balance/statement access, IFT/IBFT title+pay, privilege/config changes (non-secret).

**Mandatory fields:** timestamp, correlationId, channel clientId, customerRef (masked), route, outcome, latencyMs, redacted detail.

**Never in audit payload:** passwords, tokens, finger templates, full CNIC, raw images.

---

## 9. PCI-adjacent / compliance notes

- Phase-0: no card PAN/CVV APIs or storage.
- SBP Asaan Digital: **conceptual** KYC gates + configurable limits — not a certification claim.
- Sanctions: stub `ALLOW` with `SanctionsPort` hook for later OFAC/UN/EU lists.
- Islamic banking MVP: no interest fields; product code `ASAAN_DIGITAL`.

---

## 10. Security Phase-0 checklist

- [ ] Channel auth on all non-public endpoints  
- [ ] Customer `strToken` on customer APIs  
- [ ] Secrets via env only; scanner clean  
- [ ] Log masking tests (CNIC/token/password/biometric)  
- [ ] Rate limit + lockout (lockout mandatory Phase 0; rate limit Phase 0 preferred)  
- [ ] Ownership checks on account-scoped APIs  
- [ ] Idempotency on open/IFT/IBFT  
- [ ] Audit events for §8 list  
- [ ] TLS documented for deploy; no HTTP prod profile  
- [ ] Field-ownership reviewed with AppInSnap  
