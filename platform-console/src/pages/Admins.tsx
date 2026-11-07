import { useEffect, useState } from "react";
import { api, errText, fmtDate, type Admin } from "../api/client";
import { Alert, Badge, PageHead, Skeleton } from "../components/ui";

export default function Admins() {
  const [list, setList] = useState<Admin[] | null>(null), [err, setErr] = useState<string | null>(null);
  useEffect(() => { api<Admin[]>("/api/platform/admins").then(setList).catch(e => setErr(errText(e))); }, []);
  return (
    <>
      <PageHead title="Administrators" sub="Exactly two system administrators can exist. Registration closes for good once both have signed up." />
      {err && <Alert>{err}</Alert>}
      {!list && !err && <Skeleton h={160} />}
      {list && (
        <>
          <div className="pc-grid pc-grid-2">
            {list.map(a => (
              <section key={a.id} className="tn-card tn-card-pad">
                <h2 className="tn-card-title">Administrator {a.slot}</h2>
                <dl className="pc-dl"><dt>Name</dt><dd>{a.name}</dd><dt>Email</dt><dd>{a.email}</dd><dt>Two-factor</dt><dd><Badge tone={a.twoFactor ? "success" : "warning"} dot>{a.twoFactor ? "Active" : "Not confirmed yet"}</Badge></dd><dt>Last sign-in</dt><dd>{fmtDate(a.lastLoginAt)}</dd><dt>Registered</dt><dd>{fmtDate(a.createdAt)}</dd></dl>
              </section>
            ))}
            {list.length < 2 && <section className="tn-card tn-card-pad"><h2 className="tn-card-title">Administrator {list.length + 1}</h2><p className="pc-muted">Not registered yet. Use the Register page with the setup key.</p></section>}
          </div>
          <Alert tone="info">Having two administrators means one can still sign in if the other loses their phone. Lost authenticator? The other administrator cannot reset it here, so keep a backup of each setup key somewhere safe.</Alert>
        </>
      )}
    </>
  );
}
