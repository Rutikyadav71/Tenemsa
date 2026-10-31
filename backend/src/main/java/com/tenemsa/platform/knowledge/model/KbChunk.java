package com.tenemsa.platform.knowledge.model;

import jakarta.persistence.*;

/**
 * A passage of a document. The vector is written with SQL by the VectorStore: into embedding_json on any database,
 * and additionally into the pgvector column "embedding" (not mapped here) on PostgreSQL.
 */
@Entity
@Table(name = "kb_chunks", indexes = {
        @Index(name = "idx_kb_chunks_document", columnList = "document_id, chunk_index"),
        @Index(name = "idx_kb_chunks_company", columnList = "company_id")
})
public class KbChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "embedding_json", columnDefinition = "TEXT")
    private String embeddingJson;

    @Column(name = "embedding_model", length = 80)
    private String embeddingModel;

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long v) { this.companyId = v; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long v) { this.documentId = v; }
    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int v) { this.chunkIndex = v; }
    public String getContent() { return content; }
    public void setContent(String v) { this.content = v; }
    public String getEmbeddingJson() { return embeddingJson; }
    public void setEmbeddingJson(String v) { this.embeddingJson = v; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String v) { this.embeddingModel = v; }
}
