package com.tenemsa.platform.knowledge.vector;

import java.util.List;

/**
 * Where chunk vectors live and how they are searched. Two implementations: pgvector (PostgreSQL, index-backed)
 * and a plain scan (any database, fine for a company's worth of documents). Every method is tenant-scoped.
 */
public interface VectorStore {

    record Hit(Long chunkId, Long documentId, String title, String content, double score) {}

    String name();

    /** Saves the vector of one chunk and the model that produced it. */
    void store(Long companyId, Long chunkId, float[] vector, String model);

    /**
     * Best matches by cosine similarity among READY documents of the company that were embedded with {@code model},
     * best first. Management-only documents are included only when {@code includeManagement} is true.
     */
    List<Hit> search(Long companyId, float[] query, String model, int limit, boolean includeManagement);

    /** "[0.1,0.2,...]": a JSON array that is also a valid pgvector literal. */
    static String literal(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 9 + 2).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
