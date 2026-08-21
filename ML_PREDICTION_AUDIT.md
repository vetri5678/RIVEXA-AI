# MACHINE LEARNING & PREDICTION AUDIT

This audit reviews the machine learning architectures, pipeline stages, prediction models, explainability engines, and model retraining cycles.

---

## 1. MODEL ARCHITECTURE & INFRASTRUCTURE

### Classifier Algorithm
* **Model Type**: Random Forest Classifier (`RandomForestClassifier` from `scikit-learn`).
* **Artifact Path**: `riskvision_ai_backend/models/random_forest.joblib`.
* **Transformer Path**: `riskvision_ai_backend/transformers/data_scaler.joblib`.

### Initialization & Loading
* **Module**: `MLServiceLoader` (`services/ml_service_loader.py`).
* **Startup Check**: On FastAPI startup, the service loader searches for the `.joblib` model file.
* **Auto-Recovery**: If the model artifact is missing, it automatically calls `train_model()` on the default dataset (`synthetic_data.csv`) to retrain and save a fresh estimator, preventing API initialization crashes.

---

## 2. PREDICTION INPUTS & FEATURE ENGINEERING

The FastAPI pipeline maps repository and project metrics into a normalized vector.

### 12-Stage Pipeline Flow
1. **Stage 1 (Data Validation)**: Ensures required float/integer values are present.
2. **Stage 2 (Data Cleaning)**: Imputes missing values (NaNs to zeros or medians).
3. **Stage 3 (Feature Engineering)**:
   * `cost_overrun_ratio`: `actual_cost / budget` (if budget > 0).
   * `delay_ratio`: `actual_duration / timeline_months` (if timeline > 0).
   * `complexity_score`: `(team_size / 10.0) * (requirements_changed + 1.0)`.
4. **Stage 4 (Feature Selection)**: Filters only columns used during model training.
5. **Stage 5 (Normalization/Scaling)**: Applies `StandardScaler` to float features.
6. **Stage 6 (Model Inference)**: Executes `.predict_proba()` to compute probability classes.
7. **Stage 7 (Risk Level Mapping)**: Maps probability ranges:
   * `prob < 0.20` ──► `LOW`
   * `0.20 <= prob < 0.45` ──► `MEDIUM`
   * `0.45 <= prob < 0.75` ──► `HIGH`
   * `prob >= 0.75` ──► `CRITICAL`
8. **Stage 8 (XAI SHAP Extraction)**: Computes feature SHAP attributions.
9. **Stage 9 (Insight Synthesis)**: Sorts top risk factors.
10. **Stage 10 (Action Recommendation)**: Selects mitigation rules based on risk levels.
11. **Stage 11 (Audit Log Logging)**: Records model run metadata.
12. **Stage 12 (Result Serialization)**: Translates outputs to FastAPI schemas.

---

## 3. MODEL EXPLAINABILITY (SHAP)

### Dynamic vs Mocked SHAP
* **SHAP values are completely DYNAMIC** and calculated using a live `shap.TreeExplainer` initialized with the loaded Random Forest model.
* **Fallback Strategy**: If SHAP execution fails (e.g. dimensions mismatch), the explainability engine falls back to extracting global Gini feature importances (`model.feature_importances_`) for the individual row to prevent runtime failure.

---

## 4. MODEL PERFORMANCE & RETRAINING

* **Accuracy**: Reports a cross-validation accuracy of **93.87%** on the synthetic project training dataset.
* **Retraining Pipeline**:
  * FastAPI exposes `POST /api/v1/pipeline/retrain`.
  * Loads updated dataset vectors, retrains the Random Forest, saves new joblib binaries, and inserts a version record into `model_versions` table.
