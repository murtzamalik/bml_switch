# Error Handling Contract (LOCKED)

AppInSnap must branch on **HTTP status first**, then `Response_Code` when HTTP is 200.

---

## 1. Two worlds

| Kind | HTTP | Body | When |
|------|------|------|------|
| **Transport / auth / rate / validation** | 400, 401, 403, 415, 429, 5xx | See below | Switch edge |
| **Business / AssanPay** | **200** | `Response_Code` + `Response_Desc` (+ nested shapes) | Core banking style outcomes |

**Exception:** Auth failures are always **401**, never HTTP 200 with only a soft code.

Always include **`correlationId`** (body and/or `X-Correlation-Id` response header).

---

## 2. HTTP status catalog

| HTTP | Meaning | Body shape |
|------|---------|------------|
| 200 | Request processed (check `Response_Code`) | AssanPay / additive envelopes |
| 400 | Validation / bad JSON / base64 too large | `fieldErrors` |
| 401 | Channel JWT or strToken invalid/expired/lockout | problem+json **or** AssanPay codes `91`/`75` |
| 403 | Authenticated but not allowed (ownership / scope) | problem+json or `Response_Code=93` |
| 404 | Lookup resource missing (e.g. unknown IMD) | problem+json |
| 415 | Wrong Content-Type | problem+json |
| 429 | Rate limited | `{ "detail":"RATE_LIMIT", "Retry-After":"60", "correlationId" }` + header `Retry-After` |
| 500/502/503 | Switch/upstream failure | No stack traces; `correlationId`; optional `Response_Code=99` |

---

## 3. Validation error (HTTP 400)

```json
{
  "Response_Code": "30",
  "Response_Desc": "VALIDATION_ERROR",
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "fieldErrors": [
    { "field": "amount", "code": "INVALID_FORMAT", "message": "Amount must be string with 2 decimal places" },
    { "field": "CNIC", "code": "INVALID_LENGTH", "message": "CNIC must be 13 digits" }
  ]
}
```

---

## 4. Auth codes (HTTP 401)

| Response_Code | Meaning |
|---------------|---------|
| `91` | SESSION_EXPIRED / missing strToken / invalid strToken |
| `75` | LOCKOUT (5 failed logins) |
| *(problem detail)* | Channel JWT expired → `CHANNEL_TOKEN_EXPIRED` |

---

## 5. Business codes (HTTP 200, Response_Code ≠ 00) — seed subset

| Code | Meaning | Typical APIs |
|------|---------|--------------|
| `00` | Success | All |
| `12` | Invalid / closed account | title, balance, pay |
| `14` | Account not found | title, inquiry |
| `51` | Insufficient funds | IFT/IBFT |
| `61` | Limit exceeded (Asaan daily/max) | pay, open |
| `76` | Invalid / inactive IMD | IBFT |
| `77` | KYC incomplete / open blocked | account/open |
| `78` | OTP required / invalid otpTicket | IBFT pay |
| `79` | Manual review pending | open (odd CNIC before approve-mock) |
| `94` | Duplicate rejected without idempotency replay | rare |
| `96` | System / mock fault | any |
| `99` | Upstream / circuit open | ImalPort |

Full list also exposed at `GET /api/v1/lookups/response-codes`.

NADRA / Liveliness keep vendor-specific fields (`Status_Code` 100/122, `StatusCode` 200) **plus** outer `Response_Code` where AssanPay docs show both.

---

## 6. Idempotency replay

Duplicate `Idempotency-Key` + same client → **HTTP 200** with **original** body (success or business fail). Not a second debit.

---

## 7. Mobile handling checklist

1. If HTTP 401 → re-login / refresh channel token; show lockout if `75`.  
2. If HTTP 400 → show `fieldErrors`.  
3. If HTTP 429 → honor `Retry-After`.  
4. If HTTP 200 → if `Response_Code == "00"` success UI; else map via lookups/response-codes.  
5. Always log `correlationId` for support (never log tokens).  
