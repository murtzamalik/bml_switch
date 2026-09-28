# Mock iMAL API Contract (internal adapter)

**Purpose:** Internal contract implemented by `MockImalAdapter` (and later by `RealImalAdapter`).  
**Not** exposed to AppInSnap. AppInSnap only sees AssanPay-shaped `/api/v1/**` APIs.  
**Flag:** `MOCK_IMAL=true` (default MVP).

Base internal path (in-process; no HTTP required for MVP). If a sidecar is introduced later, mirror these as REST under `http://mock-imal:8081/imal/v1`.

---

## 1. Design principles

1. Persistent **MySQL ledger** so balance, IFT/IBFT, and statements stay consistent.
2. Idempotent financial posts keyed by channel + STAN / idempotency key.
3. Simulate Pakistan patterns: Asaan Digital account open, IBFT title→push (mock 1LINK), IFT same-bank.
4. Channel auth to core: mock token endpoint for bank-to-core (Switch injects credentials from env).
5. Money: `DECIMAL(19,4)` / `BigDecimal`; currency always `PKR` in MVP.

---

## 2. Operations (`ImalPort`)

| Operation | Description | Idempotent |
|-----------|-------------|------------|
| `authenticateChannel` | Bank channel token (client id/secret from env) | Yes |
| `fetchCustomerDetail` | Profile by CNIC | Yes |
| `listAccountsByCnic` | CASA accounts for CNIC | Yes |
| `openAccount` | Asaan Digital–style CURRENT account | Yes (same CNIC+product → existing) |
| `balanceInquiry` | Available balance for account | Yes |
| `iftTitleFetch` | Intra-bank account title | Yes |
| `iftPayment` | Same-bank transfer | Yes (key/STAN) |
| `ibftTitleFetch` | Interbank title by IBAN/IMD+account | Yes |
| `ibftPayment` | Interbank push (mock 1LINK) | Yes |
| `ibftStatusInquiry` | Optional status by transactionID | Yes |
| `statementFetch` | Date-range or last-N postings | Yes |

---

## 3. OpenAPI-style endpoint sketch (sidecar / documentation form)

```yaml
openapi: 3.0.3
info:
  title: Mock iMAL Internal API
  version: 1.0.0
paths:
  /imal/v1/auth/token:
    post:
      summary: Channel credentials → core token
  /imal/v1/customers/{cnic}:
    get:
      summary: Customer detail
  /imal/v1/customers/{cnic}/accounts:
    get:
      summary: List accounts by CNIC
  /imal/v1/accounts:
    post:
      summary: Open Asaan Digital account
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required: [cnic, fullName, mobile, productCode]
              properties:
                cnic: { type: string }
                fullName: { type: string }
                mobile: { type: string }
                productCode: { type: string, enum: [ASAAN_DIGITAL] }
                branchCode: { type: string }
                currency: { type: string, enum: [PKR] }
  /imal/v1/accounts/{accountNumber}/balance:
    get:
      summary: Balance inquiry
  /imal/v1/accounts/{accountNumber}/title:
    get:
      summary: IFT title fetch
  /imal/v1/transfers/ift:
    post:
      summary: IFT payment
      parameters:
        - in: header
          name: Idempotency-Key
          required: true
      requestBody:
        content:
          application/json:
            schema:
              type: object
              required: [fromAccount, toAccount, amount, currency, stan]
              properties:
                fromAccount: { type: string }
                toAccount: { type: string }
                amount: { type: string }
                currency: { type: string, enum: [PKR] }
                stan: { type: string }
                narration: { type: string }
                purposeOfPayment: { type: string }
  /imal/v1/transfers/ibft/title:
    post:
      summary: IBFT title fetch
      requestBody:
        content:
          application/json:
            schema:
              type: object
              properties:
                toAccount: { type: string }
                toIBAN: { type: string }
                toIMD: { type: string }
  /imal/v1/transfers/ibft:
    post:
      summary: IBFT payment
      parameters:
        - in: header
          name: Idempotency-Key
          required: true
  /imal/v1/transfers/ibft/{transactionId}/status:
    get:
      summary: IBFT status inquiry
  /imal/v1/accounts/{accountNumber}/statements:
    get:
      summary: Mini or date-range statement
      parameters:
        - name: mode
          in: query
          schema: { type: string, enum: [mini, range] }
        - name: fromDate
          in: query
          schema: { type: string, format: date }
        - name: toDate
          in: query
          schema: { type: string, format: date }
        - name: limit
          in: query
          schema: { type: integer, default: 10 }
```

---

## 4. Mock ledger behaviour

| Rule | Detail |
|------|--------|
| Seed | Demo customer CNIC `4210112345678`, account `0345001234567`, funded balance e.g. `150000.00` PKR |
| Zero-balance account | `0345009999999` for insufficient-funds tests |
| Invalid account | `0345000000000` → title/balance fail |
| IFT | Both accounts must exist, ACTIVE, same bank; debit+credit atomic |
| IBFT | Debit originator; credit is external (no local credit); require known `toIMD` in `ref_banks` |
| IBAN mock | `PK` + bank code `BMAL` (placeholder) + BBAN from account |
| Asaan limits (config) | Max balance `1000000`, daily debit `200000` (conceptual; bank circulars supersede) |
| Response codes | `00` success; map insufficient funds / invalid / limit to stable non-00 codes documented in OpenAPI |

---

## 5. Vendor mocks (not iMAL, separate ports)

| Port | MVP behaviour |
|------|----------------|
| `UnikrewPort` | Even CNIC last digit → confidence `0.95` auto-approve; odd → `0.45` manual review |
| `NadraPort` | Configurable `100`/`101`/`119`/`122` |
| `LivelinessPort` | Threshold pass → StatusCode `200` |
| `SanctionsPort` | Stub ALLOW |

Credentials for these vendors are **env-only**; never required from mobile.
