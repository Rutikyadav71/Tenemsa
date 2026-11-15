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
