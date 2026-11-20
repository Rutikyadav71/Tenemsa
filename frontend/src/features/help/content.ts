/**
 * The in-app guides, in one place. Each guide says who it is for (by permission), what to do step by step
 * using the names people see on screen, and which page it belongs to. The Help page lists them, and the
 * small "How this works" card at the top of a page is drawn from the guide whose `navId` matches that page.
 */
export interface GuideStep { title: string; text: string }
export interface Guide {
  id: string;
  title: string;
  summary: string;
  /** Shown on the Help page only to people holding one of these permissions (everyone if empty). */
  perms?: string[];
  /** The menu item (see navConfig) this guide belongs to; the card appears on that page. */
  navId?: string;
  /** Page to open, relative to the workspace, for the "Open" link. */
  path?: string;
  /** Pages that fill the whole screen (chat) show no card. */
  noCard?: boolean;
  steps: GuideStep[];
  tips?: string[];
}

export const GUIDES: Guide[] = [
  { id: "getting-started", title: "Set up your company", summary: "The first things an owner or admin does, in order.", perms: ["user:create"], path: "dashboard",
    steps: [
      { title: "Check the workspace", text: "Open Company settings and look over your company's details. This is also where AI is switched on or off for everyone." },
      { title: "Add your people", text: "Use Add employee for people you want to set up yourself, or Invitations to email a link. Either way, each person chooses their own password." },
      { title: "Give people the right role", text: "Owner, Admin, HR, Manager, Employee and Intern each see different pages. Pick the smallest role that lets someone do their job." },
      { title: "Group them into teams", text: "Create teams so managers see and approve for their own people." },
      { title: "Start a project and say hello", text: "Create a first project, then send an announcement so everyone knows where to find things." },
    ],
    tips: ["You can change a role later in Roles & admins.", "Everything sensitive you do is recorded in the Audit log."] },

  { id: "add-people", title: "Add or invite people", summary: "Two ways to bring someone in.", perms: ["user:create"], navId: "add-employee", path: "add",
    steps: [
      { title: "Choose how", text: "Add employee creates the account now. Invitations emails the person a link to join." },
      { title: "Fill in who they are", text: "Enter their name, work email and role. Department is optional but helps with announcements and reports." },
      { title: "They set their own password", text: "The person gets an email with a link to choose a password. You never see or send passwords." },
      { title: "If the email doesn't arrive", text: "Ask them to check spam. In Invitations you can revoke and send again." },
    ],
    tips: ["Your plan may limit how many people you can have. Plan & billing shows the limit."] },

  { id: "invitations", title: "Invitations", summary: "Send, track and cancel invitations.", perms: ["user:create"], navId: "invitations", path: "invitations", noCard: false,
    steps: [
      { title: "Fill in the form", text: "Full name, Work email, Role and an optional Department, then Send invitation." },
      { title: "Track it", text: "Pending invitations are listed with when they expire." },
      { title: "Change your mind", text: "Revoke an invitation to cancel the link." },
    ] },

  { id: "attendance", title: "Attendance", summary: "Check in and out, and see your month.", perms: ["attendance:mark:self", "attendance:view:team", "attendance:manage:any"], navId: "attendance", path: "attendance",
    steps: [
      { title: "Check in", text: "When you start work, press Check in. Press Check out when you finish; your hours are worked out for you." },
      { title: "Read the calendar", text: "Each day is coloured by status. The legend explains the colours, and the month summary shows your attendance rate." },
      { title: "Managers and admins", text: "Pick a person or a date to see or fix a day with Mark attendance, and add a note if you change something." },
    ] },

  { id: "apply-leave", title: "Apply for leave", summary: "Ask for time off and follow the answer.", perms: ["leave:apply:self"], navId: "apply-leave", path: "apply-leave",
    steps: [
      { title: "Pick the dates", text: "Fill in From and To, then give a Reason." },
      { title: "Submit", text: "The people who approve leave are notified. You can see the status in your leave history and in Notifications." },
      { title: "Get the answer", text: "You are told when it is approved or declined. A declined request always comes with a reason." },
    ],
    tips: ["Check team leave first if you want to avoid a clash with a colleague."] },

  { id: "approve-leave", title: "Approve leave", summary: "Decide on your team's requests.", perms: ["leave:approve:team", "leave:view:any"], navId: "team-leaves", path: "team-leaves",
    steps: [
      { title: "Open the pending requests", text: "Team leave (managers) or Leave requests (HR and admins) lists what is waiting." },
      { title: "Check for clashes", text: "The request shows who else in the team is off on those days." },
      { title: "Approve or Reject", text: "Approving is one click. Rejecting needs a short reason that is sent to the person. You cannot approve your own request." },
    ] },

  { id: "leaves-admin", title: "Leave requests", summary: "Everyone's leave in one place.", perms: ["leave:view:any"], navId: "leaves", path: "leaves",
    steps: [
      { title: "Filter and review", text: "Look at pending requests first." },
      { title: "Decide", text: "Approve, or Reject with a reason. The person is notified either way." },
    ] },

  { id: "projects", title: "Projects, boards and sprints", summary: "Plan work and move it along.", perms: ["project:view"], navId: "projects", path: "projects",
    steps: [
      { title: "Create a project", text: "Choose New project, give it a short Key such as WEB, a Name, and a Lead. (Managers and admins can do this.)" },
      { title: "Add work", text: "Open the project and add issues. Each gets a number like WEB-12, an owner, a priority and optional story points." },
      { title: "Plan a sprint", text: "In the Backlog, choose New sprint, move issues into it, then Start sprint. Only one sprint is active at a time." },
      { title: "Work the board", text: "Move cards across the board as work progresses. Comment on an issue to keep the discussion with the work." },
      { title: "Finish the sprint", text: "Choose Complete sprint and decide where unfinished issues go." },
    ],
    tips: ["Tenemsa AI can turn a goal into tasks and write a status report from the project."] },

  { id: "meetings", title: "Meeting summaries", summary: "Turn notes into a summary and tasks.", perms: ["ai:use"], navId: "meetings", path: "meetings",
    steps: [
      { title: "Add your notes", text: "Choose New summary, give the meeting a Title, and paste the Notes or transcript. Write names as they appear in Tenemsa so tasks can be matched to people." },
      { title: "Generate the summary", text: "Press Generate summary. It can take up to a minute." },
      { title: "Review before you use it", text: "Edit the summary and each suggested task: title, owner, due date and priority. The AI can get things wrong." },
      { title: "Create the tasks", text: "Pick the project and create the tasks you want. Nothing is created until you confirm." },
    ] },

  { id: "assistant", title: "AI Assistant", summary: "Ask about your own leave, attendance and work.", perms: ["ai:assistant:self"], navId: "assistant", path: "assistant", noCard: true,
    steps: [
      { title: "Ask in plain words", text: "For example: how much leave do I have left? The assistant only sees your own information." },
      { title: "Come back to it", text: "Your chats are saved privately so you can reopen and continue them. You can delete any chat." },
      { title: "Check what matters", text: "AI can be wrong. For anything important, confirm with HR or your manager." },
    ] },

  { id: "writer", title: "AI Writer", summary: "Draft a message, then edit it.", perms: ["ai:use"], navId: "writer", path: "writer",
    steps: [
      { title: "Say what you need", text: "Describe who it is for and what it should say." },
      { title: "Edit the draft", text: "Change anything you like. It is only a draft." },
      { title: "Send to people", text: "If you are allowed to send announcements, the Send to people button lets you choose the audience and send it by email." },
    ] },

  { id: "knowledge", title: "Knowledge base", summary: "Add company documents the AI can answer from.", perms: ["kb:manage"], navId: "knowledge", path: "knowledge",
    steps: [
      { title: "Add a document", text: "Choose Upload file (PDF, Word, text, Markdown or CSV up to 10 MB) or paste text." },
      { title: "Choose who can get answers", text: "Decide which audience may get answers from the document, so private papers stay private." },
      { title: "Wait for it to be ready", text: "Indexing runs in the background. The status changes from Indexing to Ready." },
      { title: "Ask away", text: "People then use Ask documents to get answers with their sources." },
    ] },

  { id: "ask-docs", title: "Ask documents", summary: "Get answers from company documents, with sources.", perms: ["kb:ask"], navId: "ask-docs", path: "ask-docs", noCard: true,
    steps: [
      { title: "Ask a question", text: "Ask about a policy, process or anything in the documents you may read." },
      { title: "Read the sources", text: "Each answer shows which documents it came from. If nothing relevant is found, it says so instead of guessing." },
    ] },

  { id: "connections", title: "Connected apps", summary: "Link your calendar, files or code so the AI can use them.", navId: "connections", path: "connections",
    steps: [
      { title: "An owner or admin switches the app on", text: "Apps are off until the company allows them." },
      { title: "You sign in to the app", text: "Choose Connect and sign in with the app's own page. Tenemsa only asks to read, and never changes anything in the app." },
      { title: "Ask your apps", text: "Open Ask my apps and ask things like what is on my calendar tomorrow. Answers can link back to the items they used." },
      { title: "Disconnect any time", text: "Disconnecting deletes the saved access. Your plan may limit how many apps you can connect." },
    ] },

  { id: "ask-apps", title: "Ask my apps", summary: "Questions about your own calendar, files and code.", perms: ["ai:assistant:self"], navId: "ask-apps", path: "ask-apps", noCard: true,
    steps: [
      { title: "Connect an app first", text: "Open Connected apps and sign in to at least one." },
      { title: "Ask about a time or a word", text: "Mention a day such as tomorrow or last week, or put a word in quotes, and Tenemsa fetches that slice from your apps." },
      { title: "Try today's brief", text: "The Your day card summarises today. It is made once a day and counts toward the company's AI allowance." },
    ] },

  { id: "announcements", title: "Announcements", summary: "Tell everyone, a role, a team or specific people.", perms: ["announcement:send"], navId: "announcements", path: "announcements",
    steps: [
      { title: "Write it", text: "Add a Subject and a Message." },
      { title: "Choose who", text: "Send to everyone, selected roles, departments, or named people." },
      { title: "Review and send", text: "You see how many people will get it before you press Review and send. It appears in their notifications and, if you choose, by email." },
    ] },

  { id: "chat", title: "Chat", summary: "Message colleagues and share files.", perms: ["chat:use"], navId: "chat", path: "chat", noCard: true,
    steps: [
      { title: "Start a conversation", text: "Pick a colleague from the list." },
      { title: "Share files", text: "Attach, paste or drag a file. Only the people in the conversation can open it." },
      { title: "Star what matters", text: "Star a message to find it again later." },
    ] },

  { id: "billing", title: "Plan and billing", summary: "Your plan, AI allowance, payments and receipts.", perms: ["billing:view"], navId: "billing", path: "billing",
    steps: [
      { title: "See where you stand", text: "The top of the page shows your plan, how many people and apps you use, and how much of this month's AI allowance is used." },
      { title: "Choose a plan", text: "Owners can subscribe. A plan with a free trial shows it before you start; nothing is charged until the trial ends." },
      { title: "Change plan", text: "Switching plans takes effect at your next renewal, so you stay on the current plan until then." },
      { title: "Need more AI?", text: "Buy a top-up. It adds tokens for the current month only." },
      { title: "Keep your records", text: "Fill in Billing details for your receipts. Receipts are proof of payment, not tax invoices." },
    ],
    tips: ["You get an email at 80% and 100% of the monthly AI allowance."] },

  { id: "ai-usage", title: "AI usage", summary: "See how the company is using AI.", perms: ["ai:usage:view"], navId: "ai-usage", path: "ai-usage",
    steps: [
      { title: "Look at the totals", text: "Requests and the estimated cost for the period." },
      { title: "Find what uses most", text: "Plan & billing breaks this month's tokens down by feature and by person, and can export it." },
    ] },

  { id: "insights", title: "Insights", summary: "Charts about your people, time and work.", navId: "insights", path: "insights",
    steps: [
      { title: "Read the overview", text: "What you see depends on your role: company-wide for admins, your teams for managers." },
    ] },

  { id: "documents", title: "Documents", summary: "Store company files and choose who sees them.", perms: ["document:view"], navId: "documents", path: "documents",
    steps: [
      { title: "Upload", text: "Add a file and choose who may see it." },
      { title: "Find and download", text: "Anyone allowed can open or download the original file." },
    ] },

  { id: "audit", title: "Audit log", summary: "Who did what, and when.", perms: ["audit:view"], navId: "audit", path: "audit",
    steps: [
      { title: "Browse", text: "Sensitive actions such as adding people, role changes and billing changes are recorded." },
      { title: "Investigate", text: "Use it to answer who changed something and when." },
    ] },

  { id: "settings", title: "Your account and security", summary: "Password, devices, theme and notifications.", path: "settings",
    steps: [
      { title: "Change your password", text: "Settings → Security. This signs you out everywhere else." },
      { title: "Check your devices", text: "See where you are signed in and sign out any you don't recognise." },
      { title: "Make it yours", text: "Choose Light, Dark or System in Appearance." },
    ] },
];

export const guideForNav = (navId: string | undefined) => (navId ? GUIDES.find(g => g.navId === navId && !g.noCard) : undefined);

export const ROLE_GLOSSARY: { role: string; text: string }[] = [
  { role: "Owner", text: "Created the workspace. Full control, including the plan and billing." },
  { role: "Admin", text: "Runs the company day to day: people, roles, announcements and settings." },
  { role: "HR", text: "Looks after people, leave and attendance." },
  { role: "Manager", text: "Leads a team: approves its leave, runs its projects, sees its attendance." },
  { role: "Employee", text: "Does their work: attendance, leave, projects, chat and the AI assistant." },
  { role: "Intern", text: "Like an employee, with the access set for interns." },
];
