import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Alert, Badge, Button, ErrorState, PageHeader, SkeletonList, useConfirm, useToast } from "../../../shared/ui";
import { can } from "../../../shared/lib/roles";
import { errorMessage } from "../../../shared/lib/errors";
import {
  authorizeApp, disconnectApp, getConnections, previewApp, setAppEnabled,
  type AppRow, type Connections, type PreviewItem,
} from "../services/connectionService";
import "../connections.css";

export default function ConnectionsPage() {
  const toast = useToast();
  const confirm = useConfirm();
  const [data, setData] = useState<Connections | null>(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [items, setItems] = useState<Record<string, PreviewItem[]>>({});
  const manage = can("company:manage");
  const base = `/${window.location.pathname.split("/")[1]}`;

  const load = useCallback(() => { setError(""); getConnections().then(setData).catch(e => setError(errorMessage(e, "We couldn't load your apps."))); }, []);
  useEffect(load, [load]);

  const run = async (key: string, fn: () => Promise<void>) => {
    setBusy(key); setNotice("");
    try { await fn(); } catch (e) { setNotice(errorMessage(e, "That didn't work. Please try again.")); } finally { setBusy(null); }
  };

  const connect = (a: AppRow) => run(a.code, async () => { window.location.href = await authorizeApp(a.code); });
  const disconnect = (a: AppRow) => run(a.code, async () => {
    if (!await confirm({ title: `Disconnect ${a.label}?`, message: "Tenemsa will forget your sign-in. Nothing in the app itself is changed. You can connect again any time.", confirmLabel: "Disconnect", tone: "danger" })) return;
    setData(await disconnectApp(a.code)); setItems(x => ({ ...x, [a.code]: [] })); toast.success(`${a.label} disconnected.`);
  });
  const show = (a: AppRow) => run(a.code, async () => { setItems(x => ({ ...x, [a.code]: [] })); const r = await previewApp(a.code); setItems(x => ({ ...x, [a.code]: r })); if (!r.length) toast.info("Nothing to show yet."); });
  const toggle = (a: AppRow) => run("t" + a.code, async () => {
    if (a.enabled && !await confirm({ title: `Switch off ${a.label} for everyone?`, message: "Everyone's connection to it is removed.", confirmLabel: "Switch off", tone: "danger" })) return;
    setData(await setAppEnabled(a.code, !a.enabled));
  });

  if (error) return <ErrorState message={error} onRetry={load} />;
  if (!data) return <SkeletonList rows={4} />;

  return (
    <div>
      <PageHeader title="Connected apps" subtitle="Connect your own accounts so Tenemsa AI can use your calendar, code and files when you ask it to." />
      {notice && <Alert tone="error">{notice}</Alert>}
      {!data.ready && <Alert tone="info">Connected apps are not set up on this server yet. The person running Tenemsa needs to add a secret key. See the connected apps guide.</Alert>}
      <p style={{ fontSize: 13.5, color: "var(--text-2)", margin: "4px 0 0" }}>
        You sign in to each app yourself, so Tenemsa only sees what you can see. Access is read only. {data.maxConnections != null && <>Your company has used {data.companyConnections} of {data.maxConnections} connections on its plan. <Link to={`${base}/billing`}>See plans</Link></>}
      </p>
      <div className="cn-grid">
        {data.apps.map(a => (
          <div className="cn-app" key={a.code}>
            <h3>{a.label}
              {a.connected && (a.status === "NEEDS_RECONNECT" ? <Badge tone="warning">Needs reconnecting</Badge> : <Badge tone="success">Connected</Badge>)}
              {!a.enabled && <Badge>Off for your company</Badge>}
            </h3>
            <p>{a.description}</p>
            <small>{a.access}</small>
            {a.connected && a.account && <small>Signed in as {a.account}</small>}
            {!a.configured && a.enabled && <small>Not set up on this server yet.</small>}
            <div className="cn-actions">
              {a.connected ? <>
                {a.status === "NEEDS_RECONNECT"
                  ? <Button variant="primary" size="sm" loading={busy === a.code} onClick={() => connect(a)}>Reconnect</Button>
                  : <Button size="sm" loading={busy === a.code} onClick={() => show(a)}>Show recent</Button>}
                <Button variant="ghost" size="sm" disabled={busy === a.code} onClick={() => disconnect(a)}>Disconnect</Button>
              </> : <Button variant="primary" size="sm" loading={busy === a.code} disabled={!a.enabled || !a.configured || !data.ready} onClick={() => connect(a)}>Connect</Button>}
            </div>
            {(items[a.code]?.length ?? 0) > 0 && (
              <ul className="cn-items">
                {items[a.code].map((i, n) => <li key={n}>{i.url ? <a href={i.url} target="_blank" rel="noreferrer noopener">{i.title}</a> : i.title}<span>{i.subtitle}</span></li>)}
              </ul>
            )}
            {manage && <label className="cn-switch"><input type="checkbox" checked={a.enabled} disabled={busy === "t" + a.code} onChange={() => toggle(a)} /> Allow in this company</label>}
          </div>
        ))}
      </div>
    </div>
  );
}
