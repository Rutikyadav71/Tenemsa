package com.tenemsa.platform.billing.service;

/** Something the person can fix or should know. The message is shown as it is. */
public class BillingException extends RuntimeException {
    private final int status;
    public BillingException(int status, String message) { super(message); this.status = status; }
    public int getStatus() { return status; }
}
