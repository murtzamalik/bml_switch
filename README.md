# BML Switch — Bank Al Murqarmah Integration Layer

Spring Boot switch between **AppInSnap** mobile and **iMAL** (mock in Phase 0).

## Quick start (local)

```bash
cp .env.example .env
docker compose up --build -d
curl -s http://localhost:8080/api/v1/system/health
./scripts/smoke.sh
```

Swagger: http://localhost:8080/swagger-ui.html

## Server deploy (isolated)

Does **not** publish MySQL on the host. App on host port **18080**.

```bash
cp .env.example .env
docker compose -p bml_switch -f docker-compose.server.yml up -d --build
curl -s http://localhost:18080/api/v1/system/health
```

Open firewall / security group: **TCP 18080**

## AppInSnap handoff

| Artifact | Path |
|----------|------|
| **PDF pack (share this)** | [`docs/handoff/pdf/BML-Switch-AppInSnap-Pack.pdf`](docs/handoff/pdf/BML-Switch-AppInSnap-Pack.pdf) |
| API Guide PDF | `docs/handoff/pdf/BML-Switch-API-Guide.pdf` |
| Postman Guide PDF | `docs/handoff/pdf/BML-Switch-Postman-Guide.pdf` |
| Test Data PDF | `docs/handoff/pdf/BML-Switch-Test-Data.pdf` |
| Postman collection | `docs/handoff/BML-Switch-Phase0.postman_collection.json` |

Sandbox OTP: **`1234`** · Channel: `appinsnap-sandbox` / `change_me_sandbox_secret`

## Security

- Never commit `.env`
- Change default DB/JWT secrets before production
- Rotate any SSH credentials shared in chat
