import axiosInstance from "../../../shared/api/axiosInstance";
import type { AnalyticsOverview, MySummary } from "../../../shared/types/models";

export async function getOverview(): Promise<AnalyticsOverview> {
  return (await axiosInstance.get<AnalyticsOverview>("/api/analytics/overview")).data;
}

export async function getMySummary(): Promise<MySummary> {
  return (await axiosInstance.get<MySummary>("/api/analytics/me")).data;
}
