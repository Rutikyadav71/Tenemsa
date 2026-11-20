import "../styles/App.css";
import { lazy, Suspense } from "react";
import { BrowserRouter as Router, Routes, Route, Navigate, Outlet } from "react-router-dom";

import HomePage from "../features/marketing/pages/HomePage";
const HelpPage = lazy(() => import("../features/help/pages/HelpPage"));
const HowItWorksPage = lazy(() => import("../features/marketing/pages/HowItWorksPage"));
const PrivacyPage = lazy(() => import("../features/marketing/pages/PrivacyPage"));
const TermsPage = lazy(() => import("../features/marketing/pages/TermsPage"));
const ContactPage = lazy(() => import("../features/marketing/pages/ContactPage"));
const EmployeeLogin = lazy(() => import("../features/auth/pages/AuthPage"));
const ForgotPasswordPage = lazy(() => import("../features/auth/pages/ForgotPasswordPage"));
const ResetPasswordPage = lazy(() => import("../features/auth/pages/ResetPasswordPage"));
const VerifyEmailPage = lazy(() => import("../features/auth/pages/VerifyEmailPage"));
const OAuthCallbackPage = lazy(() => import("../features/auth/pages/OAuthCallbackPage"));
const AdminAuthPage = lazy(() => import("../features/auth/pages/AdminAuthPage"));
const RegisterCompanyPage = lazy(() => import("../features/company/pages/RegisterCompanyPage"));
const FindWorkspacePage = lazy(() => import("../features/company/pages/FindWorkspacePage"));
const AcceptInvitePage = lazy(() => import("../features/invitations/pages/AcceptInvitePage"));
const AuditLogPage = lazy(() => import("../features/audit/pages/AuditLogPage"));
const InvitationsPage = lazy(() => import("../features/invitations/pages/InvitationsPage"));
const ProjectsPage = lazy(() => import("../features/projects/pages/ProjectsPage"));
const ProjectBoardPage = lazy(() => import("../features/projects/pages/ProjectBoardPage"));
const ProjectBacklogPage = lazy(() => import("../features/projects/pages/ProjectBacklogPage"));
const MeetingsPage = lazy(() => import("../features/meetings/pages/MeetingsPage"));
const MeetingDetailPage = lazy(() => import("../features/meetings/pages/MeetingDetailPage"));
const AiUsagePage = lazy(() => import("../features/ai/pages/AiUsagePage"));
const WriterPage = lazy(() => import("../features/ai/pages/WriterPage"));
const AppsAssistantPage = lazy(() => import("../features/connections/pages/AppsAssistantPage"));
const ConnectionsPage = lazy(() => import("../features/connections/pages/ConnectionsPage"));
const ConnectionsDone = lazy(() => import("../features/connections/pages/ConnectionsDone"));
const BillingPage = lazy(() => import("../features/billing/pages/BillingPage"));
const AnnouncementsPage = lazy(() => import("../features/announcements/pages/AnnouncementsPage"));
const AssistantPage = lazy(() => import("../features/ai/pages/AssistantPage"));
const TeamLeavesPage = lazy(() => import("../features/leave/pages/TeamLeavesPage"));
const DocumentsPage = lazy(() => import("../features/documents/pages/DocumentsPage"));
const AnalyticsPage = lazy(() => import("../features/analytics/pages/AnalyticsPage"));
const KnowledgePage = lazy(() => import("../features/knowledge/pages/KnowledgePage"));
const AskKnowledgePage = lazy(() => import("../features/knowledge/pages/AskKnowledgePage"));
const ChatPage = lazy(() => import("../features/chat/pages/ChatPage"));
const AttendancePage = lazy(() => import("../features/attendance/pages/Attendance"));
const TeamManagement = lazy(() => import("../features/team/pages/TeamManagement"));
const AdminManagement = lazy(() => import("../features/employees/pages/AdminManagement"));
const AdminNotifications = lazy(() => import("../features/notifications/pages/AdminNotifications"));
const UserNotifications = lazy(() => import("../features/notifications/pages/UserNotifications"));
const UserLeaveDetails = lazy(() => import("../features/leave/pages/UserLeaveDetails"));

const DashboardPage = lazy(() => import("../features/dashboard/pages/DashboardPage"));
const AddEmployee = lazy(() => import("../features/employees/pages/AddEmployee"));
const EmployeeList = lazy(() => import("../features/employees/pages/EmployeeList"));
const UpdateEmployee = lazy(() => import("../features/employees/pages/UpdateEmployee"));
const SettingsPage = lazy(() => import("../features/settings/pages/SettingsPage"));
const AdminProfile = lazy(() => import("../features/employees/pages/AdminProfile"));
const LeaveManagement = lazy(() => import("../features/leave/pages/LeaveManagement"));
const LeaveRequestForm = lazy(() => import("../features/leave/pages/LeaveRequestForm"));

const UserProfile = lazy(() => import("../features/employees/pages/UserProfile"));
const UserLeaveRequestForm = lazy(() => import("../features/leave/pages/UserLeaveRequestForm"));

