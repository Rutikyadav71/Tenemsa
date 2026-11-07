import { useEffect, useState } from "react";
import { api, errText, fmtDate, fmtNum } from "../api/client";
import { Alert, PageHead, Skeleton } from "../components/ui";

interface Sys { database: string; serverTime: string; javaVersion: string; usedMemoryMb: number; demoCompanies: number; tables: Record<string, number> }

export default function System() {
  const [s, setS] = useState<Sys | null>(null), [err, setErr] = useState<string | null>(null);
  useEffect(() => { api<Sys>("/api/platform/system").then(setS).catch(e => setErr(errText(e))); }, []);
  return (
    <>
      <PageHead title="System" sub="The server and database this console is connected to." />
      {err && <Alert>{err}</Alert>}
      {!s && !err && <Skeleton h={200} />}
      {s && (
        <div className="pc-two">
          <section className="tn-card tn-card-pad"><h2 className="tn-card-title">Server</h2>
            <dl className="pc-dl"><dt>Database</dt><dd>{s.database}</dd><dt>Server time</dt><dd>{fmtDate(s.serverTime)}</dd><dt>Java</dt><dd>{s.javaVersion}</dd><dt>Memory in use</dt><dd>{s.usedMemoryMb} MB</dd><dt>Demo companies</dt><dd>{s.demoCompanies}</dd></dl>
          </section>
          <section className="tn-card tn-card-pad"><h2 className="tn-card-title">Rows in the biggest tables</h2>
            <dl className="pc-dl">{Object.entries(s.tables).map(([k, v]) => <><dt key={k}>{k}</dt><dd key={k + "v"}>{fmtNum(v)}</dd></>)}</dl>
          </section>
        </div>
      )}
    </>
  );
}
