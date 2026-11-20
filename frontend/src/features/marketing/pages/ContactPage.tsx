import PublicLayout from "../components/PublicLayout";
import { SITE } from "../../../shared/lib/site";

export default function ContactPage() {
  return (
    <PublicLayout>
      <section className="mk-page mk-legal">
        <h1>Contact us</h1>
        {SITE.supportEmail ? (
          <>
            <p className="mk-lead">Write to us and we will reply by email.</p>
            <p><a className="tn-btn tn-btn-primary" href={`mailto:${SITE.supportEmail}`}>{SITE.supportEmail}</a></p>
          </>
        ) : (
          <p className="mk-lead">Contact details have not been set up for this site yet. If you are the site owner, set <code>VITE_SUPPORT_EMAIL</code> when building the frontend.</p>
        )}
        <h2>Before you write</h2>
        <ul>
          <li><b>Can't sign in?</b> Use "Forgot password" on the sign-in page, or ask your company's owner to send you a new invitation.</li>
          <li><b>Don't know your workspace?</b> Use "Find your workspace" with your company name.</li>
          <li><b>A question about your own records</b> (leave, attendance, pay) is best asked of your company's HR or owner, because they control that information.</li>
        </ul>
        {SITE.address && <p className="mk-muted">{SITE.legalName ? `${SITE.legalName}, ` : ""}{SITE.address}</p>}
      </section>
    </PublicLayout>
  );
}
