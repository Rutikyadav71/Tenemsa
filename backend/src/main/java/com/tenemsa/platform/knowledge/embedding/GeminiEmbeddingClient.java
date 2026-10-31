package com.tenemsa.platform.knowledge.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tenemsa.platform.ai.exception.AiProviderException;
import com.tenemsa.platform.knowledge.config.KbProperties;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Gemini embeddings over plain HTTPS (batchEmbedContents), no SDK. The API key is sent in a header and never
 * logged, and error messages carry only the HTTP status, never the response body. Vectors are cut to the
 * configured size and re-normalised, as Google documents for truncated embeddings.
 */
public class GeminiEmbeddingClient implements EmbeddingClient {

    private final KbProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public GeminiEmbeddingClient(KbProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    @Override public boolean isAvailable() { return true; }
    @Override public String providerName() { return "google-genai"; }
    @Override public String modelName() { return props.getEmbeddingModel(); }
    @Override public int dimensions() { return props.getEmbeddingDimensions(); }

    @Override
    public List<float[]> embed(List<String> texts, Task task) {
        if (texts.isEmpty()) return List.of();
        ObjectNode body = mapper.createObjectNode();
        ArrayNode requests = body.putArray("requests");
        for (String text : texts) {
            ObjectNode r = requests.addObject();
            r.put("model", "models/" + props.getEmbeddingModel());
            r.putObject("content").putArray("parts").addObject().put("text", text);
            r.put("taskType", task == Task.QUERY ? "RETRIEVAL_QUERY" : "RETRIEVAL_DOCUMENT");
            r.put("outputDimensionality", props.getEmbeddingDimensions());
        }
        String url = props.getGeminiBaseUrl().replaceAll("/+$", "") + "/v1beta/models/" + props.getEmbeddingModel() + ":batchEmbedContents";
        HttpResponse<String> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(props.getEmbedTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", props.getGeminiApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiProviderException("The embedding request was interrupted.", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new AiProviderException("The embedding service could not be reached.", e);
        }
        if (response.statusCode() / 100 != 2) {
            throw new AiProviderException("The embedding service returned HTTP " + response.statusCode() + ".", null);
        }
        try {
            JsonNode embeddings = mapper.readTree(response.body()).path("embeddings");
            if (!embeddings.isArray() || embeddings.size() != texts.size()) {
                throw new AiProviderException("The embedding service returned an unexpected number of vectors.", null);
            }
            List<float[]> out = new ArrayList<>(texts.size());
            for (JsonNode e : embeddings) out.add(normalise(e.path("values"), props.getEmbeddingDimensions()));
            return out;
        } catch (IOException e) {
            throw new AiProviderException("The embedding service returned an unreadable answer.", e);
        }
    }

    /** Takes the first {@code dims} numbers and scales the vector to length 1. */
    static float[] normalise(JsonNode values, int dims) {
        if (!values.isArray() || values.size() < dims) {
            throw new AiProviderException("The embedding vector has the wrong size (expected at least " + dims
                    + "). Check KB_EMBEDDING_DIMENSIONS.", null);
        }
        float[] v = new float[dims];
        double sum = 0;
        for (int i = 0; i < dims; i++) {
            v[i] = (float) values.get(i).asDouble();
            sum += (double) v[i] * v[i];
        }
        double norm = Math.sqrt(sum);
        if (norm == 0) throw new AiProviderException("The embedding vector was empty.", null);
        for (int i = 0; i < dims; i++) v[i] = (float) (v[i] / norm);
        return v;
    }
}
