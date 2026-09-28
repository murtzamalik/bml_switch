# Headers & Auth Contract (LOCKED)

**Audience:** AppInSnap  
**Base:** `http://localhost:8080` (Docker sandbox — **HTTP OK locally**). UAT/prod: **HTTPS only**.

---

## 1. Header catalog

| Header | Required when | Notes |
|--------|---------------|-------|
| `Content-Type: application/json` | All POST with body | Reject other types → 415/400 |
| `Accept: application/json` | Recommended always | |
| `Authorization: Bearer <access_token>` | All routes except `/auth/token`, `/auth/refresh`, (optional health-lite) | **Channel JWT only** in this header |
| `Idempotency-Key: <uuid>` | `payment/ift`, `payment/ibft`, `account/open` | Mobile-generated UUID; TTL 24h |
| `X-Correlation-Id: <uuid>` | Optional (recommended) | Switch mints if absent; always echoed |
| `X-Str-Token: <strToken>` | Optional **alias** | Same value as body `strToken` |
| `If-None-Match` | Lookups | Honor for 304 |

**Do not** put customer `strToken` in `Authorization`. Channel and customer tokens are separate.

---

## 2. Channel token — request / response

### `POST /api/v1/auth/token` (public)

```json
{
  "client_id": "appinsnap-sandbox",
  "client_secret": "change_me_sandbox_secret",
  "grant_type": "client_credentials"
}
```

**200 response (LOCKED):**

```json
{
  "access_token": "<jwt>",
  "token_type": "Bearer",
  "expires_in": 900,
  "refresh_token": "<opaque-or-jwt>",
  "refresh_expires_in": 604800,
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

### `POST /api/v1/auth/refresh` (public)

```json
{ "refresh_token": "<refresh_token>" }
```

**200:** same shape as token response (rotated refresh; old refresh invalid).

### Channel JWT expired / invalid

- **HTTP 401**
- `Content-Type: application/problem+json` (preferred) **or** JSON:

```json
{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "detail": "CHANNEL_TOKEN_EXPIRED",
  "correlationId": "..."
}
```

---

## 3. Customer `strToken` (LOCKED)

| Rule | Value |
|------|--------|
| Issued by | `POST /api/v1/account/login` → field `strToken` |
| On customer-scoped APIs | Body field **`strToken` REQUIRED** |
| Alias | Header `X-Str-Token` (or `X-Customer-Token`) accepted if body omitted; if both sent, must match |
| Channel header | Still required: `Authorization: Bearer <channel>` |

**Customer-scoped ops:** login (issues token — no prior strToken), register (no prior), then after login: open (if post-login), payments, inquiries, statements, change-password, account-list, customer-detail, KYC steps that bind customer, limits, receipt, ibft-status.

**Channel-only (no strToken):** auth/token|refresh, lookups/**, system/health|version, approve-mock, reset-password-mock, otp/send may use channel + CNIC before login — see cookbook.

### strToken expired / missing

- **HTTP 401**
- Body:

```json
{
  "Response_Code": "91",
  "Response_Desc": "SESSION_EXPIRED",
  "correlationId": "..."
}
```

### Login lockout (5 failures)

- **HTTP 401**
- `Response_Code": "75"`, `Response_Desc": "LOCKOUT"`
- Unlock window: **30 minutes** (or sandbox reset)

---

## 4. Logout (LOCKED)

`POST /api/v1/auth/logout` — single endpoint.

```json
{
  "refresh_token": "<optional if using Authorization>",
  "strToken": "<optional — also revoke customer session>",
  "revokeCustomerSession": true
}
```

- Always revokes channel refresh family when refresh or access presented.
- If `strToken` present or `revokeCustomerSession=true` with valid strToken → revoke customer session too.
- **No** separate `/account/logout` in Phase 0.

**200:**

```json
{ "Response_Code": "00", "Response_Desc": "Success", "correlationId": "..." }
```

---

## 5. Mock OTP (MUST for IBFT) — sandbox

Requires `MOCK_OTP=true` (default in local/mock profiles).

### `POST /api/v1/auth/otp/send`

Headers: channel Bearer. Body:

```json
{
  "CNIC": "4210112345678",
  "MobileNo": "03001234567",
  "purpose": "IBFT",
  "strToken": "<customer>"
}
```

**200:**

```json
{
  "Response_Code": "00",
  "Response_Desc": "Success",
  "otpReference": "OTP-MOCK-001",
  "expires_in": 120,
  "correlationId": "..."
}
```

### `POST /api/v1/auth/otp/verify`

```json
{
  "otpReference": "OTP-MOCK-001",
  "otp": "1234",
  "strToken": "<customer>"
}
```

Fixed mock OTP: **`1234`**. Returns `otpTicket` (short-lived, ~5 min) required on `payment/ibft` as `otpTicket` field.

Real SMS gateway: Phase 2+ (`MOCK_OTP=false`).

---

## 6. Route group → auth matrix

| Group | Channel JWT | Body strToken | Idempotency-Key |
|-------|-------------|---------------|-----------------|
| `/auth/token`, `/auth/refresh` | No | No | No |
| `/auth/logout` | Yes* | Optional | No |
| `/auth/otp/*` | Yes | Yes (after login) | No |
| `/lookups/**` | Yes | No | No |
| `/system/**` | Yes (version); health may be public lite | No | No |
| `/account/login`, `/register` | Yes | No | No |
| `/account/open` | Yes | Yes | **Yes** |
| `/account/*` KYC & detail/list | Yes | Yes (after session) | No |
| `/account/approve-mock`, `reset-password-mock` | Yes | No | No |
| `/account/change-password` | Yes | Yes | No |
| `/payment/*` | Yes | Yes | **Yes** |
| `/inquiry/*` | Yes | Yes | No |

\*Logout accepts refresh_token in body if access already expired.
