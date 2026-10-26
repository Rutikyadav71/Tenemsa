import { useEffect, useState } from "react";
import axiosInstance from "../../../shared/api/axiosInstance";
import { Link, useSearchParams } from "react-router-dom";

type Status = "verifying" | "success" | "error";

export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token") || "";
  const [status, setStatus] = useState<Status>("verifying");
  const [message, setMessage] = useState("");

  useEffect(() => {
    if (!token) {
      setStatus("error");
      setMessage("This verification link is missing its token.");
      return;
    }
    axiosInstance.post("/api/auth/verify-email", { token })
      .then(() => {
        setStatus("success");
        // localStorage.emailVerified may be stale if this is opened in the
        // same browser as an active session — refresh it so the UI (e.g. a
        // "verify your email" banner) updates without needing a re-login.
        if (localStorage.getItem("token")) {
          localStorage.setItem("emailVerified", "true");
        }
      })
      .catch((err) => {
        setStatus("error");
        setMessage(err.response?.data?.message || "This verification link is invalid or has expired.");
      });
  }, [token]);

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>Email verification</h1>
        {status === "verifying" && <p>Verifying your email...</p>}
        {status === "success" && (
          <div className="auth-success">
            <p>Your email is verified.</p>
            <Link to="/auth/employee">Continue to sign in</Link>
          </div>
        )}
        {status === "error" && (
          <div>
            <p className="auth-error">{message}</p>
            <Link to="/auth/employee">Back to sign in</Link>
          </div>
        )}
      </div>
    </div>
  );
}
