package com.tenemsa.platform.ai.exception;

/** The provider call itself failed (network, quota, bad key, safety block). HTTP 502 after one retry. */
public class AiProviderException extends RuntimeException {
    public AiProviderException(String message, Throwable cause) { super(message, cause); }
}
