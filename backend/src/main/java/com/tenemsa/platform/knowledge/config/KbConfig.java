package com.tenemsa.platform.knowledge.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenemsa.platform.knowledge.embedding.DisabledEmbeddingClient;
import com.tenemsa.platform.knowledge.embedding.EmbeddingClient;
import com.tenemsa.platform.knowledge.embedding.GeminiEmbeddingClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableConfigurationProperties(KbProperties.class)
public class KbConfig {

    @Bean
    public EmbeddingClient embeddingClient(KbProperties props, ObjectMapper mapper) {
        if (!props.isEmbeddingEnabled()) {
            return new DisabledEmbeddingClient("The knowledge base needs embeddings. An administrator can enable them by setting "
                    + "AI_PROVIDER=google-genai and GEMINI_API_KEY on the server.");
        }
        return new GeminiEmbeddingClient(props, mapper);
    }

    /** Small pool for document indexing so uploads return immediately; a full queue refuses new work instead of piling up. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService kbExecutor() {
        AtomicInteger n = new AtomicInteger();
        ThreadFactory f = r -> {
            Thread t = new Thread(r, "kb-index-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        return new ThreadPoolExecutor(1, 2, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(50), f,
                new ThreadPoolExecutor.AbortPolicy());
    }
}
