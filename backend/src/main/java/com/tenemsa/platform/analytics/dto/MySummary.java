package com.tenemsa.platform.analytics.dto;

/** The signed-in person's own numbers. Nothing here is about anyone else. */
public record MySummary(int leaveDaysApprovedThisYear, int pendingLeaveRequests, Double presentRateLast30Days,
                        int openIssues, int overdueIssues, long unreadNotifications) {}
