import { useState } from "react";
import "../styles/App.css";
import { BrowserRouter as Router, Routes, Route, Navigate, Outlet } from "react-router-dom";

import HomePage from "../features/home/pages/HomePage";
import EmployeeLogin from "../features/auth/pages/AuthPage";
import ForgotPasswordPage from "../features/auth/pages/ForgotPasswordPage";
import ResetPasswordPage from "../features/auth/pages/ResetPasswordPage";
import VerifyEmailPage from "../features/auth/pages/VerifyEmailPage";
import OAuthCallbackPage from "../features/auth/pages/OAuthCallbackPage";
import AdminAuthPage from "../features/auth/pages/AdminAuthPage";
import RegisterCompanyPage from "../features/company/pages/RegisterCompanyPage";
import FindWorkspacePage from "../features/company/pages/FindWorkspacePage";
import AcceptInvitePage from "../features/invitations/pages/AcceptInvitePage";
import AuditLogPage from "../features/audit/pages/AuditLogPage";
import InvitationsPage from "../features/invitations/pages/InvitationsPage";
import ProjectsPage from "../features/projects/pages/ProjectsPage";
import ProjectBoardPage from "../features/projects/pages/ProjectBoardPage";
import ProjectBacklogPage from "../features/projects/pages/ProjectBacklogPage";
import MeetingsPage from "../features/meetings/pages/MeetingsPage";
import MeetingDetailPage from "../features/meetings/pages/MeetingDetailPage";
import AiUsagePage from "../features/ai/pages/AiUsagePage";
import WriterPage from "../features/ai/pages/WriterPage";
import AssistantPage from "../features/ai/pages/AssistantPage";
import TeamLeavesPage from "../features/leave/pages/TeamLeavesPage";
import DocumentsPage from "../features/documents/pages/DocumentsPage";
import AnalyticsPage from "../features/analytics/pages/AnalyticsPage";
import KnowledgePage from "../features/knowledge/pages/KnowledgePage";
import AskKnowledgePage from "../features/knowledge/pages/AskKnowledgePage";
import ChatPage from "../features/chat/pages/ChatPage";
import AttendancePage from "../features/attendance/pages/Attendance";
import TeamManagement from "../features/team/pages/TeamManagement";
import AdminManagement from "../features/employees/pages/AdminManagement";
import AdminNotifications from "../features/notifications/pages/AdminNotifications";
import UserNotifications from "../features/notifications/pages/UserNotifications";
import UserLeaveDetails from "../features/leave/pages/UserLeaveDetails";

import AdminDashboard from "../features/dashboard/pages/AdminDashboard";
import AddEmployee from "../features/employees/pages/AddEmployee";
import EmployeeList from "../features/employees/pages/EmployeeList";
import UpdateEmployee from "../features/employees/pages/UpdateEmployee";
import CompanySettings from "../features/company/pages/CompanySettings";
import AdminProfile from "../features/employees/pages/AdminProfile";
import AdminNavbar from "../layouts/admin/AdminNavbar";
import AdminSidebar from "../layouts/admin/AdminSidebar";
import LeaveManagement from "../features/leave/pages/LeaveManagement";
import LeaveRequestForm from "../features/leave/pages/LeaveRequestForm";

import UserDashboard from "../features/dashboard/pages/UserDashboard";
import UserProfile from "../features/employees/pages/UserProfile";
import UserLeaveRequestForm from "../features/leave/pages/UserLeaveRequestForm";
import UserNavbar from "../layouts/user/UserNavbar";
import UserSidebar from "../layouts/user/UserSidebar";

import { workspaceFor, type Workspace } from "../shared/lib/roles";

interface ProtectedRouteProps {
  workspace: Workspace;
}

// Which workspace a person may open depends on their business role (Owner/Admin/HR: admin,
// Manager/Employee/Intern: user), not on which sign-in page they used. Someone in the wrong
// workspace is sent to their own instead of being bounced to the home page.
const ProtectedRoute = ({ workspace }: ProtectedRouteProps) => {
  const token = localStorage.getItem("token");
  const role = localStorage.getItem("userRole");
  if (!token || !role) return <Navigate to="/" />;
  return workspaceFor(role) === workspace ? <Outlet /> : <Navigate to={`/${workspaceFor(role)}/dashboard`} />;
};

