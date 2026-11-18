import axiosInstance from "../../../shared/api/axiosInstance";

export interface PlanView {
  code: string; name: string; description: string | null; pricePaise: number;
  maxUsers: number | null; monthlyAiTokens: number | null; maxConnections: number | null; contactOnly: boolean; trialDays: number;
}
export interface TopupPack { code: string; name: string; tokens: number; pricePaise: number }
export interface TopupRow { date: string; tokens: number; amountPaise: number; period: string; paymentId: string | null }
export interface BillingOverview {
  paymentsEnabled: boolean;
  currentPlan: string;
  usage: { people: number; maxPeople: number | null; aiTokensUsed: number; aiTokenBudget: number | null; aiTopupTokens: number; aiResetsOn: string };
  trialEligible: boolean;
  topupPacks: TopupPack[];
  topups: TopupRow[];
  plans: PlanView[];
  subscription: { planCode: string; status: string; renewsOn: string | null; cancelAtPeriodEnd: boolean; trial: boolean; pendingPlanCode: string | null } | null;
  payments: { date: string; amountPaise: number | null; paymentId: string | null; status: string | null }[];
}
export interface Checkout { keyId: string; subscriptionId: string; planName: string; amountPaise: number }

export const getBilling = async () => (await axiosInstance.get<BillingOverview>("/api/billing/overview")).data;
export const startCheckout = async (plan: string) => (await axiosInstance.post<Checkout>("/api/billing/checkout", { plan })).data;
export const verifyPayment = async (v: { paymentId: string; subscriptionId: string; signature: string }) =>
  (await axiosInstance.post<BillingOverview>("/api/billing/verify", v)).data;
export const changePlan = async (plan: string) => (await axiosInstance.post<BillingOverview>("/api/billing/plan-change", { plan })).data;
export const startTopup = async (pack: string) => (await axiosInstance.post<{ keyId: string; orderId: string; amountPaise: number; packName: string }>("/api/billing/topup/checkout", { pack })).data;
export const verifyTopup = async (v: { orderId: string; paymentId: string; signature: string }) =>
  (await axiosInstance.post<BillingOverview>("/api/billing/topup/verify", v)).data;

export interface BillingDetails { legalName: string; gstin: string; address: string; city: string; state: string; pincode: string; billingEmail: string }
export const getDetails = async () => (await axiosInstance.get<BillingDetails>("/api/billing/details")).data;
export const saveDetails = async (d: BillingDetails) => (await axiosInstance.put<BillingDetails>("/api/billing/details", d)).data;

interface UsageRow { name: string; requests: number; tokens: number }
export interface Usage { period: string; tokensUsed: number; budget: number | null; topupTokens: number; resetsOn: string;
  byFeature: UsageRow[]; byPerson: UsageRow[]; byDay: { usage_day: string; requests: number; tokens: number }[] }
export const getUsage = async () => (await axiosInstance.get<Usage>("/api/billing/usage")).data;

/** Turns the usage tables into a CSV a finance person can open in a spreadsheet. */
export function usageCsv(u: Usage): string {
  const q = (v: string | number) => `"${String(v).replace(/"/g, '""')}"`;
  const lines = [["Section", "Name", "Requests", "Tokens"].map(q).join(",")];
  u.byFeature.forEach(r => lines.push(["Feature", r.name, r.requests, r.tokens].map(q).join(",")));
  u.byPerson.forEach(r => lines.push(["Person", r.name, r.requests, r.tokens].map(q).join(",")));
  u.byDay.forEach(r => lines.push(["Day", r.usage_day, r.requests, r.tokens].map(q).join(",")));
  return lines.join("\n");
}

export const cancelSubscription = async () => (await axiosInstance.post<BillingOverview>("/api/billing/cancel", {})).data;

export const rupees = (paise: number) => `₹${(paise / 100).toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
export const tokens = (n: number | null) => n == null ? "Unlimited" : n >= 1_000_000 ? `${+(n / 1_000_000).toFixed(1)}M` : n >= 1000 ? `${+(n / 1000).toFixed(1)}K` : String(n);

interface RazorpayResponse { razorpay_payment_id: string; razorpay_subscription_id: string; razorpay_signature: string }
interface RazorpayOrderResponse { razorpay_payment_id: string; razorpay_order_id: string; razorpay_signature: string }
interface RazorpayCtor { new (o: Record<string, unknown>): { open(): void; on(ev: string, cb: (r: { error?: { description?: string } }) => void): void } }

let loading: Promise<boolean> | null = null;
/** Loads Razorpay's checkout script once. It is Razorpay's page that takes the card; Tenemsa never sees it. */
function loadCheckout(): Promise<boolean> {
  if ((window as unknown as { Razorpay?: unknown }).Razorpay) return Promise.resolve(true);
  loading ??= new Promise(res => {
    const s = document.createElement("script");
    s.src = "https://checkout.razorpay.com/v1/checkout.js";
    s.onload = () => res(true);
    s.onerror = () => { loading = null; res(false); };
    document.body.appendChild(s);
  });
  return loading;
}

/** Opens the payment window. Resolves with the signed result, or null if the person closed it. Rejects with a message on failure. */
export async function openCheckout(c: Checkout, who: { name?: string; email?: string }): Promise<RazorpayResponse | null> {
  if (!(await loadCheckout())) throw new Error("The payment window could not load. Check your connection and try again.");
  const Razorpay = (window as unknown as { Razorpay: RazorpayCtor }).Razorpay;
  return new Promise((resolve, reject) => {
    const rz = new Razorpay({
      key: c.keyId, subscription_id: c.subscriptionId, name: "Tenemsa", description: `${c.planName} plan, monthly`,
      prefill: { name: who.name, email: who.email }, theme: { color: "#4f6ef7" },
      handler: (r: RazorpayResponse) => resolve(r),
      modal: { ondismiss: () => resolve(null) },
    });
    rz.on("payment.failed", r => reject(new Error(r.error?.description || "The payment failed. You have not been charged for a plan.")));
    rz.open();
  });
}

/** Opens the payment window for a one-off token top-up (a Razorpay order). */
export async function openTopupCheckout(c: { keyId: string; orderId: string; amountPaise: number; packName: string }, who: { name?: string; email?: string }): Promise<RazorpayOrderResponse | null> {
  if (!(await loadCheckout())) throw new Error("The payment window could not load. Check your connection and try again.");
  const Razorpay = (window as unknown as { Razorpay: RazorpayCtor }).Razorpay;
  return new Promise((resolve, reject) => {
    const rz = new Razorpay({
      key: c.keyId, order_id: c.orderId, amount: c.amountPaise, currency: "INR", name: "Tenemsa", description: `${c.packName} (AI tokens)`,
      prefill: { name: who.name, email: who.email }, theme: { color: "#4f6ef7" },
      handler: (r: RazorpayOrderResponse) => resolve(r),
      modal: { ondismiss: () => resolve(null) },
    });
    rz.on("payment.failed", r => reject(new Error(r.error?.description || "The payment failed. You have not been charged.")));
    rz.open();
  });
}
