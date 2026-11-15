import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { EmptyState, Skeleton } from "../../../shared/ui";
import { getAiStatus, askMyApps } from "../../ai/services/aiService";
import type { AiStatus } from "../../../shared/types/models";
import AiChat, { type ChatAnswer } from "../../ai/components/AiChat";
import { getConnections, type Connections } from "../services/connectionService";

const SUGGESTIONS = [
  "What is on my calendar tomorrow?",
  "Do I have any meetings that overlap this week?",
  "Which GitHub issues are assigned to me?",
  "Which files did I change most recently?",
];

function used(details: unknown) {
  const d = details as { appsRead?: string[]; unavailable?: string[] } | null;
  const read = d?.appsRead ?? [];
  const bad = d?.unavailable ?? [];
  if (!read.length && !bad.length) return null;
  return (
    <div className="ai-extra">
      {read.length > 0 && <small>Based on recent items from {read.join(", ")}.</small>}
      {bad.length > 0 && <small> Could not read: {bad.join("; ")}</small>}
    </div>
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
    return { conversationId: r.conversationId, answer: r.answer, details: { appsRead: r.appsRead, unavailable: r.unavailable } };
  };
  const blocked = status && !status.enabled
    ? { tone: "error" as const, text: status.companyEnabled ? "Tenemsa AI isn't available right now. Please contact your administrator." : "Tenemsa AI is switched off for your company." }
    : null;

  return (
    <AiChat kind="CONNECTED_APPS" title="Ask my apps"
      subtitle={`Ask about your ${mine.map(a => a.label).join(", ")}. It can only read, never change anything.`}
      welcomeTitle="What would you like to know?"
      welcomeText="Tenemsa AI reads recent items from the apps you connected, with your permission, and answers from them. Only your question and the answer are saved."
      placeholder="Ask about your calendar, code or files" suggestions={SUGGESTIONS}
      ask={ask} blocked={blocked} renderDetails={used} onAnswered={loadStatus}
      note={<>Tenemsa AI sees a recent snapshot, not everything in your apps, and can make mistakes.{status?.enabled && ` ${status.requestsLeftThisHour} question${status.requestsLeftThisHour === 1 ? "" : "s"} left this hour.`}</>} />
  );
}
