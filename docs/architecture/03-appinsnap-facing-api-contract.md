# AppInSnap-Facing API Contract

**Source shapes:** `docs/AssanPay_ThirdParty_API_Docs_2.txt`  
**Base path:** `/api/v1`  
**Rule:** Preserve AssanPay request/response **body** shapes. Routes are remapped under versioned REST paths. Switch injects bank/system fields from env — mobile values for those fields are **overwritten**.

All responses also include `correlationId` (header `X-Correlation-Id` and/or body field; non-breaking additive).

---

## 1. Route map (AssanPay legacy → Switch)

| # | Legacy (AssanPay) | Switch route | Request entity | Response notes |
|---|-------------------|--------------|----------------|----------------|
| — | (channel auth) | `POST /api/v1/auth/token` | client_id/secret | JWT access + refresh |
| — | — | `POST /api/v1/auth/refresh` | refresh_token | Rotated tokens |
| — | — | `POST /api/v1/auth/logout` | — | Revoke |
| 6 | Account/Login | `POST /api/v1/account/login` | LoginRequestEntity | `strToken` |
| 7 | Account/Register | `POST /api/v1/account/register` | RegisterRequestEntity | flat `00` |
| 13 | CustomerDetail | `POST /api/v1/account/customer-detail` | CustomerDetailRequestEntity | nested `Response` |
| 8 | Account/GetAccountList | `POST /api/v1/account/account-list` | AccountListRequestEntity | `Response.CASA_Account_List` |
| — | (new) | `POST /api/v1/account/open` | AccountOpenRequest (additive) | **MUST** return `accountNumber` + `IBAN` |
| 9 | UploadDocumentsUnikrew | `POST /api/v1/account/upload-documents-unikrew` | UploadDocumentUnikrewRequestEntity | OCR + face compare |
| 12 | ValidateDocumentUnikrew | `POST /api/v1/account/validate-document-unikrew` | ValidateDocumentUnikrewRequestEntity | authenticity |
| 11 | VerifyLiveliness | `POST /api/v1/account/verify-liveliness` | VerifyLivelinessRequestEntity | StatusCode 200 |
| 10 | VerifyFingers | `POST /api/v1/account/verify-fingers` | VerifyFingerRequestEntity | Status_Code 100 |
| 1 | Payment/IFT | `POST /api/v1/payment/ift` | IftPaymentRequestEntity | **nested `return`** |
| 2 | Inquiry/IBFT (pay) | `POST /api/v1/payment/ibft` | IbftPaymentRequestEntity | **flat** Code/Desc only |
| 3 | Inquiry/IFT (title) | `POST /api/v1/inquiry/ift-title` | IftTitleFetchRequestEntity | nested `return` |
| 4 | Inquiry/IBFT (title) | `POST /api/v1/inquiry/ibft-title` | IbftTitleFetchRequestEntity | **double-nested** |
| 5 | Inquiry/Balance | `POST /api/v1/inquiry/balance` | BalanceInquiryRequestEntity | capital **`Return`** |
| 14 | Inquiry/CNIC | `POST /api/v1/inquiry/cnic` | CnicTitleFetchRequestEntity | `Res.GetAccountFromCNICItems` |
| — | (new) | `POST /api/v1/inquiry/mini-statement` | MiniStatementRequest | last N txns |
| — | (new) | `POST /api/v1/inquiry/statement` | StatementRequest | date range |
| — | (new) | `POST /api/v1/inquiry/ibft-status` | IbftStatusRequest | Poll 2s/30s; stan|transactionID |
| — | (new) | `POST /api/v1/inquiry/limits` | LimitsRequest | **MUST** — remaining daily limit |
| — | (new) | `POST /api/v1/inquiry/receipt` | ReceiptRequest | **MUST** — by STAN/ref |
| — | (new) | `POST /api/v1/auth/otp/send\|verify` | Otp* | Mock OTP `1234` before IBFT |
| — | (new) | `POST /api/v1/account/approve-mock` | ApproveMock | Sandbox odd-CNIC |
| — | (new) | `POST /api/v1/account/change-password` | ChangePassword | Authenticated |
| — | (new) | `POST /api/v1/account/reset-password-mock` | ResetMock | Sandbox |
| — | (new) | `POST /api/v1/accounts/information` | AccountInfoRequest | product metadata |
| — | (new) | `GET /api/v1/system/health` | — | AppInSnap smoke |
| — | (new) | `GET /api/v1/system/version` | — | app + catalogVersion |

