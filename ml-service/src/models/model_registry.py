"""
Model Registry — loads all three serialized pipelines at startup.
Caches them in memory for fast inference.
"""
from __future__ import annotations

import os
import json
import joblib
import numpy as np
from dataclasses import dataclass


@dataclass
class LoadedModel:
    pipeline: object
    metadata: dict
    background: np.ndarray


class ModelRegistry:
    """Holds all three loaded models in memory."""

    # Maps URL model_id → directory name
    MODEL_MAP = {
        "cvd-risk":       ("cvd_risk",       "v1.0.0"),
        "diabetes-risk":  ("diabetes_risk",  "v1.0.0"),
        "readmission-30d": ("readmission_30d", "v1.0.0"),
    }

    def __init__(self):
        self._models: dict[str, LoadedModel] = {}
        self._base = os.path.dirname(os.path.dirname(os.path.dirname(
            os.path.abspath(__file__)
        )))

    def load_all(self):
        """Load all three models at startup."""
        for url_id, (dir_name, version) in self.MODEL_MAP.items():
            model_dir = os.path.join(self._base, "models", dir_name, version)
            self._load_one(url_id, model_dir)

    def _load_one(self, url_id: str, model_dir: str):
        pipe_path = os.path.join(model_dir, "pipeline.joblib")
        meta_path = os.path.join(model_dir, "metadata.json")
        bg_path   = os.path.join(model_dir, "shap_background.joblib")

        if not os.path.exists(pipe_path):
            raise FileNotFoundError(f"Pipeline not found: {pipe_path}")
        if not os.path.exists(meta_path):
            raise FileNotFoundError(f"Metadata not found: {meta_path}")
        if not os.path.exists(bg_path):
            raise FileNotFoundError(f"SHAP background not found: {bg_path}")

        pipeline = joblib.load(pipe_path)
        with open(meta_path) as f:
            metadata = json.load(f)
        background = joblib.load(bg_path)

        self._models[url_id] = LoadedModel(pipeline, metadata, background)

    def get(self, url_id: str) -> LoadedModel:
        if url_id not in self._models:
            raise KeyError(f"Model '{url_id}' not found or not loaded")
        return self._models[url_id]

    def is_ready(self) -> bool:
        return len(self._models) == len(self.MODEL_MAP)

    def unload_all(self):
        self._models.clear()
