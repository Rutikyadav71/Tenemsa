import LegalPage from "../components/LegalPage";

export default function PrivacyPage() {
  return (
    <LegalPage title="Privacy" updated="18 November 2026">
      <p>This page explains what Tenemsa stores about you and your company, why, and who can see it. It is written in plain language on purpose.</p>

      <h2>Who is responsible for what</h2>
      <p>Your company decides what goes into its workspace (people, attendance, leave, projects, messages, documents). The company is the one that controls that information; Tenemsa stores it and runs the service on the company's behalf. If you have a question about your own records, ask your company's owner or HR first.</p>

      <h2>What we store</h2>
      <ul>
        <li><b>Account details:</b> name, work email, role, and a password that is stored only in a scrambled (hashed) form.</li>
        <li><b>Workspace content:</b> what you and your colleagues enter, such as attendance, leave requests, projects and tasks, chat messages and files, and documents.</li>
        <li><b>Security records:</b> sign-in sessions and an audit log of sensitive actions, so owners can see who changed what.</li>
        <li><b>AI usage records:</b> which AI feature was used, when, by whom and how large the request was, so the company can see and limit its use.</li>
        <li><b>Billing records:</b> the plan, status and payment references of a company's subscription. Card and bank details are entered with Razorpay and are never seen or stored by Tenemsa.</li>
      </ul>

      <h2>Tenemsa AI</h2>
      <p>When you use an AI feature, the text needed to answer you (your question and the relevant records or documents you are allowed to see) is sent to an AI service to write the reply. The company can switch AI off at any time. AI answers can be wrong, so check anything important.</p>

      <h2>Connected apps</h2>
      <p>If you connect an app such as a calendar or file store, Tenemsa receives read-only access that you can remove at any time. The access keys are stored encrypted, and are deleted when you disconnect, when your account is removed, or when the company closes. Tenemsa never changes anything in those apps.</p>

      <h2>Who can see your information</h2>
      <p>People in your own company, according to their role. People in other companies cannot. Tenemsa's operators can see company-level information needed to run the service (such as a company's name, plan and status), and do not read workspace content as part of normal operation.</p>

      <h2>Cookies and browser storage</h2>
      <p>Tenemsa keeps your sign-in and a few preferences (such as light or dark theme) in your browser. It does not use advertising or cross-site tracking.</p>

      <h2>How long we keep it, and your choices</h2>
      <p>Information stays while your company's workspace is open. A company owner can close the workspace, and people can ask their company to correct or remove their records. To ask us anything about your information, use the contact details below.</p>

      <h2>Changes</h2>
      <p>If this page changes in a way that matters, we will update the date above and tell company owners.</p>
    </LegalPage>
  );
}
