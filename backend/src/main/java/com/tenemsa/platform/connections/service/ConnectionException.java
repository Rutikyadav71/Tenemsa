package com.tenemsa.platform.connections.service;

/** Something the person can fix or should know. The message is shown as it is. */
public class ConnectionException extends RuntimeException {
    private final int status;
    public ConnectionException(int status, String message) { super(message); this.status = status; }
    public int getStatus() { return status; }
}
