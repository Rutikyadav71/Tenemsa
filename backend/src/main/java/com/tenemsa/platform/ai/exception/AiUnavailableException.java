package com.tenemsa.platform.ai.exception;

/** AI is switched off or not configured. HTTP 503. */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message) { super(message); }
}
