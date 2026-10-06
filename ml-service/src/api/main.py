"""
MediSphere ML Service — FastAPI application factory.
"""
from __future__ import annotations

import os
import sys
from contextlib import asynccontextmanager

# Add the ml-service root to sys.path so imports work
_BASE = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if _BASE not in sys.path:
    sys.path.insert(0, _BASE)

from fastapi import FastAPI
from src.models.model_registry import ModelRegistry
from src.api.routes import predict, explain, health


# Global registry
registry = ModelRegistry()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Load all models at startup, unload at shutdown."""
    registry.load_all()
    predict.set_registry(registry)
    yield
    registry.unload_all()


app = FastAPI(
    title="MediSphere ML Service",
    version="1.0.0",
    description="ML inference service for CVD-10Y, Diabetes-Risk, and Readmission-30D models",
    lifespan=lifespan,
)

app.include_router(predict.router, prefix="/predict")
app.include_router(explain.router, prefix="/explain")
app.include_router(health.router)
