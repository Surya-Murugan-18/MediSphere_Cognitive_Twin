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
FHIR Layer  (mock | live)
AI Layer    (stub | ml)
    │
    ▼ (when AI_PREDICTION_MODE=ml)
FastAPI ML Service (Python 3.11)
    ├── CVD-10Y            (Logistic Regression, AUROC 0.723)
    ├── Diabetes-Risk      (XGBoost, AUROC 0.759)
    └── Readmission-30D    (Logistic Regression, AUROC 0.556)
```

**Core workflow:** Collect → Interoperate (FHIR) → Stream (Kafka) → Store (MongoDB) → Predict (AI / ML) → Monitor → Alert → Prevent (Care Plans)

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Docker Desktop | 24.x + | Required for `docker compose up` |
| Docker Compose | 2.x (bundled with Docker Desktop) | |
| Node.js | 20.x LTS | Local frontend development only |
| Java JDK | 21 LTS | Local backend development only |
| Maven | 3.9.x | Local backend build only — bundled inside Docker |
| Python | 3.11 | Local ML service development only |

> **Docker Compose is the primary way to run this project.** Java 21 and Node 20 are only needed if you want to run the frontend or backend outside of Docker.

---

## Quick Start

### 1. Clone and configure

```bash
git clone <repository-url>
cd medisphere
cp .env.example .env
```

Open `.env` and set a strong JWT secret and ML token:

```env
JWT_SECRET=your-strong-random-secret-minimum-32-characters
ML_INTERNAL_TOKEN=your-strong-random-ml-token
```

Generate suitable values with:

```bash
openssl rand -base64 48   # for JWT_SECRET
openssl rand -hex 32       # for ML_INTERNAL_TOKEN
```

To enable real ML predictions, also set:

```env
AI_PREDICTION_MODE=ml
```

### 2. Start the full stack

```bash
docker compose up --build
```

This starts five services in order:

| Service | URL | Notes |
|---|---|---|
| **frontend** | http://localhost:3000 | React app served by Nginx |
| **backend** | http://localhost:8080 | Spring Boot API |
| **ml-service** | http://localhost:8000 | FastAPI ML inference service |
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

## ML Service

The ML inference service provides three real trained risk prediction models for clinical screening and decision support.

> **Important:** These models are clinical risk screening tools only. They do not provide medical diagnoses and require clinical oversight.

### ML Health Check

```bash
# Liveness
curl http://localhost:8000/health

# Readiness — confirms all three models are loaded
curl http://localhost:8000/health/ready
```

Expected readiness response:
```json
{
  "status": "ready",
  "models_loaded": ["cvd-risk", "diabetes-risk", "readmission-30d"]
}
```

### Trained Models

| Model ID | Algorithm | AUROC (Test) | Task |
|---|---|---|---|
| `cvd-risk` | Logistic Regression | 0.723 | 10-year cardiovascular risk screening |
| `diabetes-risk` | XGBoost | 0.759 | Diabetes complication risk screening |
| `readmission-30d` | Logistic Regression | 0.556 | 30-day hospital readmission risk |

> **Readmission model limitation:** The Readmission-30D model achieved AUROC 0.556, below the target of 0.62. This is documented honestly. The model was trained only on features available at inference time in MediSphere — no unavailable features (inpatient history, race, admission type, BMI) were fabricated to improve the score. It should be treated as a preliminary indicator, not a validated clinical tool.

All models are:
- Trained on real public datasets (Framingham, BRFSS 2015, UCI Diabetes readmission)
- Serialized as scikit-learn/XGBoost pipelines (`.joblib`) with isotonic calibration
- Equipped with SHAP explainability using background samples
- Version-pinned at `v1.0.0`

### Security

The ML service is an **internal service** — it is never called directly by the React frontend.

```
React → Spring Boot → FastAPI ML service (internal only)
```

All requests to the ML service require an `X-Internal-Token` header. This token:
- Is configured via `ML_INTERNAL_TOKEN` environment variable
- Must match between `backend` and `ml-service` containers
- Is never exposed to the browser
- Is never logged

### ML Prediction Flow

```
POST /api/predictions/run    (React → Spring Boot, JWT-authenticated)
  ↓
