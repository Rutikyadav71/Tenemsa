import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import axios from "axios";
import { API_BASE_URL } from "../../../shared/api/apiConfig";
import { storeAuthResponse } from "../../../shared/lib/authStorage";
import type { AuthResponse } from "../../../shared/types/models";

/**
 * Lands here after Google redirects back through the backend
 * (OAuth2LoginSuccessHandler). The URL carries token+refreshToken as query
 * params — a full-page redirect can't deliver a JSON body, so this page's
 * only job is: store those two, use the access token to fetch the rest of
 * the profile via /api/auth/me, store that too, then route into the app
 * the same way a normal login would.
 */
export default function OAuthCallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const ranOnce = useRef(false);

  useEffect(() => {
    if (ranOnce.current) return; // StrictMode double-invokes effects in dev
    ranOnce.current = true;

    const urlError = searchParams.get("error");
    if (urlError) {
      setError(urlError);
      return;
    }

    const token = searchParams.get("token");
    const refreshToken = searchParams.get("refreshToken");
    if (!token || !refreshToken) {
      setError("Sign-in with Google didn't complete correctly. Please try again.");
      return;
    }

    axios.get<AuthResponse>(`${API_BASE_URL}/api/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
    }).then((res) => {
      const profile = { ...res.data, token, refreshToken };
      const roleTag = ["OWNER", "SUPER_ADMIN"].includes(profile.role) ? "ADMIN" as const : "USER" as const;
      storeAuthResponse(profile, roleTag);
      navigate(roleTag === "ADMIN" ? "/admin/dashboard" : "/user/dashboard");
    }).catch(() => {
      setError("Couldn't complete sign-in. Please try again.");
    });
  }, [searchParams, navigate]);

  return (
    <div className="auth-page">
      <div className="auth-card">
        {error ? (
          <>
            <h1>Sign-in failed</h1>
            <p className="auth-error">{error}</p>
            <div style={{ display: "flex", gap: 12, justifyContent: "center", marginTop: 12 }}>
              <a href="/auth/employee">Employee sign in</a>
              <a href="/auth/admin">Admin sign in</a>
            </div>
          </>
        ) : (
          <p>Signing you in...</p>
        )}
      </div>
    </div>
  );
}
