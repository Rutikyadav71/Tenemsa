package com.tenemsa.platform.knowledge.vector;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * PostgreSQL with the pgvector extension: cosine distance (&lt;=&gt;) served by an HNSW index. Score is
 * 1 - distance, which equals cosine similarity. The vector column is created by Flyway V7 only when the extension exists.
 */
public class PgVectorStore implements VectorStore {

    private final JdbcTemplate jdbc;

    public PgVectorStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public String name() { return "pgvector"; }

    @Override
    public void store(Long companyId, Long chunkId, float[] vector, String model) {
        jdbc.update("UPDATE kb_chunks SET embedding = CAST(? AS vector), embedding_model = ? WHERE id = ? AND company_id = ?",
                VectorStore.literal(vector), model, chunkId, companyId);
    }

    @Override
    public List<Hit> search(Long companyId, float[] query, String model, int limit, boolean includeManagement) {
        String literal = VectorStore.literal(query);
        String sql = "SELECT c.id, c.document_id, d.title, c.content, 1 - (c.embedding <=> CAST(? AS vector)) AS score "
                + "FROM kb_chunks c JOIN kb_documents d ON d.id = c.document_id AND d.company_id = c.company_id "
                + "WHERE c.company_id = ? AND d.status = 'READY' AND c.embedding_model = ? AND c.embedding IS NOT NULL "
                + (includeManagement ? "" : "AND d.audience = 'ALL_STAFF' ")
                + "ORDER BY c.embedding <=> CAST(? AS vector) LIMIT ?";
        return jdbc.query(sql, (rs, i) -> new Hit(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getDouble(5)),
                literal, companyId, model, literal, limit);
    }
}
