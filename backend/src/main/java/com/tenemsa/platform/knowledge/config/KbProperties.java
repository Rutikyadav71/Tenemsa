package com.tenemsa.platform.knowledge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings under "kb.*" (see application.properties). */
@ConfigurationProperties(prefix = "kb")
public class KbProperties {

    /** google-genai uses Gemini embeddings (needs GEMINI_API_KEY); anything else switches embedding off. */
    private String embeddingProvider = "none";
    private String geminiApiKey = "";
    private String geminiBaseUrl = "https://generativelanguage.googleapis.com";
    private String embeddingModel = "gemini-embedding-001";
    /** Must match the pgvector column (vector(768)). Gemini embeddings are truncated to this size and re-normalised. */
    private int embeddingDimensions = 768;
    private int embedBatchSize = 16;
    private int embedTimeoutSeconds = 30;
    /** Embedding requests (batches) a company may send per day, on top of the chat quotas. */
    private int embedPerCompanyPerDay = 2000;

    private int chunkChars = 900;
    private int chunkOverlap = 150;
    private int maxDocumentChars = 200_000;
    private int maxChunksPerDocument = 400;

    private int topK = 6;
    /** Cosine similarity (0..1) a chunk needs to count as relevant. Tune per embedding model. */
    private double minScore = 0.55;

    /** auto = pgvector when PostgreSQL has it, otherwise an in-Java scan; pgvector or scan force one. */
    private String vectorStore = "auto";
    private int scanMaxChunks = 20_000;

    /** false runs ingestion inline (used by tests). */
    private boolean async = true;

    public boolean isEmbeddingEnabled() {
        return "google-genai".equalsIgnoreCase(embeddingProvider) && geminiApiKey != null && !geminiApiKey.isBlank();
    }

    public String getEmbeddingProvider() { return embeddingProvider; }
    public void setEmbeddingProvider(String v) { this.embeddingProvider = v; }
    public String getGeminiApiKey() { return geminiApiKey; }
    public void setGeminiApiKey(String v) { this.geminiApiKey = v; }
    public String getGeminiBaseUrl() { return geminiBaseUrl; }
    public void setGeminiBaseUrl(String v) { this.geminiBaseUrl = v; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String v) { this.embeddingModel = v; }
    public int getEmbeddingDimensions() { return embeddingDimensions; }
    public void setEmbeddingDimensions(int v) { this.embeddingDimensions = v; }
    public int getEmbedBatchSize() { return embedBatchSize; }
    public void setEmbedBatchSize(int v) { this.embedBatchSize = v; }
    public int getEmbedTimeoutSeconds() { return embedTimeoutSeconds; }
    public void setEmbedTimeoutSeconds(int v) { this.embedTimeoutSeconds = v; }
    public int getEmbedPerCompanyPerDay() { return embedPerCompanyPerDay; }
    public void setEmbedPerCompanyPerDay(int v) { this.embedPerCompanyPerDay = v; }
    public int getChunkChars() { return chunkChars; }
    public void setChunkChars(int v) { this.chunkChars = v; }
    public int getChunkOverlap() { return chunkOverlap; }
    public void setChunkOverlap(int v) { this.chunkOverlap = v; }
    public int getMaxDocumentChars() { return maxDocumentChars; }
    public void setMaxDocumentChars(int v) { this.maxDocumentChars = v; }
    public int getMaxChunksPerDocument() { return maxChunksPerDocument; }
    public void setMaxChunksPerDocument(int v) { this.maxChunksPerDocument = v; }
    public int getTopK() { return topK; }
    public void setTopK(int v) { this.topK = v; }
    public double getMinScore() { return minScore; }
    public void setMinScore(double v) { this.minScore = v; }
    public String getVectorStore() { return vectorStore; }
    public void setVectorStore(String v) { this.vectorStore = v; }
    public int getScanMaxChunks() { return scanMaxChunks; }
    public void setScanMaxChunks(int v) { this.scanMaxChunks = v; }
    public boolean isAsync() { return async; }
    public void setAsync(boolean v) { this.async = v; }
}
