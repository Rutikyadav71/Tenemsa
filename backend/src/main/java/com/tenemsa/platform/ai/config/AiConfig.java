package com.tenemsa.platform.ai.config;

import com.tenemsa.platform.ai.provider.AiClient;
import com.tenemsa.platform.ai.provider.DisabledAiClient;
import com.tenemsa.platform.ai.provider.SpringAiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public AiClient aiClient(AiProperties props, ObjectProvider<ChatModel> chatModel) {
        if (!props.isEnabled()) {
            log.info("Tenemsa AI is off. To enable it set AI_PROVIDER=google-genai and GEMINI_API_KEY in backend/.env.");
            return new DisabledAiClient("Tenemsa AI is switched off on this server. Please ask your administrator to turn it on.");
        }
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            log.warn("AI_PROVIDER is set but no model could be created. Check GEMINI_API_KEY and AI_PROVIDER in backend/.env.");
            return new DisabledAiClient("Tenemsa AI isn't set up on this server yet. Please ask your administrator to finish the AI setup.");
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
