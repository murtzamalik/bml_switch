# Lookups & Master Data Catalog

**Bounded context:** Lookups  
**Package:** `com.bankalmurqarmah.switchapp.lookups`  
**Auth:** Channel JWT required; **customer `strToken` NOT required**  
**Base path:** `/api/v1/lookups/**`  
**Admin:** Flyway seed only in MVP (no admin UI)

---

## 1. Purpose

AppInSnap cannot utilize payment/onboarding APIs without **supporting lookup data** (banks for IBFT, purpose codes, provinces, etc.). Internal `mock_banks` alone was half-cooked — this catalog is the **AppInSnap-facing** contract + persistence design.

---

## 2. Reference tables

| Table | Replaces / new | Purpose |
|-------|----------------|---------|
| `ref_banks` | **replaces `mock_banks`** | IBFT member banks (IMD, names, IBAN bank code, flags) |
| `ref_purpose_codes` | new | PurposeOfPayment for IFT/IBFT |
| `ref_response_codes` | new | Subset of business codes for mobile UX mapping |
| `ref_account_types` | new | CURRENT / SAVINGS / ASAAN_DIGITAL etc. |
| `ref_provinces` | new | NADRA `areaName` / onboarding |
| `ref_id_types` | new | `id_type` on title fetch (CNIC, NTN, …) |
| `ref_app_config` | new | Safe public mobile config (limits display, statement N, …) |
| `ref_finger_indexes` | new | Allowed finger indexes for NADRA mock |
| `ref_onboarding_steps` | new | step_id / status_id meanings for KYC UX |
| `ref_branches` | optional | Mock branches if `branchCode` needed |
| `ref_currencies` | should | PKR now; list for future |

**Conventions:** UUID PK; soft delete `deleted_at`; `is_active`; `created_at`/`updated_at`; catalog `version` + `as_of` for cache busting.

### 2.1 `ref_banks` (critical)

| Column | Notes |
|--------|--------|
| `imd` | Unique active IMD (MOCK series until bank provides official 1LINK list) |
| `bank_code` / `short_name` / `legal_name` | Display |
| `iban_bank_code` | 4-letter style code where applicable |
| `supports_ibft` / `is_active` | Filters |
| `logo_url` | Optional nullable |
| `sort_order` | UI ordering |

IBFT payment validation: `toIMD` **must** exist in `ref_banks` with `supports_ibft=true` and `is_active=true`.

### 2.2 Seed — Pakistan major banks (MOCK IMDs)

Document in Flyway comments: **MOCK until official list**. Include at minimum:

HBL, UBL, MCB, Meezan, BankIslami, Allied, Askari, Alfalah, JS Bank, Standard Chartered, NBP, BOP, Faysal, Dubai Islamic, Bank Al Habib, Soneri, Silk, **Bank Al Murqarmah** (own `BANK_IMD`).

Use a dedicated mock IMD series (e.g. `601001`…) — never invent production IMDs as “real”.

---

## 3. API catalog

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/v1/lookups/banks` | `?active=true` (default), `?supportsIbft=true`, `?q=`, cursor pagination `cursor`/`limit` (default 50, max 200) |
| GET | `/api/v1/lookups/banks/{imd}` | Single bank; 404 if missing/inactive |
| GET | `/api/v1/lookups/purpose-of-payment` | Alias also `/purpose-codes` |
| GET | `/api/v1/lookups/purpose-of-account` | Account opening (≠ payment purpose) |
| GET | `/api/v1/lookups/occupations` | Account opening SBP-style dropdown |
| GET | `/api/v1/lookups/response-codes` | Subset: `00`, NSF, invalid account, invalid IMD, limit, auth, etc. |
| GET | `/api/v1/lookups/account-types` | |
| GET | `/api/v1/lookups/provinces` | |
| GET | `/api/v1/lookups/id-types` | |
| GET | `/api/v1/lookups/app-config` | `is_public=1` keys only |
| GET | `/api/v1/lookups/finger-indexes` | |
| GET | `/api/v1/lookups/onboarding-steps` | Ordered by ordinal |
| GET | `/api/v1/lookups/branches` | If seeded |
| GET | `/api/v1/lookups/currencies` | PKR |
| GET | `/api/v1/lookups/version` | `{ catalogVersion, asOf, correlationId }` |

### 3.1 Sample — banks list

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

### 3.2 Sample — purpose of payment

```json
{
  "correlationId": "...",
  "catalogVersion": 3,
  "asOf": "2026-09-28T08:00:00.000Z",
  "items": [
    { "code": "FAM", "description": "Family Support", "appliesTo": ["IFT", "IBFT"], "isActive": true },
    { "code": "SAL", "description": "Salary", "appliesTo": ["IFT", "IBFT"], "isActive": true },
    { "code": "GDS", "description": "Goods Payment", "appliesTo": ["IFT", "IBFT"], "isActive": true }
  ],
  "Response_Code": "00",
  "Response_Desc": "Success"
}
```

### 3.3 Sample — occupations / purpose-of-account

```json
{
  "correlationId": "...",
  "catalogVersion": 3,
  "items": [
    { "code": "EMP", "description": "Salaried Employee", "isActive": true },
    { "code": "SLF", "description": "Self Employed", "isActive": true }
  ],
  "Response_Code": "00",
  "Response_Desc": "Success"
}
```

```json
{
  "items": [
    { "code": "SAV", "description": "Savings / Personal", "isActive": true },
    { "code": "SAL", "description": "Salary Credit", "isActive": true }
  ],
  "Response_Code": "00",
  "Response_Desc": "Success"
}
```

### 3.4 Sample — app-config (public)

```json
{
  "correlationId": "...",
  "catalogVersion": 3,
  "items": {
    "currencyDefault": "PKR",
    "miniStatementCount": 10,
    "statementMaxDays": 90,
    "asaanMaxBalanceDisplay": "1000000",
    "asaanDailyDebitLimitDisplay": "200000",
    "ibftEnabled": true,
    "iftEnabled": true,
    "mockOtp": true,
    "lookupsVersion": "3",
    "mockImal": true
  },
  "Response_Code": "00",
  "Response_Desc": "Success"
}
```

**Never** expose: JWT secrets, DB passwords, Unikrew/Liveliness/NADRA credentials, iMAL client secrets.

---

## 4. Cache rules

| Mechanism | Rule |
|-----------|------|
| `ETag` | Hash of `catalogVersion` + resource + filter |
| `Cache-Control` | `private, max-age=300, must-revalidate` |
| `If-None-Match` | → HTTP **304** |
| Body | Always include `catalogVersion` + `asOf` |
| Bust | Flyway seed/migration bumps `ref_app_config.lookupsVersion` / table versions |

---

## 5. Security

- Channel Bearer JWT mandatory on all lookup routes (no anonymous bank scrape).
- Soft rate limit recommended (e.g. 60/min/client).
- Read-only; no write APIs in MVP.

---

## 6. Cross-BC usage

| Consumer | Uses |
|----------|------|
| Payments IBFT | Validate `toIMD` against `ref_banks` |
| Payments IFT/IBFT | Validate `PurposeOfPayment` against `ref_purpose_codes` |
| Onboarding | Provinces, finger indexes, onboarding steps, account types |
| Inquiry title | Optional `id_type` against `ref_id_types` |
| AppInSnap UI | All lists for pickers / error mapping / config |

Lookups BC does **not** call `ImalPort`. Other BCs query via application services / repositories — no cross-module entity leak.
