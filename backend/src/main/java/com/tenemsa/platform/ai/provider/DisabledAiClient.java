package com.tenemsa.platform.ai.provider;

import com.tenemsa.platform.ai.exception.AiUnavailableException;

/** Used when AI is off (the default) or the provider has no model bean. Every call explains how to switch it on. */
public class DisabledAiClient implements AiClient {

    private final String reason;

    public DisabledAiClient(String reason) { this.reason = reason; }

    @Override public boolean isAvailable() { return false; }
    @Override public String providerName() { return "none"; }

    @Override
    public AiCompletion complete(String systemPrompt, String userPrompt, String model, double temperature) {
        throw new AiUnavailableException(reason);
    }
}
