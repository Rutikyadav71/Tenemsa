import axiosInstance from "../../../shared/api/axiosInstance";
import type {
  AiStatus, AiUsage, CommunicationDraft, CommunicationInput, HrAnswer, Issue, ProjectReport, ReportType,
  TaskDraft, TaskSuggestions,
} from "../../../shared/types/models";

// A model call can take a while; the server stops waiting after its own timeout.
const SLOW = { timeout: 90000 };

export async function getAiStatus(): Promise<AiStatus> {
  return (await axiosInstance.get<AiStatus>("/api/ai/status")).data;
}

export async function getAiUsage(days = 30): Promise<AiUsage> {
  return (await axiosInstance.get<AiUsage>("/api/ai/usage", { params: { days } })).data;
}

export async function setCompanyAi(enabled: boolean) {
  return (await axiosInstance.put("/api/companies/me/ai", { enabled })).data;
}

export async function generateTasks(projectId: number, goal: string, maxTasks: number): Promise<TaskSuggestions> {
  return (await axiosInstance.post<TaskSuggestions>("/api/ai/tasks/generate", { projectId, goal, maxTasks }, SLOW)).data;
}

export async function createAiTasks(projectId: number, tasks: TaskDraft[]): Promise<Issue[]> {
  return (await axiosInstance.post<{ created: Issue[] }>("/api/ai/tasks/create", { projectId, tasks })).data.created;
}

export async function draftMessage(input: CommunicationInput): Promise<CommunicationDraft> {
  return (await axiosInstance.post<CommunicationDraft>("/api/ai/writer/draft", input, SLOW)).data;
}

export async function projectReport(projectId: number, type: ReportType, opts: { days?: number; sprintId?: number } = {}): Promise<ProjectReport> {
  return (await axiosInstance.post<ProjectReport>(`/api/ai/projects/${projectId}/report`, { type, ...opts }, SLOW)).data;
}

export async function askHrAssistant(question: string): Promise<HrAnswer> {
  return (await axiosInstance.post<HrAnswer>("/api/ai/hr-assistant/ask", { question }, SLOW)).data;
}

/** One place that turns any AI failure into a sentence a person can act on. */
export function aiErrorMessage(err: any, fallback: string): string {
  if (err?.code === "ECONNABORTED") return "The AI took too long. Please try again.";
  const data = err?.response?.data;
  const text = (data && typeof data === "object" ? data.message : data) || "";
  return typeof text === "string" && text ? text : fallback;
}
