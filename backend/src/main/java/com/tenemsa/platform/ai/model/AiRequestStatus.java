package com.tenemsa.platform.ai.model;

public enum AiRequestStatus {
    SUCCESS,
    /** Provider error after the retry. */
    FAILED,
    /** Model answered but the output was not valid JSON / failed validation after repair attempts. */
    INVALID_OUTPUT,
    TIMEOUT,
    /** Refused before calling the model because a quota was used up. Not counted towards quotas. */
    RATE_LIMITED
}
