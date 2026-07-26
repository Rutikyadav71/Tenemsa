from flask import Flask, request, jsonify
import pickle
import numpy as np

app = Flask(__name__)

# ================= LOAD MODEL & ENCODER =================
with open("model/leave_model.pkl", "rb") as f:
    model = pickle.load(f)

with open("model/leave_type_encoder.pkl", "rb") as f:
    leave_type_encoder = pickle.load(f)

# ================= HEALTH CHECK =================
@app.route("/")
def home():
    return "ML Leave Prediction Service is running"

# ================= PREDICTION API =================
@app.route("/predict-leave", methods=["POST"])
def predict_leave():

    data = request.json
    if not data:
        return jsonify({"error": "Invalid input"}), 400

    try:
        # -------- INPUT FEATURES --------
        leave_type = data.get("leave_type")
        leave_days = data.get("leave_days")
        leave_month = data.get("leave_month")
        past_month_leave_days = data.get("past_month_leave_days")
        past_year_leave_days = data.get("past_year_leave_days")

        # -------- BASIC VALIDATION --------
        if None in [
            leave_type,
            leave_days,
            leave_month,
            past_month_leave_days,
            past_year_leave_days
        ]:
            return jsonify({"error": "Missing required fields"}), 400

        # -------- ENCODE LEAVE TYPE --------
        leave_type_encoded = leave_type_encoder.transform([leave_type])[0]

        # -------- PREPARE INPUT --------
        X = np.array([[
            leave_type_encoded,
            leave_days,
            leave_month,
            past_month_leave_days,
            past_year_leave_days
        ]])

        # -------- MODEL PREDICTION --------
        prediction = model.predict(X)[0]
        confidence = model.predict_proba(X).max() * 100

        result = "APPROVED" if prediction == 1 else "REJECTED"

        return jsonify({
            "prediction": result,
            "confidence": round(confidence, 2)
        })

    except Exception as e:
        print("ML ERROR:", str(e))
        return jsonify({"error": "ML prediction failed"}), 500


# ================= RUN APP =================
if __name__ == "__main__":
    app.run(debug=True)
