package com.tenemsa.platform.platformadmin.service;

/** A platform-console failure with the HTTP status it should answer. */
public class PlatformException extends RuntimeException {
    private final int status;
    public PlatformException(int status, String message) { super(message); this.status = status; }
    public int getStatus() { return status; }
}
