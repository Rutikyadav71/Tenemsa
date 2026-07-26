# Tenemsa — ML Service

Flask microservice predicting leave approval likelihood from historical leave data — confidence score and LOW/MEDIUM/HIGH risk classification, trained with logistic regression.

See the repo root [`README.md`](../README.md) for the full project overview and [`../docs/deployment.md`](../docs/deployment.md) for how this fits into deployment.

## ⚠️ Known issue

`app.py` requires a `leave_type` field as an input feature, but the backend's `Leave` entity has no corresponding column (see [`../docs/database.md`](../docs/database.md)). The prediction endpoint is very likely non-functional against real data as a result. Confirm and fix this — either add the column on the backend or adjust the feature set here — before relying on this service.

## Architecture

```
React frontend → Spring Boot backend → Flask ML service → prediction + confidence + risk
```

The backend calls this service over plain HTTP and treats it as an optional dependency — leave management works without it, predictions just aren't available if this isn't running.

## Local setup

```bash
pip install -r requirements.txt
python app.py
```

Runs on port 5000 by default, matching what the backend expects (`LeaveService.ML_API_URL`, currently hardcoded rather than configurable — see deployment docs).

## Files

- `app.py` — Flask API, loads the trained model from `model/`.
- `train_model.py` — retrains the model from `leave_training_dataset.csv`.
- `build_training_dataset.py` — regenerates the training CSV.
- `model/` — the trained, serialized model artifacts the API loads at startup.
