import requests
import pandas as pd

# ================= CONFIG =================
EXPORT_API_URL = "http://localhost:8080/api/leaves/admin/export"
OUTPUT_FILE = "leave_training_dataset.csv"

print("Fetching leave data from Spring Boot export API...")

response = requests.get(EXPORT_API_URL)
if response.status_code != 200:
    raise Exception("Failed to fetch data from Spring Boot API")

data = response.json()
df = pd.DataFrame(data)

if df.empty:
    raise Exception("No leave data available for training")

print(f"Fetched {len(df)} leave records")

# ================= DATE PROCESSING =================
df["startDate"] = pd.to_datetime(df["startDate"])
df["endDate"] = pd.to_datetime(df["endDate"])

# ================= BASIC FEATURES =================

# Leave duration (days)
df["leave_days"] = (df["endDate"] - df["startDate"]).dt.days + 1

# Month & year
df["leave_month"] = df["startDate"].dt.month
df["leave_year"] = df["startDate"].dt.year

# ================= MAP REASON → LEAVE TYPE =================
def map_reason_to_leave_type(reason):
    reason = str(reason).lower()
    if any(x in reason for x in ["fever", "sick", "ill"]):
        return "SICK"
    elif any(x in reason for x in ["marriage", "function", "family"]):
        return "CASUAL"
    elif any(x in reason for x in ["vacation", "trip", "holiday"]):
        return "PAID"
    else:
        return "CASUAL"

df["leave_type"] = df["reason"].apply(map_reason_to_leave_type)

# ================= SORT FOR HISTORY CALCULATION =================
df = df.sort_values(by=["empId", "startDate"])

# ================= EMPLOYEE HISTORY (LEAVE DAYS) =================
df["past_month_leave_days"] = 0
df["past_year_leave_days"] = 0

for idx, row in df.iterrows():
    emp_id = row["empId"]
    leave_month = row["leave_month"]
    leave_year = row["leave_year"]
    leave_start = row["startDate"]

    past_leaves = df[
        (df["empId"] == emp_id) &
        (df["startDate"] < leave_start) &
        (df["status"] == "APPROVED")
    ]

    # Total leave DAYS in same month
    month_days = past_leaves[
        (past_leaves["leave_month"] == leave_month) &
        (past_leaves["leave_year"] == leave_year)
    ]["leave_days"].sum()

    # Total leave DAYS in same year
    year_days = past_leaves[
        (past_leaves["leave_year"] == leave_year)
    ]["leave_days"].sum()

    df.at[idx, "past_month_leave_days"] = month_days
    df.at[idx, "past_year_leave_days"] = year_days

# ================= FINAL DATASET =================
final_df = df[
    [
        "leave_type",
        "leave_days",
        "leave_month",
        "past_month_leave_days",
        "past_year_leave_days",
        "status"
    ]
]

# ================= SAVE CSV =================
final_df.to_csv(OUTPUT_FILE, index=False)

print(f"Training dataset created successfully: {OUTPUT_FILE}")
