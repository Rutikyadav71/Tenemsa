# Connected apps (Phase 10b)

People can connect their own GitHub, Google Calendar and Google Drive accounts, and ask Tenemsa AI questions about that data (**Ask my apps**).

## How it works

- **Each person signs in to the app themselves** (OAuth). Tenemsa can only see what that person can see, and the access asked for is read only.
- **A company switches an app on first.** Apps are off by default. An owner or admin ticks "Allow in this company" on the Connected apps page. Switching an app off removes everyone's sign-in to it.
- **The plan caps connections** (`plans.max_connections`, counted per company). Reconnecting an app you already have is always allowed.
- **Tokens are encrypted** (AES-256-GCM, key from `CONNECTIONS_SECRET`) and are never returned by any API or written to logs. Without `CONNECTIONS_SECRET` the feature stays off.
- **The sign-in return address is protected** by a signed, 10-minute `state` value that carries the company, person and app, so a forged or replayed callback is refused.
- **Expired sign-ins** are refreshed automatically (Google). When an app stops accepting a sign-in the connection shows "Needs reconnecting".
- **Only fixed apps exist.** There is no way to enter an arbitrary server address, which removes a whole class of server-side request attacks.

## Hardening

- **PKCE** is used for Google sign-in (S256). The verifier is derived from the signed state, so nothing is stored between the two steps.
- **Key rotation:** stored tokens carry the id of the key that encrypted them. To change `CONNECTIONS_SECRET`, put the old value in `CONNECTIONS_PREVIOUS_SECRETS`; saved sign-ins keep working and are re-encrypted under the new key the next time each is used. Remove the old value once every connection has been used or reconnected.
- **People leaving:** deleting an employee removes their app connections and asks the app to forget the token when it can. Closing a company in the platform console removes every connection of that company (nothing is sent to the apps, so people may also revoke access in GitHub or Google). Restoring the company means people connect again.
- **Rate limits** (per IP): 20 connect requests and 30 callbacks per 10 minutes.

## Why not an MCP client library?

The apps are called through their own REST APIs behind a small `ConnectorProvider` interface. That keeps the feature free, testable without extra libraries, and easy to audit. An MCP server can later be added as one more `ConnectorProvider` without changing the page, the plan limits or the encryption.

## Setting up (all free)

1. Add `CONNECTIONS_SECRET` (32+ random characters) and `BACKEND_URL` to `backend/.env`.
2. **GitHub:** Settings > Developer settings > OAuth Apps > New. Homepage `http://localhost:5173`, callback `http://localhost:8080/api/connections/callback/github`. Put the id and secret in `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET`. The app asks for `read:user repo` (GitHub has no read-only scope for private repositories).
3. **Google:** Google Cloud Console > new project > enable the Google Calendar API and Google Drive API > OAuth consent screen (External, Testing) > add yourself as a test user > Credentials > OAuth client ID (Web). Add both redirect URIs: `.../callback/google_calendar` and `.../callback/google_drive`. Put the id and secret in `GOOGLE_CONNECT_CLIENT_ID` / `GOOGLE_CONNECT_CLIENT_SECRET`.
4. Restart the backend. Sign in as an owner, open **Connected apps**, tick "Allow in this company", then Connect.

Notes: in Google's Testing mode only listed test users can connect, and sign-ins expire after 7 days. Going public needs Google's app verification, and `drive.readonly` is a restricted scope that needs a security review. For a small rollout, keep it in Testing.

## API

- `GET /api/connections`: apps, whether each is on, and the person's own connections.
- `POST /api/connections/{app}/authorize` returns the address to send the browser to.
- `GET /api/connections/callback/{app}` (public, signed state) redirects to `/connections/done`.
- `GET /api/connections/{app}/preview`; `DELETE /api/connections/{app}`.
- `PUT /api/connections/apps/{app}` `{enabled}` (needs `company:manage`).

## Not built yet

- Writing back to apps (creating events, issues). Everything is read only by design.
- Backend Java and tests for this stage were written without a Maven run; V16 SQL was checked on PostgreSQL 16 only.

## Ask my apps (Phase 10c)

A saved chat (like the AI Assistant) that answers from the person's own connected apps: "What is on my calendar tomorrow?", "Which issues are assigned to me?", "Which files changed recently?".

How it stays safe:

