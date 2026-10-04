# MediSphere Cognitive Twin

AI-based healthcare management platform — clinical operations, digital health twins, real-time vitals monitoring, AI risk prediction, and care plan management.

---

## Architecture

```
Browser (React 18 + TypeScript + Vite + Tailwind)
    │  REST + JWT          │  WebSocket / STOMP
    ▼                      ▼
Spring Boot 3.x (Java 21)  ←──  Apache Kafka 3.7 (KRaft)
    │                               │
    ▼                               ▼
MongoDB 7.0                  Wearable Simulator
    │                         (dev profile only)
    ▼
FHIR Layer (mock | live)
AI Layer   (stub | tff)
```

**Core workflow:** Collect → Interoperate (FHIR) → Stream (Kafka) → Store (MongoDB) → Predict (AI) → Monitor → Alert → Prevent (Care Plans)

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Docker Desktop | 24.x + | Required for `docker compose up` |
| Docker Compose | 2.x (bundled with Docker Desktop) | |
| Node.js | 20.x LTS | Local frontend development only |
| Java JDK | 21 LTS | Local backend development only |
| Maven | 3.9.x | Local backend build only — bundled inside Docker |

> **Docker Compose is the primary way to run this project.** Java 21 and Node 20 are only needed if you want to run the frontend or backend outside of Docker.

---

## Quick Start

### 1. Clone and configure

```bash
git clone <repository-url>
cd medisphere
cp .env.example .env
```

Open `.env` and set a strong JWT secret:

```env
JWT_SECRET=your-strong-random-secret-minimum-32-characters
```

Generate one with:

```bash
openssl rand -base64 48
```

### 2. Start the full stack

```bash
docker compose up --build
```

This starts four services in order:

| Service | URL | Notes |
|---|---|---|
| **frontend** | http://localhost:3000 | React app served by Nginx |
| **backend** | http://localhost:8080 | Spring Boot API |
| **mongo** | localhost:27017 | MongoDB 7.0 |
| **kafka** | localhost:9092 | Apache Kafka 3.7 (KRaft) |

The backend starts in `dev` profile — the DataSeeder runs automatically and creates all required accounts and seed data on first boot.

### 3. Open the application

Navigate to **http://localhost:3000** in your browser.

---

## Development Login Credentials

These credentials are created by the `DataSeeder` on first startup in the `dev` profile. They are development-only and must never be used in production.

| Role | Email | Password |
|---|---|---|
| **Clinician** | `a.mehta@medisphere.dev` | `Medisphere@123` |
| **Admin** | `admin@medisphere.dev` | `Admin@123` |

The DataSeeder also creates one E2E test patient:

| Field | Value |
|---|---|
| Patient ID | `P-E2E-01` |
| Name | Eva Testpatient |
| Conditions | Type 2 Diabetes, Hypertension |
| Risk Level | High |
| Alert | Pre-seeded HR spike (Unacknowledged) |

---

## API Documentation

Swagger UI is available at: **http://localhost:8080/swagger-ui.html**

All endpoints are documented with request/response schemas and require a Bearer JWT token (except `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`).

---

## Environment Variables

All variables are documented in `.env.example`. Key variables:

| Variable | Default | Required |
|---|---|---|
| `JWT_SECRET` | — | **Yes** — must be set before first run |
| `JWT_ACCESS_EXPIRY` | `900` (15 min) | No |
| `JWT_REFRESH_EXPIRY` | `2592000` (30 days) | No |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | No |
| `SPRING_PROFILES_ACTIVE` | `dev` | No |
| `FHIR_MODE` | `mock` | No — `live` requires FHIR server credentials |
| `AI_PREDICTION_MODE` | `stub` | No — `tff` requires TFF endpoint |
| `AI_CAREPLAN_MODE` | `stub` | No |
| `FEDERATED_MODE` | `stub` | No |
| `SIMULATOR_ENABLED` | `true` | No — set `false` to disable wearable simulator |
| `SIMULATOR_INTERVAL` | `5` | No — seconds between simulated vitals readings |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | Managed by Docker Compose |

---

## Local Development (without Docker)

### Backend

Requirements: Java 21 JDK, Maven 3.9+, running MongoDB and Kafka.

```bash
cd backend
cp .env.example .env
# Edit .env — set MONGODB_URI, JWT_SECRET, KAFKA_BOOTSTRAP_SERVERS
mvn spring-boot:run
```

### Frontend

Requirements: Node.js 20 LTS.

```bash
# From project root
npm install
npm run dev
```

The frontend dev server runs at http://localhost:5173 and proxies are NOT configured — set `VITE_API_BASE_URL=http://localhost:8080` in `.env.local`.

---

## Testing

### Backend unit + integration tests (embedded infra)

```bash
cd backend
mvn test
```

Runs all unit tests and controller integration tests using embedded MongoDB (Flapdoodle) and embedded Kafka.

