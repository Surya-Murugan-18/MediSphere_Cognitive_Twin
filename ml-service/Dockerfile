# =============================================================================
# MediSphere ML Service — Dockerfile
# FastAPI inference service for CVD-10Y, Diabetes-Risk, Readmission-30D models.
#
# Python version matches the training environment (3.11.9 per model metadata.json).
# Dependencies pinned to exact versions from requirements.txt.
#
# Build context: ml-service/
# Startup: uvicorn src.api.main:app --host 0.0.0.0 --port 8000
# Health:  GET /health
# =============================================================================

FROM python:3.11-slim

# ── System dependencies ────────────────────────────────────────────────────────
# gcc / g++ required by some compiled wheels (e.g. scikit-learn, xgboost)
RUN apt-get update && apt-get install -y --no-install-recommends \
        gcc \
        g++ \
        curl \
    && rm -rf /var/lib/apt/lists/*

# ── Working directory ──────────────────────────────────────────────────────────
WORKDIR /app

# ── Install Python dependencies ────────────────────────────────────────────────
# Copy requirements first to leverage Docker layer caching.
# Exact versions are pinned in requirements.txt — do NOT use --upgrade.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# ── Copy source code ───────────────────────────────────────────────────────────
COPY src/ ./src/
COPY training/ ./training/

# ── Copy trained model artifacts ──────────────────────────────────────────────
# pipeline.joblib, shap_background.joblib, metadata.json for each model version.
COPY models/ ./models/

# ── Environment defaults ───────────────────────────────────────────────────────
# ML_INTERNAL_TOKEN MUST be overridden at runtime — never use the default in production.
ENV ML_INTERNAL_TOKEN=dev-token \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

# ── Expose port ────────────────────────────────────────────────────────────────
EXPOSE 8000

# ── Health check ──────────────────────────────────────────────────────────────
# /health returns {"status":"ok"} once the process is up.
# /health/ready returns {"status":"ready",...} once all three models are loaded.
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD curl -f http://localhost:8000/health || exit 1

# ── Startup ────────────────────────────────────────────────────────────────────
# Module path: src.api.main  App object: app
# Models are loaded at startup via the FastAPI lifespan event handler.
CMD ["uvicorn", "src.api.main:app", "--host", "0.0.0.0", "--port", "8000"]
