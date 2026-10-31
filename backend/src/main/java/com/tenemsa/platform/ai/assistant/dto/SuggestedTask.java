package com.tenemsa.platform.ai.assistant.dto;

import com.tenemsa.platform.project.issue.model.IssuePriority;
import com.tenemsa.platform.project.issue.model.IssueType;

import java.util.List;

/** One cleaned-up suggestion. possibleDuplicate is true when the project already has an issue with the same title. */
public record SuggestedTask(String title, String description, IssueType type, IssuePriority priority,
                            Integer storyPoints, List<String> labels, boolean possibleDuplicate) {}
