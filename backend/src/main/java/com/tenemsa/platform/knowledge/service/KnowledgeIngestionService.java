package com.tenemsa.platform.knowledge.service;

import com.tenemsa.platform.audit.model.AuditAction;
import com.tenemsa.platform.audit.service.AuditService;
import com.tenemsa.platform.common.ResourceNotFoundException;
import com.tenemsa.platform.employee.model.User;
import com.tenemsa.platform.employee.repository.UserRepository;
import com.tenemsa.platform.employee.service.EmployeeNameLookup;
import com.tenemsa.platform.knowledge.config.KbProperties;
import com.tenemsa.platform.knowledge.dto.KbDocumentResponse;
import com.tenemsa.platform.knowledge.embedding.EmbeddingClient;
import com.tenemsa.platform.knowledge.embedding.EmbeddingGateway;
import com.tenemsa.platform.knowledge.model.DocumentAudience;
import com.tenemsa.platform.knowledge.model.DocumentStatus;
import com.tenemsa.platform.knowledge.model.KbChunk;
import com.tenemsa.platform.knowledge.model.KbDocument;
import com.tenemsa.platform.knowledge.repository.KbChunkRepository;
import com.tenemsa.platform.knowledge.repository.KbDocumentRepository;
import com.tenemsa.platform.knowledge.vector.VectorStore;
import com.tenemsa.platform.security.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * Adds documents to the company knowledge base and keeps them indexed. Adding is quick (extract, chunk, save as
 * PROCESSING); embedding runs afterwards on a background thread and ends in READY or FAILED. Everything is scoped to
 * the caller's company; the background thread receives the company id explicitly because it has no logged-in user.
 */
