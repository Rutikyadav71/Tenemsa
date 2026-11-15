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

## Who can do what

| Action | Who |
|---|---|
| See plan, usage, payments (`billing:view`) | Owner, Admin |
| Choose a plan, cancel (`subscription:manage`) | Owner |
| Edit plans, create Razorpay plans, override a company's limits | Platform console |

## Limits and known simplifications

- A plan change means cancelling first and subscribing to the new plan after the period ends; there is no mid-cycle upgrade or proration yet.
- Companies on Free with more than 10 people can no longer add people (the Free limit is 10). Raise it in **Plans** or per company.
- Prices are in rupees (INR) only. No GST invoices are generated; Razorpay sends its own receipts.
- Existing subscribers keep the price they signed up at when a plan's price changes.
- Backend Java and tests for this phase were written without a Maven run; run `mvn test` once.

## API

- `GET /api/billing/overview`: plan, usage, plans, subscription, recent payments.
- `POST /api/billing/checkout` `{plan}`; `POST /api/billing/verify`; `POST /api/billing/cancel`.
- `POST /api/billing/webhook/razorpay` (public, signed).
- Console: `GET /api/platform/billing`, `PUT /api/platform/billing/plans/{code}`, `POST /api/platform/billing/plans/{code}/razorpay`; company limits accept `aiMonthlyTokens`.
