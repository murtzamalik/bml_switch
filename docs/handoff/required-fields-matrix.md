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

## 2. Account / onboarding

| Endpoint | Required (mobile) | Optional | Ignored/Switch |
|----------|-------------------|----------|----------------|
| `POST /account/login` | UserName **or** CNIC, password (AssanPay LoginRequest fields) | device fields | envelope I |
| `POST /account/register` | CNIC, AccountNo, MobileNo, UserName, password | device | envelope I |
| `POST /account/customer-detail` | CNIC, strToken | CustomerID | envelope I |
| `POST /account/account-list` | CNIC (in Request), strToken | — | envelope I |
| `POST /account/open` | See §2.1 + Idempotency-Key | email, motherName | product defaults, IBAN bank code |
| `POST /account/upload-documents-unikrew` | nicFrontImage, nicBackImage, livenessImage (base64 ≤2MB each) | comparison flags | userName/password vendor |
| `POST /account/validate-document-unikrew` | Base64Image, CNIC | — | userName/password |
| `POST /account/verify-liveliness` | Base64Image, CNIC, strToken | — | XApi key |
| `POST /account/verify-fingers` | citizenNumber/CNIC, fingerIndex, fingerTemplate, areaName, strToken | contactNumber | envelope I |
| `POST /account/approve-mock` | CNIC **or** onboardingApplicationId | — | sandbox profile only |
| `POST /account/change-password` | strToken, currentPassword, newPassword | — | — |
| `POST /account/reset-password-mock` | CNIC, newPassword | — | sandbox + channel JWT |
| `POST /accounts/information` | fromAccount **or** AccountNo, strToken | — | — |

### 2.1 Account open — full JSON (LOCKED)

**Request** (`POST /api/v1/account/open`):

```json
{
  "strToken": "<customer-jwt>",
  "CNIC": "4210112345678",
  "fullName": "ALI KHAN",
  "MobileNo": "03001234567",
  "dateOfBirth": "1990-01-01",
  "fatherName": "AHMED KHAN",
  "motherName": "",
  "occupationCode": "EMP",
  "purposeOfAccountCode": "SAV",
  "mailingAddress": "House 123, Street 4, Karachi",
  "city": "Karachi",
  "provinceCode": "SD",
  "email": "ali.khan@example.com",
  "productCode": "ASAAN_DIGITAL",
  "onboardingApplicationId": "<uuid-from-kyc-flow-optional>"
}
```

Header: `Idempotency-Key: <uuid>`, `Authorization: Bearer <channel>`.

**Success response:**

```json
{
  "Response_Code": "00",
  "Response_Desc": "Success",
  "correlationId": "...",
  "accountNumber": "0345001234567",
  "IBAN": "PK00BMAL0000000345001234567",
  "accountTitle": "ALI KHAN",
  "currency": "PKR",
  "productCode": "ASAAN_DIGITAL",
  "status": "ACTIVE"
}
```

Blocked KYC → HTTP 200, `Response_Code=77` or `79` (manual review).

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
