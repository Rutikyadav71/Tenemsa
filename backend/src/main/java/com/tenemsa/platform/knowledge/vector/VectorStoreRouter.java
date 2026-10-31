package com.tenemsa.platform.knowledge.vector;

import com.tenemsa.platform.knowledge.config.KbProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

/**
 * Chooses the store on first use (after Flyway has run): pgvector when the database is PostgreSQL and the
 * kb_chunks.embedding column exists, otherwise the portable scan. kb.vector-store can force either.
 */
@Component
public class VectorStoreRouter implements VectorStore {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreRouter.class);

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final KbProperties props;
    private volatile VectorStore delegate;

    public VectorStoreRouter(JdbcTemplate jdbc, DataSource dataSource, KbProperties props) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.props = props;
    }

    private VectorStore store() {
        VectorStore d = delegate;
        if (d == null) {
            synchronized (this) {
                if (delegate == null) delegate = choose();
                d = delegate;
            }
        }
        return d;
    }

    private VectorStore choose() {
        String mode = props.getVectorStore() == null ? "auto" : props.getVectorStore().toLowerCase();
        if (mode.equals("scan")) return announce(new ScanVectorStore(jdbc, props), "forced by kb.vector-store");
        boolean pg = false, column = false;
        try (Connection c = dataSource.getConnection()) {
            pg = "PostgreSQL".equalsIgnoreCase(c.getMetaData().getDatabaseProductName());
        } catch (Exception e) {
            log.warn("Could not inspect the database: {}", e.toString());
        }
        if (pg) {
            try {
                Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'kb_chunks' AND column_name = 'embedding'", Integer.class);
                column = n != null && n > 0;
            } catch (Exception e) {
                log.warn("Could not look for the pgvector column: {}", e.toString());
            }
        }
        if (pg && column) return announce(new PgVectorStore(jdbc), "PostgreSQL with pgvector");
        if (mode.equals("pgvector")) {
            throw new IllegalStateException("kb.vector-store=pgvector but PostgreSQL has no kb_chunks.embedding column. "
                    + "Install the pgvector extension and re-run migration V7, or use kb.vector-store=auto.");
        }
        return announce(new ScanVectorStore(jdbc, props), pg ? "pgvector is not installed in this database" : "database is not PostgreSQL");
    }

    private VectorStore announce(VectorStore s, String why) {
        log.info("Knowledge base vector store: {} ({})", s.name(), why);
        return s;
    }

    @Override public String name() { return store().name(); }

    @Override
    public void store(Long companyId, Long chunkId, float[] vector, String model) { store().store(companyId, chunkId, vector, model); }

    @Override
    public List<Hit> search(Long companyId, float[] query, String model, int limit, boolean includeManagement) {
        return store().search(companyId, query, model, limit, includeManagement);
    }
}
