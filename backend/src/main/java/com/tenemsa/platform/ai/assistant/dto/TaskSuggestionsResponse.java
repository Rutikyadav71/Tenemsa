package com.tenemsa.platform.ai.assistant.dto;

import java.util.List;

public record TaskSuggestionsResponse(Long projectId, List<SuggestedTask> tasks, String model, String promptVersion) {}
