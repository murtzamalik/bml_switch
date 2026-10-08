# Required Fields Matrix (LOCKED)

**Legend**
- **R** = Required from mobile  
- **O** = Optional (Switch fills / defaults if blank)  
- **I** = Ignored / overwritten from env (do not trust mobile)  
- **A** = AssanPay envelope common block (see §0)

Formats (global):
- **Amount:** string, 2 decimal places, no commas → `"1000.00"` (≤14 digits before `.`)
- **CNIC:** 13 digits; dashes stripped if sent
- **Mobile:** `03XXXXXXXXX`; `+92` / `92` normalized to `03…`
- **strDate:** `yyyyMMdd` · **strTime:** `HHmmss` · **transmissionDateTime:** `yyyy-MM-dd HH:mm:ss.SSS` (Asia/Karachi)
- **Statement dates:** `yyyy-MM-dd`
- **Idempotency-Key:** UUID (header) — mobile generates for open/IFT/IBFT
- **stan / intSTAN / intRefNum:** O — Switch generates if blank

---

## 0. Common AssanPay envelope (core-banking POSTs)

| Field | Mobile | Notes |
|-------|--------|-------|
| strAppID, strADC_TranCode, strDelimiter, strNetID, strMerName, strMCC, channelId, Channel_type, processingCode, agentAccount | **I** | Switch injects |
| strDate, strTime, transmissionDateTime | **O** | Generated if blank |
| intRefNum, intSTAN, stan | **O** | Switch generates if blank |
| strToken | **R*** | *Required on customer-scoped; see headers-and-auth |
| Device_*, Client_IP, UUID, Latitude/Langitude, Application_Version, Cusomter_ID | **O** | Audit |
| password (payment envelopes) | **O/I** | Ignored as channel secret |

---

## 1. Auth

| Endpoint | Required (mobile) | Optional | Ignored/Switch |
|----------|-------------------|----------|----------------|
| `POST /auth/token` | client_id, client_secret | grant_type=`client_credentials` | — |
| `POST /auth/refresh` | refresh_token | — | — |
| `POST /auth/logout` | (Bearer or refresh_token) | strToken, revokeCustomerSession | — |
| `POST /auth/otp/send` | CNIC or MobileNo, purpose=`IBFT`, strToken | otp channel | — |
| `POST /auth/otp/verify` | otpReference, otp, strToken | — | — |

---

## 2. Account / onboarding (channel JWT only)

| Endpoint | Required (mobile) | Optional | Ignored/Switch |
|----------|-------------------|----------|----------------|
| `POST /account/open` | CNIC, productCode, accGl, customer fields for iMal CIF/account | address lines, motherName, email, simulateAuthorizeFail (sandbox) | company/branch/currency/requester from `switch.imal.*` |
| `POST /account/account-list` | CNIC (body or `Request.CNIC`) | — | — |

Login/register/KYC Unikrew/liveliness/fingers/approve-mock/change-password are **removed** (AIS owns).

### 2.1 Account open — JSON (LOCKED)

**Request** (`POST /api/v1/account/open`):

```json
{
  "CNIC": "4589652158798",
  "fullName": "Talha Idris",
  "firstName": "Talha",
  "lastName": "Idris",
  "MobileNo": "03110537212",
  "dateOfBirth": "1995-10-10",
  "idDeliveryDate": "2018-01-01",
  "idExpiryDate": "2030-01-01",
  "gender": "M",
  "maritalStatus": "M",
  "email": "demot5054@gmail.com",
  "mailingAddress": "HOUSE # 252-D MOHALLA WADHAT COLONY TAXILA",
  "city": "KARACHI",
  "productCode": "ASAAN_DIGITAL",
  "accGl": "203153"
}
```

Header: `Authorization: Bearer <channel>` only (no Idempotency-Key, no strToken).

**Success response:**

```json
{
  "Response_Code": "00",
  "Response_Desc": "Success",
  "correlationId": "...",
  "cifNo": "9052640",
  "cifCreated": true,
  "accountCreated": true,
  "accounts": [
    {
      "accountNumber": "0209586020000002",
      "IBAN": "PK32BMLP0209586020000002",
      "accountTitle": "Talha Idris",
      "productCode": "ASAAN_DIGITAL",
      "accGl": "203153",
      "cifNo": "9052640",
      "currency": "PKR",
      "status": "ACTIVE",
      "balance": "0.00"
    }
  ]
}
```

Step failures return HTTP 4xx/5xx with `failedStep` (`createRetailCif` | `validateRetailCif` | `createGeneralAccount` | `authorizeGeneralAccount`).

### 2.2 Account list (Flow 2)

```json
{ "CNIC": "4210112345678" }
```

Same `accounts[]` shape as open (no cifCreated/accountCreated flags).

---

## 3. Payments

| Endpoint | Required (mobile) | Optional | Ignored/Switch |
|----------|-------------------|----------|----------------|
| `POST /payment/ift` | fromAccount, toAccount, amount, PurposeOfPayment, strToken, Idempotency-Key | Description, titles, stan | agentAccount, processingCode, envelope |
| `POST /payment/ibft` | fromAccount, toIBAN, toIMD, amount, PurposeOfPayment, otpTicket, strToken, Idempotency-Key | toAccount, fromIBAN, Description, transactionID | fee (always 0), agentAccount |

**IFT note:** `toAccount` must be same-bank seed (use second funded `0345001234568` for demo).  
**IBFT note:** Prefer `toIBAN` + `toIMD` (IMD from `/lookups/banks`). `fromIBAN` from account-list. Fee always `"0.00"`.

---

## 4. Inquiry / statements

| Endpoint | Required (mobile) | Optional | Notes |
|----------|-------------------|----------|-------|
| `/inquiry/balance` | fromAccount, strToken | — | |
| `/inquiry/ift-title` | Account, strToken | id_type, id_value | |
| `/inquiry/ibft-title` | toIMD + (toIBAN **or** toAccount), strToken | amount | |
| `/inquiry/cnic` | CNIC, strToken | — | |
| `/inquiry/ibft-status` | strToken + (**transactionID** or **stan** or **intRefNum**) | — | Poll 2s, max 30s |
| `/inquiry/mini-statement` | fromAccount, strToken | limit (default 10) | |
| `/inquiry/statement` | fromAccount, fromDate, toDate, strToken | page, size | max 90 days |
| `/inquiry/limits` | fromAccount, strToken | — | MUST |
| `/inquiry/receipt` | strToken + (stan **or** intRefNum **or** transactionID) | — | MUST |

---

## 5. Lookups / system

| Endpoint | Required | Optional query |
|----------|----------|----------------|
| All `GET /lookups/*` | Channel JWT only | active, q, cursor, limit |
| `/lookups/banks/{imd}` | path imd | — |
| `/system/health` | none or channel | — |
| `/system/version` | channel JWT | — |

Empty list → HTTP 200, `items: []`, `Response_Code=00` (not an error).
