# AppInSnap handoff pack

**Audience:** AppInSnap mobile / integration engineers  
**Product:** Bank Al Murqarmah Integration Switch (sandbox Phase 0)  
**Mock OTP:** **`1234`**

## Share these PDFs (preferred)

| PDF | Purpose |
|-----|---------|
| **[pdf/BML-Switch-AppInSnap-Pack.pdf](./pdf/BML-Switch-AppInSnap-Pack.pdf)** | Full pack (API + Postman + test data + index) |
| [pdf/BML-Switch-API-Guide.pdf](./pdf/BML-Switch-API-Guide.pdf) | API reference |
| [pdf/BML-Switch-Postman-Guide.pdf](./pdf/BML-Switch-Postman-Guide.pdf) | Postman how-to |
| [pdf/BML-Switch-Test-Data.pdf](./pdf/BML-Switch-Test-Data.pdf) | Credentials & accounts |

Also share: [BML-Switch-Phase0.postman_collection.json](./BML-Switch-Phase0.postman_collection.json)

## Markdown sources (internal / editable)

| Doc | Purpose |
|-----|---------|
| **[APPINSNAP-API-GUIDE.md](./APPINSNAP-API-GUIDE.md)** | Main API reference: auth, endpoints, samples, flows, test data |
| **[APPINSNAP-POSTMAN-GUIDE.md](./APPINSNAP-POSTMAN-GUIDE.md)** | How to import and run the Postman collection |
| **[APPINSNAP-TEST-DATA.md](./APPINSNAP-TEST-DATA.md)** | Standalone sandbox credentials & accounts (Slack/email friendly) |
| [BML-Switch-Phase0.postman_collection.json](./BML-Switch-Phase0.postman_collection.json) | Ready-to-import Postman collection |
| [appinsnap-integration-cookbook.md](./appinsnap-integration-cookbook.md) | Longer recipes (onboarding Path A/B, edge cases) |
| [headers-and-auth.md](./headers-and-auth.md) | Locked header + dual-auth rules |
| [required-fields-matrix.md](./required-fields-matrix.md) | Field R/O/I matrix |
| [error-handling.md](./error-handling.md) | HTTP vs `Response_Code` |
| [smoke-curl.md](./smoke-curl.md) · [smoke-test-checklist.md](./smoke-test-checklist.md) | Manual / scripted verification |

**OpenAPI / live docs**

- Spec: [`docs/openapi/assanpay-switch-v1.yaml`](../openapi/assanpay-switch-v1.yaml)
- Shape examples: [`docs/openapi/assanpay-examples.md`](../openapi/assanpay-examples.md)
- Swagger UI (when stack is up): `http://localhost:8080/swagger-ui.html`

**Bring-up (one-liner)**

```bash
docker compose up --build -d && ./scripts/smoke.sh
```

Base URL (local Docker): `http://localhost:8080`
