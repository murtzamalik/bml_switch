# AppInSnap — Postman Collection Guide

**Audience:** AppInSnap mobile / QA  
**Collection:** [`BML-Switch-AIS.postman_collection.json`](./BML-Switch-AIS.postman_collection.json)  
**API doc:** [APPINSNAP-API-GUIDE.md](./APPINSNAP-API-GUIDE.md)

---

## 1. Import

1. Postman → **Import** → select `docs/handoff/BML-Switch-AIS.postman_collection.json`
2. Collection name: **BML Switch — AppInSnap (AIS)**
3. Open **Variables** and set:

| Variable | Default | Notes |
|----------|---------|-------|
| `baseUrl` | `http://localhost:8080` | Server deploy often `http://<host>:18080` |
| `staticToken` | `BML-POC-STATIC-TOKEN-2026-AIS` | Non-expiring; override if bank changes `SWITCH_STATIC_TOKEN` |
| `cnicNew` | `4589652158800` | Use a **fresh** CNIC for each real open |
| `cnicExisting` | `4210112345678` | Seed / already-opened customer |
| `accGl` / `productCode` | `203153` / `ASAAN_DIGITAL` | Match bank product |

Collection auth is already `Bearer {{staticToken}}` — no `/auth/token` step.

---

## 2. Run order (AIS)

| # | Folder / request | Purpose |
|---|------------------|---------|
| 1 | `00 — Setup` → health | Confirm UP; note `mockImal` |
| 2 | `01 — Flow 1` → **account/open** | New account (live iMal) |
| 3 | `02 — Flow 2` → **account/account-list** | Accounts + balances by CNIC |
| 4 | `03 — Lookups` | Banks / purpose (do not hardcode) |
| 5 | `04 — Inquiry` | Balance, titles, limits, statements |
| 6 | `05 — Payments` | IFT (live SOAP); IBFT = OTP send → verify (`1234`) → ibft |

Removed from switch (AIS owns): login, register, KYC, approve-mock, change-password.

---

## 3. Auth reminder

```http
Authorization: Bearer BML-POC-STATIC-TOKEN-2026-AIS
Content-Type: application/json
```

Health only is public (no Bearer).

---

## 4. Quick checks

```bash
curl -s http://localhost:8080/api/v1/system/health
# or server:
curl -s http://46.224.146.158:18080/api/v1/system/health
```

Legacy Phase-0 collection (`BML-Switch-Phase0.postman_collection.json`) is outdated (login / JWT flow) — use **AIS** collection above.
