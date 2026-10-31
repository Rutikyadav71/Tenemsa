package com.tenemsa.platform.ai.provider;

import com.tenemsa.platform.ai.exception.AiProviderException;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

/**
 * Provider implementation on top of Spring AI's ChatModel. Together with AiConfig it is the ONLY code in the
 * project that imports org.springframework.ai: Gemini today (spring-ai-starter-model-google-genai),
 * anything Spring AI supports tomorrow. The model name and temperature are passed per request so the
 * values in application.properties ("ai.model", "ai.temperature") are the single source of truth.
 */
public class SpringAiClient implements AiClient {

    private final ChatModel chatModel;
    private final String provider;

    public SpringAiClient(ChatModel chatModel, String provider) {
        this.chatModel = chatModel;
        this.provider = provider;
    }

    @Override public boolean isAvailable() { return true; }
    @Override public String providerName() { return provider; }

    @Override
    public AiCompletion complete(String systemPrompt, String userPrompt, String model, double temperature) {
        try {
            List<Message> messages = List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt));
            ChatOptions options = ChatOptions.builder().model(model).temperature(temperature).build();
            ChatResponse response = chatModel.call(new Prompt(messages, options));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                throw new AiProviderException("The model returned an empty response.", null);
            }
            String text = response.getResult().getOutput().getText();
            Integer in = null, out = null;
            if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                Number p = response.getMetadata().getUsage().getPromptTokens();
                Number c = response.getMetadata().getUsage().getCompletionTokens();
                in = p == null ? null : p.intValue();
                out = c == null ? null : c.intValue();
            }
            return new AiCompletion(text, model, in, out);
        } catch (AiProviderException e) {
            throw e;
        } catch (RuntimeException e) {
            // Keep the message short and free of request content; the gateway stores only a code.
            throw new AiProviderException("The AI provider call failed (" + e.getClass().getSimpleName() + ").", e);
        }
    }
}