/* ── Admin layout ──────────────────────────────────────────────────────── */
const AdminLayout = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);  // mobile overlay
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);  // desktop collapse

  const toggleSidebar = () => setSidebarOpen(v => !v);
  const toggleCollapsed = () => setSidebarCollapsed(v => !v);

  return (
    <div className="app-container">
      {/* Sidebar */}
      <div className={`layout-sidebar${sidebarOpen ? " open" : ""}${sidebarCollapsed ? " collapsed" : ""}`}>
        <AdminSidebar
          isOpen={sidebarOpen}
          collapsed={sidebarCollapsed}
          toggleSidebar={toggleSidebar} />
      </div>
      {/* Mobile overlay backdrop */}
      {sidebarOpen && (
        <div className="sidebar-overlay" onClick={toggleSidebar} style={{ zIndex: 998 }} />
      )}
      <div className={`layout-body${sidebarCollapsed ? " sidebar-collapsed" : ""}`}>
        <div className="layout-navbar">
          <AdminNavbar
            toggleSidebar={toggleSidebar}
            toggleCollapsed={toggleCollapsed}
            sidebarCollapsed={sidebarCollapsed} />
        </div>
        <main className="layout-main">
          <Routes>
            <Route path="dashboard" element={<AdminDashboard />} />
            <Route path="employees" element={<EmployeeList />} />
            <Route path="add" element={<AddEmployee />} />
            <Route path="update/:id" element={<UpdateEmployee />} />
            <Route path="profile" element={<AdminProfile />} />
            <Route path="apply-leave" element={<LeaveRequestForm />} />
            <Route path="leaves" element={<LeaveManagement />} />
            <Route path="attendance" element={<AttendancePage isAdmin={true} />} />
            <Route path="teams" element={<TeamManagement isAdmin={true} />} />
            <Route path="notifications" element={<AdminNotifications />} />
            <Route path="chat" element={<ChatPage />} />
            <Route path="manage-admins" element={<AdminManagement />} />
            <Route path="company" element={<CompanySettings />} />
            <Route path="invitations" element={<InvitationsPage />} />
            <Route path="audit" element={<AuditLogPage />} />
            <Route path="projects" element={<ProjectsPage />} />
            <Route path="projects/:id" element={<ProjectBoardPage />} />
            <Route path="projects/:id/backlog" element={<ProjectBacklogPage />} />
            <Route path="meetings" element={<MeetingsPage />} />
            <Route path="meetings/:id" element={<MeetingDetailPage />} />
            <Route path="ai-usage" element={<AiUsagePage />} />
            <Route path="writer" element={<WriterPage />} />
            <Route path="assistant" element={<AssistantPage />} />
            <Route path="team-leaves" element={<TeamLeavesPage />} />
            <Route path="documents" element={<DocumentsPage />} />
            <Route path="insights" element={<AnalyticsPage />} />
            <Route path="knowledge" element={<KnowledgePage />} />
            <Route path="ask-docs" element={<AskKnowledgePage />} />
          </Routes>
        </main>
      </div>
    </div>
  );
};

/* ── User layout ───────────────────────────────────────────────────────── */
const UserLayout = () => {
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const empId = Number(localStorage.getItem("empId"));

  const toggleSidebar = () => setSidebarOpen(v => !v);
  const toggleCollapsed = () => setSidebarCollapsed(v => !v);

  return (
    <div className="app-container">
      <div className={`layout-sidebar${sidebarOpen ? " open" : ""}${sidebarCollapsed ? " collapsed" : ""}`}>
        <UserSidebar
          isOpen={sidebarOpen}
          collapsed={sidebarCollapsed}
          toggleSidebar={toggleSidebar} />
      </div>
      {sidebarOpen && (
        <div className="sidebar-overlay" onClick={toggleSidebar} style={{ zIndex: 998 }} />
      )}
      <div className={`layout-body${sidebarCollapsed ? " sidebar-collapsed" : ""}`}>
        <div className="layout-navbar">
          <UserNavbar
            toggleSidebar={toggleSidebar}
            toggleCollapsed={toggleCollapsed}
            sidebarCollapsed={sidebarCollapsed} />
        </div>
        <main className="layout-main">
          <Routes>
            <Route path="dashboard" element={<UserDashboard />} />
            <Route path="profile" element={<UserProfile />} />
            <Route path="apply-leave" element={<UserLeaveRequestForm />} />
            <Route path="leaves/employee/:empId" element={<UserLeaveDetails />} />
            <Route path="attendance" element={<AttendancePage isAdmin={false} />} />
            <Route path="teams" element={<TeamManagement isAdmin={false} empId={empId} />} />
            <Route path="projects" element={<ProjectsPage />} />
            <Route path="projects/:id" element={<ProjectBoardPage />} />
            <Route path="projects/:id/backlog" element={<ProjectBacklogPage />} />
            <Route path="meetings" element={<MeetingsPage />} />
            <Route path="meetings/:id" element={<MeetingDetailPage />} />
            <Route path="writer" element={<WriterPage />} />
            <Route path="assistant" element={<AssistantPage />} />
            <Route path="team-leaves" element={<TeamLeavesPage />} />
            <Route path="documents" element={<DocumentsPage />} />
            <Route path="insights" element={<AnalyticsPage />} />
            <Route path="knowledge" element={<KnowledgePage />} />
            <Route path="ask-docs" element={<AskKnowledgePage />} />
            <Route path="notifications" element={<UserNotifications />} />
            <Route path="chat" element={<ChatPage />} />
          </Routes>
        </main>
      </div>
    </div>
  );
};

export default function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
        <Route path="/register" element={<RegisterCompanyPage />} />
        <Route path="/workspace" element={<FindWorkspacePage />} />
        <Route path="/accept-invite" element={<AcceptInvitePage />} />
        <Route path="/auth/employee" element={<EmployeeLogin />} />
        <Route path="/auth/admin" element={<AdminAuthPage />} />
        <Route element={<ProtectedRoute workspace="admin" />}>
          <Route path="/admin/*" element={<AdminLayout />} />
        </Route>
        <Route element={<ProtectedRoute workspace="user" />}>
          <Route path="/user/*" element={<UserLayout />} />
        </Route>
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </Router>
  );
}
