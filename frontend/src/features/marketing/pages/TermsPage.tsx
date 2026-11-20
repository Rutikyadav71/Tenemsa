import LegalPage from "../components/LegalPage";
import { SITE } from "../../../shared/lib/site";

export default function TermsPage() {
  return (
    <LegalPage title="Terms of use" updated="18 November 2026">
      <p>These terms apply when you create a workspace or use one. By doing either you agree to them.</p>

      <h2>Workspaces and accounts</h2>
      <p>A workspace belongs to the company that created it, and the person who registers it is its owner. Owners are responsible for the people they add and for what is entered in the workspace. Keep your password private and tell your owner if you think someone else has used your account.</p>

      <h2>Acceptable use</h2>
      <ul>
        <li>Do not use Tenemsa to break the law, to harass people, or to store content you have no right to store.</li>
        <li>Do not try to reach another company's data, probe the service for weaknesses, or overload it.</li>
        <li>Do not misuse the AI features, for example to produce harmful or deceptive content.</li>
      </ul>

      <h2>Plans, trials and payments</h2>
      <p>You can start on the free plan. Paid plans are billed monthly through Razorpay and renew until cancelled. If a plan offers a free trial, the first payment is taken when the trial ends, and you can cancel before then to pay nothing. When you cancel, the plan stays until the end of the period you paid for. Plan changes take effect at the next renewal. Extra AI tokens bought as a top-up apply to the month they were bought in. Receipts shown in Tenemsa are proof of payment and are not tax invoices.</p>

      <h2>AI features</h2>
      <p>AI replies are generated automatically and can be incomplete or wrong. You are responsible for checking them before you act on them or send them to anyone.</p>

      <h2>Your content</h2>
      <p>Your company keeps ownership of what it puts into the workspace. You allow Tenemsa to store and process it only to provide the service.</p>

      <h2>Availability</h2>
      <p>We work to keep Tenemsa running, but we do not promise it will always be available or free of errors. We may change or retire features with notice where we can.</p>

      <h2>Ending use</h2>
      <p>An owner can close a workspace at any time. We may suspend a workspace that breaks these terms or is not paid for.</p>

      <h2>Liability</h2>
      <p>To the extent the law allows, Tenemsa is not liable for indirect or consequential losses, and its total liability is limited to what the company paid in the three months before the problem arose. Nothing here limits rights you have by law.</p>

      <h2>Governing law</h2>
      <p>These terms are governed by the laws of {SITE.governingLaw}.</p>
    </LegalPage>
  );
}
