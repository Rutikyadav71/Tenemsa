package com.tenemsa.platform.knowledge.embedding;

import java.util.List;

/** Turns text into vectors. The knowledge base knows only this interface, never a provider. */
public interface EmbeddingClient {

    /** DOCUMENT for text being stored, QUERY for a question being searched (providers tune each differently). */
    enum Task { DOCUMENT, QUERY }

    boolean isAvailable();

    String providerName();

    String modelName();

    int dimensions();

    /** One vector per text, same order, each normalised to length 1. Throws AiProviderException / AiUnavailableException. */
    List<float[]> embed(List<String> texts, Task task);
}
