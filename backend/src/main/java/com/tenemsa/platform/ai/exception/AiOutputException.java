package com.tenemsa.platform.ai.exception;

/** The model answered, but not with usable, valid structured output even after a repair attempt. HTTP 502. */
public class AiOutputException extends RuntimeException {
    public AiOutputException(String message) { super(message); }
}
