# Consumability Locked Decisions (Architect)

Mirror of locks used in handoff docs. Do not diverge without updating cookbook + matrix + error-handling.

| # | Topic | LOCKED |
|---|-------|--------|
| 1 | Auth token | `{ access_token, token_type:"Bearer", expires_in:900, refresh_token, refresh_expires_in:604800, correlationId }` |
| 2 | strToken | Body **REQUIRED** on customer ops; alias `X-Str-Token`; channel = `Authorization: Bearer` only |
| 3 | Expiry | HTTP **401** both; strToken → `Response_Code:91`; channel → problem+json / CHANNEL_TOKEN_EXPIRED |
| 4 | Lockout | 5 fails → HTTP 401 + `75` LOCKOUT; 30 min unlock |
| 5 | Logout | Single `/auth/logout`; optional revoke customer via strToken |
| 6 | Amount | String 2 decimals, no commas, ≤14 digits before `.` |
| 7 | Formats | CNIC 13; Mobile `03…`; strDate `yyyyMMdd`; strTime `HHmmss`; transmissionDateTime `yyyy-MM-dd HH:mm:ss.SSS`; statement `yyyy-MM-dd`; TZ Asia/Karachi |
| 8 | Idempotency | Mobile UUID on IFT/IBFT/open; Switch fills STAN if blank |
| 9 | AccountOpen | Full fields in required-fields-matrix; response MUST accountNumber+IBAN |
| 10 | IBFT | Prefer toIBAN+toIMD; fromIBAN from account-list; fee `0.00` |
| 11 | IFT seed | Second funded `0345001234568` |
| 12 | Status poll | Every 2s, max 30s; terminals POSTED/FAILED/REVERSED |
| 13 | Fee | Always `0.00` mock |
| 14 | OTP | `/auth/otp/send`+`verify`; fixed **`1234`**; MUST before IBFT; `MOCK_OTP=true` |
| 15 | approve-mock | Odd CNIC sandbox unblock |
| 16 | Lookups | + occupations, purpose-of-account |
| 17 | Password | change-password + reset-password-mock |
| 18 | Limits+receipt | Phase 0 MUST |
| 19 | Errors | 400 fieldErrors; 401/403/429; business often HTTP 200 + non-00; auth always 401; always correlationId |
| 20 | Images | Max 2MB decoded per base64 field |
| 21 | TLS | Local HTTP OK; UAT/prod HTTPS |
| 22 | Beneficiaries | Mobile-local |
| 23 | Confirm transfer | Title screen for IFT; mock OTP for IBFT (no real SMS in P0) |
