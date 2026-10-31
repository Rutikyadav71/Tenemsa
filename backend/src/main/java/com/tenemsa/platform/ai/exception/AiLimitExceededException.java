package com.tenemsa.platform.ai.exception;

/** A per-user or per-company quota is used up. HTTP 429. */
public class AiLimitExceededException extends RuntimeException {
    public AiLimitExceededException(String message) { super(message); }
}
