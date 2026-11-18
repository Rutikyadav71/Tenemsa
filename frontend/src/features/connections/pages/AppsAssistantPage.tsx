import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Alert, Button, Card, CardHeader, EmptyState, Skeleton } from "../../../shared/ui";
import { getAiStatus, askMyApps, getAppsBrief, aiErrorMessage, type AppLink, type AppsAnswer } from "../../ai/services/aiService";
import type { AiStatus } from "../../../shared/types/models";
import AiChat, { type ChatAnswer } from "../../ai/components/AiChat";
import { getConnections, type Connections } from "../services/connectionService";

const SUGGESTIONS = [
  "What is on my calendar tomorrow?",
  "Do I have any meetings that overlap this week?",
  "Which GitHub issues are assigned to me?",
  "Which files did I change most recently?",
  "What did I work on in GitHub last week?",
  "Find my files about \"budget\"",
];

function Links({ links }: { links: AppLink[] }) {
  if (!links.length) return null;
  return (
    <div style={{ marginTop: 6, display: "flex", flexWrap: "wrap", gap: 6, alignItems: "center" }}>
      <small>Sources:</small>
      {links.map((l, i) => <a key={i} className="tn-link" href={l.url} target="_blank" rel="noopener noreferrer" style={{ fontSize: 12.5 }}>{l.app}: {l.title}</a>)}
    </div>
  );
}

function used(details: unknown) {
  const d = details as { appsRead?: string[]; unavailable?: string[]; links?: AppLink[]; range?: string } | null;
  const read = d?.appsRead ?? [];
  const bad = d?.unavailable ?? [];
  if (!read.length && !bad.length) return null;
  return (
    <div className="ai-extra">
      {read.length > 0 && <small>Based on {d?.range ? `items for ${d.range}` : "recent items"} from {read.join(", ")}.</small>}
      {bad.length > 0 && <small> Could not read: {bad.join("; ")}</small>}
      <Links links={d?.links ?? []} />
    </div>
  );
}

/** One short summary of today from the connected apps, made once a day. */
function Brief() {
  const [b, setB] = useState<AppsAnswer | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState("");
  const load = async (refresh: boolean) => {
    setBusy(true); setErr("");
    try { setB(await getAppsBrief(refresh)); }
    catch (e) { setErr(aiErrorMessage(e, "We couldn't make the brief.")); }
    finally { setBusy(false); }
  };
  return (
    <Card>
      <CardHeader title="Your day" subtitle="A short summary of today from your connected apps."
        actions={<Button size="sm" variant={b ? "ghost" : "primary"} loading={busy} onClick={() => load(!!b)}>{b ? "Refresh" : "Show today's brief"}</Button>} />
      {err && <Alert tone="error">{err}</Alert>}
      {b && <><p style={{ whiteSpace: "pre-wrap", margin: 0 }}>{b.answer}</p><Links links={b.links} />
        <small style={{ display: "block", marginTop: 6, opacity: .7 }}>Made once a day and counted in your company's AI allowance. Refresh to make a new one.</small></>}
    </Card>
  );
}

export default function AppsAssistantPage() {
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [apps, setApps] = useState<Connections | null>(null);
  const loadStatus = useCallback(() => { getAiStatus().then(setStatus).catch(() => {}); }, []);
  useEffect(() => { loadStatus(); getConnections().then(setApps).catch(() => setApps({ ready: false, apps: [], companyConnections: 0, maxConnections: null })); }, [loadStatus]);

  if (!apps) return <Skeleton height={200} />;
  const mine = apps.apps.filter(a => a.connected && a.enabled && a.status === "ACTIVE");
  const base = `/${window.location.pathname.split("/")[1]}`;
  if (mine.length === 0) {
    return <EmptyState title="Connect an app first" text="Ask my apps answers from your own calendar, GitHub or Drive. Connect one to get started."
      action={<Link to={`${base}/connections`} className="tn-link">Open Connected apps</Link>} />;
  }

  const ask = async (question: string, conversationId: number | null): Promise<ChatAnswer> => {
    const r = await askMyApps(question, conversationId);
    return { conversationId: r.conversationId, answer: r.answer, details: { appsRead: r.appsRead, unavailable: r.unavailable, links: r.links, range: r.range } };
  };
  const blocked = status && !status.enabled
    ? { tone: "error" as const, text: status.companyEnabled ? "Tenemsa AI isn't available right now. Please contact your administrator." : "Tenemsa AI is switched off for your company." }
    : null;

  return (
    <>
    {!blocked && <Brief />}
    <AiChat kind="CONNECTED_APPS" title="Ask my apps"
      subtitle={`Ask about your ${mine.map(a => a.label).join(", ")}. It can only read, never change anything.`}
      welcomeTitle="What would you like to know?"
      welcomeText="Tenemsa AI reads recent items from the apps you connected, with your permission, and answers from them. Only your question and the answer are saved."
      placeholder="Ask about your calendar, code or files" suggestions={SUGGESTIONS}
      ask={ask} blocked={blocked} renderDetails={used} onAnswered={loadStatus}
      note={<>Tenemsa AI sees a recent snapshot, not everything in your apps, and can make mistakes.{status?.enabled && ` ${status.requestsLeftThisHour} question${status.requestsLeftThisHour === 1 ? "" : "s"} left this hour.`}</>} />
    </>
  );
}