### Backend integration tests (Testcontainers — requires Docker)

```bash
cd backend
mvn verify
```

Runs all tests including Phase 8 Testcontainers integration tests. Docker must be running.

### Frontend unit tests (Vitest)

```bash
npm run test
```

Runs all React component and API hook tests using Vitest + jsdom + MSW.

### End-to-end tests (Playwright)

Requires the full Docker Compose stack to be running.

```bash
# Start the stack
docker compose up --build

# In a second terminal, run E2E tests
npm run test:e2e

# View the HTML report
npm run test:e2e:report
```

To run against a local dev server instead of Docker:

```bash
BASE_URL=http://localhost:5173 npm run test:e2e
```

---

## Project Structure

```
medisphere/
├── src/                        # React frontend (TypeScript + Vite)
│   ├── api/                    # Axios API clients (one per domain)
│   ├── components/             # Reusable UI components
│   ├── context/                # AuthContext, QueryProvider
│   ├── hooks/                  # WebSocket hooks (Vitals, Alerts, Monitoring)
│   ├── pages/                  # 25 application pages
│   ├── schemas/                # Zod validation schemas
│   └── types/                  # TypeScript type definitions
├── backend/                    # Spring Boot backend (Java 21)
│   └── src/main/java/com/medisphere/
│       ├── ai/                 # AI prediction + care plan interfaces + implementations
│       ├── audit/              # AuditAspect + AuditService (@Auditable annotation)
│       ├── config/             # Spring configuration classes
│       ├── controller/         # REST controllers (19 controllers, 25+ endpoints)
│       ├── domain/             # MongoDB documents
│       ├── dto/                # Request + Response DTOs
│       ├── fhir/               # FHIR abstraction layer (mock + live)
│       ├── kafka/              # KafkaConsumer + KafkaEventPublisher + topics
│       ├── repository/         # Spring Data MongoDB repositories
│       ├── security/           # JWT filter + token provider
│       ├── service/            # Business logic (23 services)
│       ├── simulator/          # Wearable vitals simulator (dev profile only)
│       └── websocket/          # STOMP WebSocket broadcaster
├── tests/e2e/                  # Playwright E2E tests
├── docker/                     # Docker init scripts
│   └── mongo-init.js           # MongoDB index initialization
├── docker-compose.yml          # Full stack orchestration
├── Dockerfile                  # Frontend multi-stage Docker build
├── nginx.conf                  # Nginx SPA + reverse proxy config
├── backend/Dockerfile          # Backend multi-stage Docker build
└── playwright.config.ts        # Playwright E2E configuration
```

---

## Kafka Topics

All topics are auto-created on startup:

| Topic | Phase | Description |
|---|---|---|
| `vitals.raw` | 5 | Raw vital sign readings from wearable devices |
| `vitals.anomaly` | 5 | Anomaly events from clinical rules engine |
| `alert.created` | 5 | Alert lifecycle events |
| `twin.update` | 5 | Digital twin state change notifications |
| `federated.round` | 4 | Federated learning round progress |
| `careplan.approved` | 6 | Care plan approval notifications |
| `fhir.ingested` | 7 | FHIR resource ingestion events |
| `report.ready` | 7 | Async report generation completion |

---

## Development vs Production

| Concern | Development | Production |
|---|---|---|
| Profile | `dev` — DataSeeder + simulator active | `prod` — no seed data, no simulator |
| JWT secret | Set in `.env` (never commit) | Set via secrets manager |
| MongoDB | No auth (dev convenience) | Auth credentials via env vars |
| FHIR | `mock` — no external server needed | `live` — requires FHIR_BASE_URL + credentials |
| AI | `stub` — deterministic, no GPU | `tff` — requires TFF_ENDPOINT |
| CORS | `http://localhost:3000` | Set `CORS_ALLOWED_ORIGINS` to production URL |

---

## Phase Completion Status

| Phase | Scope | Status |
|---|---|---|
| 1 | Backend foundation + JWT auth + frontend auth wiring | ✅ Complete |
| 2 | Patient CRUD + Patient 360 + Digital Health Twin | ✅ Complete |
| 3 | Vitals + Lab Results + FHIR integration layer | ✅ Complete |
| 4 | AI Risk Prediction + SHAP + Federated Learning | ✅ Complete |
| 5 | Kafka + Real-time monitoring + Anomaly detection + Alerts | ✅ Complete |
| 6 | Care Plans + AI generation + Review + Adherence | ✅ Complete |
| 7 | Consent + Audit Logs + Reports + Population Health + System Status + Settings | ✅ Complete |
| 8 | Docker Compose + Playwright E2E + Testcontainers + Security hardening | ✅ Complete |

---

## Stopping the Stack

```bash
docker compose down
```

To remove all data volumes (full reset):

```bash
docker compose down -v
```
