import type { ReactNode } from "react";
import PublicLayout from "./PublicLayout";
import { SITE } from "../../../shared/lib/site";

/** Frame for Privacy and Terms. Until a lawyer has reviewed the text and VITE_LEGAL_REVIEWED=true, a notice says so. */
export default function LegalPage({ title, updated, children }: { title: string; updated: string; children: ReactNode }) {
  return (
    <PublicLayout>
      <article className="mk-page mk-legal">
        <h1>{title}</h1>
        <p className="mk-muted">Last updated {updated}</p>
        {!SITE.legalReviewed && (
          <div className="mk-notice" role="note">This text has not been reviewed by a lawyer yet. It describes how Tenemsa works today; the company running this site must confirm it before relying on it.</div>
        )}
        {children}
        <p className="mk-muted">{SITE.legalName ? `${SITE.legalName}${SITE.address ? `, ${SITE.address}` : ""}.` : ""}{SITE.supportEmail ? ` Questions: ${SITE.supportEmail}.` : ""}</p>
      </article>
    </PublicLayout>
  );
}