import AppShell from "../layouts/shell/AppShell";
import { NotFound } from "../layouts/shell/GuardPages";
import PageLoading from "../layouts/shell/PageLoading";
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

export default function App() {
  return (
    <Router>
      <Suspense fallback={<PageLoading full />}>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/how-it-works" element={<HowItWorksPage />} />
        <Route path="/privacy" element={<PrivacyPage />} />
        <Route path="/terms" element={<TermsPage />} />
        <Route path="/contact" element={<ContactPage />} />
        <Route path="/connections/done" element={<ConnectionsDone />} />
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
          <Route path="/admin" element={<AppShell workspace="admin" />}>
            <Route index element={<Navigate to="dashboard" replace />} />
            <Route path="dashboard" element={<DashboardPage />} />
            <Route path="employees" element={<EmployeeList />} />
            <Route path="add" element={<AddEmployee />} />
            <Route path="update/:id" element={<UpdateEmployee />} />
            <Route path="profile" element={<AdminProfile />} />
            <Route path="settings" element={<SettingsPage workspace="admin" />} />
            <Route path="apply-leave" element={<LeaveRequestForm />} />
            <Route path="leaves" element={<LeaveManagement />} />
            <Route path="attendance" element={<AttendancePage isAdmin={true} />} />
            <Route path="teams" element={<TeamManagement isAdmin={true} />} />
            <Route path="notifications" element={<AdminNotifications />} />
            <Route path="chat" element={<ChatPage />} />
            <Route path="manage-admins" element={<AdminManagement />} />
            <Route path="company" element={<Navigate to="/admin/settings?tab=workspace" replace />} />
            <Route path="invitations" element={<InvitationsPage />} />
            <Route path="audit" element={<AuditLogPage />} />
            <Route path="projects" element={<ProjectsPage />} />
            <Route path="projects/:id" element={<ProjectBoardPage />} />
            <Route path="projects/:id/backlog" element={<ProjectBacklogPage />} />
            <Route path="meetings" element={<MeetingsPage />} />
            <Route path="meetings/:id" element={<MeetingDetailPage />} />
            <Route path="ai-usage" element={<AiUsagePage />} />
            <Route path="writer" element={<WriterPage />} />
            <Route path="announcements" element={<AnnouncementsPage />} />
            <Route path="billing" element={<BillingPage />} />
            <Route path="help" element={<HelpPage workspace="admin" />} />
            <Route path="assistant" element={<AssistantPage />} />
            <Route path="team-leaves" element={<TeamLeavesPage />} />
            <Route path="documents" element={<DocumentsPage />} />
            <Route path="connections" element={<ConnectionsPage />} />
            <Route path="ask-apps" element={<AppsAssistantPage />} />
            <Route path="insights" element={<AnalyticsPage />} />
            <Route path="knowledge" element={<KnowledgePage />} />
            <Route path="ask-docs" element={<AskKnowledgePage />} />
          
            <Route path="*" element={<NotFound home="/admin/dashboard" />} />
          </Route>
        </Route>
        <Route element={<ProtectedRoute workspace="user" />}>
          <Route path="/user" element={<AppShell workspace="user" />}>
            <Route index element={<Navigate to="dashboard" replace />} />
            <Route path="dashboard" element={<DashboardPage />} />
            <Route path="profile" element={<UserProfile />} />
            <Route path="settings" element={<SettingsPage workspace="user" />} />
            <Route path="apply-leave" element={<UserLeaveRequestForm />} />
            <Route path="leaves/employee/:empId" element={<UserLeaveDetails />} />
            <Route path="attendance" element={<AttendancePage isAdmin={false} />} />
            <Route path="teams" element={<TeamManagement isAdmin={false} empId={Number(localStorage.getItem("empId"))} />} />
            <Route path="projects" element={<ProjectsPage />} />
            <Route path="projects/:id" element={<ProjectBoardPage />} />
            <Route path="projects/:id/backlog" element={<ProjectBacklogPage />} />
            <Route path="meetings" element={<MeetingsPage />} />
            <Route path="meetings/:id" element={<MeetingDetailPage />} />
            <Route path="writer" element={<WriterPage />} />
            <Route path="assistant" element={<AssistantPage />} />
            <Route path="team-leaves" element={<TeamLeavesPage />} />
            <Route path="documents" element={<DocumentsPage />} />
            <Route path="connections" element={<ConnectionsPage />} />
            <Route path="ask-apps" element={<AppsAssistantPage />} />
            <Route path="insights" element={<AnalyticsPage />} />
            <Route path="knowledge" element={<KnowledgePage />} />
            <Route path="ask-docs" element={<AskKnowledgePage />} />
            <Route path="notifications" element={<UserNotifications />} />
            <Route path="help" element={<HelpPage workspace="user" />} />
            <Route path="chat" element={<ChatPage />} />
          
            <Route path="*" element={<NotFound home="/user/dashboard" />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
      </Suspense>
    </Router>
  );
}
