package com.tenemsa.platform.knowledge.embedding;

import com.tenemsa.platform.ai.exception.AiUnavailableException;

import java.util.List;

public class DisabledEmbeddingClient implements EmbeddingClient {

    private final String reason;

    public DisabledEmbeddingClient(String reason) { this.reason = reason; }

    @Override public boolean isAvailable() { return false; }
    @Override public String providerName() { return "none"; }
    @Override public String modelName() { return "none"; }
    @Override public int dimensions() { return 0; }

    @Override
    public List<float[]> embed(List<String> texts, Task task) { throw new AiUnavailableException(reason); }
}
