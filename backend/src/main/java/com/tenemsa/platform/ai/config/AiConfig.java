package com.tenemsa.platform.ai.config;

import com.tenemsa.platform.ai.provider.AiClient;
import com.tenemsa.platform.ai.provider.DisabledAiClient;
import com.tenemsa.platform.ai.provider.SpringAiClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wires the AI provider. With ai.provider=none (the default) the application starts with no model
 * and no API key and every AI endpoint answers 503. With ai.provider=google-genai Spring AI creates a
 * ChatModel from spring.ai.google.genai.api-key and it is wrapped in {@link SpringAiClient}.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean
    public AiClient aiClient(AiProperties props, ObjectProvider<ChatModel> chatModel) {
        if (!props.isEnabled()) {
            return new DisabledAiClient("AI features are switched off. An administrator can enable them by setting "
                    + "AI_PROVIDER=google-genai and GEMINI_API_KEY on the server.");
        }
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            return new DisabledAiClient("AI is enabled but no model is configured. Check GEMINI_API_KEY and AI_PROVIDER.");
        }
        return new SpringAiClient(model, props.getProvider());
    }

    /** Bounded pool for model calls: a slow provider can never create unbounded threads or an unbounded queue. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService aiExecutor() {
        AtomicInteger n = new AtomicInteger();
        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "ai-call-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        return new ThreadPoolExecutor(2, 8, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(20), tf,
                new ThreadPoolExecutor.AbortPolicy());
    }
}
