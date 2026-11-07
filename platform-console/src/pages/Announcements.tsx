import { useCallback, useEffect, useState } from "react";
import { api, errText, fmtDate, type CompanyRow, type Page } from "../api/client";
import { Alert, Badge, Button, Field, Input, Modal, PageHead, Pager, Select, Skeleton, Textarea } from "../components/ui";

interface Row { id: number; title: string; message: string; severity: "INFO" | "WARNING" | "CRITICAL"; audience: "ALL" | "SELECTED"; companies: number; sendEmail: boolean; emailsSent: number; createdBy: string; createdAt: string; expiresAt: string; state: "ACTIVE" | "EXPIRED" | "WITHDRAWN" }

const sevTone = { INFO: "info", WARNING: "warning", CRITICAL: "danger" } as const;

export default function Announcements() {
  const [title, setTitle] = useState(""), [message, setMessage] = useState(""), [severity, setSeverity] = useState("INFO");
  const [days, setDays] = useState("7"), [email, setEmail] = useState(false), [target, setTarget] = useState<"ALL" | "SELECTED">("ALL");
  const [q, setQ] = useState(""), [found, setFound] = useState<CompanyRow[]>([]), [picked, setPicked] = useState<Record<number, string>>({});
  const [busy, setBusy] = useState(false), [err, setErr] = useState<string | null>(null), [ok, setOk] = useState<string | null>(null);
  const [confirm, setConfirm] = useState(false);
  const [list, setList] = useState<Page<Row> | null>(null), [page, setPage] = useState(0);

  const load = useCallback((p = page) => api<Page<Row>>(`/api/platform/announcements?page=${p}&size=8`).then(setList).catch(e => setErr(errText(e))), [page]);
  useEffect(() => { load(page); }, [page, load]);
  useEffect(() => {
    if (target !== "SELECTED") return;
    const t = setTimeout(() => { api<Page<CompanyRow>>(`/api/platform/companies?page=0&size=15&status=ACTIVE${q ? `&search=${encodeURIComponent(q)}` : ""}`).then(r => setFound(r.content)).catch(() => {}); }, 250);
    return () => clearTimeout(t);
  }, [q, target]);

  const ids = Object.keys(picked).map(Number);
  const valid = title.trim().length >= 3 && message.trim().length >= 5 && (target === "ALL" || ids.length > 0);

  async function send() {
    setBusy(true); setErr(null);
    try {
      await api("/api/platform/announcements", { method: "POST", body: { title, message, severity, expiresInDays: Number(days), sendEmail: email, companyIds: target === "SELECTED" ? ids : [] } });
      setOk("Announcement published. Company people see it as a banner now."); setConfirm(false);
      setTitle(""); setMessage(""); setPicked({}); setPage(0); load(0);
    } catch (e) { setErr(errText(e)); setConfirm(false); } finally { setBusy(false); }
  }
  async function withdraw(id: number) {
    try { await api(`/api/platform/announcements/${id}/withdraw`, { method: "POST" }); setOk("Withdrawn. The banner is gone."); load(page); } catch (e) { setErr(errText(e)); }
  }

  return (
    <>
      <PageHead title="Announcements" sub="Tell companies about maintenance, changes or anything important. It shows as a banner for everyone in the company, and can be emailed to owners and admins." />
      {ok && <Alert tone="success">{ok}</Alert>}
      {err && <Alert>{err}</Alert>}
      <div className="pc-two">
        <section className="tn-card tn-card-pad" style={{ display: "grid", gap: 14 }}>
          <h2 className="tn-card-title">New announcement</h2>
          <Field label="Title"><Input value={title} maxLength={150} onChange={e => setTitle(e.target.value)} /></Field>
          <Field label="Message" hint={`${message.length} / 3000`}><Textarea rows={6} maxLength={3000} value={message} onChange={e => setMessage(e.target.value)} /></Field>
          <div className="pc-filters">
            <Select aria-label="Importance" value={severity} onChange={e => setSeverity(e.target.value)}><option value="INFO">Information</option><option value="WARNING">Warning</option><option value="CRITICAL">Critical</option></Select>
            <Select aria-label="Show for" value={days} onChange={e => setDays(e.target.value)}><option value="1">Show for 1 day</option><option value="3">Show for 3 days</option><option value="7">Show for 7 days</option><option value="30">Show for 30 days</option></Select>
            <Select aria-label="Audience" value={target} onChange={e => setTarget(e.target.value as "ALL" | "SELECTED")}><option value="ALL">All active companies</option><option value="SELECTED">Chosen companies</option></Select>
          </div>
          {target === "SELECTED" && (
            <div style={{ display: "grid", gap: 8 }}>
              <Input type="search" placeholder="Search companies…" aria-label="Search companies" value={q} onChange={e => setQ(e.target.value)} />
              <div className="pc-pick">
                {found.map(c => <label key={c.id}><input type="checkbox" checked={c.id in picked} onChange={() => setPicked(p => { const n = { ...p }; if (c.id in n) delete n[c.id]; else n[c.id] = c.name; return n; })} /> {c.name} <span className="pc-sub">{c.slug}</span></label>)}
                {found.length === 0 && <span className="pc-muted">No companies found.</span>}
              </div>
              <span className="pc-muted">{ids.length} chosen{ids.length > 0 && `: ${Object.values(picked).slice(0, 3).join(", ")}${ids.length > 3 ? "…" : ""}`}</span>
            </div>
          )}
          <label className="pc-check"><input type="checkbox" checked={email} onChange={e => setEmail(e.target.checked)} /> Also email the owners and admins of these companies</label>
          <div><Button variant="primary" disabled={!valid} onClick={() => setConfirm(true)}>Review and publish</Button></div>
        </section>

        <section className="tn-card">
          <div className="tn-card-head"><h2 className="tn-card-title">Published</h2></div>
          {!list ? <div style={{ padding: 16 }}><Skeleton h={120} /></div> : (
            <ul className="pc-list">
              {list.content.map(a => (
                <li key={a.id}>
                  <div>
                    <b>{a.title}</b>
                    <span>{a.audience === "ALL" ? "All companies" : `${a.companies} companies`} · {fmtDate(a.createdAt)} · {a.createdBy}</span>
                    <span>{a.sendEmail ? `${a.emailsSent} emailed` : "Banner only"} · until {fmtDate(a.expiresAt)}</span>
                  </div>
                  <div style={{ display: "grid", gap: 6, justifyItems: "end" }}>
                    <Badge tone={a.state === "ACTIVE" ? sevTone[a.severity] : "neutral"}>{a.state === "ACTIVE" ? a.severity.toLowerCase() : a.state.toLowerCase()}</Badge>
                    {a.state === "ACTIVE" && <Button size="sm" onClick={() => withdraw(a.id)}>Withdraw</Button>}
                  </div>
                </li>
              ))}
              {list.content.length === 0 && <li className="pc-muted">Nothing published yet.</li>}
            </ul>
          )}
          {list && <Pager page={list.page} pages={list.totalPages} onPage={setPage} />}
        </section>
      </div>
      {confirm && (
        <Modal title="Publish this announcement?" onClose={() => setConfirm(false)} footer={<><Button onClick={() => setConfirm(false)}>Cancel</Button><Button variant="primary" loading={busy} onClick={send}>Publish</Button></>}>
          <p>It will be shown to everyone at {target === "ALL" ? "every active company" : `${ids.length} chosen ${ids.length === 1 ? "company" : "companies"}`} for {days} {days === "1" ? "day" : "days"}{email ? ", and emailed to their owners and admins" : ""}.</p>
          <p><b>{title}</b></p>
        </Modal>
      )}
    </>
  );
}
