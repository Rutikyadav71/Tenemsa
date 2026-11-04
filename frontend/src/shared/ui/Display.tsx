import type { HTMLAttributes, ReactNode } from "react";
import { LuCircleAlert, LuInbox } from "react-icons/lu";
import { Button } from "./Button";

export function Card({ pad = true, className = "", ...rest }: HTMLAttributes<HTMLDivElement> & { pad?: boolean }) {
  return <div className={`tn-card ${pad ? "tn-card-pad" : ""} ${className}`} {...rest} />;
}

export function CardHeader({ title, subtitle, actions }: { title: ReactNode; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="tn-card-head">
      <div><div className="tn-card-title">{title}</div>{subtitle && <div className="tn-card-sub">{subtitle}</div>}</div>
      {actions && <div style={{ display: "flex", gap: 8 }}>{actions}</div>}
    </div>
  );
}

export type Tone = "neutral" | "accent" | "success" | "warning" | "danger" | "info";
export function Badge({ tone = "neutral", dot = false, children }: { tone?: Tone; dot?: boolean; children: ReactNode }) {
  return <span className={`tn-badge tn-badge-${tone} ${dot ? "tn-badge-dot" : ""}`}>{children}</span>;
}

export function PageHeader({ title, subtitle, actions }: { title: ReactNode; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="tn-page-head">
      <div><h1 className="tn-page-title">{title}</h1>{subtitle && <p className="tn-page-sub">{subtitle}</p>}</div>
      {actions && <div className="tn-page-actions">{actions}</div>}
    </div>
  );
}

export function Skeleton({ width = "100%", height = 14, radius, style }: { width?: number | string; height?: number | string; radius?: number; style?: React.CSSProperties }) {
  return <div className="tn-skeleton" aria-hidden="true" style={{ width, height, borderRadius: radius, ...style }} />;
}

/** A few skeleton lines or cards while data loads. */
export function SkeletonList({ rows = 4 }: { rows?: number }) {
  return (
    <div role="status" aria-label="Loading" style={{ display: "grid", gap: 12 }}>
      {Array.from({ length: rows }, (_, i) => <Skeleton key={i} height={46} />)}
      <span className="sr-only">Loading…</span>
    </div>
  );
}

export function EmptyState({ title, text, icon, action }: { title: string; text?: string; icon?: ReactNode; action?: ReactNode }) {
  return (
    <div className="tn-state">
      <div className="tn-state-icon">{icon ?? <LuInbox size={24} />}</div>
      <div className="tn-state-title">{title}</div>
      {text && <div className="tn-state-text">{text}</div>}
      {action}
    </div>
  );
}

export function ErrorState({ message, onRetry, title = "We couldn't load this" }: { message: string; onRetry?: () => void; title?: string }) {
  return (
    <div className="tn-state tn-state-error" role="alert">
      <div className="tn-state-icon"><LuCircleAlert size={24} /></div>
      <div className="tn-state-title">{title}</div>
      <div className="tn-state-text">{message}</div>
      {onRetry && <Button variant="secondary" onClick={onRetry}>Try again</Button>}
    </div>
  );
}

export function Alert({ tone = "info", children }: { tone?: "error" | "success" | "info" | "warning"; children: ReactNode }) {
  return <div className={`tn-alert tn-alert-${tone}`} role={tone === "error" ? "alert" : "status"}>{children}</div>;
}

export function Pagination({ page, pageCount, total, onPage, label = "items" }: { page: number; pageCount: number; total: number; onPage: (p: number) => void; label?: string }) {
  if (pageCount <= 1) return <div className="tn-pagination"><span>{total} {label}</span></div>;
  return (
    <nav className="tn-pagination" aria-label="Pagination">
      <span>{total} {label} · page {page + 1} of {pageCount}</span>
      <span style={{ display: "flex", gap: 8 }}>
        <Button size="sm" disabled={page <= 0} onClick={() => onPage(page - 1)}>Previous</Button>
        <Button size="sm" disabled={page >= pageCount - 1} onClick={() => onPage(page + 1)}>Next</Button>
      </span>
    </nav>
  );
}
