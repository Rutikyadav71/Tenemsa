package com.tenemsa.platform.analytics.dto;

import java.util.List;

/** What a manager, HR or owner sees. scope is COMPANY or TEAM; projects is null for TEAM. */
public record AnalyticsOverview(String scope, int headcount, List<Count> byDepartment, List<Count> byRole,
                                LeaveStats leave, AttendanceStats attendance, ProjectStats projects) {

    public record Count(String label, long value) {}

    /** daysByMonth: approved leave days per calendar month for the last 6 months, oldest first. */
    public record LeaveStats(int pending, List<String> offToday, List<Count> daysByMonth, List<Count> statusCounts) {}

    /** Last 30 days. presentRate = (present + late + half day) / (those + absent), as a percentage; null with no records. */
    public record AttendanceStats(int days, long present, long late, long halfDay, long absent, long onLeave,
                                  Double presentRate, Double averageWorkHours) {}

    public record ProjectStats(List<Count> issuesByStatus, long overdueIssues) {}
}
