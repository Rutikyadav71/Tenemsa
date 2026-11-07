import { useEffect, useState } from "react";
import { getPlatformNotices, type PlatformNotice } from "../services/announcementService";
import "../announcements.css";

const KEY = "tn-dismissed-notices";
const read = (): number[] => { try { return JSON.parse(localStorage.getItem(KEY) || "[]"); } catch { return []; } };

/** Notices from the Tenemsa team. A person can dismiss one; that is remembered only in this browser. */
export default function PlatformNotices() {
  const [items, setItems] = useState<PlatformNotice[]>([]);
  const [gone, setGone] = useState<number[]>(read);
  useEffect(() => { getPlatformNotices().then(setItems).catch(() => { /* a banner is optional */ }); }, []);
  const shown = items.filter(n => !gone.includes(n.id));
  if (!shown.length) return null;
  const dismiss = (id: number) => {
    const next = [...gone, id].slice(-50);
    setGone(next);
    try { localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* private mode */ }
  };
  return (
    <div className="pn-wrap" aria-label="Notices from Tenemsa">
      {shown.map(n => (
        <div key={n.id} className={`pn ${n.severity === "CRITICAL" ? "crit" : n.severity === "WARNING" ? "warn" : ""}`} role={n.severity === "INFO" ? "status" : "alert"}>
          <div><b>{n.title}</b><p>{n.message}</p></div>
          <button onClick={() => dismiss(n.id)} aria-label={`Dismiss ${n.title}`}>×</button>
        </div>
      ))}
    </div>
  );
}
