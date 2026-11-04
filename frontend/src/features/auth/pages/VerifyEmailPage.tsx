import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { LuCircleCheck, LuCircleAlert } from "react-icons/lu";
import axiosInstance from "../../../shared/api/axiosInstance";
import { errorMessage } from "../../../shared/lib/errors";
import AuthLayout from "../components/AuthLayout";

type Status = "verifying" | "success" | "error";

export default function VerifyEmailPage() {
  const [params] = useSearchParams();
  const token = params.get("token") || "";
  const [status, setStatus] = useState<Status>(token ? "verifying" : "error");
  const [message, setMessage] = useState(token ? "" : "This verification link is incomplete.");

  useEffect(() => {
    if (!token) return;
    axiosInstance.post("/api/auth/verify-email", { token })
      .then(() => {
        setStatus("success");
        // Keep an already signed-in tab in sync so any "verify your email" prompt disappears.
        if (localStorage.getItem("token")) localStorage.setItem("emailVerified", "true");
      })
      .catch(err => { setStatus("error"); setMessage(errorMessage(err, "This verification link is invalid or has expired.")); });
  }, [token]);

  return (
    <AuthLayout title="Email verification">
      <div className="au-status" role="status" aria-live="polite">
        {status === "verifying" && <><span className="tn-spinner" aria-hidden="true" /><p className="sub" style={{ margin: 0 }}>Verifying your email…</p></>}
        {status === "success" && <>
          <div className="big ok"><LuCircleCheck size={26} /></div>
          <p className="sub" style={{ margin: 0 }}>Your email address is verified.</p>
          <Link className="tn-btn tn-btn-primary" to="/auth/employee">Continue to sign in</Link>
        </>}
        {status === "error" && <>
          <div className="big bad"><LuCircleAlert size={26} /></div>
          <p className="sub" style={{ margin: 0 }}>{message}</p>
          <Link className="au-link" to="/auth/employee">Back to sign in</Link>
        </>}
      </div>
    </AuthLayout>
  );
}
