# Docker Runtime — `bml_switch`

**Topology (MVP):** `app` (Spring Boot) + `mysql` (8.4)  
**Mock iMAL:** in-process (`MOCK_IMAL=true`). Optional `mock-imal` compose profile deferred.

---

## 1. Services

| Service | Image / build | Port | Role |
|---------|---------------|------|------|
| `mysql` | `mysql:8.4` | 3306 | App + mock ledger + audit |
| `app` | Multi-stage Dockerfile (Temurin 21) | 8080 | Integration Switch |
| `mock-imal` (optional profile) | Future HTTP sidecar | 8081 | Same `ImalPort` HTTP surface |

---

## 2. Dockerfile (target design)

- **Builder:** `eclipse-temurin:21-jdk` → Maven package  
- **Runtime:** `eclipse-temurin:21-jre`, non-root user, expose 8080  
- **HEALTHCHECK:** `GET /actuator/health/liveness`  
- **`.dockerignore`:** `.git`, `target`, `.env`, IDE files  

Pin digests for UAT/prod; never rely on `latest`.

---

## 3. docker-compose (conceptual)

```yaml
services:
  mysql:
    image: mysql:8.4
    environment:
      MYSQL_DATABASE: bml_switch
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    ports: ["3306:3306"]
    volumes: [mysql_data:/var/lib/mysql]
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "127.0.0.1"]
      interval: 10s
      retries: 10

  app:
    build: .
    depends_on:
      mysql: { condition: service_healthy }
    env_file: .env
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-local}
      MOCK_IMAL: ${MOCK_IMAL:-true}
    ports: ["8080:8080"]
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://127.0.0.1:8080/actuator/health"]
      interval: 15s
      retries: 5

volumes:
  mysql_data:
```

Bring-up: `docker compose up --build`  
Profiles: `local` (default), `mock`, `uat`.

---

## 4. Spring profiles

| Profile | Behavior |
|---------|----------|
| **local** | Compose MySQL, `MOCK_IMAL=true`, verbose logs |
| **mock** | Seeded `ref_banks` / `ref_*` + deterministic KYC stubs |
| **uat** | Real/UAT iMAL URLs, `MOCK_IMAL=false`, actuator hardened |

Property: `switch.imal.mock=${MOCK_IMAL}`.

---

## 5. `.env.example` catalog

```bash
# Runtime
SPRING_PROFILES_ACTIVE=local
SERVER_PORT=8080
MOCK_IMAL=true
MOCK_OTP=true

# Datasource
MYSQL_HOST=mysql
MYSQL_PORT=3306
MYSQL_DATABASE=bml_switch
MYSQL_USER=bml_switch
MYSQL_PASSWORD=change_me
MYSQL_ROOT_PASSWORD=change_me_root
SPRING_DATASOURCE_URL=jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DATABASE}?useSSL=false&allowPublicKeyRetrieval=true
SPRING_DATASOURCE_USERNAME=${MYSQL_USER}
SPRING_DATASOURCE_PASSWORD=${MYSQL_PASSWORD}
FLYWAY_ENABLED=true

# Channel JWT
JWT_SECRET=change_me_min_32_chars_256bit_entropy____
JWT_ACCESS_TTL_SECONDS=900
JWT_REFRESH_TTL_SECONDS=604800

# Switch envelope (injected — not trusted from mobile)
SWITCH_APP_ID=MB
SWITCH_DELIMITER=^
SWITCH_NET_ID=BML
SWITCH_MERCHANT_NAME=BankAlMurqarmah
SWITCH_MCC=6012
SWITCH_CHANNEL_ID=MOBILE
SWITCH_ADC_TRAN_CODE_IFT=IFT001
SWITCH_ADC_TRAN_CODE_IBFT=IBFT001
SWITCH_PROC_IFT=000000
SWITCH_PROC_IBFT=000000

# Bank identity
BANK_AGENT_ACCOUNT=
BANK_BRANCH_DEFAULT=001
BANK_IMD=627000
BANK_IBAN_CODE=BMAL

# Asaan conceptual limits
ASAAN_MAX_BALANCE=1000000
ASAAN_DAILY_DEBIT_LIMIT=200000

# iMAL
IMAL_BASE_URL=http://localhost:8081
IMAL_CONNECT_TIMEOUT_MS=3000
IMAL_READ_TIMEOUT_MS=10000
IMAL_CLIENT_ID=
IMAL_CLIENT_SECRET=

# Vendor mocks / future live
LIVELINESS_API_URL=
LIVELINESS_API_KEY=
UNIKREW_API_URL=
UNIKREW_USERNAME=
UNIKREW_PASSWORD=
NADRA_VERIFY_URL=

# Resilience4j
R4J_CB_FAILURE_RATE=50
R4J_CB_WAIT_SECONDS=30

# Actuator
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics
MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true
MANAGEMENT_ENDPOINT_ENV_ENABLED=false

LOG_LEVEL_ROOT=INFO
```

Commit **only** `.env.example`. Never commit `.env`.

---

## 6. Health & Actuator

| Endpoint | local/mock | uat/prod |
|----------|------------|----------|
| `/actuator/health` | yes | yes (sanitized) |
| `/actuator/health/liveness` | yes | yes |
| `/actuator/health/readiness` | yes (DB) | yes |
| `/actuator/info` | yes | build info only |
| `/actuator/prometheus` | optional | private scrape |
| `/actuator/env`, `/beans`, `/mappings` | off (local override only) | **always off** |

Readiness fails if MySQL down. iMAL CB open ≠ kill pod (component detail only unless configured critical).

---

## 7. CI / delivery notes

- Pipeline: build → unit → Testcontainers Flyway migrate → contract tests.  
- Image reused for UAT with env/secrets injected.  
- K8s later: same Dockerfile; ConfigMap + Secret for env catalog.  
- 12-factor: config via env; no baked secrets; disposability via health probes.

---

## 8. Local demo script (target)

```bash
cp .env.example .env
docker compose up --build
# wait for healthy
curl -s http://localhost:8080/actuator/health
# then: auth/token → account/login → inquiry/balance (Postman collection TBD in impl)
```
