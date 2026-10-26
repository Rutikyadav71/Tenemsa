package com.tenemsa.platform.project.sprint.dto;

import com.tenemsa.platform.project.sprint.model.Sprint;
import com.tenemsa.platform.project.sprint.model.SprintStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Issue and point totals are live for planned/active sprints; completedCount/carriedOverCount are the snapshot from completion. */
public record SprintResponse(Long id, Long projectId, String name, String goal, SprintStatus status,
                             LocalDate startDate, LocalDate endDate, LocalDateTime startedAt, LocalDateTime completedAt,
                             int totalIssues, int doneIssues, int totalPoints, int donePoints,
                             Integer completedCount, Integer carriedOverCount) {
    public static SprintResponse from(Sprint s, int totalIssues, int doneIssues, int totalPoints, int donePoints) {
        return new SprintResponse(s.getId(), s.getProjectId(), s.getName(), s.getGoal(), s.getStatus(),
                s.getStartDate(), s.getEndDate(), s.getStartedAt(), s.getCompletedAt(),
                totalIssues, doneIssues, totalPoints, donePoints, s.getCompletedCount(), s.getCarriedOverCount());
    }
}
