import { useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { api, errText, setToken } from "../api/client";
import { Alert, Button, Field, Input } from "../components/ui";
import TwoFactorSetup from "../components/TwoFactorSetup";

type Resp = { status: "OK" | "TOTP_SETUP" | "CODE_REQUIRED"; token?: string; secret?: string; otpauthUri?: string };

export default function Login() {
  const nav = useNavigate();
  const loc = useLocation() as { state?: { expired?: boolean } };
  const [email, setEmail] = useState(""), [password, setPassword] = useState(""), [code, setCode] = useState("");
  const [stage, setStage] = useState<"creds" | "code" | "setup">("creds");
  const [setup, setSetup] = useState<{ secret: string; uri: string } | null>(null);
  const [err, setErr] = useState<string | null>(null), [busy, setBusy] = useState(false);

  const done = (r: Resp) => { if (r.token) { setToken(r.token); nav("/", { replace: true }); } };

  async function submit(e: FormEvent) {
    e.preventDefault(); setErr(null); setBusy(true);
    try {
      if (stage === "setup") {
        done(await api<Resp>("/api/platform/auth/confirm-2fa", { method: "POST", auth: false, body: { email, password, code } }));
        return;
      }
      const r = await api<Resp>("/api/platform/auth/login", { method: "POST", auth: false, body: { email, password, code: stage === "code" ? code : undefined } });
      if (r.status === "TOTP_SETUP" && r.secret && r.otpauthUri) { setSetup({ secret: r.secret, uri: r.otpauthUri }); setStage("setup"); setCode(""); }
      else if (r.status === "CODE_REQUIRED") { setStage("code"); setCode(""); }
      else done(r);
    } catch (x) { setErr(errText(x)); setCode(""); } finally { setBusy(false); }
  }

  return (
    <div className="pc-auth">
      <form className="pc-auth-card tn-card tn-card-pad" onSubmit={submit} noValidate>
        <img src="/brand/tenemsa-logo.png" alt="Tenemsa" height={34} />
        <h1>Platform console</h1>
        <p className="pc-muted">System administrators only.</p>
        {loc.state?.expired && !err && <Alert tone="info">Please sign in.</Alert>}
        {err && <Alert>{err}</Alert>}
        {stage === "setup" && setup && (
          <>
            <Alert tone="info">First sign-in: set up your authenticator app. You need it every time you sign in.</Alert>
            <TwoFactorSetup uri={setup.uri} secret={setup.secret} />
          </>
        )}
        <Field label="Email"><Input type="email" autoComplete="username" value={email} onChange={e => setEmail(e.target.value)} disabled={stage !== "creds"} required autoFocus /></Field>
        <Field label="Password"><Input type="password" autoComplete="current-password" value={password} onChange={e => setPassword(e.target.value)} disabled={stage !== "creds"} required /></Field>
        {stage !== "creds" && (
          <Field label="6-digit code" hint="From your authenticator app."><Input inputMode="numeric" autoComplete="one-time-code" maxLength={7} value={code} onChange={e => setCode(e.target.value)} required autoFocus /></Field>
        )}
        <Button variant="primary" size="lg" className="tn-btn-block" type="submit" loading={busy}>{stage === "setup" ? "Confirm and sign in" : stage === "code" ? "Verify" : "Continue"}</Button>
        {stage !== "creds" && <button type="button" className="pc-linkbtn" onClick={() => { setStage("creds"); setCode(""); setErr(null); }}>Start over</button>}
        <p className="pc-muted pc-foot"><Link to="/register">Register an administrator</Link></p>
      </form>
    </div>
  );
}
