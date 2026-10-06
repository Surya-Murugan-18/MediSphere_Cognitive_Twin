import json, os, pandas as pd

for model_id in ['cvd_risk', 'diabetes_risk', 'readmission_30d']:
    meta = json.load(open(os.path.join('models', model_id, 'v1.0.0', 'metadata.json')))
    print(f'--- {meta["model_id"]} ---')
    print(f'  algorithm={meta["algorithm"]} experiment={meta["imbalance_experiment"]}')
    print(f'  threshold={meta["prediction_threshold"]}')
    print(f'  val_metrics={meta["evaluation"]["val"]}')
    print(f'  test_metrics={meta["evaluation"]["test"]}')
    print(f'  test_cm={meta["evaluation"]["test_confusion_matrix"]}')
    print(f'  n_train={meta["n_train"]} n_val={meta["n_val"]} n_test={meta["n_test"]}')
    print(f'  train_pos={meta["class_distribution"]["train_pos"]} train_neg={meta["class_distribution"]["train_neg"]}')
    print(f'  features={meta["training_features"]}')
    print(f'  removed={meta["features_removed_from_training"]}')
    print(f'  proxies={meta.get("proxy_features", {})}')
    print(f'  libs={meta["library_versions"]}')
    print()

for name in ['cvd', 'diabetes', 'readmission']:
    path = f'training/results/{name}_results.csv'
    if os.path.exists(path):
        df = pd.read_csv(path)
        print(f'--- {name}_results.csv ({len(df)} rows) ---')
        print(df[['algorithm','experiment','roc_auc','pr_auc','precision','recall','f1','threshold']].to_string(index=False))
        print()
