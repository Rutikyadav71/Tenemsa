package com.tenemsa.platform.company.service;

/** The company's plan does not allow this. The message is shown to the person as it is. */
public class PlanLimitException extends RuntimeException {
    public PlanLimitException(String message) { super(message); }
}
