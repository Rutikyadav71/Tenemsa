# Plans, AI tokens and billing (Phase 10a)

## How it fits together

- **Plans** live in the `plans` table (Free, Starter, Business, Enterprise). Each has a price (paise), a people limit, a monthly AI token allowance and a connected-apps limit. A blank limit means unlimited. The prices and limits in migration V15 are placeholders; edit them in the platform console under **Plans**.
- **A company's effective limit** is its own override (set on the company's page in the console) if there is one, otherwise the plan's value.
- **AI tokens** are counted from `ai_request_logs` (input + output tokens, all AI features including document search). The month is the calendar month in the server's time zone. Calls refused for being over a limit are not counted. When a company reaches its allowance, AI calls are refused with a message saying so; nothing else stops working. Owners and admins see a banner from 80%.
- **Payments** use Razorpay Subscriptions (monthly). Tenemsa never sees card details: Razorpay's checkout window takes them.

## Money flow

1. Owner opens **Plan & billing** and picks a plan.
2. Backend creates a Razorpay subscription (and, the first time, the Razorpay plan with the current price) and returns the public key and subscription id.
3. The browser opens Razorpay's checkout. The customer pays.
4. The browser sends `payment_id`, `subscription_id` and `signature` to `POST /api/billing/verify`. The backend checks the signature (HMAC-SHA256 of `payment_id|subscription_id` with the key secret) and switches the plan on.
5. Razorpay then charges every month. Each charge, failure or cancellation arrives as a webhook (`POST /api/billing/webhook/razorpay`, signature checked with the webhook secret, repeated deliveries ignored) and keeps the plan in step.
6. When a subscription is cancelled, halted (payments kept failing) or completed, the company stays on its paid plan until the paid period ends plus `BILLING_GRACE_DAYS`, then a daily job (03:15) moves it to Free.

Because step 4 works without a webhook, **you do not need a public URL to test**. Webhooks are needed for renewals; to try them locally use a tunnel (for example ngrok) and register its URL in the Razorpay dashboard.

## Try it (free, test mode)

1. Create a Razorpay account and stay in **Test mode**. Copy the Key Id and Key Secret into `backend/.env` as `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET`. Restart the backend.
2. Sign in as an owner, open **Plan & billing**, choose Starter.
3. Pay with Razorpay's test card (see Razorpay's "test cards" page; any future expiry and CVV; the test OTP page accepts success).
4. The plan switches to Starter. Check the **Plans** page in the console: the Razorpay plan shows as Ready.

Going live later means complete Razorpay KYC, switch to live keys and register the webhook. Razorpay charges a fee per successful payment (check their current pricing); there is no monthly fee for the Subscriptions product in test mode.

## Safety nets

- **Daily reconcile (03:05):** asks Razorpay for the real state of every live subscription and corrects ours, so a missed or out-of-order webhook never leaves a company on the wrong plan. A failed lookup changes nothing and is retried the next day.
- **Rate limits** (per IP): 10 checkouts per 10 minutes; the webhook allows 600 per minute.

## Who can do what

| Action | Who |
|---|---|
| See plan, usage, payments (`billing:view`) | Owner, Admin |
| Choose a plan, cancel (`subscription:manage`) | Owner |
| Edit plans, create Razorpay plans, override a company's limits | Platform console |

## Limits and known simplifications

- Plan changes always take effect at the next renewal (see Stage 2 below); there is no mid-cycle upgrade or proration.
- Companies on Free with more than 10 people can no longer add people (the Free limit is 10). Raise it in **Plans** or per company.
- Prices are in rupees (INR) only. The receipt Tenemsa shows and emails is proof of payment, not a GST tax invoice.
- Existing subscribers keep the price they signed up at when a plan's price changes.
- Backend Java and tests for this phase were written without a Maven run; run `mvn test` once.

## API

- `GET /api/billing/overview`: plan, usage, plans, subscription, recent payments.
- `POST /api/billing/checkout` `{plan}`; `POST /api/billing/verify`; `POST /api/billing/cancel`.
- `POST /api/billing/webhook/razorpay` (public, signed).
- Console: `GET /api/platform/billing`, `PUT /api/platform/billing/plans/{code}`, `POST /api/platform/billing/plans/{code}/razorpay`; company limits accept `aiMonthlyTokens`.

## Stage 2: billing completeness

- **Free trial.** A plan with `trial_days` above 0 (Starter and Business: 14) starts with a trial on the company's first subscription. Checkout creates the Razorpay subscription with `start_at` set to the end of the trial, so the card is checked now and charged then. The plan works during the trial; the subscription shows as `AUTHENTICATED` with `trial = true` until the first charge arrives. One trial per company. Edit the days per plan in the console. *Using `start_at` for a trial is how Razorpay's docs describe delayed first charges, but it was not tested against Razorpay here; try it once in test mode.*
- **Plan change at renewal.** Owners pick another paid plan and Tenemsa calls Razorpay's update-subscription API with `schedule_change_at = cycle_end`. The new plan is stored as `pending_plan_code` and applied when Razorpay reports the new `plan_id` (webhook or the daily reconcile). Razorpay does not allow changes on UPI or eMandate subscriptions, and does not document proration, so there is no mid-cycle change. If Razorpay refuses, the owner sees its message.
- **Token top-ups.** One-off packs (Small, Medium, Large; prices are placeholders, edit `topup_packs`) are paid through Razorpay Orders. The browser verifies `HMAC(order_id|payment_id)`; the `order.paid` webhook is a backup, and a paid top-up is counted once. Bought tokens add to the budget for the calendar month of purchase only, and do not carry over. Not offered when the plan has no token limit.
- **Emails to owners:** payment received, payment failed, subscription cancelled, and AI allowance at 80% and 100% (checked every 30 minutes, once per month per level, recorded as `alert:{level}:{company}:{yyyy-MM}` in `billing_events`).
- **Usage breakdown:** `GET /api/billing/usage` gives this month's tokens by feature, by person (top 15) and by day; the page can download it as CSV.
- **Billing details:** legal name, optional GSTIN (format-checked, not verified with the government), address and billing email, at `GET/PUT /api/billing/details`. The printable receipt list is proof of payment, not a tax invoice.
- **Console:** **Revenue** page with monthly recurring revenue (trials not counted), money collected this month, subscribers per plan and recent payments. Edit trial days in **Plans**.

New endpoints: `POST /api/billing/plan-change`, `POST /api/billing/topup/checkout`, `POST /api/billing/topup/verify`, `GET|PUT /api/billing/details`, `GET /api/billing/usage`, console `GET /api/platform/billing/revenue`. Migration: `V17__billing_depth` (PostgreSQL applied on 16; MySQL syntax-parsed only).
