import pandas as pd
from sklearn.preprocessing import LabelEncoder
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score
import pickle
import os

# ================= CONFIG =================
DATA_FILE = "leave_training_dataset.csv"
MODEL_DIR = "model"

# ================= LOAD DATA =================
if not os.path.exists(DATA_FILE):
    raise Exception("Training dataset not found. Run build_training_dataset.py first.")

data = pd.read_csv(DATA_FILE)
print(f"Training on {len(data)} records")

# ================= ENCODE CATEGORICAL FEATURES =================
leave_type_encoder = LabelEncoder()
data["leave_type"] = leave_type_encoder.fit_transform(data["leave_type"])

# Target variable
data["status"] = data["status"].map({
    "APPROVED": 1,
    "REJECTED": 0
})

# ================= FEATURE SELECTION =================
X = data[
    [
        "leave_type",
        "leave_days",
        "leave_month",
        "past_month_leave_days",
        "past_year_leave_days"
    ]
]

y = data["status"]

# ================= TRAIN MODEL =================
model = LogisticRegression(max_iter=1000)

# Handle small dataset safely
if len(data) < 20 or y.value_counts().min() < 2:
    print("Dataset too small for train-test split. Training on full dataset.")
    model.fit(X, y)
else:
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )

    model.fit(X_train, y_train)

    y_pred = model.predict(X_test)
    accuracy = accuracy_score(y_test, y_pred)
    print(f"Model Accuracy: {accuracy * 100:.2f}%")

# ================= SAVE MODEL & ENCODER =================
os.makedirs(MODEL_DIR, exist_ok=True)

with open(f"{MODEL_DIR}/leave_model.pkl", "wb") as f:
    pickle.dump(model, f)

with open(f"{MODEL_DIR}/leave_type_encoder.pkl", "wb") as f:
    pickle.dump(leave_type_encoder, f)

print("Model trained and saved successfully")
