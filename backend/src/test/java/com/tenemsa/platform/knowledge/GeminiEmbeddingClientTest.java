package com.tenemsa.platform.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.tenemsa.platform.ai.exception.AiProviderException;
import com.tenemsa.platform.knowledge.config.KbProperties;
import com.tenemsa.platform.knowledge.embedding.EmbeddingClient;
import com.tenemsa.platform.knowledge.embedding.GeminiEmbeddingClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The real HTTP client against a local fake of the Gemini endpoint: request shape, key in a header, normalising, error handling. */
class GeminiEmbeddingClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> keyHeader = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String reply = "";
    private KbProperties props;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            keyHeader.set(ex.getRequestHeaders().getFirst("x-goog-api-key"));
            path.set(ex.getRequestURI().toString());
            byte[] out = reply.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        props = new KbProperties();
        props.setGeminiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        props.setGeminiApiKey("secret-key-123");
        props.setEmbeddingDimensions(4);
    }

    @AfterEach
    void stop() { server.stop(0); }

    @Test
    void sendsOneBatchWithTaskTypeAndKeyInHeaderAndReturnsUnitVectors() throws Exception {
        reply = "{\"embeddings\":[{\"values\":[3,4,0,0,9,9]},{\"values\":[0,0,2,0,1]}]}";
        List<float[]> out = new GeminiEmbeddingClient(props, mapper).embed(List.of("alpha", "beta"), EmbeddingClient.Task.QUERY);

        assertThat(keyHeader.get()).isEqualTo("secret-key-123");
        assertThat(path.get()).doesNotContain("secret-key-123").contains(":batchEmbedContents");
        JsonNode sent = mapper.readTree(body.get());
        assertThat(sent.get("requests")).hasSize(2);
        assertThat(sent.get("requests").get(0).get("taskType").asText()).isEqualTo("RETRIEVAL_QUERY");
        assertThat(sent.get("requests").get(0).get("outputDimensionality").asInt()).isEqualTo(4);
        assertThat(sent.get("requests").get(1).get("content").get("parts").get(0).get("text").asText()).isEqualTo("beta");

        assertThat(out).hasSize(2);
        assertThat(out.get(0)).hasSize(4);                         // cut to the configured size
        assertThat(out.get(0)[0]).isEqualTo(0.6f);
        assertThat(out.get(0)[1]).isEqualTo(0.8f);
        assertThat(out.get(1)[2]).isEqualTo(1f);
    }

    @Test
    void documentsUseTheDocumentTaskType() throws Exception {
        reply = "{\"embeddings\":[{\"values\":[1,0,0,0]}]}";
        new GeminiEmbeddingClient(props, mapper).embed(List.of("alpha"), EmbeddingClient.Task.DOCUMENT);
        assertThat(mapper.readTree(body.get()).get("requests").get(0).get("taskType").asText()).isEqualTo("RETRIEVAL_DOCUMENT");
    }

    @Test
    void httpErrorsNeverLeakTheResponseBodyOrTheKey() {
        status = 403;
        reply = "{\"error\":\"API key secret-key-123 is invalid\"}";
        assertThatThrownBy(() -> new GeminiEmbeddingClient(props, mapper).embed(List.of("alpha"), EmbeddingClient.Task.QUERY))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("403")
                .hasMessageNotContaining("secret-key-123");
    }

    @Test
    void wrongCountOrTooShortVectorsAreRejected() {
        reply = "{\"embeddings\":[{\"values\":[1,0,0,0]}]}";
        assertThatThrownBy(() -> new GeminiEmbeddingClient(props, mapper).embed(List.of("a", "b"), EmbeddingClient.Task.QUERY))
                .isInstanceOf(AiProviderException.class);
        reply = "{\"embeddings\":[{\"values\":[1,0]}]}";
        assertThatThrownBy(() -> new GeminiEmbeddingClient(props, mapper).embed(List.of("a"), EmbeddingClient.Task.QUERY))
                .isInstanceOf(AiProviderException.class).hasMessageContaining("wrong size");
    }

    @Test
    void unreachableServiceIsAProviderError() {
        props.setGeminiBaseUrl("http://127.0.0.1:1");
        assertThatThrownBy(() -> new GeminiEmbeddingClient(props, mapper).embed(List.of("a"), EmbeddingClient.Task.QUERY))
                .isInstanceOf(AiProviderException.class);
    }
}