@Service
public class KnowledgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionService.class);
    private static final long STUCK_AFTER_MINUTES = 10;

    private final KbProperties props;
    private final TextExtractor extractor;
    private final KbDocumentRepository documents;
    private final KbChunkRepository chunks;
    private final EmbeddingGateway embeddings;
    private final VectorStore vectors;
    private final UserRepository users;
    private final EmployeeNameLookup names;
    private final AuthContext authContext;
    private final AuditService audit;
    private final ExecutorService executor;
    private final TransactionTemplate tx;

    public KnowledgeIngestionService(KbProperties props, TextExtractor extractor, KbDocumentRepository documents,
                                     KbChunkRepository chunks, EmbeddingGateway embeddings, VectorStore vectors,
                                     UserRepository users, EmployeeNameLookup names, AuthContext authContext,
                                     AuditService audit, ExecutorService kbExecutor, PlatformTransactionManager txManager) {
        this.props = props;
        this.extractor = extractor;
        this.documents = documents;
        this.chunks = chunks;
        this.embeddings = embeddings;
        this.vectors = vectors;
        this.users = users;
        this.names = names;
        this.authContext = authContext;
        this.audit = audit;
        this.executor = kbExecutor;
        this.tx = new TransactionTemplate(txManager);
    }

    private Long cid() { return authContext.currentCompanyId(); }

    private User caller() {
        return users.findByEmailAndCompanyId(authContext.currentEmail(), cid())
                .orElseThrow(() -> new AccessDeniedException("Unknown user."));
    }

    // ---- reading ---------------------------------------------------------------------------

    public List<KbDocumentResponse> list() {
        List<KbDocument> rows = documents.findByCompanyIdOrderByCreatedAtDescIdDesc(cid());
        Map<Integer, String> who = names.namesFor(cid(), rows.stream().map(KbDocument::getUploadedByEmpId).toList());
        return rows.stream().map(d -> KbDocumentResponse.from(d, who.get(d.getUploadedByEmpId()), embeddings.modelName())).toList();
    }

    public KbDocumentResponse get(Long id) {
        KbDocument d = require(id);
        return KbDocumentResponse.from(d, names.namesFor(cid(), List.of(d.getUploadedByEmpId() == null ? -1 : d.getUploadedByEmpId()))
                .get(d.getUploadedByEmpId()), embeddings.modelName());
    }

    private KbDocument require(Long id) {
        return documents.findByIdAndCompanyId(id, cid()).orElseThrow(() -> new ResourceNotFoundException("Document not found."));
    }

    // ---- adding ----------------------------------------------------------------------------

    public KbDocumentResponse addFile(MultipartFile file, String title, DocumentAudience audience) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a file to upload.");
        String name = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename().replaceAll("[\\r\\n]", " ");
        byte[] bytes;
        try { bytes = file.getBytes(); } catch (IOException e) { throw new IllegalArgumentException("The file could not be read."); }
        String text = extractor.extract(name, bytes);
        if (text.isBlank()) throw new IllegalArgumentException("No text was found in this file. Scanned documents (images) are not supported.");
        String fallbackTitle = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        return add(title == null || title.isBlank() ? fallbackTitle : title.strip(), name, audience, text);
    }

    public KbDocumentResponse addText(String title, DocumentAudience audience, String text) {
        return add(title.strip(), null, audience, TextExtractor.normalise(text));
    }

    private KbDocumentResponse add(String title, String fileName, DocumentAudience audience, String text) {
        Long companyId = cid();
        User me = caller();
        if (title.length() > 200) title = title.substring(0, 200);
        if (text.length() < 50) throw new IllegalArgumentException("The document is too short to be useful (50 characters at least).");
        if (text.length() > props.getMaxDocumentChars()) {
            throw new IllegalArgumentException("The document is too long (" + text.length() + " characters). The limit is "
                    + props.getMaxDocumentChars() + "; split it into parts.");
        }
        embeddings.requireReady(companyId);                         // 503 now, instead of a document that can never finish
        String hash = sha256(text);
        documents.findFirstByCompanyIdAndContentHash(companyId, hash).ifPresent(existing -> {
            throw new IllegalArgumentException("This text was already added as \"" + existing.getTitle() + "\".");
        });
        List<String> pieces = TextChunker.chunk(text, props.getChunkChars(), props.getChunkOverlap());
        if (pieces.size() > props.getMaxChunksPerDocument()) {
            throw new IllegalArgumentException("The document is too large to index (" + pieces.size() + " passages, limit "
                    + props.getMaxChunksPerDocument() + "). Split it into parts.");
        }
        final String finalTitle = title;
        Long docId = tx.execute(s -> {
            KbDocument d = new KbDocument();
            d.setCompanyId(companyId);
            d.setTitle(finalTitle);
            d.setFileName(fileName == null ? null : (fileName.length() > 255 ? fileName.substring(0, 255) : fileName));
            d.setAudience(audience);
            d.setContentHash(hash);
            d.setCharCount(text.length());
            d.setChunkCount(pieces.size());
            d.setUploadedByEmpId(me.getEmpId());
            d = documents.save(d);
            for (int i = 0; i < pieces.size(); i++) {
                KbChunk c = new KbChunk();
                c.setCompanyId(companyId);
                c.setDocumentId(d.getId());
                c.setChunkIndex(i);
                c.setContent(pieces.get(i));
                chunks.save(c);
            }
            return d.getId();
        });
        audit.recordCurrent(AuditAction.KB_DOCUMENT_ADDED, "KB_DOCUMENT", String.valueOf(docId), "Added \"" + finalTitle + "\" to the knowledge base");
        schedule(companyId, docId, me.getEmpId());
        return get(docId);
    }

    // ---- reindexing and deleting -------------------------------------------------------------

    /** Re-embeds a document with the current model (after a model change, or to retry a failure). */
    public KbDocumentResponse reindex(Long id) {
        KbDocument d = require(id);
        boolean stuck = d.getStatus() == DocumentStatus.PROCESSING && d.getUpdatedAt().isAfter(LocalDateTime.now().minusMinutes(STUCK_AFTER_MINUTES));
        if (stuck) throw new IllegalArgumentException("This document is still being indexed. Try again in a few minutes.");
        embeddings.requireReady(cid());
        tx.executeWithoutResult(s -> {
            KbDocument fresh = documents.findByIdAndCompanyId(id, cid()).orElseThrow();
            fresh.setStatus(DocumentStatus.PROCESSING);
            fresh.setErrorMessage(null);
            documents.save(fresh);
        });
        audit.recordCurrent(AuditAction.KB_DOCUMENT_REINDEXED, "KB_DOCUMENT", String.valueOf(id), "Re-indexed \"" + d.getTitle() + "\"");
        schedule(cid(), id, caller().getEmpId());
        return get(id);
    }

    public void delete(Long id) {
        KbDocument d = require(id);
        tx.executeWithoutResult(s -> {
            chunks.deleteAllForDocument(cid(), id);
            documents.delete(d);
        });
        audit.recordCurrent(AuditAction.KB_DOCUMENT_DELETED, "KB_DOCUMENT", String.valueOf(id), "Deleted \"" + d.getTitle() + "\" from the knowledge base");
    }

    // ---- indexing (background) ---------------------------------------------------------------

    private void schedule(Long companyId, Long documentId, Integer empId) {
        if (!props.isAsync()) { index(companyId, documentId, empId); return; }
        try {
            executor.execute(() -> index(companyId, documentId, empId));
        } catch (RejectedExecutionException e) {
            fail(companyId, documentId, "The indexing queue is full. Use Re-index in a few minutes.");
        }
    }

    /** Never throws: every outcome ends as READY or FAILED on the document row. */
    void index(Long companyId, Long documentId, Integer empId) {
        try {
            List<KbChunk> rows = chunks.findByCompanyIdAndDocumentIdOrderByChunkIndexAsc(companyId, documentId);
            List<String> texts = rows.stream().map(KbChunk::getContent).toList();
            List<float[]> vectorsOut = embeddings.embed(companyId, empId, texts, EmbeddingClient.Task.DOCUMENT);
            String model = embeddings.modelName();
            tx.executeWithoutResult(s -> {
                for (int i = 0; i < rows.size(); i++) vectors.store(companyId, rows.get(i).getId(), vectorsOut.get(i), model);
                KbDocument d = documents.findByIdAndCompanyId(documentId, companyId).orElse(null);
                if (d == null) return;                              // deleted while indexing
                d.setStatus(DocumentStatus.READY);
                d.setErrorMessage(null);
                d.setEmbeddingModel(model);
                d.setChunkCount(rows.size());
                documents.save(d);
            });
        } catch (Exception e) {
            log.warn("Indexing document {} failed: {}", documentId, e.toString());
            fail(companyId, documentId, e instanceof RuntimeException && e.getMessage() != null && e.getMessage().length() <= 300
                    ? e.getMessage() : "Indexing failed. Use Re-index to try again.");
        }
    }

    private void fail(Long companyId, Long documentId, String message) {
        try {
            tx.executeWithoutResult(s -> documents.findByIdAndCompanyId(documentId, companyId).ifPresent(d -> {
                d.setStatus(DocumentStatus.FAILED);
                d.setErrorMessage(message.length() > 300 ? message.substring(0, 300) : message);
                documents.save(d);
            }));
        } catch (Exception e) {
            log.warn("Could not mark document {} as failed: {}", documentId, e.toString());
        }
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
