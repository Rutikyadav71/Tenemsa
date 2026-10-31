package com.tenemsa.platform.meeting.dto;

import com.tenemsa.platform.meeting.model.MeetingStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Full meeting summary, including the original notes so the reviewer can check the AI against its source. */
public record MeetingResponse(Long id, String title, LocalDate meetingDate, String sourceRef, String sourceText,
                              String summary, List<String> decisions, List<String> openQuestions,
                              List<ActionItemResponse> actionItems, MeetingStatus status, Long projectId,
                              String model, String promptVersion, Integer createdByEmpId, String createdByName,
                              LocalDateTime approvedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {}
