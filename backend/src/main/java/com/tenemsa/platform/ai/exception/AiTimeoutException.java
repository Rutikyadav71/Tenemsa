package com.tenemsa.platform.ai.exception;

/** The model did not answer within ai.timeout-seconds. HTTP 504. */
public class AiTimeoutException extends RuntimeException {
    public AiTimeoutException(String message) { super(message); }
}
