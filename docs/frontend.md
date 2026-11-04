# Frontend design system and shell (Phase 9)

## Theme
- `src/styles/tokens.css` holds every colour, radius and shadow as CSS variables. Dark is the default; `:root[data-theme="light"]` overrides them. Existing screens use the same variable names, so they follow the theme automatically.
- `shared/theme/ThemeProvider.tsx` stores the choice (Light, Dark or System) in `localStorage` under `tenemsa-theme`. A small script in `index.html` applies it before first paint. The choice survives sign-out.
- New code uses the variables, never hard-coded colours. Charts read `--chart-1…6` and `--chart-grid`.

## Brand
- The official logo lives in `public/brand/tenemsa-logo.png`; `tenemsa-icon.png` is the cloud mark cropped from the same file. Use `<Logo />` (`shared/ui`). The logo has dark lettering, so in the dark theme it sits on a light plate (`--logo-plate`).

## Components (`shared/ui`)
Button, Field/Input/Select/Textarea, Card, Badge, Tabs, Modal (also a drawer), Skeleton, EmptyState, ErrorState, Alert, Pagination, PageHeader, Logo, `useToast()`, `useConfirm()`, `useAsyncAction()` (stops double submits). Never use `alert()` or `confirm()`.

## Errors
`shared/lib/errors.ts` turns any failed request into one short sentence (never stack traces, SQL or internal paths). `toast.fail(error, "fallback")` shows it. `shared/lib/validators.ts` has the field checks (email, password policy, dates).

## Shell (`layouts/shell`)
- One `AppShell` serves both workspaces (`/admin/*`, `/user/*`). `navConfig.tsx` is the single list of pages and groups; each item names the permissions that show it. The same rule guards the route (Access Denied page) and fills the command palette (Ctrl/Cmd+K).
- Collapsible grouped sidebar on desktop, drawer plus bottom bar on phones, breadcrumbs, notifications, profile menu with the theme switch, and an error boundary so one broken page cannot blank the app.
- To add a page: add the route in `app/App.tsx` and one entry in `navConfig.tsx`.

## Dependencies
Bootstrap, Tailwind, the emoji picker, postcss and autoprefixer were unused and are removed. Other packages are on their latest compatible versions. Fonts (DM Sans, Outfit) are bundled with `@fontsource-variable`, not loaded from a CDN.

## Sign-in screens (`features/auth`)
- `AuthLayout` frames every signed-out screen (brand panel on wide screens, logo on top on phones). `LoginForm` is the one sign-in screen for employees and admins; `PasswordField` has the show/hide toggle and the live password checklist (8+ characters, upper, lower, number).
- When the session expires, `axiosInstance` sets a one-time flag (`sessionNotice.ts`) and the sign-in page shows "Your session has expired. Please sign in again."
- Register, find-workspace, forgot/reset password, verify email, OAuth callback and accept-invite all use the same layout and `shared/lib/validators.ts`.