- **Only your own data.** The connections read are the signed-in person's, taken from the login, never from the request. Apps switched off for the company, and sign-ins that need reconnecting, are skipped.
- **No tools, read only.** The model gets text and returns an answer. It cannot call an app, create events or change anything.
- **App text is untrusted.** Event titles, issue titles and file names are fenced inside `<<<DATA ... DATA>>>` after marker-like text is defused, and the prompt (`prompts/apps-assistant.v1.txt`) tells the model to ignore instructions inside them.
- **Tokens never reach the model.** Only item titles and short details do.
- **Nothing from the apps is stored.** The chat keeps the question, the answer and which apps were read.
- **It is metered.** The call goes through the same gateway as every AI feature (feature `CONNECTED_APPS`), so it counts against the company's monthly token budget and the per-hour and per-day limits, and it stops with a clear message when the budget is spent.
- **A snapshot, not a search.** It sees the most recent items (about 25 per app: upcoming calendar events, assigned issues and review requests, recently changed files). It says so when a question needs more.

Data leaves the company when this is used: the selected items are sent to the AI provider like any other AI feature. Companies that do not allow AI can switch it off in Settings.

API: `POST /api/ai/apps/ask` `{question, conversationId?}` returns `{answer, appsRead, unavailable, conversationId}`; saved chats use `/api/ai/conversations?kind=CONNECTED_APPS`. Needs `ai:assistant:self`.

## Stage 3: Ask my apps goes deeper

- **Question-aware fetching.** The server (not the AI) reads a time window out of the question with plain rules: today, tomorrow, yesterday, the day after tomorrow, this/next/last week, this/next/last month, "next N days". It also reads up to three words in double quotes as search words. Calendar fetches that window (and searches events), Drive searches file names and contents and filters by modified time, GitHub adds issues and pull requests you are involved in that match. A question with neither gets the same recent snapshot as before. English only; the window uses the server's time zone. The model is told what was fetched, so "nothing matched" is not mistaken for "I can't see it".
- **Source links.** Every data line given to the model is numbered. The model returns the numbers it used and the server turns them into links from the app's own data. A number that was never shown is ignored, only `https://` links are shown, and the model never writes a URL itself.
- **Daily brief.** `GET /api/ai/apps/brief` (`?refresh=true` for a new one) summarises today's meetings (with overlaps), pull requests waiting for review and assigned issues. One per person per day is kept in memory and reused; it is lost on restart. Each new one is one AI call counted in the company's allowance, and it is not saved as a chat.
- **Not done in this stage:** links between connected apps and Tenemsa's own projects, leave and meetings, and any action that writes to an app. Apps stay read-only.
- The prompt `apps-assistant.v1.txt` changed in place (item numbers, `itemsUsed`, a note about the fetched window).

## Stage 4: Microsoft 365

Two new apps, **Outlook Calendar** (`microsoft_calendar`, scope `Calendars.Read`) and **OneDrive** (`microsoft_onedrive`, scope `Files.Read`), both read only. They use the Microsoft identity platform v2 authorization-code flow with PKCE and Microsoft Graph, share one app registration, and behave like the Google apps: off until an owner or admin enables them, per-person sign-in, AES-GCM encrypted tokens, plan connection cap, deleted with the person. Question-aware fetching applies: Outlook uses the question's window (default next 14 days) and filters by a quoted word in the title; OneDrive uses file search for a quoted word and filters by modified time. Microsoft rotates refresh tokens, so the newest one is always saved.

Setup (Azure portal > Microsoft Entra ID > App registrations > New registration):
1. Supported account types: "Accounts in any organizational directory and personal Microsoft accounts" (tenant `common`), or put one tenant id in `MICROSOFT_TENANT`.
2. Redirect URIs (platform Web): `.../api/connections/callback/microsoft_calendar` and `.../api/connections/callback/microsoft_onedrive`.
3. API permissions (delegated, Microsoft Graph): `User.Read`, `offline_access`, `Calendars.Read`, `Files.Read`.
4. Certificates & secrets > New client secret. Put the ids in `MICROSOFT_CLIENT_ID` / `MICROSOFT_CLIENT_SECRET`.
Some organisations require an admin to approve the permissions; the person then sees Microsoft's own message.

Event times from Outlook come in the calendar's time zone (shown next to the time); the AI is given that zone as written.

### Not built (and why)

- **Slack, Jira, Notion:** each needs its own app registration and has its own sign-in rules (Jira needs a site lookup after sign-in, Notion only sees pages the person shares with the app). Without accounts to test against, I did not ship providers I could not exercise. Each is one class implementing `ConnectorProvider`.
- **Real MCP connector:** needs an MCP client library and lets a company point at a server URL, which opens server-side request forgery risks. Apps today are a fixed list; `ConnectorProvider` is where an MCP-backed provider would plug in, behind an allow-list set by the Tenemsa team.
- **Drive/OneDrive files into the knowledge base** and **write actions** in apps: not built.
