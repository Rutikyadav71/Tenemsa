import axiosInstance from "../../../shared/api/axiosInstance";
import type {
  Issue, IssueComment, IssueInput, IssueStatus, Person, Project, ProjectStatus, Sprint, SprintInput,
} from "../../../shared/types/models";

export async function listProjects(): Promise<Project[]> {
  return (await axiosInstance.get<Project[]>("/api/projects")).data;
}

export async function getProject(id: number): Promise<Project> {
  return (await axiosInstance.get<Project>(`/api/projects/${id}`)).data;
}

export async function createProject(input: { key: string; name: string; description?: string; leadEmpId?: number | null }): Promise<Project> {
  return (await axiosInstance.post<Project>("/api/projects", input)).data;
}

export async function updateProject(id: number, input: { name: string; description?: string; leadEmpId?: number | null; status?: ProjectStatus }): Promise<Project> {
  return (await axiosInstance.put<Project>(`/api/projects/${id}`, input)).data;
}

export async function deleteProject(id: number): Promise<void> {
  await axiosInstance.delete(`/api/projects/${id}`);
}

export async function listPeople(): Promise<Person[]> {
  return (await axiosInstance.get<Person[]>("/api/projects/people")).data;
}

export async function listIssues(projectId: number): Promise<Issue[]> {
  return (await axiosInstance.get<Issue[]>(`/api/projects/${projectId}/issues`)).data;
}

export async function createIssue(projectId: number, input: IssueInput): Promise<Issue> {
  return (await axiosInstance.post<Issue>(`/api/projects/${projectId}/issues`, input)).data;
}

export async function updateIssue(id: number, input: IssueInput): Promise<Issue> {
  return (await axiosInstance.put<Issue>(`/api/issues/${id}`, input)).data;
}

export async function moveIssue(id: number, status: IssueStatus): Promise<Issue> {
  return (await axiosInstance.patch<Issue>(`/api/issues/${id}/status`, { status })).data;
}

export async function deleteIssue(id: number): Promise<void> {
  await axiosInstance.delete(`/api/issues/${id}`);
}

export async function myIssues(): Promise<Issue[]> {
  return (await axiosInstance.get<Issue[]>("/api/issues/mine")).data;
}

export async function listComments(issueId: number): Promise<IssueComment[]> {
  return (await axiosInstance.get<IssueComment[]>(`/api/issues/${issueId}/comments`)).data;
}

export async function addComment(issueId: number, body: string): Promise<IssueComment> {
  return (await axiosInstance.post<IssueComment>(`/api/issues/${issueId}/comments`, { body })).data;
}

export async function listSprints(projectId: number): Promise<Sprint[]> {
  return (await axiosInstance.get<Sprint[]>(`/api/projects/${projectId}/sprints`)).data;
}

export async function createSprint(projectId: number, input: SprintInput): Promise<Sprint> {
  return (await axiosInstance.post<Sprint>(`/api/projects/${projectId}/sprints`, input)).data;
}

export async function updateSprint(id: number, input: SprintInput): Promise<Sprint> {
  return (await axiosInstance.put<Sprint>(`/api/sprints/${id}`, input)).data;
}

export async function startSprint(id: number): Promise<Sprint> {
  return (await axiosInstance.post<Sprint>(`/api/sprints/${id}/start`)).data;
}

export async function completeSprint(id: number, moveUnfinishedTo: number | null): Promise<Sprint> {
  return (await axiosInstance.post<Sprint>(`/api/sprints/${id}/complete`, { moveUnfinishedTo })).data;
}

export async function deleteSprint(id: number): Promise<void> {
  await axiosInstance.delete(`/api/sprints/${id}`);
}

export async function assignToSprint(issueId: number, sprintId: number | null): Promise<Issue> {
  return (await axiosInstance.patch<Issue>(`/api/issues/${issueId}/sprint`, { sprintId })).data;
}

/** Roles that may create/edit projects and change any issue (mirrors project:manage / issue:manage:any). */
export const MANAGER_ROLES = ["OWNER", "ADMIN", "MANAGER"];
