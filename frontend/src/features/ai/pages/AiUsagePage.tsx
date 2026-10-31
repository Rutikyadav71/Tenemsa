import { useEffect, useState } from "react";
import { getAiStatus, getAiUsage } from "../services/aiService";
import type { AiStatus, AiUsage } from "../../../shared/types/models";

const stat = (label: string, value: string | number) => (
  <div className="ems-card" style={{ padding: 14, minWidth: 140 }}>
    <div style={{ fontSize: 12, color: "var(--text-3)" }}>{label}</div>
    <div style={{ fontSize: 22, fontWeight: 700, color: "var(--text-1)" }}>{value}</div>
  </div>
);

export default function AiUsagePage() {
  const [days, setDays] = useState(30);
  const [usage, setUsage] = useState<AiUsage | null>(null);
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    getAiUsage(days).then(setUsage).catch(() => setError("Could not load AI usage."));
  }, [days]);
  useEffect(() => { getAiStatus().then(setStatus).catch(() => {}); }, []);

  const peak = Math.max(1, ...(usage?.byDay.map(d => d.requests) ?? [1]));

  return (
    <div className="fade-in">
      <div className="page-header">
        <div>
          <div className="page-title">AI usage</div>
          <div className="page-sub">
            {status ? (status.enabled ? `Enabled · ${status.provider} · ${status.model}` : "AI is switched off on this server.") : ""}
          </div>
        </div>
        <select className="form-control-ems" style={{ width: "auto" }} value={days} onChange={e => setDays(Number(e.target.value))}>
          {[7, 30, 90].map(d => <option key={d} value={d}>Last {d} days</option>)}
        </select>
      </div>
      {error && <div className="alert-ems alert-error">{error}</div>}
      {usage && (
        <>
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap", marginBottom: 16 }}>
            {stat("Requests", usage.totalRequests)}
            {stat("Successful", usage.successful)}
            {stat("Failed", usage.failed)}
            {stat("Blocked by limits", usage.rateLimited)}
            {stat("Tokens in / out", `${usage.inputTokens.toLocaleString()} / ${usage.outputTokens.toLocaleString()}`)}
            {stat("Avg. response", `${(usage.averageLatencyMs / 1000).toFixed(1)} s`)}
            {usage.estimatedCostUsd !== null && stat("Estimated cost", `$${usage.estimatedCostUsd.toFixed(4)}`)}
          </div>
          {status?.enabled && (
            <div style={{ fontSize: 13, color: "var(--text-3)", marginBottom: 14 }}>
              {usage.estimatedCostUsd !== null && "Cost is an estimate from the price list configured on the server. "}Your remaining requests this hour: {status.requestsLeftThisHour} · Company requests left today: {status.companyRequestsLeftToday}
            </div>
          )}
          <div className="ems-card" style={{ padding: 16, marginBottom: 16 }}>
            <div style={{ fontWeight: 600, color: "var(--text-1)", marginBottom: 10 }}>Requests per day</div>
            {usage.byDay.length === 0 && <div style={{ color: "var(--text-3)" }}>No AI requests in this period.</div>}
            <div style={{ display: "flex", alignItems: "flex-end", gap: 4, height: 120 }}>
              {usage.byDay.map(d => (
                <div key={d.date} title={`${d.date}: ${d.requests} request(s), ${d.failed} failed`}
                     style={{ flex: 1, maxWidth: 28, height: `${(d.requests / peak) * 100}%`, minHeight: 4, background: d.failed > 0 ? "#f59e0b" : "var(--accent-light)", borderRadius: 3 }} />
              ))}
            </div>
          </div>
          <div className="ems-card" style={{ padding: 16 }}>
            <div style={{ fontWeight: 600, color: "var(--text-1)", marginBottom: 10 }}>By feature</div>
            {usage.byFeature.length === 0 && <div style={{ color: "var(--text-3)" }}>Nothing yet.</div>}
            {usage.byFeature.map(f => (
              <div key={f.feature} style={{ display: "flex", justifyContent: "space-between", fontSize: 14, padding: "6px 0", color: "var(--text-2)" }}>
                <span>{f.feature.replace(/_/g, " ").toLowerCase()}</span>
                <span>{f.requests} request(s) · {f.inputTokens.toLocaleString()} in / {f.outputTokens.toLocaleString()} out</span>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