PredictionService            (orchestrates, persists)
  ↓
TFFAIPredictionService       (feature extraction from Patient/Labs/Vitals/CarePlan/Alerts)
  ↓
MLServiceClient              (HTTP POST with X-Internal-Token)
  ↓
FastAPI /predict/{model-id}  (serialized sklearn/XGBoost pipeline)
  ↓
Real trained model + SHAP    (genuine probability + feature contributions)
  ↓
Prediction persisted to MongoDB
  ↓
PredictionResponse → React   (probability %, category, SHAP factors, clinical evidence)
```

### Feature Mapping

**CVD-10Y** features sourced from MediSphere domain objects:

| Feature | Source |
|---|---|
| `male` | `Patient.gender` |
| `age` | Computed from `Patient.dob` |
| `sys_bp` / `dia_bp` | `VitalsSnapshot.bloodPressure` |
| `heart_rate` | `VitalsSnapshot.heartRate` |
| `tot_chol` | `LabResult` where `test="Total Cholesterol"` |
| `glucose` | `LabResult` where `test="Fasting Glucose"` |
| `prevalent_hyp` | `Patient.conditions` contains "hypertension" |
| `prevalent_stroke` | `Patient.conditions` contains "stroke" |
| `diabetes` | `Patient.conditions` contains "diabetes" |
| `bp_meds` | `CarePlanRecommendation.intervention` keyword proxy |

Not included (removed from training due to unavailability at inference): `currentSmoker`, `cigsPerDay`, `BMI`, `education`.

**Diabetes-Risk** features:

| Feature | Source |
|---|---|
| `age_years` | Computed from `Patient.dob` (FastAPI converts to BRFSS bracket) |
| `sex_male` | `Patient.gender` |
| `high_bp` | `Patient.conditions` contains "hypertension" |
| `high_chol` | `LabResult` "Total Cholesterol" with status="High" |
| `chol_check` | Any "Total Cholesterol" lab result exists |
| `stroke` | `Patient.conditions` contains "stroke" |
| `heart_disease_or_attack` | `Patient.conditions` contains "heart"/"coronary"/"cad" |
| `phys_hlth_alert_count_30d` | Alert count proxy (null → pipeline imputes median) |

**Readmission-30D** features:

| Feature | Source |
|---|---|
| `gender_male` | `Patient.gender` |
| `age_years` | Computed from `Patient.dob` |
| `number_diagnoses` | `Patient.conditions.size()` |
| `diag_group_primary/secondary/tertiary` | `ConditionMapper.conditionNameToGroup()` |
| `number_high_alerts_prior_year` | `AlertRepository` — HIGH alerts in last 365 days |
| `diabetes_med` | `CarePlanRecommendation.intervention` keyword proxy |
| `care_plan_changed_30d` | ACTIVE `CarePlan.updatedAt` within last 30 days |

Not included: `number_inpatient`, `admission_type_id`, `race`, `medical_specialty`, `BMI`, any fabricated field.

All Python preprocessing (imputation, encoding, scaling, feature engineering) is performed by the serialized pipeline — Java only extracts raw domain values.

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
| `AI_PREDICTION_MODE` | `stub` | No — set `ml` to enable real ML predictions |
| `AI_CAREPLAN_MODE` | `stub` | No |
| `ML_SERVICE_URL` | `http://localhost:8000` | Required when `AI_PREDICTION_MODE=ml` |
| `ML_INTERNAL_TOKEN` | `dev-token` | **Required in production** — use `openssl rand -hex 32` |
| `FEDERATED_MODE` | `stub` | No |
| `SIMULATOR_ENABLED` | `true` | No — set `false` to disable wearable simulator |
| `SIMULATOR_INTERVAL` | `5` | No — seconds between simulated vitals readings |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | Managed by Docker Compose |

