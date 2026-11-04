import type { ReactNode } from "react";
import { LuBuilding2, LuCalendarCheck, LuSparkles, LuUsersRound, LuShieldCheck, LuKanban } from "react-icons/lu";

export interface BrandPoint { icon: ReactNode; title: string; text: string }

export const DEFAULT_POINTS: BrandPoint[] = [
  { icon: <LuUsersRound size={18} />, title: "People, in one place", text: "Profiles, teams, leave and attendance for the whole company." },
  { icon: <LuKanban size={18} />, title: "Projects that move", text: "Boards, backlogs and meetings your team can follow." },
  { icon: <LuSparkles size={18} />, title: "Tenemsa AI", text: "Ask questions, draft documents and summarise meetings." },
];

export const EMPLOYEE_POINTS: BrandPoint[] = [
  { icon: <LuCalendarCheck size={18} />, title: "Leave and attendance", text: "Apply for time off and follow every approval." },
  { icon: <LuKanban size={18} />, title: "Your work", text: "See your tasks, projects and upcoming meetings." },
  { icon: <LuSparkles size={18} />, title: "Tenemsa AI assistant", text: "Get quick answers about your workspace." },
];

export const ADMIN_POINTS: BrandPoint[] = [
  { icon: <LuUsersRound size={18} />, title: "Manage your people", text: "Invite, organise and review your whole team." },
  { icon: <LuShieldCheck size={18} />, title: "Role-based access", text: "Everyone sees only what their role allows." },
  { icon: <LuBuilding2 size={18} />, title: "Your private workspace", text: "Company data is never shared with other companies." },
];

