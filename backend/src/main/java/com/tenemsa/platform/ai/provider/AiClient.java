package com.tenemsa.platform.ai.provider;

/**
 * The only thing the rest of the application knows about an AI model provider. Business code
 * never imports Spring AI or Gemini classes, so the provider can be swapped (another model,
 * another vendor, a local model) by writing one new implementation of this interface.
 */
public interface AiClient {

    /** False when AI is switched off or not configured. */
    boolean isAvailable();

    /** Short name stored in the usage log, for example "google-genai". */
    String providerName();

    /** One blocking model call. Implementations throw {@link com.tenemsa.platform.ai.exception.AiProviderException} on failure. */
    AiCompletion complete(String systemPrompt, String userPrompt, String model, double temperature);

    /** What came back, plus the token counts the provider reported (null when it did not report them). */
    record AiCompletion(String text, String model, Integer inputTokens, Integer outputTokens) {}
}
