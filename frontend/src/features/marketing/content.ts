/** The words on the public pages, in one place so they can be edited without touching layout code. */

export interface Step { n: number; title: string; who: string; text: string; points: string[] }

export const STEPS: Step[] = [
  { n: 1, title: "Create your workspace", who: "Company owner",
    text: "Register your company. It gets its own private workspace with its own address, and you become its owner.",
    points: ["Pick a workspace name (it becomes your sign-in address)", "Confirm your email from the message we send", "Nothing is shared with any other company"] },
  { n: 2, title: "Add your people", who: "Owner, admin or HR",
    text: "Add people directly or invite them by email. Each person sets their own password, and their role decides what they can see and do.",
    points: ["Roles: Owner, Admin, HR, Manager, Employee, Intern", "Group people into teams and departments", "Remove access at any time"] },
  { n: 3, title: "Run the working day", who: "Everyone",
    text: "People check in, apply for leave, work on projects and talk in chat. Managers approve leave for their team.",
    points: ["Leave: apply, get approved or declined with a reason", "Projects: boards, sprints and a backlog", "Announcements reach everyone, a role or a team"] },
  { n: 4, title: "Let Tenemsa AI help", who: "Whoever the company allows",
    text: "Ask questions about your own leave and attendance, turn meeting notes into tasks, draft messages, and ask questions across your company documents.",
    points: ["Switched on by the company, off whenever they choose", "Answers from your documents come with their sources", "Usage is counted so the company stays in control"] },
];

export interface RoleCard { role: string; text: string; can: string[] }

export const ROLES: RoleCard[] = [
  { role: "Owner and admins", text: "Set the company up and keep it running.",
    can: ["Add, invite and remove people", "Choose who can do what", "Manage the plan and billing", "Switch AI and connected apps on or off", "See the audit log"] },
  { role: "HR", text: "Look after people and time off.",
    can: ["Keep the employee directory", "See and handle leave requests", "Send announcements", "Review attendance"] },
  { role: "Managers", text: "Keep their team moving.",
    can: ["Approve or decline team leave", "See team attendance", "Run projects and sprints", "Turn meeting notes into tasks with AI"] },
  { role: "Employees and interns", text: "Get their own work done.",
    can: ["Check in and see their hours", "Apply for leave and track it", "Work on assigned tasks", "Chat and ask the AI assistant about their own data"] },
];

export interface Faq { q: string; a: string }

export const FAQ: Faq[] = [
  { q: "Is my company's data visible to other companies?", a: "No. Every company has its own workspace, and every request is checked against the company of the person making it." },
  { q: "Who decides what each person can see?", a: "The company. Roles come with sensible defaults, only owners and admins can change who holds which role, and every sensitive change is written to an audit log." },
  { q: "Do we have to use the AI features?", a: "No. AI is something the company switches on, and it can be switched off again at any time. Each company also has a monthly AI allowance." },
  { q: "Can the AI change my data or send messages by itself?", a: "No. It reads what the person asking is allowed to see and writes a reply. Connected apps such as Calendar or GitHub are read only." },
  { q: "How do we pay?", a: "You can start on the free plan. Paid plans are billed monthly through Razorpay; Tenemsa never sees or stores card details. Plans that include a trial show it before you subscribe." },
  { q: "What if someone forgets their password?", a: "They use \"Forgot password\" on the sign-in page and get a link by email. Owners can also invite a person again." },
  { q: "How do I find my workspace?", a: "Use \"Find your workspace\" with your company name or the address your owner gave you. Your owner can also send you an invitation link." },
];
