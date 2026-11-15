import axiosInstance from "../../../shared/api/axiosInstance";

export interface PlanView {
  code: string; name: string; description: string | null; pricePaise: number;
  maxUsers: number | null; monthlyAiTokens: number | null; maxConnections: number | null; contactOnly: boolean;
}
export interface BillingOverview {
  paymentsEnabled: boolean;
  currentPlan: string;
  usage: { people: number; maxPeople: number | null; aiTokensUsed: number; aiTokenBudget: number | null; aiResetsOn: string };
  plans: PlanView[];
  subscription: { planCode: string; status: string; renewsOn: string | null; cancelAtPeriodEnd: boolean } | null;
  payments: { date: string; amountPaise: number | null; paymentId: string | null; status: string | null }[];
}
export interface Checkout { keyId: string; subscriptionId: string; planName: string; amountPaise: number }

export const getBilling = async () => (await axiosInstance.get<BillingOverview>("/api/billing/overview")).data;
export const startCheckout = async (plan: string) => (await axiosInstance.post<Checkout>("/api/billing/checkout", { plan })).data;
export const verifyPayment = async (v: { paymentId: string; subscriptionId: string; signature: string }) =>
  (await axiosInstance.post<BillingOverview>("/api/billing/verify", v)).data;
export const cancelSubscription = async () => (await axiosInstance.post<BillingOverview>("/api/billing/cancel", {})).data;

export const rupees = (paise: number) => `₹${(paise / 100).toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
export const tokens = (n: number | null) => n == null ? "Unlimited" : n >= 1_000_000 ? `${+(n / 1_000_000).toFixed(1)}M` : n >= 1000 ? `${+(n / 1000).toFixed(1)}K` : String(n);

interface RazorpayResponse { razorpay_payment_id: string; razorpay_subscription_id: string; razorpay_signature: string }
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
