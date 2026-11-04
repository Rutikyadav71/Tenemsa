import { useRef, type KeyboardEvent, type ReactNode } from "react";

export interface TabItem<T extends string> { id: T; label: string; count?: number; icon?: ReactNode }

/** Accessible tab list: arrow keys move between tabs. Render the panel yourself based on `value`. */
export function Tabs<T extends string>({ tabs, value, onChange, label }: { tabs: TabItem<T>[]; value: T; onChange: (id: T) => void; label?: string }) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  const onKey = (e: KeyboardEvent, i: number) => {
    if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
    const next = (i + (e.key === "ArrowRight" ? 1 : tabs.length - 1)) % tabs.length;
    onChange(tabs[next].id);
    refs.current[next]?.focus();
  };
  return (
    <div className="tn-tabs" role="tablist" aria-label={label}>
      {tabs.map((t, i) => (
        <button key={t.id} ref={el => { refs.current[i] = el; }} role="tab" className="tn-tab" aria-selected={t.id === value}
          tabIndex={t.id === value ? 0 : -1} onClick={() => onChange(t.id)} onKeyDown={e => onKey(e, i)}>
          {t.icon}{t.label}{t.count !== undefined && <span className="tn-tab-count">{t.count}</span>}
        </button>
      ))}
    </div>
  );
}
