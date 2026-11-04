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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(KbConfig.class);

    @Bean
    public EmbeddingClient embeddingClient(KbProperties props, ObjectMapper mapper) {
        if (!props.isEmbeddingEnabled()) {
            log.info("Document search is off. To enable it set AI_PROVIDER=google-genai and GEMINI_API_KEY in backend/.env.");
            return new DisabledEmbeddingClient("Document search is switched off on this server. Please ask your administrator to turn it on.");
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
