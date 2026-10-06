"""Health check endpoints."""
from __future__ import annotations
from fastapi import APIRouter

router = APIRouter()


@router.get("/health")
def health():
    return {"status": "ok"}


@router.get("/health/ready")
def health_ready():
    from src.api.routes.predict import get_registry
    try:
        reg = get_registry()
        models = list(reg._models.keys()) if hasattr(reg, "_models") else []
        return {"status": "ready", "models_loaded": models}
    except Exception:
        return {"status": "not_ready", "models_loaded": []}
