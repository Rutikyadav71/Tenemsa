import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api, errText } from "../api/client";
import { Alert, Button, Field, Input, Skeleton } from "../components/ui";
import TwoFactorSetup from "../components/TwoFactorSetup";

export default function Register() {
  const [open, setOpen] = useState<boolean | null>(null);
  const [f, setF] = useState({ name: "", email: "", password: "", confirm: "", setupKey: "" });
  const [err, setErr] = useState<string | null>(null), [busy, setBusy] = useState(false);
  const [done, setDone] = useState<{ secret: string; uri: string } | null>(null);

  useEffect(() => { api<{ registrationOpen: boolean }>("/api/platform/auth/status", { auth: false }).then(r => setOpen(r.registrationOpen)).catch(e => { setErr(errText(e)); setOpen(false); }); }, []);

  async function submit(e: FormEvent) {
    e.preventDefault(); setErr(null);
    if (f.password !== f.confirm) { setErr("The two passwords are not the same."); return; }
    setBusy(true);
    try {
      const r = await api<{ secret: string; otpauthUri: string }>("/api/platform/auth/register", { method: "POST", auth: false, body: { name: f.name, email: f.email, password: f.password, setupKey: f.setupKey } });
      setDone({ secret: r.secret, uri: r.otpauthUri });
    } catch (x) { setErr(errText(x)); } finally { setBusy(false); }
  }
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });

  return (
    <div className="pc-auth">
      <form className="pc-auth-card tn-card tn-card-pad" onSubmit={submit} noValidate>
        <img src="/brand/tenemsa-logo.png" alt="Tenemsa" height={34} />
        <h1>Register administrator</h1>
        {open === null && <Skeleton h={120} />}
        {open === false && !done && (
          <>
            <Alert tone="info">Registration is closed. {err ?? "Both system administrator accounts already exist (or no setup key is configured on the server)."}</Alert>
            <Link className="tn-btn tn-btn-primary tn-btn-lg tn-btn-block" to="/login">Go to sign in</Link>
          </>
        )}
        {done && (
          <>
            <Alert tone="success">Account created. Add it to your authenticator app now. You will confirm it at your first sign-in.</Alert>
            <TwoFactorSetup uri={done.uri} secret={done.secret} />
            <Link className="tn-btn tn-btn-primary tn-btn-lg tn-btn-block" to="/login">Continue to sign in</Link>
          </>
        )}
        {open && !done && (
          <>
            <p className="pc-muted">Only two accounts can ever be registered. You need the setup key from the server's <code>.env</code>.</p>
            {err && <Alert>{err}</Alert>}
            <Field label="Name"><Input value={f.name} onChange={set("name")} autoComplete="name" required autoFocus /></Field>
            <Field label="Email"><Input type="email" value={f.email} onChange={set("email")} autoComplete="email" required /></Field>
            <Field label="Password" hint="At least 12 characters with upper case, lower case and a number."><Input type="password" value={f.password} onChange={set("password")} autoComplete="new-password" required /></Field>
            <Field label="Confirm password"><Input type="password" value={f.confirm} onChange={set("confirm")} autoComplete="new-password" required /></Field>
            <Field label="Setup key"><Input type="password" value={f.setupKey} onChange={set("setupKey")} autoComplete="off" required /></Field>
            <Button variant="primary" size="lg" className="tn-btn-block" type="submit" loading={busy}>Create account</Button>
            <p className="pc-muted pc-foot"><Link to="/login">I already have an account</Link></p>
          </>
        )}
      </form>
    </div>
  );
}
