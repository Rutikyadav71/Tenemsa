import { useEffect, useRef, type ReactNode, type ButtonHTMLAttributes } from "react";

export function Button({ variant = "secondary", size, loading, children, ...r }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: "primary" | "secondary" | "ghost" | "danger"; size?: "sm" | "lg"; loading?: boolean }) {
  return (
    <button {...r} disabled={r.disabled || loading} className={`tn-btn tn-btn-${variant}${size ? ` tn-btn-${size}` : ""} ${r.className ?? ""}`}>
      {loading && <span className="tn-spinner" aria-hidden />}{children}
    </button>
  );
}

export function Field({ label, hint, error, children }: { label: string; hint?: string; error?: string | null; children: ReactNode }) {
  return (
    <label className="tn-field">
      <span className="tn-label">{label}</span>
      {children}
      {hint && !error && <span className="tn-hint">{hint}</span>}
      {error && <span className="tn-error" role="alert">{error}</span>}
    </label>
  );
}

export const Input = (p: React.InputHTMLAttributes<HTMLInputElement>) => <input {...p} className={`tn-input ${p.className ?? ""}`} />;
export const Textarea = (p: React.TextareaHTMLAttributes<HTMLTextAreaElement>) => <textarea {...p} className={`tn-input ${p.className ?? ""}`} />;
export const Select = (p: React.SelectHTMLAttributes<HTMLSelectElement>) => <select {...p} className={`tn-input ${p.className ?? ""}`} />;

export function Badge({ tone = "neutral", dot, children }: { tone?: "neutral" | "accent" | "success" | "warning" | "danger" | "info"; dot?: boolean; children: ReactNode }) {
  return <span className={`tn-badge tn-badge-${tone}${dot ? " tn-badge-dot" : ""}`}>{children}</span>;
}

export const statusTone = (s: string) => (s === "ACTIVE" ? "success" : s === "FROZEN" ? "warning" : "danger") as "success" | "warning" | "danger";
export const statusLabel = (s: string) => (s === "DELETED" ? "Closed" : s === "FROZEN" ? "Frozen" : "Active");

export function Alert({ tone = "error", children }: { tone?: "error" | "info" | "success"; children: ReactNode }) {
  return <div className={`pc-alert pc-alert-${tone}`} role={tone === "error" ? "alert" : "status"}>{children}</div>;
}

export function Skeleton({ h = 16, w = "100%" }: { h?: number; w?: number | string }) {
  return <div className="pc-skel" style={{ height: h, width: w }} />;
}

export function Modal({ title, onClose, children, footer }: { title: string; onClose: () => void; children: ReactNode; footer?: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const prev = document.activeElement as HTMLElement | null;
    ref.current?.querySelector<HTMLElement>("input,textarea,button.tn-btn-primary,button.tn-btn-danger")?.focus();
    const h = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    window.addEventListener("keydown", h);
    return () => { window.removeEventListener("keydown", h); prev?.focus(); };
  }, [onClose]);
  return (
    <div className="pc-modal-bd" onMouseDown={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="pc-modal tn-card" role="dialog" aria-modal="true" aria-label={title} ref={ref}>
        <div className="tn-card-head"><h2 className="tn-card-title">{title}</h2><button className="pc-x" onClick={onClose} aria-label="Close">×</button></div>
        <div className="tn-card-pad">{children}</div>
        {footer && <div className="pc-modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

export function Pager({ page, pages, onPage }: { page: number; pages: number; onPage: (p: number) => void }) {
  if (pages <= 1) return null;
  return (
    <nav className="pc-pager" aria-label="Pages">
      <Button size="sm" disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</Button>
      <span>Page {page + 1} of {pages}</span>
      <Button size="sm" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>Next</Button>
    </nav>
  );
}

export function PageHead({ title, sub, actions }: { title: string; sub?: string; actions?: ReactNode }) {
  return (
    <header className="pc-head">
      <div><h1>{title}</h1>{sub && <p>{sub}</p>}</div>
      {actions && <div className="pc-head-actions">{actions}</div>}
    </header>
  );
}

export function Stat({ label, value, sub, tone }: { label: string; value: ReactNode; sub?: string; tone?: "warning" | "danger" | "success" }) {
  return (
    <div className={`pc-stat tn-card${tone ? ` pc-stat-${tone}` : ""}`}>
      <div className="pc-stat-label">{label}</div>
      <div className="pc-stat-value">{value}</div>
      {sub && <div className="pc-stat-sub">{sub}</div>}
    </div>
  );
}
