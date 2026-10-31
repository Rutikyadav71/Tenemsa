package com.tenemsa.platform.meeting.dto;

import com.tenemsa.platform.meeting.model.MeetingStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MeetingListItem(Long id, String title, LocalDate meetingDate, MeetingStatus status,
                              int actionItemCount, int convertedCount, String createdByName, LocalDateTime createdAt) {}
