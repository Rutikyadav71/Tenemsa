package com.tenemsa.platform.knowledge.vector;

import com.tenemsa.platform.knowledge.config.KbProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Works on every database: vectors are stored as JSON text and compared in Java. Vectors are unit length, so the
 * dot product is the cosine similarity. Cost grows with the number of chunks, so it is capped (kb.scan-max-chunks);
 * beyond that, use PostgreSQL with pgvector.
 */
public class ScanVectorStore implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(ScanVectorStore.class);

    private final JdbcTemplate jdbc;
    private final KbProperties props;

    public ScanVectorStore(JdbcTemplate jdbc, KbProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    @Override public String name() { return "scan"; }

    @Override
    public void store(Long companyId, Long chunkId, float[] vector, String model) {
        jdbc.update("UPDATE kb_chunks SET embedding_json = ?, embedding_model = ? WHERE id = ? AND company_id = ?",
                VectorStore.literal(vector), model, chunkId, companyId);
    }

    @Override
    public List<Hit> search(Long companyId, float[] query, String model, int limit, boolean includeManagement) {
        String sql = "SELECT c.id, c.document_id, d.title, c.content, c.embedding_json FROM kb_chunks c "
                + "JOIN kb_documents d ON d.id = c.document_id AND d.company_id = c.company_id "
                + "WHERE c.company_id = ? AND d.status = 'READY' AND c.embedding_model = ? AND c.embedding_json IS NOT NULL "
                + (includeManagement ? "" : "AND d.audience = 'ALL_STAFF' ")
                + "LIMIT ?";
        int cap = props.getScanMaxChunks();
        PriorityQueue<Hit> best = new PriorityQueue<>(Comparator.comparingDouble(Hit::score)); // min-heap of the top `limit`
        int[] seen = {0};
        jdbc.query(sql, rs -> {
            seen[0]++;
            float[] v = parse(rs.getString(5), query.length);
            if (v == null) return;
            double score = 0;
            for (int i = 0; i < v.length; i++) score += (double) v[i] * query[i];
            if (best.size() < limit) best.add(new Hit(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), score));
            else if (score > best.peek().score()) {
                best.poll();
                best.add(new Hit(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), score));
            }
        }, companyId, model, cap);
        if (seen[0] >= cap) log.warn("Knowledge search for company {} hit the scan cap of {} chunks; use PostgreSQL with pgvector.", companyId, cap);
        List<Hit> out = new ArrayList<>(best);
        out.sort(Comparator.comparingDouble(Hit::score).reversed());
        return out;
    }

    static float[] parse(String json, int expected) {
        if (json == null || json.length() < 2) return null;
        float[] v = new float[expected];
        int n = 0, start = 1;
        for (int i = 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == ',' || ch == ']') {
                if (n >= expected) return null;
                try { v[n++] = Float.parseFloat(json.substring(start, i)); } catch (NumberFormatException e) { return null; }
                start = i + 1;
            }
        }
        return n == expected ? v : null;
    }
}
