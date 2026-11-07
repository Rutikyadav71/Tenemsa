import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api, ago, errText, fmtDay, fmtNum, type CompanyRow, type Page } from "../api/client";
import { Alert, Badge, Button, Input, PageHead, Pager, Select, Skeleton, statusLabel, statusTone } from "../components/ui";

const csvCell = (v: unknown) => `"${String(v ?? "").replace(/"/g, '""')}"`;

export default function Companies() {
  const nav = useNavigate();
  const [q, setQ] = useState(""), [search, setSearch] = useState(""), [status, setStatus] = useState(""), [sort, setSort] = useState("created"), [desc, setDesc] = useState(true), [page, setPage] = useState(0);
  const [data, setData] = useState<Page<CompanyRow> | null>(null), [err, setErr] = useState<string | null>(null), [busy, setBusy] = useState(false);

  useEffect(() => { const t = setTimeout(() => { setSearch(q); setPage(0); }, 300); return () => clearTimeout(t); }, [q]);

  const url = useCallback((p: number, size: number) =>
    `/api/platform/companies?page=${p}&size=${size}&sort=${sort}&desc=${desc}${search ? `&search=${encodeURIComponent(search)}` : ""}${status ? `&status=${status}` : ""}`, [search, status, sort, desc]);

  useEffect(() => { setData(null); setErr(null); api<Page<CompanyRow>>(url(page, 20)).then(setData).catch(e => setErr(errText(e))); }, [url, page]);

  async function exportCsv() {
    setBusy(true);
    try {
      const rows: CompanyRow[] = [];
      for (let p = 0; p < 50; p++) {
        const r = await api<Page<CompanyRow>>(url(p, 100));
        rows.push(...r.content);
        if (p + 1 >= r.totalPages) break;
      }
      const head = ["Name", "Workspace", "Status", "Owner", "Owner email", "Members", "AI", "Created", "Last sign-in"];
      const body = rows.map(c => [c.name, c.slug, c.status, c.ownerName, c.ownerEmail, c.members, c.aiEnabled ? "on" : "off", c.createdAt, c.lastActiveAt ?? ""].map(csvCell).join(","));
      const blob = new Blob([[head.map(csvCell).join(","), ...body].join("\n")], { type: "text/csv" });
      const a = document.createElement("a"); a.href = URL.createObjectURL(blob); a.download = "companies.csv"; a.click(); URL.revokeObjectURL(a.href);
    } catch (e) { setErr(errText(e)); } finally { setBusy(false); }
  }

  return (
    <>
      <PageHead title="Companies" sub={data ? `${fmtNum(data.totalElements)} found` : undefined} actions={<Button onClick={exportCsv} loading={busy}>Export CSV</Button>} />
      <div className="pc-filters">
        <Input type="search" placeholder="Search by name or workspace…" aria-label="Search companies" value={q} onChange={e => setQ(e.target.value)} />
        <Select aria-label="Status" value={status} onChange={e => { setStatus(e.target.value); setPage(0); }}>
          <option value="">All statuses</option><option value="ACTIVE">Active</option><option value="FROZEN">Frozen</option><option value="DELETED">Closed</option>
        </Select>
        <Select aria-label="Sort by" value={sort} onChange={e => { setSort(e.target.value); setPage(0); }}>
          <option value="created">Newest</option><option value="name">Name</option><option value="members">Members</option><option value="lastActive">Last sign-in</option><option value="status">Status</option>
        </Select>
        <Button onClick={() => { setDesc(!desc); setPage(0); }} aria-label={desc ? "Descending" : "Ascending"}>{desc ? "↓ High to low" : "↑ Low to high"}</Button>
      </div>
      {err && <Alert>{err}</Alert>}
      {!data && !err && <Skeleton h={300} />}
      {data && (
        <div className="tn-card pc-table-wrap">
          <table className="pc-table">
            <thead><tr><th>Company</th><th>Owner</th><th className="num">Members</th><th>Status</th><th>AI</th><th>Last sign-in</th><th>Created</th></tr></thead>
            <tbody>
              {data.content.map(c => (
                <tr key={c.id} onClick={() => nav(`/companies/${c.id}`)} className="pc-row">
                  <td><Link to={`/companies/${c.id}`} onClick={e => e.stopPropagation()}><b>{c.name}</b></Link><div className="pc-sub">{c.slug}</div></td>
                  <td>{c.ownerName ?? "—"}<div className="pc-sub">{c.ownerEmail ?? ""}</div></td>
                  <td className="num">{fmtNum(c.members)}</td>
                  <td><Badge tone={statusTone(c.status)} dot>{statusLabel(c.status)}</Badge></td>
                  <td>{c.aiEnabled ? "On" : "Off"}</td>
                  <td>{ago(c.lastActiveAt)}</td>
                  <td>{fmtDay(c.createdAt)}</td>
                </tr>
              ))}
              {data.content.length === 0 && <tr><td colSpan={7} className="pc-empty">No companies match.</td></tr>}
            </tbody>
          </table>
        </div>
      )}
      {data && <Pager page={data.page} pages={data.totalPages} onPage={setPage} />}
    </>
  );
}
