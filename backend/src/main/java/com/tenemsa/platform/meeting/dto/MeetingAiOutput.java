package com.tenemsa.platform.meeting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The JSON the model must return (see prompts/meeting-summary.v1.txt). Validation here is only about
 * "usable": a summary exists and the lists are sane. Overlong text is trimmed by the service, not rejected.
 */
public record MeetingAiOutput(
        @NotBlank(message = "summary is missing") String summary,
        @Size(max = 100, message = "too many decisions") List<String> decisions,
        @Valid @Size(max = 100, message = "too many action items") List<AiActionItem> actionItems,
        @Size(max = 100, message = "too many open questions") List<String> openQuestions
) {
    public record AiActionItem(
            @NotBlank(message = "an action item has no title") String title,
            String description,
            String owner,
            String deadline,
            String priority
    ) {}
}
