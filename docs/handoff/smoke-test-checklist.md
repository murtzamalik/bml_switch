# Smoke Test Checklist — AppInSnap (≈15 minutes)

**Prereq:** `docker compose up --build` healthy; OpenAPI + this handoff folder.

| # | Step | Call | Pass |
|---|------|------|------|
| 1 | Health | `GET /api/v1/system/health` | `status=UP`, `mockImal=true` |
| 2 | Channel token | `POST /auth/token` | `access_token`, `expires_in=900` |
| 3 | Lookups banks | `GET /lookups/banks?active=true` | ≥16 banks, has `imd` |
| 4 | Purpose + occupations | `GET /lookups/purpose-of-payment`, `/occupations`, `/purpose-of-account` | non-empty |
| 5 | Login | `POST /account/login` demo user | `strToken`, code `00` |
| 6 | Account list | `POST /account/account-list` | ≥2 accounts incl. IBANs + second funded |
| 7 | Balance | `POST /inquiry/balance` | `Return.Amount`, capital `Return` |
| 8 | IFT title + pay | title then IFT to `0345001234568` | nested `return.status=00` |
| 9 | Mini-statement | `POST /inquiry/mini-statement` | sees IFT |
| 10 | IBFT banks→title | pick MOCK IMD + title | double-nested title |
| 11 | Mock OTP | send + verify `1234` | `otpTicket` |
| 12 | IBFT pay + status | pay with otpTicket; poll status ≤30s | terminal POSTED |
| 13 | Limits + receipt | `/inquiry/limits`, `/inquiry/receipt` | remaining limit; receipt by stan |
| 14 | Bad token | call balance without strToken | HTTP 401, code `91` |
| 15 | Unknown IMD | IBFT title bad IMD | business non-00 / `76` |

**Sign-off:** AppInSnap engineer initials + date + `correlationId` from step 12.
