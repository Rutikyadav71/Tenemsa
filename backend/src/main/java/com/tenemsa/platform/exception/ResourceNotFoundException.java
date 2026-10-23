package com.tenemsa.platform.exception;

/** A record that does not exist in the caller's company. Mapped to HTTP 404 by GlobalExceptionHandler. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
