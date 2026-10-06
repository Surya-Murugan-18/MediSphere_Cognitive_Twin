import json, os, joblib
import numpy as np
import pandas as pd

for model_id in ['cvd_risk', 'diabetes_risk', 'readmission_30d']:
    d = os.path.join('models', model_id, 'v1.0.0')
    meta_path = os.path.join(d, 'metadata.json')
    pipe_path = os.path.join(d, 'pipeline.joblib')
    bg_path   = os.path.join(d, 'shap_background.joblib')

    with open(meta_path) as f:
        m = json.load(f)

    pipe = joblib.load(pipe_path)
    bg = joblib.load(bg_path)

    print(f'=== {m["model_id"]} ===')
    print(f'  Algorithm: {m["algorithm"]} / {m["imbalance_experiment"]}')
    print(f'  Training features ({len(m["training_features"])}): {m["training_features"]}')
    print(f'  Inference features ({len(m["inference_features"])}): {m["inference_features"]}')
    print(f'  Training == Inference: {m["training_features"] == m["inference_features"]}')
    print(f'  Threshold: {m["prediction_threshold"]}')
    print(f'  Calibration: {m["calibration_method"]}')
    print(f'  Val AUROC: {m["evaluation"]["val"]["roc_auc"]}')
    print(f'  Test AUROC: {m["evaluation"]["test"]["roc_auc"]}')
    print(f'  Test PR-AUC: {m["evaluation"]["test"]["pr_auc"]}')
    print(f'  Test F1: {m["evaluation"]["test"]["f1"]}')
    print(f'  Test Confusion: {m["evaluation"]["test_confusion_matrix"]}')
    print(f'  Pipeline type: {type(pipe).__name__}')
    print(f'  Background shape: {bg.shape}')
    print(f'  n_train={m["n_train"]} n_val={m["n_val"]} n_test={m["n_test"]}')
    print(f'  dataset_sha256: {m["dataset_sha256"][:16]}...')

    # The SHAP background is in preprocessed space (numpy array).
    # The pipeline expects raw DataFrames. Verify by constructing a raw DataFrame
    # from the metadata training_features and feeding it to the pipeline.
    feat_names = m["training_features"]
    # For models with categorical features, we need to handle differently
    if model_id == 'cvd_risk':
        raw_cols = [c for c in feat_names if c != 'pulse_pressure']
        sample = pd.DataFrame({c: [0.0] for c in raw_cols})
        sample['male'] = 1; sample['age'] = 55; sample['sysBP'] = 120; sample['diaBP'] = 80
        sample['heartRate'] = 72; sample['totChol'] = 200; sample['glucose'] = 90
        sample['prevalentHyp'] = 0; sample['prevalentStroke'] = 0
        sample['diabetes'] = 0; sample['BPMeds'] = 0
    elif model_id == 'diabetes_risk':
        sample = pd.DataFrame({c: [0.0] for c in feat_names})
        sample['Age'] = 7; sample['Sex'] = 1; sample['HighBP'] = 0
        sample['HighChol'] = 0; sample['CholCheck'] = 1; sample['Stroke'] = 0
        sample['HeartDiseaseorAttack'] = 0; sample['PhysHlth'] = 0
    else:
        sample = pd.DataFrame({c: [0.0] for c in feat_names})
        sample['gender'] = 1; sample['age_midpoint'] = 65
        sample['number_diagnoses'] = 5
        sample['diag_group_1'] = 'Circulatory'
        sample['diag_group_2'] = 'Metabolic/Endocrine'
        sample['diag_group_3'] = 'Unknown'
        sample['number_emergency'] = 0; sample['diabetesMed'] = 1; sample['change'] = 0

    proba = pipe.predict_proba(sample)
    print(f'  Pipeline predict_proba on sample: {proba[0]}')
    print()