### Lookups (Phase 0 CRITICAL — master data)

| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/api/v1/lookups/banks` | Channel JWT | IBFT bank picker; ETag |
| GET | `/api/v1/lookups/banks/{imd}` | Channel JWT | Single bank |
| GET | `/api/v1/lookups/purpose-of-payment` | Channel JWT | IFT/IBFT purpose |
| GET | `/api/v1/lookups/purpose-of-account` | Channel JWT | Account open |
| GET | `/api/v1/lookups/occupations` | Channel JWT | Account open |
| GET | `/api/v1/lookups/response-codes` | Channel JWT | UX mapping |
| GET | `/api/v1/lookups/account-types` | Channel JWT | |
| GET | `/api/v1/lookups/provinces` | Channel JWT | |
| GET | `/api/v1/lookups/id-types` | Channel JWT | |
| GET | `/api/v1/lookups/app-config` | Channel JWT | Public only |
| GET | `/api/v1/lookups/finger-indexes` | Channel JWT | |
| GET | `/api/v1/lookups/onboarding-steps` | Channel JWT | |
| GET | `/api/v1/lookups/branches` | Channel JWT | Optional |
| GET | `/api/v1/lookups/currencies` | Channel JWT | PKR |
| GET | `/api/v1/lookups/version` | Channel JWT | catalogVersion |

**Lookups auth:** Channel JWT only.  
**Consumability:** [../handoff/required-fields-matrix.md](../handoff/required-fields-matrix.md), [../handoff/headers-and-auth.md](../handoff/headers-and-auth.md), [../handoff/error-handling.md](../handoff/error-handling.md).

OpenAPI: `docs/openapi/assanpay-switch-v1.yaml`.

---

## 2. Critical response shape quirks (must not “fix”)

| API | Shape |
|-----|--------|
| IFT Payment | `{ "return": { "status": "00", ... }, "Response_Code": "00", "Response_Desc": "Success" }` |
| IBFT Payment | `{ "Response_Code": "00", "Response_Desc": "Success" }` only (flat) |
| IBFT Title | `{ "IBFTTitleFetchResponse": { "return": { ... } }, "Response_Code": "00", ... }` |
| Balance | `{ "Return": { "Amount", "BranchCode", "FromAccount", "CURRENCY_CODE" }, ... }` — capital **R** |
| Wire typos | Preserve aliases: `Cusomter_ID`, `Langitude` (also accept corrected spellings) |

---

## 3. Field ownership — Mobile vs Switch

### 3.1 Switch-injected (env/config — overwrite if mobile sends)

| Category | Fields | Example env keys |
|----------|--------|------------------|
| Envelope | `strAppID`, `strADC_TranCode`, `strDelimiter`, `strNetID`, `strMerName`, `strMCC`, `channelId`, `Channel_type` | `SWITCH_APP_ID`, `SWITCH_ADC_TRAN_CODE_*`, `SWITCH_DELIMITER`, `SWITCH_NET_ID`, `SWITCH_MERCHANT_NAME`, `SWITCH_MCC`, `SWITCH_CHANNEL_ID` |
| Processing | `processingCode` per operation | `SWITCH_PROC_IFT`, `SWITCH_PROC_IBFT`, … |
| Bank identity | `agentAccount`, default `branchCode`, bank IMD, IBAN bank code | `BANK_AGENT_ACCOUNT`, `BANK_BRANCH_DEFAULT`, `BANK_IMD`, `BANK_IBAN_CODE` |
| iMAL | Base URL, client id/secret, timeouts, CB | `IMAL_*`, `MOCK_IMAL` |
| KYC vendors | Unikrew user/pass, Liveliness `XApi`, NADRA URL | `UNIKREW_*`, `LIVELINESS_API_KEY`, `NADRA_VERIFY_URL` |
| JWT | Signing material, TTLs | `JWT_*` |
| Limits | Asaan max balance / daily debit | `ASAAN_MAX_BALANCE`, `ASAAN_DAILY_DEBIT_LIMIT` |

### 3.2 Mobile-provided (business intent)

| Category | Fields |
|----------|--------|
| Identity | `CNIC`, `UserName`, `CustomerID`/`Cusomter_ID`, `citizenNumber`, `MobileNo`, `contactNumber` |
| Credentials | Customer password / login secrets (never logged) |
| Money movement | `fromAccount`, `toAccount`, `fromIBAN`, `toIBAN`, `toIMD`, `amount`, titles, `Description`, `PurposeOfPayment`, `AccountNo` |
| Device audit | `Client_IP`, `Device_*`, `UUID`, geo, `Application_Version` |
| Idempotency / refs | `intRefNum`, `intSTAN`, `stan`, `transactionID`, `Idempotency-Key`, `X-Correlation-Id` |
| KYC payloads | base64 images, `fingerTemplate`, comparison flags |
| Statements | `fromDate`, `toDate`, pagination |

### 3.3 Hybrid

| Field | Rule |
|-------|------|
| `strDate` / `strTime` / `transmissionDateTime` | Accept if valid; else Switch generates (`Asia/Karachi`) |
| `strToken` | Must match Switch-issued session; ignore forged claims |
| `password` on payment envelopes | Legacy AssanPay field — **do not** treat as iMAL channel password; channel auth is JWT |
| `userId` | Must match authenticated customer |

### 3.4 Per-API matrix (summary)

| API | Mobile owns | Switch injects |
|-----|-------------|----------------|
| auth/token | client_id, client_secret | JWT TTLs, signing |
| login / register | UserName, CNIC, device, credentials, AccountNo/Mobile | Envelope + token issuance |
| Unikrew / Liveliness / NADRA | Images/templates, CNIC, flags | API keys, XApi, vendor URLs, vendor passwords |
| IFT / IBFT | Accounts, amount, titles, purpose **from lookups**, stan/refs, device, toIMD from banks list | AppID, codes, agentAccount, processingCode, channelId |
| Balance / Title / CNIC | Account / CNIC / toIMD | Envelope |
| Account open / Statement | Customer + account + dates | Product codes, limits, IBAN bank code |
| **Lookups** | Query filters only (`active`, `q`, cursor) | All catalog rows from `ref_*` |

**Mobile must never be trusted for:** `Response_Code`, balances, KYC approve/reject, self-issued `strToken`, Unikrew/Liveliness credentials, fee/limit overrides, ownership of accounts, **bank list contents** (must come from Switch).

---

## 4. Auth model for AppInSnap

1. **Channel:** `POST /api/v1/auth/token` (OAuth2 client_credentials) → Bearer JWT (15 min) + refresh (7d, rotated).
2. **Customer:** `POST /api/v1/account/login` → `strToken` (customer-scoped JWT).
3. Protected ops require channel Bearer; customer-scoped ops also require valid `strToken` (body field and/or `X-Str-Token` / `X-Customer-Token`).
4. **Lookups:** channel JWT only.
5. Financial POSTs require `Idempotency-Key` (or STAN/ref composite).

---

## 5. Sample lookup JSON (banks)

```json
{
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "catalogVersion": 3,
  "asOf": "2026-09-28T08:00:00.000Z",
  "items": [
    {
      "imd": "601001",
      "bankCode": "HBL",
      "bankShortName": "HBL",
      "bankName": "Habib Bank Limited",
      "ibanBankCode": "HABP",
      "supportsIbft": true,
      "isActive": true,
      "logoUrl": null,
      "sortOrder": 10
    }
  ],
  "nextCursor": null,
  "Response_Code": "00",
  "Response_Desc": "Success"
}
```

IBFT client sequence: **banks → ibft-title → ibft pay → ibft-status**. See [../handoff/appinsnap-integration-cookbook.md](../handoff/appinsnap-integration-cookbook.md).

---

## 6. AppInSnap integration notes

- Start against Mock profile; no real iMAL needed.
- **Load lookups before building IBFT/onboarding UI** — do not hardcode banks or purpose codes.
- Keep parsers for nested/flat quirks exactly as AssanPay.
- Additive APIs: lookups, account open (accountNumber+IBAN), ibft-status, mini/statement, system health, channel auth.
- Confirm with AppInSnap: tolerance for `correlationId`, multi-account `Item` as array when >1, payment envelope `password`, OTP/MPIN Phase 1 need.