package com.tenemsa.platform.leave.dto;

import com.tenemsa.platform.leave.model.LeaveStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** One row of the team-leave screen. clashesWith = people in the same scope who are off on overlapping days. */
public record TeamLeaveResponse(int id, int empId, String employeeName, String department, LocalDate startDate, LocalDate endDate,
                                int days, String reason, LeaveStatus status, String decidedByName, LocalDateTime decidedAt,
                                String decisionNote, List<String> clashesWith, boolean canDecide) {}
