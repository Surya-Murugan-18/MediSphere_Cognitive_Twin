import sys, os
sys.path.insert(0, '.')
import joblib, pandas as pd, numpy as np
from src.explainability.shap_explainer import compute_shap_values

tests = [
    ('cvd_risk', 'models/cvd_risk/v1.0.0',
     {'male':1,'age':67,'sysBP':158.0,'diaBP':92.0,'heartRate':72,
      'totChol':245.0,'glucose':88.0,'prevalentHyp':1,
      'prevalentStroke':0,'diabetes':0,'BPMeds':1}),
    ('diabetes_risk', 'models/diabetes_risk/v1.0.0',
     {'Age':7,'Sex':0,'HighBP':1,'HighChol':1,'CholCheck':1,
      'Stroke':0,'HeartDiseaseorAttack':0,'PhysHlth':2}),
    ('readmission_30d', 'models/readmission_30d/v1.0.0',
     {'gender':1.0,'age_midpoint':75.0,'number_diagnoses':7.0,
      'diag_group_1':'Circulatory','diag_group_2':'Metabolic/Endocrine',
      'diag_group_3':'Unknown','number_emergency':2.0,
      'diabetesMed':1.0,'change':1.0}),
]

for name, path, data in tests:
    pipe = joblib.load(os.path.join(path, 'pipeline.joblib'))
    bg = joblib.load(os.path.join(path, 'shap_background.joblib'))
    X = pd.DataFrame([data])
    results = compute_shap_values(pipe, bg, X, n_features=5)
    print(f'=== {name} ===')
    for r in results:
        feat = r['feature']
        val = r['value']
        contrib = r['contribution']
        direction = r['direction']
        print(f'  {feat:30s} value={val:20s} contribution={contrib:+.4f} {direction}')
    print()