## Dashboard (`features/dashboard`)
- One `DashboardPage` serves every role. Each block is shown only if the person has the permission behind it (`report:view:*` company/team figures and charts, `leave:approve:team` approvals, `project:view` tasks, `ai:use` meetings, `attendance:mark:self` today's check-in). Every block loads on its own with skeleton, empty and error states.
- All numbers come from existing endpoints (`/api/analytics/overview`, `/api/analytics/me`, `/api/issues/mine`, `/api/leaves/team`, `/api/meetings`, `/api/notifications`, `/api/attendance/today/{id}`). No new backend endpoint was needed.
- "Download CSV" replaces the old Excel/PDF export, so `xlsx`, `file-saver` and `jspdf` are removed.

## Core pages on the shared kit (Phase 9, stage 4)
- Employees list, add and edit (one `EmployeeForm`), Apply for leave (one `ApplyLeave` for every role), Leave requests table, My leave details, and Notifications (one `NotificationsView` for both workspaces) use the shared Card, Field, Badge, Tabs, table (`tn-table`, turns into cards on phones), Avatar and the loading/empty/error states.
- Failed requests now show a readable message and a Retry button instead of being logged to the console.
- Removed the leave "prediction" button: it called the parked ML service, which never worked (no `leave_type` column). It returns when the predictor is built.

## Dashboard motion and employee filters (Phase 9, checkpoint 5)
- `HeroScene` is choreographed, not looped: sun/moon travel an arc, a plane crosses every ~24 s, two cars stop and go, windows light in an upward sweep, the chart line draws itself, and layers shift slightly with the pointer (fine pointers only). Only transform/opacity animate; the scene pauses when off-screen or the tab is hidden and is static under `prefers-reduced-motion`.
- Employees: search plus department, role, status and sort filters (salary sort only for users who can edit), a "Clear filters" action, and the `?department=` deep link still pre-selects the department.
- Mobile audit (`scrollWidth == clientWidth`) is clean at 320, 390 and 768 px on all 18 main routes.
- Employees table paginates the filtered and sorted list (10/25/50/100 per page, page resets when search/filters change; select-all covers the current page and skips the primary owner). The API still returns up to 1000 rows, so filters and sort cover everyone.
- Hero under `prefers-reduced-motion` keeps only gentle motion (window light, float, slow clouds); travelling motion (sun arc, plane, cars, parallax) stays off. Windows with "Show animations" off reports this setting.

## Invitations, Documents and Teams (Phase 9, checkpoint 7)
- Rebuilt on the shared kit: validated forms, toasts, confirm dialogs for revoke/delete, loading/empty/error-with-retry states, responsive card tables and a bottom-sheet modal on phones.
- Documents: category tabs, file cards with visibility and "Used by Tenemsa AI" badges, upload dialog. Teams: status tabs with counts, searchable member picker, delete errors are now reported instead of ignored.

## Tenemsa AI chat (Phase 9, checkpoint 8)
- `features/ai/components/AiChat.tsx` is the one chat screen behind AI Assistant and Ask documents: saved-chats list (rename, delete, delete all; a drawer on phones), continue any chat, suggested questions, copy, retry on errors, Enter to send.
- Provider, model and setting names (Gemini, AI_PROVIDER, embedding model, vector store) no longer appear anywhere in the UI; the app says "Tenemsa AI". Server "AI is off" messages were reworded the same way and the setup hint moved to the server log.

## Settings (Phase 9, checkpoint 9)

`features/settings` is one page at `/{workspace}/settings` (linked from the account menu) with tabs kept in `?tab=`: My account (photo, details, sign out / all devices), Appearance (Light, Dark, System), Notifications (email switch), Security (change password with live rules, signed-in devices with per-device sign-out), Workspace (admin side only: details, rename, links to invitations, employees and audit), AI and privacy (company AI switch for managers, delete your own saved AI chats). Only settings that really exist are shown. Device names are parsed from the browser's User-Agent on the client and are best effort.

## Remaining pages on the kit (Phase 9, checkpoint 10)

Every screen now uses `shared/ui`. Rebuilt in this pass: one shared profile page (`employees/pages/ProfilePage.tsx`, wrapped by `AdminProfile` and `UserProfile`), Team leave decisions, Admin control, AI writer, AI usage, Meetings (list and detail), Knowledge base, Insights, Audit log, Projects (list, board with issue dialogs, backlog and sprints) and Attendance (calendar as real buttons with labels, check in/out, admin day view). The task generator and project report dialogs use the shared `Modal`. The old Company settings page is gone; `/admin/company` redirects to Settings → Workspace.

Notes for future pages: use `Card pad={false}` when a card starts with a `CardHeader`, or rely on the `.tn-card-pad > .tn-card-head:first-child` rule that cancels the double padding; give every icon-only button an `aria-label`; keep grid tracks at `minmax(0,1fr)`.

Accessibility pass: axe-core (WCAG 2.0/2.1 A and AA) reports no violations on the checked pages in light and dark. To get there the muted text colour and the status colours in the light theme were darkened, and the dark accent used for filled buttons was deepened to keep white text above 4.5:1.