> In Docker Compose, the backend automatically uses `ML_SERVICE_URL=http://ml-service:8000` (internal network). Never use `localhost:8000` from inside the backend container.

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

### ML Service (local)

Requirements: Python 3.11, dependencies from `ml-service/requirements.txt`.

```bash
cd ml-service
pip install -r requirements.txt

# Start with default dev token
ML_INTERNAL_TOKEN=dev-token uvicorn src.api.main:app --host 0.0.0.0 --port 8000

# Verify models loaded
curl http://localhost:8000/health/ready
```

Then set in backend `.env`:
```env
AI_PREDICTION_MODE=ml
ML_SERVICE_URL=http://localhost:8000
ML_INTERNAL_TOKEN=dev-token
```

---

## Testing

### Backend unit + integration tests (embedded infra)

```bash
cd backend
mvn test -Dtest='!MlServiceIntegrationTest,!AlertIntegrationTest,!AuthIntegrationTest,!CarePlanIntegrationTest,!PatientIntegrationTest'
```

Runs all unit tests including the new ML integration tests (387 tests).

### Backend integration tests (Testcontainers — requires Docker)

```bash
cd backend
mvn verify
```

Runs all tests including Phase 8 and ML service integration tests. Docker must be running. Add `-Ddocker.available=true` to also enable `MlServiceIntegrationTest`.

### ML service tests (Python)

```bash
cd ml-service

# Requires Python 3.11 + requirements.txt installed
PYTHONIOENCODING=utf-8 python tests/run_all_tests.py
```

Runs 261 tests covering: preprocessing, condition mapping, SHAP, model registry, FastAPI endpoints, contract validation, training integrity, and pipeline round-trip.

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
│       │   ├── ml/             # MLServiceClient + ConditionMapper + request/response DTOs
│       │   ├── stub/           # Deterministic stub implementations (AI_PREDICTION_MODE=stub)
│       │   └── tff/            # Real ML implementations (AI_PREDICTION_MODE=ml)
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
├── ml-service/                 # FastAPI ML inference service (Python 3.11)
│   ├── models/                 # Serialized model artifacts (pipeline.joblib, shap_background.joblib, metadata.json)
│   │   ├── cvd_risk/v1.0.0/
│   │   ├── diabetes_risk/v1.0.0/
│   │   └── readmission_30d/v1.0.0/
│   ├── src/
│   │   ├── api/                # FastAPI routes + Pydantic schemas
│   │   ├── explainability/     # SHAP explainer
│   │   ├── models/             # Model registry (loads all pipelines at startup)
│   │   └── preprocessing/      # CVD / Diabetes / Readmission preprocessors
│   ├── training/               # Training scripts + condition mapper utilities
│   ├── tests/                  # 261 ML tests (unit + integration + contract + integrity)
│   ├── Dockerfile
│   └── requirements.txt
├── tests/e2e/                  # Playwright E2E tests
├── docker/                     # Docker init scripts
│   └── mongo-init.js           # MongoDB index initialization
├── docker-compose.yml          # Full stack orchestration (5 services)
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
| ML token | `dev-token` (dev only) | Strong random token via `openssl rand -hex 32` |
| MongoDB | No auth (dev convenience) | Auth credentials via env vars |
| FHIR | `mock` — no external server needed | `live` — requires FHIR_BASE_URL + credentials |
| AI | `stub` — deterministic, no ML service needed | `ml` — requires ML service running |
| ML URL | `http://localhost:8000` (local) / `http://ml-service:8000` (Docker) | Same Docker pattern in production |
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
| ML | Real ML pipeline integration (Spring Boot → FastAPI → trained models) | ✅ Complete |

---

## Stopping the Stack

```bash
docker compose down
```

To remove all data volumes (full reset):

```bash
docker compose down -v
```

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
| `AI_PREDICTION_MODE` | `tff` | No — `tff` requires TFF endpoint |
| `AI_CAREPLAN_MODE` | `tff` | No |
| `FEDERATED_MODE` | `tff` | No |
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
| AI | `tff` — deterministic, no GPU | `tff` — requires TFF_ENDPOINT |
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
