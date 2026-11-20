/**
 * Facts about the company that runs this Tenemsa site, set at build time (see frontend/.env.example).
 * Nothing here is secret: it is printed on the public pages and in the footer.
 */
const env = import.meta.env as Record<string, string | undefined>;

export const SITE = {
  name: "Tenemsa",
  /** Where "Contact us" goes. Leave empty until a real mailbox exists. */
  supportEmail: (env.VITE_SUPPORT_EMAIL || "").trim(),
  /** The legal name and address printed on Privacy and Terms. */
  legalName: (env.VITE_LEGAL_NAME || "").trim(),
  address: (env.VITE_LEGAL_ADDRESS || "").trim(),
  governingLaw: (env.VITE_GOVERNING_LAW || "India").trim(),
  /** Privacy and Terms carry a "not yet reviewed" notice until this is set to "true" after a lawyer has read them. */
  legalReviewed: (env.VITE_LEGAL_REVIEWED || "").trim() === "true",
  /** Shown in the footer, for example "v1.0". */
  version: (env.VITE_APP_VERSION || "").trim(),
};
