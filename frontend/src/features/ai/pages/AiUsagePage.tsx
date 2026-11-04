import { useCallback, useEffect, useState } from "react";
import { getAiStatus, getAiUsage } from "../services/aiService";
import type { AiStatus, AiUsage } from "../../../shared/types/models";
import { Card, CardHeader, EmptyState, ErrorState, PageHeader, Select, SkeletonList } from "../../../shared/ui";
import "./aiPages.css";

const Stat = ({ label, value }: { label: string; value: string | number }) => (
  <Card className="ai-stat"><div className="ai-muted">{label}</div><div className="ai-stat-v">{value}</div></Card>
);

export default function AiUsagePage() {
  const [days, setDays] = useState(30);
  const [usage, setUsage] = useState<AiUsage | null>(null);
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [failed, setFailed] = useState(false);

  const load = useCallback(() => {
    setFailed(false); setUsage(null);
    getAiUsage(days).then(setUsage).catch(() => setFailed(true));
  }, [days]);
  useEffect(load, [load]);
  useEffect(() => { getAiStatus().then(setStatus).catch(() => {}); }, []);

  const peak = Math.max(1, ...(usage?.byDay.map(d => d.requests) ?? [1]));

  return (
    <div>
      <PageHeader title="AI usage" subtitle={status ? (status.enabled ? "Tenemsa AI is on." : "Tenemsa AI is switched off.") : undefined}
        actions={<Select aria-label="Period" value={days} onChange={e => setDays(Number(e.target.value))}>{[7, 30, 90].map(d => <option key={d} value={d}>Last {d} days</option>)}</Select>} />
      {failed ? <ErrorState message="We couldn't load AI usage." onRetry={load} />
        : !usage ? <SkeletonList rows={3} />
        : (
          <>
            <div className="ai-stats">
              <Stat label="Requests" value={usage.totalRequests} />
              <Stat label="Successful" value={usage.successful} />
              <Stat label="Failed" value={usage.failed} />
              <Stat label="Blocked by limits" value={usage.rateLimited} />
              <Stat label="Tokens in / out" value={`${usage.inputTokens.toLocaleString()} / ${usage.outputTokens.toLocaleString()}`} />
              <Stat label="Average response" value={`${(usage.averageLatencyMs / 1000).toFixed(1)} s`} />
              {usage.estimatedCostUsd !== null && <Stat label="Estimated cost" value={`$${usage.estimatedCostUsd.toFixed(4)}`} />}
            </div>
            {status?.enabled && (
              <p className="ai-muted" style={{ margin: "14px 0" }}>
                {usage.estimatedCostUsd !== null && "Cost is an estimate from the price list set up on the server. "}
                Your requests left this hour: {status.requestsLeftThisHour} · Company requests left today: {status.companyRequestsLeftToday}
              </p>
            )}
            <Card style={{ marginBottom: 16 }}>
              <CardHeader title="Requests per day" />
              {usage.byDay.length === 0 ? <EmptyState title="No AI requests" text="Nothing was sent to Tenemsa AI in this period." /> : (
                <div className="ai-bars" role="img" aria-label={`Requests per day, highest ${peak}`}>
                  {usage.byDay.map(d => (
                    <div key={d.date} className={d.failed > 0 ? "warn" : ""} title={`${d.date}: ${d.requests} request(s), ${d.failed} failed`}
                      style={{ height: `${(d.requests / peak) * 100}%` }} />
                  ))}
                </div>
              )}
              {usage.byDay.some(d => d.failed > 0) && <p className="ai-muted" style={{ marginTop: 8 }}>Amber bars are days with failed requests.</p>}
            </Card>
            <Card>
              <CardHeader title="By feature" />
              {usage.byFeature.length === 0 ? <EmptyState title="Nothing yet" text="Feature usage will show here." /> : usage.byFeature.map(f => (
                <div className="ai-feat" key={f.feature}>
                  <b>{f.feature.replace(/_/g, " ").toLowerCase()}</b>
                  <span>{f.requests} requests · {f.inputTokens.toLocaleString()} in / {f.outputTokens.toLocaleString()} out</span>
                </div>
              ))}
            </Card>
          </>
        )}
    </div>
  );
}
