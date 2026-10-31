package com.tenemsa.platform.ai.model;

/** Which product feature made an AI call; stored as text in ai_request_logs.feature. */
public enum AiFeature {
    MEETING_SUMMARY,
    TASK_GENERATION,
    COMMUNICATION_DRAFT,
    PROJECT_REPORT,
    HR_ASSISTANT,
    KB_EMBEDDING,
    KB_ANSWER
}
